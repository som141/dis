import { test } from 'node:test';
import assert from 'node:assert/strict';
import { EventEmitter } from 'node:events';
import { BrowserPageTransport, AUCTION_ORIGIN, browserProxy, pickClientHeaders } from '../src/browser-transport.mjs';

function harness() {
  const wire = new BrowserPageTransport(), calls = [], replies = [];
  const page = new EventEmitter();
  page.url = () => AUCTION_ORIGIN + '/buy'; page.isClosed = () => false;
  page.mainFrame = () => page;
  page.evaluate = async (_fn, arg) => { calls.push(arg); return replies.shift(); };
  wire.page = page; wire.context = {}; wire.observe(page);
  page.emit('request', { url:() => 'https://api.mskr.nexon.com/v1/accounts', headers:() => ({
    cookie:'never-export', authorization:'never-export', 'x-platform':'PC_WEB', 'x-device-id':'fixture-device', 'x-client-version':'1.0.2',
  }) });
  const account = id => ({ id:'', ok:true, status:200, bodyText:JSON.stringify({ accounts:[{ accountId:id }] }) });
  return { wire, page, calls, replies, account };
}
const accounts = { url:'https://api.mskr.nexon.com/v1/accounts', method:'GET' };
const search = { url:'https://api.mskr.nexon.com/v1/market/web/items/searches/tool-tip', method:'POST', body:'{}', headers:{ cookie:'must-not-forward', authorization:'must-not-forward' } };

test('Docker transport uses in-browser fetch and only observed non-secret client headers', async () => {
  const { wire, calls, replies, account } = harness();
  replies.push(account(1), account(1), { id:'', ok:true, status:200, bodyText:'{}' });
  assert.equal((await wire.request(accounts)).ok, true);
  assert.equal((await wire.request(search)).ok, true);
  assert.equal(calls.length, 3); // Accounts verification precedes the one search POST.
  assert.equal(calls.filter(c => c.method === 'POST').length, 1);
  for (const c of calls) {
    assert.equal('cookie' in c.headers, false); assert.equal('authorization' in c.headers, false);
    assert.equal(c.headers['x-device-id'], 'fixture-device');
  }
  assert.equal((await wire.request({ url:'https://api.mskr.nexon.com/v1/buy', method:'POST' })).code, 'FORBIDDEN_URL');
  assert.equal(calls.length, 3);
  assert.deepEqual(pickClientHeaders({ cookie:'secret' }), {});
});
test('account switch and expired authentication invalidate the old scope before a search POST', async () => {
  const { wire, calls, replies, account } = harness();
  replies.push(account(1), account(2));
  await wire.request(accounts);
  const scope = wire.scope(); let changed = 0; wire.onChange = () => ++changed;
  assert.equal((await scope.request(search)).code, 'SESSION_CHANGED');
  assert.equal(scope.connected, false); assert.equal(changed, 1);
  assert.equal(calls.some(c => c.method === 'POST'), false);
  assert.equal((await wire.request(search)).code, 'SESSION_REQUIRED');
  replies.push(account(2), { id:'', ok:false, status:401, bodyText:'sensitive-provider-error' });
  await wire.request(accounts);
  const second = wire.scope(), failure = await second.request(search);
  assert.equal(failure.status, 401); assert.equal(failure.bodyText, undefined);
  assert.equal(second.connected, false); assert.equal(calls.some(c => c.method === 'POST'), false);
});
test('official tab reload drops previous identity and captured headers; stale calls are refused', async () => {
  const { wire, page, calls } = harness(), scope = wire.scope();
  page.emit('framenavigated', page);
  assert.equal(scope.connected, false); assert.equal((await wire.request(accounts)).code, 'NO_CLIENT_HEADERS');
  assert.equal(calls.length, 0);
  page.emit('close'); wire.page = null;
  assert.equal((await wire.request(accounts)).code, 'DISCONNECTED');
});
test('browser proxy retains configured routing and protects local UI from external proxying', () => {
  assert.equal(browserProxy({}), undefined);
  const proxy = browserProxy({ HTTPS_PROXY:'http://name:pass@proxy.example:8080', NO_PROXY:'.internal.test' });
  assert.equal(proxy.server, 'http://proxy.example:8080'); assert.equal(proxy.username, 'name');
  assert.ok(proxy.bypass.includes('.internal.test')); assert.ok(proxy.bypass.includes('127.0.0.1'));
});
