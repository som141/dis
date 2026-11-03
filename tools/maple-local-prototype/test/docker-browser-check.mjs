// Explicit fixture-only integration check. Run inside the desktop container:
// docker compose exec maple-desktop mkdir -p /app/test
// docker compose cp test/docker-browser-check.mjs maple-desktop:/app/test/
// docker compose exec maple-desktop node test/docker-browser-check.mjs
// Every official-site/API request in THIS test context is fulfilled locally.
import assert from 'node:assert/strict';
import { mkdtemp, rm } from 'node:fs/promises';
import { chromium } from 'playwright-core';
import { createApp } from '../src/server.mjs';
import { PrototypeEngine, INTERVAL_MS } from '../src/domain.mjs';
import { BrowserPageTransport, AUCTION_ORIGIN, browserProxy } from '../src/browser-transport.mjs';

const profile = await mkdtemp('/tmp/maple-test-profile-');
let now = Date.now(), posts = 0, cookieObserved = false;
const wire = new BrowserPageTransport();
const snapshot = async () => ({ name:'에반이였솜', job:'에반', level:280,
  equipment:{ item_equipment:[{ item_equipment_slot:'장갑', item_name:'가상 장갑', starforce:'17', potential_option_grade:'레전드리' }] },
  stats:{ 기본:{ 마력:100, 방무:[100] }, AP:{ INT:1000, LUK:100 }, 장비:{ 장갑:{ name:'현재 장갑', stat:{ INT:100, INT퍼:21, 마력:10 } } }, 세트효과:{} },
});
const app = createApp({ port:8879, mode:'server-browser', wireFactory:() => wire,
  engineFactory:w => new PrototypeEngine(w, { now:() => now, loadSnapshot:snapshot, refreshSnapshot:snapshot }) });
try {
  await app.listen();
  const ctx = await chromium.launchPersistentContext(profile, { executablePath:'/usr/bin/chromium', headless:false, viewport:{ width:1280, height:900 }, proxy:browserProxy() });
  wire.context = ctx;
  ctx.on('page', page => wire.observe(page));
  const auction = ctx.pages()[0]; wire.observe(auction);
  await ctx.addCookies([{ name:'fixture-only', value:'fake-session', domain:'api.mskr.nexon.com', path:'/', secure:true, httpOnly:true, sameSite:'None' }]);
  await ctx.route('https://**/*', async route => {
    const req = route.request(), u = new URL(req.url());
    const headers = { 'access-control-allow-origin':AUCTION_ORIGIN, 'access-control-allow-credentials':'true', 'access-control-allow-headers':'content-type,x-platform,x-device-id,x-client-version', 'access-control-allow-methods':'GET,POST,OPTIONS' };
    if (req.method() === 'OPTIONS') return route.fulfill({ status:204, headers });
    if (u.origin === AUCTION_ORIGIN) return route.fulfill({ contentType:'text/html; charset=utf-8', body:`<!doctype html><title>TEST FIXTURE ONLY</title><h1>가상 경매장 · 실제 로그인 아님</h1><script>fetch('https://api.mskr.nexon.com/v1/accounts',{credentials:'include',headers:{'x-platform':'PC_WEB','x-device-id':'fixture-device','x-client-version':'1.0.2'}}).then(()=>document.body.dataset.ready='yes')</script>` });
    assert.equal(u.origin, 'https://api.mskr.nexon.com');
    cookieObserved ||= (await req.allHeaders()).cookie?.includes('fixture-only=fake-session') || false;
    let data = {};
    if (u.pathname === '/v1/accounts') data = { accounts:[{ accountId:111 }] };
    else if (u.pathname.endsWith('/characters')) data = { characters:u.pathname.includes('/gameWorlds/0/') ? [{ characterId:222, characterName:'에반이였솜', level:280 }] : [] };
    else if (u.pathname.endsWith('/daily-limit')) data = { search:{ limit:100, remaining:100-posts } };
    else if (u.pathname.endsWith('/balance')) data = { meso:10000000000, maplePoint:0 };
    else if (u.pathname.includes('/searches/')) {
      if (req.method() === 'POST') ++posts;
      const item = (id, price, mad) => ({ _id:id, itemName:'아케인셰이드 메이지글러브', pricePerItem:String(price), quantity:1, starforce:17, status:'ON_SALE',
        toolTip:{ categories:['방어구','장갑'], reqJob:'마법사', stat:{ int:100, mad }, upgradeInfo:{ potential:{ grade:4, description:'잠재능력 : 레전드리', entries:[{ text:'INT +12%' }, { text:'INT +9%' }] } } } });
      data = { searchKey:'fixture-search', page:1, total:2, totalPages:1, hasNext:false, items:[item('fixture-A',2500000000,21),item(posts > 1 ? 'fixture-new' : 'fixture-B',3500000000,15)] };
    } else assert.fail('Unexpected fixture path');
    await route.fulfill({ status:200, contentType:'application/json', headers, body:JSON.stringify(data) });
  });
  await auction.goto(AUCTION_ORIGIN);
  await auction.waitForSelector('body[data-ready=yes]');
  const ui = await ctx.newPage(), errors = [];
  ui.on('pageerror', e => errors.push(e.message));
  await ui.goto('http://127.0.0.1:8879');
  await ui.waitForFunction(() => document.getElementById('connectTitle').textContent === '서버 브라우저 로그인');
  assert.equal(await ui.locator('#extensionInstructions').isVisible(), false);
  await ui.click('#connect');
  await ui.waitForFunction(() => document.getElementById('characters').options.length > 1);
  await ui.click('#selectCharacter');
  await ui.waitForFunction(() => document.getElementById('characterInfo').textContent.includes('Lv.280'));
  await ui.locator('summary').click();
  await ui.fill('#apiKey', 'fixture-only-never-real-key'); await ui.click('#saveKey');
  await ui.waitForFunction(() => document.getElementById('keyInfo').textContent.includes('키 적용됨'));
  assert.equal(await ui.inputValue('#apiKey'), '');
  await ui.click('#equipment'); await ui.waitForSelector('#equipmentList .equip');
  await ui.click('#search'); await ui.waitForSelector('.listing');
  assert.match(await ui.locator('.listing').first().innerText(), /110\.00점/);
  assert.match(await ui.locator('.listing').first().innerText(), /0\.400점/);
  await ui.click('#startWatch');
  await ui.waitForFunction(() => document.getElementById('watchState').textContent.includes('감시 중'));
  now += INTERVAL_MS + 1; await app.engine.tick();
  assert.equal(app.engine.state().notices.length, 1); assert.equal(posts, 2);
  now += INTERVAL_MS + 1; await app.engine.tick();
  assert.equal(app.engine.state().notices.length, 1); assert.equal(posts, 3);
  await ui.reload(); await ui.waitForSelector('.alertitem');
  await ui.screenshot({ path:'/tmp/maple-docker-fixture.png', fullPage:true });
  await ui.click('#stopWatch');
  await ui.waitForFunction(() => document.getElementById('watchState').textContent.includes('종료'));
  assert.ok(cookieObserved); assert.deepEqual(errors, []);
  console.log('PASS: real headed Chromium + cookie fetch + Docker UI + Evan calculation + simulated 30-minute watch/dedup. FIXTURE DATA ONLY.');
} finally { await app.close(); await rm(profile, { recursive:true, force:true }); }
