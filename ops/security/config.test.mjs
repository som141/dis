import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, mkdirSync, writeFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, dirname, resolve } from 'node:path';
import { execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { collectTextFiles, scanExposure, secretCandidates, runPreflight } from './preflight.mjs';

const root = fileURLToPath(new URL('../../', import.meta.url));

test('nested chunks and source maps are inspected without revealing matched secrets', () => {
  const dir = mkdtempSync(join(tmpdir(), 'dis-secret-test-'));
  try {
    mkdirSync(join(dir, 'assets/chunks'), { recursive: true });
    const secret = 'test-fixture-only-secret-123456789';
    writeFileSync(join(dir, 'assets/chunks/app.js.map'), JSON.stringify({ sourcesContent: [secret] }));
    const candidates = secretCandidates({ FINNHUB_API_KEY: secret, WEBHOOK_URL: secret, DISCORD_TOKEN: 'replace-with-your-token', GRAFANA_ALERT_NOOP_WEBHOOK_URL: 'http://127.0.0.1:9/disabled' });
    assert.deepEqual(Object.keys(candidates).sort(), ['FINNHUB_API_KEY', 'WEBHOOK_URL']);
    const findings = scanExposure(candidates, collectTextFiles(dir));
    assert.equal(findings.length, 2);
    assert.ok(!JSON.stringify(findings).includes(secret));
    assert.deepEqual(collectTextFiles(join(dir, 'missing')), []);
  } finally {
    assert.equal(dirname(resolve(dir)), resolve(tmpdir()));
    assert.ok(dir.startsWith(join(tmpdir(), 'dis-secret-test-')));
    rmSync(dir, { recursive: true, force: true });
  }
});

test('private runtime files are ignored while env examples remain visible', () => {
  const paths = ['.env.production', 'apps/maple-portal/.env.staging', 'ngrok.env', 'secrets/key', '.docker-secrets/key', 'capture.har', 'backup.dump', 'client.pem'];
  const ignored = execFileSync('git', ['check-ignore', '--no-index', '--stdin'], { cwd: root, input: paths.join('\n'), encoding: 'utf8' }).trim().split(/\r?\n/);
  assert.deepEqual(ignored, paths);
  try { execFileSync('git', ['check-ignore', '--no-index', '.env.example'], { cwd: root }); assert.fail('example must not be ignored'); }
  catch (error) { assert.equal(error.status, 1); }
});

test('resolved Compose example publishes only host-local ports including observability', () => {
  const config = JSON.parse(execFileSync('docker', ['compose', '--env-file', '.env.example', '--profile', 'maple', '--profile', 'observability', 'config', '--format', 'json'], { cwd: root, encoding: 'utf8', maxBuffer: 4 * 1024 * 1024 }));
  for (const [name, service] of Object.entries(config.services)) {
    for (const port of service.ports || []) assert.ok(['127.0.0.1', '::1'].includes(port.host_ip), `${name}: host-local binding required`);
  }
  for (const name of ['prometheus', 'loki', 'alloy', 'grafana', 'redis-exporter', 'postgres-exporter']) assert.ok(config.services[name].ports.length);
  assert.equal(config.services['maple-portal'].ports, undefined);
  assert.equal(String(config.services['maple-portal'].environment.MAPLE_PORTAL_USERS), '4');
});

test('production check rejects development passwords and missing config', () => {
  const findings = runPreflight({ repoRoot: root, envFile: join(root, '.env.example'), production: true });
  assert.ok(findings.some(f => f.priority === 'P0' && f.detail.includes('POSTGRES_PASSWORD')));
  assert.ok(findings.some(f => f.priority === 'P0' && f.detail.includes('GRAFANA_ADMIN_PASSWORD')));
  assert.ok(!findings.some(f => f.name === '보유 비밀값 노출'), 'public example sentinels are not credentials');
  const missing = runPreflight({ repoRoot: root, envFile: join(root, 'not-a-real-env-file'), production: true });
  assert.ok(missing.some(f => f.priority === 'P0' && f.name === '설정 파일'));
});
