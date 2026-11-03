import { test } from 'node:test';
import assert from 'node:assert/strict';
import { once } from 'node:events';
import { request as httpRequest } from 'node:http';
import { WebSocket } from 'ws';
import { createApp } from '../src/server.mjs';
import { allowedCommand } from '../src/bridge.mjs';

test('local HTTP API blocks foreign Origin, DNS rebinding, missing token and non-JSON; never echoes a key', async t => {
  const app = createApp({ port:0 }); const addr = await app.listen();
  t.after(() => app.close());
  const base = `http://127.0.0.1:${addr.port}`;
  const boot = await (await fetch(base + '/api/bootstrap')).json();
  const post = (path, body, extra = {}) => fetch(base + path, { method:'POST', headers:{ Origin:base, 'X-Local-Token':boot.csrf, 'Content-Type':'application/json', ...extra }, body:JSON.stringify(body) });
  assert.equal((await post('/api/key', { key:'' }, { Origin:'https://example.org' })).status, 403);
  assert.equal((await post('/api/key', { key:'' }, { 'X-Local-Token':'' })).status, 403);
  assert.equal((await post('/api/key', { key:'' }, { 'Content-Type':'text/plain' })).status, 415);
  const reboundStatus = await new Promise((resolve, reject) => {
    const req = httpRequest(base, { headers:{ Host:'evil.test' } }, res => { res.resume(); resolve(res.statusCode); });
    req.on('error', reject); req.end();
  });
  assert.equal(reboundStatus, 403);
  const key = 'fixture-only-local-api-key';
  assert.equal((await post('/api/key', { key })).status, 200);
  assert.equal((await (await fetch(base + '/api/state')).text()).includes(key), false);
  const noBrowser = await post('/api/connect', {});
  assert.equal(noBrowser.status, 409); assert.equal((await noBrowser.json()).code, 'NO_BROWSER');
  const html = await fetch(base);
  assert.equal(html.status, 200); assert.ok(html.headers.get('content-security-policy').includes("frame-ancestors 'none'"));
  assert.ok((await html.text()).includes('에반이였솜'));
});
test('only paired extension can connect; disconnect rejects pending calls and invalidates identity', async t => {
  const app = createApp({ port:0 }); const addr = await app.listen();
  t.after(() => app.close());
  const base = `http://127.0.0.1:${addr.port}`;
  const boot = await (await fetch(base + '/api/bootstrap')).json();
  const bad = new WebSocket(`ws://127.0.0.1:${addr.port}/bridge?token=${boot.pairing}`, { origin:'http://evil.test' });
  await new Promise(resolve => { bad.once('unexpected-response', (_req, res) => { assert.equal(res.statusCode, 403); bad.terminate(); resolve(); }); bad.on('error', () => {}); });
  const ws = new WebSocket(`ws://127.0.0.1:${addr.port}/bridge?token=${boot.pairing}`, { origin:`chrome-extension://${'a'.repeat(32)}` });
  await once(ws, 'open');
  const scope = app.bridge.scope();
  ws.on('message', raw => { const cmd = JSON.parse(String(raw)); ws.send(JSON.stringify({ id:cmd.id, ok:true, status:200, bodyText:'{}' })); });
  assert.equal((await scope.request({ type:'fetch', url:'https://api.mskr.nexon.com/v1/market/web/daily-limit', method:'GET' })).ok, true);
  assert.equal((await scope.request({ type:'fetch', url:'https://api.mskr.nexon.com/v1/buy', method:'POST' })).code, 'FORBIDDEN_URL');
  const closed = once(ws, 'close'); ws.close(); await closed;
  assert.equal(scope.connected, false); assert.equal(app.engine.state().character, null);
});
test('bridge allowlist permits search but blocks purchases, cookie access and arbitrary destinations', () => {
  const cmd = (path, method='GET') => ({ url:'https://api.mskr.nexon.com/v1' + path, method });
  assert.equal(allowedCommand(cmd('/market/web/items/searches/tool-tip', 'POST')), true);
  assert.equal(allowedCommand(cmd('/market/web/items/searches/sold/tool-tip', 'POST')), true);
  assert.equal(allowedCommand(cmd('/market/web/items/searches/abc/tool-tip')), true);
  assert.equal(allowedCommand(cmd('/market/web/items/abc/purchase', 'POST')), false);
  assert.equal(allowedCommand(cmd('/auth/web-token/session', 'POST')), false);
  assert.equal(allowedCommand({ url:'https://example.org/', method:'GET' }), false);
  assert.equal(allowedCommand(cmd('/accounts', 'DELETE')), false);
});
