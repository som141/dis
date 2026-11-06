// Explicit fixture-only NAVER round trips. Run with --network none.
import assert from 'node:assert/strict';
import { mkdtemp, rm } from 'node:fs/promises';
import { chromium } from 'playwright-core';
import { createPortal } from '../src/portal-server.mjs';
import { PortalBrowser } from '../src/portal-browser.mjs';
import { AUCTION_ORIGIN } from '../src/browser-transport.mjs';

const dir=await mkdtemp('/tmp/maple-naver-check-'), fixtures=[], errors=[];
const NAVER='https://nid.naver.com';
const style='<style>body{margin:0;background:#edf6f0;font:18px sans-serif}h1{position:absolute;left:40px;top:10px;font-size:22px}input,button{position:absolute;left:40px;width:300px;height:40px;box-sizing:border-box}#u,#nav{top:80px}#p{top:140px}#submit{top:200px}</style>';
function makeBrowser() {
  const id=fixtures.length+31, f={id,mode:fixtures.length ? 'same-tab' : 'popup',posts:0,logged:false};fixtures.push(f);
  const wire=new PortalBrowser({configureContext:async ctx=>{
    await ctx.route('https://**/*',async route=>{
      const req=route.request(),u=new URL(req.url());
      const headers={'access-control-allow-origin':AUCTION_ORIGIN,'access-control-allow-credentials':'true',
        'access-control-allow-headers':'content-type,x-platform,x-device-id,x-client-version','access-control-allow-methods':'GET,POST,OPTIONS'};
      if(req.method()==='OPTIONS') return route.fulfill({status:204,headers});
      if(u.origin===NAVER) {
        if(u.pathname==='/fixture-login' && req.method()==='POST') {
          const values=JSON.parse(req.postData());
          assert.equal(values.user,`fixture-user-${id}`);assert.equal(values.password,`fixture-pass-${id}`);f.logged=true;
          return route.fulfill({contentType:'application/json',body:'{}'});
        }
        return route.fulfill({contentType:'text/html; charset=utf-8',body:`<!doctype html><title>FIXTURE NAVER ONLY</title>${style}<h1>가상 네이버 인증 · 실제 계정 사용 금지</h1><form id="f"><input id="u"><input id="p" type="password"><button id="submit">가상 로그인</button></form><script>f.onsubmit=async e=>{e.preventDefault();await fetch('/fixture-login',{method:'POST',body:JSON.stringify({user:u.value,password:p.value})});${f.mode==='popup' ? `window.opener.location.href='${AUCTION_ORIGIN}/fixture-callback';window.close();` : `location.href='${AUCTION_ORIGIN}/fixture-callback';`}};</script>`});
      }
      if(u.origin===AUCTION_ORIGIN) {
        if(u.pathname==='/fixture-callback') return route.fulfill({contentType:'text/html; charset=utf-8',body:`<!doctype html><title>FIXTURE NEXON CALLBACK</title><h1>가상 인증 완료</h1><script>fetch('https://api.mskr.nexon.com/v1/accounts',{credentials:'include',headers:{'x-platform':'PC_WEB','x-device-id':'fixture-${id}','x-client-version':'1.0.2'}});</script>`});
        return route.fulfill({contentType:'text/html; charset=utf-8',body:`<!doctype html><title>FIXTURE NEXON ONLY</title>${style}<h1>가상 넥슨 · 네이버 로그인 선택</h1><button id="nav">가상 네이버 로그인</button><script>nav.onclick=()=>{${f.mode==='popup' ? `window.open('${NAVER}/fixture','naver','width=600,height=700');` : `location.href='${NAVER}/fixture';`}};</script>`});
      }
      assert.equal(u.origin,'https://api.mskr.nexon.com');
      if(req.method()==='POST') {f.posts++;assert.fail('Login checks must not create a search');}
      assert.equal(f.logged,true);let data;
      if(u.pathname==='/v1/accounts') data={accounts:[{accountId:id}]};
      else if(u.pathname.endsWith('/characters')) data={characters:u.pathname.includes('/gameWorlds/0/') ? [{characterId:id*10,characterName:`가상네이버${id}`,level:288}]:[]};
      else if(u.pathname.endsWith('/daily-limit')) data={search:{limit:100,remaining:100}};
      else assert.fail('Unexpected fixture API path');
      await route.fulfill({headers,contentType:'application/json',body:JSON.stringify(data)});
    });
  }});f.wire=wire;return wire;
}
let app,userBrowser;
try {
  app=await createPortal({port:8881,dataDir:dir,keyFile:dir+'/missing-key',timerMs:100,browserFactory:makeBrowser});
  await app.listen();
  userBrowser=await chromium.launch({executablePath:'/usr/bin/chromium',headless:false,args:['--disable-dev-shm-usage']});
  for(let i=0;i<2;i++) {
    const ctx=await userBrowser.newContext({viewport:{width:1280,height:1000}}),page=await ctx.newPage();
    page.on('pageerror',e=>errors.push(e.message));await page.goto('http://127.0.0.1:8881');await page.click('#begin');
    await page.waitForFunction(()=>!document.getElementById('screen').hidden);
    const clickScreen=async(x,y)=>{
      const box=await page.locator('#screen').boundingBox();
      await page.mouse.click(box.x+x*box.width/1280,box.y+y*box.height/900);
    };
    await clickScreen(100,100);
    await page.waitForFunction(()=>document.getElementById('officialOrigin').textContent==='https://nid.naver.com' && !document.getElementById('screen').hidden);
    assert.equal(await page.locator('#loginTitle').innerText(),'네이버 공식 로그인');
    assert.equal(fixtures[i].wire.context.pages().length,i===0 ? 2 : 1);
    assert.equal(fixtures[i].wire.loginState().origin,NAVER);
    await clickScreen(100,100);await page.keyboard.type(`fixture-user-${i+31}`);await page.keyboard.press('Tab');
    await page.keyboard.type(`fixture-pass-${i+31}`);await page.keyboard.press('Tab');await page.keyboard.press('Enter');
    await page.waitForFunction(()=>document.getElementById('statusTitle').textContent==='조건을 선택해 주세요',{},{timeout:20000});
    assert.match(await page.locator('#character').innerText(),new RegExp(`가상네이버${i+31}`));
    assert.equal(await page.locator('#login').isVisible(),false);
    assert.equal(fixtures[i].wire.context.pages().length,1);
    assert.equal(fixtures[i].wire.loginState().origin,AUCTION_ORIGIN);
    assert.equal(fixtures[i].posts,0);
  }
  assert.deepEqual(errors,[]);
  console.log('PASS: NAVER popup and same-tab login through portal canvas/input; provider origin/title/frame, Nexon callback and popup close, owned character auto-connect, zero search POSTs. FIXTURE ONLY; no external network.');
} finally {await userBrowser?.close();await app?.close();await rm(dir,{recursive:true,force:true});}
