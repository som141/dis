import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import vm from 'node:vm';

test('MV3 connector includes browser credentials but never reads cookies; refuses purchase and foreign URLs', async () => {
  const callbacks = {}, fetches = [], replies = [];
  const sandbox = {
    URL, AbortSignal, setInterval, clearInterval,
    WebSocket:{ OPEN:1, CONNECTING:0 },
    chrome:{
      runtime:{ id:'fixture-extension', onMessage:{ addListener:fn => { callbacks.message = fn; } }, onStartup:{ addListener() {} } },
      storage:{ session:{ get:async () => ({}), set:async () => {}, clear:async () => {} } },
      action:{ setBadgeText() {} }, alarms:{ create() {}, onAlarm:{ addListener() {} } },
    },
    fetch:async (url, options) => { fetches.push({ url, options }); return { ok:true, status:200, text:async () => '{}' }; },
  };
  const context = vm.createContext(sandbox);
  await vm.runInContext(await readFile(new URL('../extension/background.js', import.meta.url), 'utf8'), context);
  const ws = { readyState:1, send:raw => replies.push(JSON.parse(raw)) };
  context.testSocket = ws;
  await vm.runInContext("execute({id:'read',type:'fetch',url:'https://api.mskr.nexon.com/v1/market/web/daily-limit',method:'GET',headers:{accept:'application/json',Cookie:'must-not-forward',Authorization:'must-not-forward'}},testSocket)", context);
  assert.equal(fetches.length, 1); assert.equal(fetches[0].options.credentials, 'include');
  assert.equal(fetches[0].options.headers.Cookie, undefined); assert.equal(fetches[0].options.headers.Authorization, undefined);
  await vm.runInContext("execute({id:'buy',type:'fetch',url:'https://api.mskr.nexon.com/v1/market/web/items/x/purchase',method:'POST'},testSocket)", context);
  await vm.runInContext("execute({id:'foreign',type:'fetch',url:'https://example.org/',method:'GET'},testSocket)", context);
  assert.equal(fetches.length, 1); assert.equal(replies.at(-1).code, 'FORBIDDEN_URL');
  let answered = false;
  callbacks.message({ type:'status' }, { id:'another-extension' }, () => { answered = true; });
  assert.equal(answered, false);
});
