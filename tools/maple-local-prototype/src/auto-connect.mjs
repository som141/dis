import { DEFAULT_CHARACTER } from './domain.mjs';

const SESSION_PROBE = { url:'https://api.mskr.nexon.com/v1/market/web/daily-limit', method:'GET' };

// Only read-only session/owned-character checks run here. A successful login
// never creates a search, starts a watch, exports cookies, or fills credentials.
export class BrowserAutoConnect {
  constructor(engine, wire, { now = Date.now, retryMs = 5000, maxRetryMs = 60000 } = {}) {
    this.engine = engine; this.wire = wire; this.now = now;
    this.retryMs = retryMs; this.maxRetryMs = maxRetryMs;
    this.epoch = wire.epoch; this.running = false; this.failures = 0;
    this.phase = 'waiting-login'; this.error = null; this.nextAttemptAt = 0; this.lastConnectedAt = null;
  }
  state() { return { phase:this.phase, error:this.error, nextAttemptAt:this.nextAttemptAt || null, lastConnectedAt:this.lastConnectedAt }; }
  async tick() {
    if (this.running || this.engine.busy) return;
    if (this.epoch !== this.wire.epoch) {
      this.epoch = this.wire.epoch; this.failures = 0; this.nextAttemptAt = 0;
      this.phase = 'waiting-login'; this.error = null;
    }
    if (!this.wire.connected || !this.wire.status().clientReady) {
      this.phase = 'waiting-login'; return;
    }
    if (this.engine.character) {
      if (this.phase !== 'ready') this.lastConnectedAt = new Date(this.now()).toISOString();
      this.phase = 'ready'; this.error = null; this.nextAttemptAt = 0; this.failures = 0; return;
    }
    if (this.wire.status().upgradeRequired) { this.phase = 'upgrade-required'; this.error = 'UPGRADE_REQUIRED'; return; }
    if (['choose-character', 'upgrade-required'].includes(this.phase) || this.now() < this.nextAttemptAt) return;
    this.running = true; this.phase = 'connecting';
    try {
      await this.engine.run(async () => {
        // The first-party site must create its own session during manual login.
        // Do not repeatedly list every world or exchange/rotate session tokens.
        const epoch = this.wire.epoch;
        const probe = await this.wire.request(SESSION_PROBE);
        if (!probe.ok) {
          const e = new Error('Session not ready');
          e.code = probe.status === 426 ? 'UPGRADE_REQUIRED' : 'LOGIN_REQUIRED'; throw e;
        }
        this.engine.guard(epoch);
        await this.engine.connect();
        const matches = this.engine.characters.filter(c => c.name === DEFAULT_CHARACTER);
        if (matches.length !== 1) {
          this.phase = 'choose-character';
          this.error = matches.length ? 'AMBIGUOUS_CHARACTER' : 'DEFAULT_CHARACTER_MISSING'; return;
        }
        await this.engine.selectCharacter(matches[0].characterId);
        this.phase = 'ready'; this.error = null; this.failures = 0; this.nextAttemptAt = 0;
        this.lastConnectedAt = new Date(this.now()).toISOString();
      });
    } catch (e) {
      // Keep only our own diagnostic codes, never a provider message or body.
      this.error = e.code === 'UPGRADE_REQUIRED' || this.wire.status().upgradeRequired ? 'UPGRADE_REQUIRED' : 'LOGIN_REQUIRED';
      this.phase = this.error === 'UPGRADE_REQUIRED' ? 'upgrade-required' : 'waiting-login';
      this.nextAttemptAt = this.now() + Math.min(this.maxRetryMs, this.retryMs * 2 ** Math.min(this.failures++, 4));
    } finally {
      // 401/403 can invalidate the epoch inside the probe. Preserve backoff
      // instead of treating that invalidation as a new login on the next tick.
      this.epoch = this.wire.epoch; this.running = false;
    }
  }
}
