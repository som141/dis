// Explicit fixture-only headed Chromium check. Run with --network none.
import assert from 'node:assert/strict';
import { mkdtemp, rm, writeFile } from 'node:fs/promises';
import { chromium } from 'playwright-core';
import { createPortal } from '../src/portal-server.mjs';
import { PortalBrowser } from '../src/portal-browser.mjs';
import { PrototypeEngine, INTERVAL_MS } from '../src/domain.mjs';
import { AUCTION_ORIGIN } from '../src/browser-transport.mjs';

const dir=await mkdtemp('/tmp/maple-portal-browser-'), keyFile=dir+'/fixture-key';
await writeFile(keyFile,'fixture-only-service-key');
let now=Date.now(); const fixtures=[], errors=[];
const snapshot=async name => ({name,job:'에반',level:288,
  equipment:{item_equipment:[{item_equipment_slot:'장갑',item_name:'가상 장갑',starforce:'17'}]},
  stats:{기본:{마력:100,방무:[100]},AP:{INT:1000,LUK:100},장비:{장갑:{name:'현재 장갑',stat:{INT:100,마력:10}}},세트효과:{}},
});
function makeBrowser() {
  const id=fixtures.length+11, fixture={id,posts:0,logged:false,cookie:false,failFrames:id===11}; fixtures.push(fixture);
  const wire=new PortalBrowser({configureContext:async ctx => {
    await ctx.route('https://**/*',async route => {
      const req=route.request(), u=new URL(req.url());
      // Leave a web font pending. Login frames must not wait on fonts.ready.
      if(u.hostname==='fonts.fixture.test') { fixture.fontRoute=route; return; }
      const headers={'access-control-allow-origin':AUCTION_ORIGIN,'access-control-allow-credentials':'true','access-control-allow-headers':'content-type,x-platform,x-device-id,x-client-version','access-control-allow-methods':'GET,POST,OPTIONS'};
      if(req.method()==='OPTIONS') return route.fulfill({status:204,headers});
      if(u.origin===AUCTION_ORIGIN && u.pathname==='/fixture-login') {
        const data=JSON.parse(req.postData()); assert.equal(data.user,`fixture-user-${id}`); assert.equal(data.password,`fixture-pass-${id}`);
        fixture.logged=true;
        return route.fulfill({status:200,contentType:'application/json',body:'{}'});
      }
      if(u.origin===AUCTION_ORIGIN) return route.fulfill({contentType:'text/html; charset=utf-8',body:`<!doctype html><title>FIXTURE ONLY</title><style>body{margin:0;background:#f5f8f7;font:18px sans-serif}h1{position:absolute;left:40px;top:10px;font-size:22px}input,button{position:absolute;left:40px;width:300px;height:40px;box-sizing:border-box}#u{top:80px}#p{top:140px}button{top:200px}</style><h1>가상 공식 로그인 · 실제 넥슨 아님</h1><form id="f"><input id="u" aria-label="fixture user"><input id="p" type="password" aria-label="fixture password"><button>가상 로그인</button></form><script>f.onsubmit=async e=>{e.preventDefault();await fetch('/fixture-login',{method:'POST',body:JSON.stringify({user:u.value,password:p.value})});await fetch('https://api.mskr.nexon.com/v1/accounts',{credentials:'include',headers:{'x-platform':'PC_WEB','x-device-id':'fixture-${id}','x-client-version':'1.0.2'}});};</script>`});
      assert.equal(u.origin,'https://api.mskr.nexon.com');
      let data={};
      if(!fixture.logged) return route.fulfill({status:401,headers,contentType:'application/json',body:'{}'});
      const cookie=(await req.allHeaders()).cookie || '';
      if(cookie) {assert.ok(cookie.includes(`fixture_session=${id}`));fixture.cookie=true;}
      if(u.pathname==='/v1/accounts') {data={accounts:[{accountId:id}]};headers['set-cookie']=`fixture_session=${id}; Path=/; HttpOnly; Secure; SameSite=None`;}
      else if(u.pathname.endsWith('/characters')) data={characters:u.pathname.includes('/gameWorlds/0/') ? [{characterId:id*10,characterName:`가상사용자${id}`,level:288}]:[]};
      else if(u.pathname.endsWith('/daily-limit')) data={search:{limit:100,remaining:100-fixture.posts}};
      else if(u.pathname.includes('/searches/')) {
        if(req.method()==='POST') fixture.posts++;
        const item=(key,mad) => ({_id:`${id}-${key}`,itemName:'가상 장갑',pricePerItem:'2500000000',quantity:1,starforce:17,status:'ON_SALE',toolTip:{categories:['방어구','장갑'],reqJob:'마법사',stat:{int:100,mad},upgradeInfo:{}}});
        data={searchKey:`fixture-${id}`,page:1,total:fixture.posts>1?2:1,totalPages:1,hasNext:false,items:[item('old',21),...(fixture.posts>1?[item('new',15)]:[])]};
      } else assert.fail('Unexpected fixture API path');
      await route.fulfill({status:200,headers,contentType:'application/json',body:JSON.stringify(data)});
    });
  }});
  const capture=wire.frame.bind(wire);
  wire.frame=async()=>{
    if(fixture.failFrames) throw Object.assign(new Error(),{code:'LOGIN_FRAME_UNAVAILABLE',status:503});
    return capture();
  };
  fixture.wire=wire;return wire;
}
let app, userBrowser;
try {
  app=await createPortal({port:8880,dataDir:dir+'/state',keyFile,now:()=>now,timerMs:100,
    browserFactory:makeBrowser,engineFactory:w=>new PrototypeEngine(w,{now:()=>now,loadSnapshot:snapshot,manageKeyEnvironment:false})});
  await app.listen();
  userBrowser=await chromium.launch({executablePath:'/usr/bin/chromium',headless:false,args:['--disable-dev-shm-usage']});
  const users=[];
  for(let i=0;i<2;i++) {
    const ctx=await userBrowser.newContext({viewport:{width:1280,height:1000}}), page=await ctx.newPage();
    page.on('pageerror',e=>errors.push(e.message)); await page.goto('http://127.0.0.1:8880'); await page.click('#begin');
    if(i===0) {
      await page.waitForFunction(()=>document.getElementById('loginLoading').textContent.includes('지연'),{},{timeout:10000});
      assert.equal(await page.locator('#screenPlaceholder').isVisible(),true);
      assert.equal(await page.locator('#screen').isVisible(),false);
      fixtures[0].failFrames=false;
    }
    await page.waitForFunction(()=>!document.getElementById('screen').hidden,{},{timeout:15000});
    const wire=fixtures[i].wire,original=wire.surface(),blank=await wire.context.newPage();
    assert.equal(wire.surface(),original,'a blank popup must not replace the login surface');
    await blank.close();
    if(i===0) {
      await original.evaluate(()=>{
        const font=new FontFace('FixturePendingFont','url(https://fonts.fixture.test/slow.woff2)');
        document.fonts.add(font);void font.load().catch(()=>{});
      });
      await original.waitForFunction(()=>document.fonts.status==='loading');
      while(!fixtures[0].fontRoute) await new Promise(r=>setTimeout(r,10));
      const before=Date.now();await wire.frame();assert.ok(Date.now()-before<2000,'frames must render while fonts are pending');
      await fixtures[0].fontRoute.abort();
      await page.click('#reloadLogin');
      await page.waitForFunction(()=>!document.getElementById('screen').hidden && !document.getElementById('reloadLogin').disabled,{},{timeout:15000});
      assert.equal(fixtures[0].posts,0,'reopening login must not consume a search');
    }
    users.push({ctx,page});
  }
  async function login(user,id) {
    const box=await user.page.locator('#screen').boundingBox(), scale=box.width/1280;
    await user.page.mouse.click(box.x+100*scale,box.y+100*scale);
    await user.page.keyboard.type(`fixture-user-${id}`); await user.page.keyboard.press('Tab');
    await user.page.keyboard.type(`fixture-pass-${id}`); await user.page.keyboard.press('Tab'); await user.page.keyboard.press('Enter');
    await user.page.waitForFunction(()=>document.getElementById('statusTitle').textContent==='조건을 선택해 주세요',{},{timeout:20000});
    assert.equal(fixtures.find(f=>f.id===id).posts,0,'login alone must not search');
    await user.page.locator('[data-field="keyword"]').fill('가상 장갑');
    await user.page.locator('[data-field="watch"]').check();
    await user.page.click('#runSearch');
    await user.page.waitForFunction(()=>document.getElementById('statusTitle').textContent==='자동 감시 중',{},{timeout:20000});
  }
  await login(users[0],11);
  assert.equal(fixtures[0].posts,1); assert.equal(fixtures[1].posts,0);
  assert.equal(await users[1].page.locator('#login').isVisible(),true);
  await login(users[1],12);
  for(let i=0;i<2;i++) {
    assert.match(await users[i].page.locator('#character').innerText(),new RegExp(`가상사용자${i+11}`));
    assert.match(await users[i].page.locator('#listings').innerText(),/110\.00점/);
    assert.equal(await users[i].page.locator('#login').isVisible(),false);
    assert.equal(fixtures[i].posts,1); assert.equal(fixtures[i].cookie,true);
  }
  await app.tick(); now+=INTERVAL_MS+1; await app.tick();
  for(const user of users) await user.page.waitForFunction(()=>document.getElementById('notices').textContent.includes('가상 장갑'));
  assert.equal(fixtures[0].posts,2);assert.equal(fixtures[1].posts,2);
  await users[0].page.getByRole('button',{name:'중지',exact:true}).click();
  await users[0].page.waitForFunction(()=>document.querySelector('.job-state')?.textContent==='중지됨');
  await app.tick();now+=INTERVAL_MS+1;await app.tick(); assert.equal(fixtures[0].posts,2);assert.equal(fixtures[1].posts,3);
  await users[1].page.screenshot({path:'/tmp/maple-portal-fixture.png',fullPage:true});
  await users[1].page.setViewportSize({width:390,height:900});
  assert.equal(await users[1].page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth),true);
  assert.deepEqual(errors,[]);
  console.log('PASS: failed-frame UI recovery, blank popup isolation, capture with pending fonts, safe login reopen; two isolated headed browser logins through portal input; automatic character/equipment/search/110-point comparison/watch; simulated 30-minute notices; per-user stop; no external network. FIXTURE ONLY.');
} finally {
  await userBrowser?.close(); await app?.close(); await rm(dir,{recursive:true,force:true});
}
