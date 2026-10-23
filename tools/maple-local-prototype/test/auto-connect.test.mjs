import { test } from 'node:test';
import assert from 'node:assert/strict';
import { BrowserAutoConnect } from '../src/auto-connect.mjs';
import { PrototypeEngine } from '../src/domain.mjs';

function setup({ names = ['에반이였솜'], remaining = 100 } = {}) {
  const clock = { now:1000000 }, calls = [];
  let ready = false, session = false, probeStatus = 200, blockedProbe;
  const wire = {
    connected:true, epoch:1, onChange:() => {}, status:() => ({ clientReady:ready }),
    scope() { const epoch = this.epoch; return { get connected() { return epoch === wire.epoch && wire.connected; }, request:cmd => wire.request(cmd) }; },
    async request(cmd) {
      calls.push({ method:cmd.method, path:new URL(cmd.url).pathname });
      assert.equal(cmd.method, 'GET', 'automatic connection may only perform reads');
      const path = new URL(cmd.url).pathname;
      if (path.endsWith('/daily-limit')) {
        if (blockedProbe) await blockedProbe;
        if (!session || probeStatus !== 200) {
          // Model the real transport invalidating its scope after a 401.
          ++this.epoch; this.onChange();
          return { ok:false, status:probeStatus === 200 ? 401 : probeStatus };
        }
        return { ok:true, bodyText:JSON.stringify({ search:{ limit:100, remaining } }) };
      }
      if (path === '/v1/accounts') return { ok:true, bodyText:JSON.stringify({ accounts:[{ accountId:111 }] }) };
      if (path.endsWith('/characters')) return { ok:true, bodyText:JSON.stringify({ characters:path.includes('/gameWorlds/0/') ? names.map((name, i) => ({ characterId:222+i, characterName:name, level:280 })) : [] }) };
      assert.fail('Unexpected request');
    },
    close:async () => {},
  };
  const engine = new PrototypeEngine(wire, { loadSnapshot:async () => { throw new Error('fixture key absent'); } });
  wire.onChange = () => engine.connectionChanged();
  const auto = new BrowserAutoConnect(engine, wire, { now:() => clock.now });
  return { wire, engine, auto, calls, clock,
    login() { ready = true; session = true; }, clientReady() { ready = true; },
    status(n) { probeStatus = n; }, block(promise) { blockedProbe = promise; },
  };
}

test('manual login automatically selects only the owned default character without a key or search POST', async () => {
  const f = setup({ remaining:0 });
  await f.auto.tick(); assert.equal(f.calls.length, 0);
  f.login(); await f.auto.tick();
  assert.equal(f.auto.state().phase, 'ready');
  assert.equal(f.engine.character.name, '에반이였솜');
  assert.equal(f.engine.key, ''); assert.equal(f.engine.result, null); assert.equal(f.engine.watch, null);
  const count = f.calls.length;
  await f.auto.tick(); assert.equal(f.calls.length, count, 'do not relist every world after connection');
  assert.equal(f.calls.every(c => c.method === 'GET'), true);
});

test('missing or ambiguous default requires manual choice and never substitutes another owned character', async () => {
  for (const names of [['다른캐릭터'], ['에반이였솜', '에반이였솜']]) {
    const f = setup({ names }); f.login(); await f.auto.tick();
    assert.equal(f.auto.state().phase, 'choose-character'); assert.equal(f.engine.character, null);
    const count = f.calls.length; f.clock.now += 100000; await f.auto.tick(); assert.equal(f.calls.length, count);
    await f.engine.selectCharacter(222); await f.auto.tick();
    assert.equal(f.auto.state().phase, 'ready', 'manual selection stays usable');
  }
});

test('login waits use capped backoff even when authentication errors invalidate the epoch', async () => {
  const f = setup(); f.clientReady();
  await f.auto.tick(); assert.equal(f.calls.length, 1); assert.equal(f.auto.state().nextAttemptAt, f.clock.now + 5000);
  await f.auto.tick(); assert.equal(f.calls.length, 1);
  f.clock.now += 5000; await f.auto.tick(); assert.equal(f.calls.length, 2);
  assert.equal(f.auto.state().nextAttemptAt, f.clock.now + 10000);
  for (let i = 0; i < 6; ++i) { f.clock.now = f.auto.state().nextAttemptAt; await f.auto.tick(); }
  assert.equal(f.auto.state().nextAttemptAt, f.clock.now + 60000);
  f.login(); f.clock.now = f.auto.state().nextAttemptAt; await f.auto.tick();
  assert.equal(f.engine.character.name, '에반이였솜');
});

test('automatic connection cannot overlap manual work and rejects a changed session during discovery', async () => {
  const f = setup(); f.login(); f.engine.busy = true;
  await f.auto.tick(); assert.equal(f.calls.length, 0); f.engine.busy = false;
  let release; f.block(new Promise(resolve => { release = resolve; }));
  const active = f.auto.tick(); await f.auto.tick(); assert.equal(f.calls.length, 1);
  ++f.wire.epoch; f.wire.onChange(); release(); await active;
  assert.equal(f.engine.character, null); assert.equal(f.engine.characters.length, 0);
  f.block(null); f.clock.now = f.auto.state().nextAttemptAt; await f.auto.tick();
  assert.equal(f.engine.character.name, '에반이였솜');
});

test('HTTP 426 stops automatic retries until the official browser connection changes', async () => {
  const f = setup(); f.login(); f.status(426); await f.auto.tick();
  assert.equal(f.auto.state().phase, 'upgrade-required');
  f.clock.now += 100000; await f.auto.tick(); assert.equal(f.calls.length, 1);
  f.status(200); ++f.wire.epoch; f.wire.onChange(); await f.auto.tick();
  assert.equal(f.auto.state().phase, 'ready');
});
