import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, readFile, writeFile, rm, readdir } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { createPortal } from '../src/portal-server.mjs';
import { PortalFlow, normalizeJobs } from '../src/portal-flow.mjs';
import { PortalStore } from '../src/portal-store.mjs';
import { PrototypeEngine, INTERVAL_MS } from '../src/domain.mjs';
import { isLoginSurface, PortalBrowser } from '../src/portal-browser.mjs';

function browser(identity) {
  let ready=false, posts=0, fail=false;
  const wire = { epoch:1, connected:true, accounts:null, onChange:() => {},
    async start(dir) { this.profile=dir; }, async close() {}, status:() => ({clientReady:ready}),
    loginState:() => ({origin:'https://nxlogin.nexon.com',width:1280,height:900}), frame:async () => Buffer.from('fixture-jpeg'),
    async input(value) { if (value.type==='text') assert.equal(typeof value.text,'string'); },
    async reloadLogin() { ready=false; },
    scope() { const epoch=this.epoch; return { get connected() { return epoch===wire.epoch && wire.connected; }, request:c => wire.request(c) }; },
    async request(cmd) {
      const path=new URL(cmd.url).pathname; let data;
      if (path==='/v1/accounts') { this.accounts=String(identity); data={accounts:[{accountId:identity}]}; }
      else if (path.endsWith('/characters')) data={characters:path.includes('/gameWorlds/0/') ? [
        {characterId:identity*10,characterName:`캐릭터${identity}`,level:288},
        {characterId:identity*10+1,characterName:'낮은캐릭터',level:250},
      ] : []};
      else if (path.endsWith('/daily-limit')) data={search:{limit:100,remaining:100-posts}};
      else if (cmd.method==='POST') { posts++; if(fail) throw new Error('fixture upstream error with a secret'); data={searchKey:'fixture',total:0,items:[],page:1,totalPages:0,hasNext:false}; }
      else throw new Error('Unexpected fixture request');
      return {ok:true,status:200,bodyText:JSON.stringify(data)};
    },
    login() { ready=true; }, posts:() => posts, fail() {fail=true;},
    switchAccount(id) { identity=id; this.accounts=null; ++this.epoch; this.onChange(); },
    drop() { ready=false; this.connected=false; ++this.epoch; this.onChange(); },
    reconnect() {ready=true;this.connected=true;++this.epoch;this.onChange();},
  }; return wire;
}
function engine(wire,clock) {
  return new PrototypeEngine(wire,{manageKeyEnvironment:false, now:() => clock.now,
    loadSnapshot:async name => ({name,job:'에반',level:288,
      equipment:{item_equipment:[{item_equipment_slot:'장갑',item_name:'가상 장갑',starforce:'17'}]},
      stats:{기본:{마력:100,방무:[100]},AP:{INT:1000,LUK:100},장비:{장갑:{name:'현재 장갑',stat:{INT:100,마력:10}}},세트효과:{}},
    }),
  });
}
const config=(extra={})=>({id:'glove',enabled:true,keyword:'가상 장갑',category:'ARMOR_ARMOR_GLOVE',budget:200,budgetMode:'range',starforceMin:17,potentialGrade:4,mode:'live',compare:true,watch:true,days:1,intervalMinutes:30,...extra});
function connectChanges(e,w,flow){w.onChange=()=>{e.connectionChanged();flow.connectionChanged();};}
async function ready(flow,w){w.login();await flow.tick();if(!flow.engine.character)await flow.selectCharacter(110);await flow.tick();}
test('login offers owned characters and switching keeps each character workspace isolated',async()=>{
 const clock={now:1000000},w=browser(11),e=engine(w,clock),flow=new PortalFlow(e,w,{now:()=>clock.now});connectChanges(e,w,flow);
 w.login();await flow.tick();assert.equal(flow.phase,'character-unavailable');assert.equal(flow.state().character,null);
 assert.deepEqual(flow.state().characters.map(c=>c.id),[110,111]);assert.equal(w.posts(),0);
 await assert.rejects(()=>flow.selectCharacter(999),/현재 계정/);
 flow.running=true;await assert.rejects(()=>flow.selectCharacter(110),error=>error.code==='BUSY');flow.running=false;
 await flow.selectCharacter(110);await flow.configure([config({id:'first',compare:false})]);await flow.tick();assert.equal(w.posts(),1);
 await flow.selectCharacter(111);assert.equal(flow.state().character.id,111);assert.equal(flow.jobs.length,0);assert.equal(flow.configs.length,0);
 await flow.configure([config({id:'second',compare:false,watch:false})]);await flow.tick();assert.equal(w.posts(),2);
 await flow.selectCharacter(110);assert.equal(flow.jobs[0].id,'first-live');assert.equal(flow.jobs[0].phase,'paused');assert.equal(flow.jobs[0].stopped,true);
 clock.now+=INTERVAL_MS;await flow.tick();assert.equal(w.posts(),2);
 const saved=structuredClone(flow.saved),w2=browser(11),e2=engine(w2,clock);w2.login();const resumed=new PortalFlow(e2,w2,{saved,now:()=>clock.now});connectChanges(e2,w2,resumed);await resumed.tick();
 assert.equal(resumed.state().character.id,110);assert.equal(w2.posts(),0);
 await resumed.selectCharacter(111);assert.deepEqual(resumed.jobs.map(job=>job.id),['second-live']);assert.equal(resumed.jobs[0].stopped,true);
});
test('login performs no search; explicit selection runs the configured job and restores watch ownership',async()=>{
  const clock={now:1000000},w=browser(11),e=engine(w,clock);e.key='fixture-service-key';let saved={};
  const flow=new PortalFlow(e,w,{now:()=>clock.now,save:async value=>{saved=structuredClone(value);}});connectChanges(e,w,flow);
  await ready(flow,w);await flow.tick();assert.equal(flow.phase,'ready');assert.equal(w.posts(),0);
  await flow.configure([config(),config({id:'not-selected',enabled:false})]);await flow.tick();
  assert.equal(flow.jobs.length,1);assert.equal(flow.jobs[0].phase,'watching');assert.equal(w.posts(),1);
  assert.equal(flow.jobs[0].watch.input.budget,200);assert.equal(flow.jobs[0].watch.nextPollAt,clock.now+INTERVAL_MS);
  w.drop();await flow.tick();assert.equal(flow.phase,'login');w.reconnect();await flow.tick();assert.equal(w.posts(),1);
  const w2=browser(11),e2=engine(w2,clock);e2.key='fixture-service-key';w2.login();
  const resumed=new PortalFlow(e2,w2,{saved,now:()=>clock.now});connectChanges(e2,w2,resumed);await resumed.tick();assert.equal(w2.posts(),0);
  clock.now+=INTERVAL_MS;await resumed.tick();assert.equal(w2.posts(),1);
  w2.switchAccount(22);await resumed.tick();assert.equal(resumed.phase,'account-changed');assert.equal(w2.posts(),1);
});
test('uncertain user-requested POST is durable and never automatically replayed',async()=>{
  const clock={now:1000000},w=browser(11),e=engine(w,clock);e.key='fixture-service-key';let saved;
  const flow=new PortalFlow(e,w,{now:()=>clock.now,save:async value=>{saved=structuredClone(value);}});connectChanges(e,w,flow);
  await flow.configure([config()]);w.fail();await ready(flow,w);assert.equal(w.posts(),1);assert.equal(flow.jobs[0].phase,'check-required');
  await flow.tick();const resumed=new PortalFlow(e,w,{saved,now:()=>clock.now});await resumed.tick();assert.equal(w.posts(),1);
  assert.ok(saved.jobs[0].attemptedAt);assert.doesNotMatch(JSON.stringify(saved),/fixture-service-key|fixture upstream/);
});
test('comparison key wait is per job; repairing the key resumes the explicit request',async()=>{
  const clock={now:1000000},w=browser(11),e=engine(w,clock);e.key='';const flow=new PortalFlow(e,w,{now:()=>clock.now});connectChanges(e,w,flow);
  await flow.configure([config()]);await ready(flow,w);await flow.tick();assert.equal(flow.jobs[0].phase,'operator-setup');assert.equal(w.posts(),0);
  e.key='fixture-service-key';await flow.tick();assert.equal(flow.jobs[0].phase,'watching');assert.equal(w.posts(),1);
  await flow.stop('glove-live');assert.equal(flow.jobs[0].phase,'paused');await flow.retry('glove-live');await flow.tick();assert.equal(w.posts(),2);
});
test('watch poll intent advances the durable schedule before POST and retains inbox on reconnect',async()=>{
  const clock={now:1000000},w=browser(11),e=engine(w,clock);e.key='fixture-service-key';let saved={},armed;
  const flow=new PortalFlow(e,w,{now:()=>clock.now,save:async value=>{saved=structuredClone(value);if(value.jobs?.[0]?.pollAttemptedAt&&!armed)armed=structuredClone(value);}});connectChanges(e,w,flow);
  await flow.configure([config()]);await ready(flow,w);await flow.tick();flow.jobs[0].engine.notices=[{item:{id:'fixture-notice'},foundAt:new Date(clock.now).toISOString()}];flow.checkpoint(flow.jobs[0]);await flow.persist();
  w.drop();await flow.tick();w.reconnect();await flow.tick();assert.equal(flow.jobs[0].notices[0].item.id,'fixture-notice');
  clock.now+=INTERVAL_MS;await flow.tick();assert.equal(w.posts(),2);assert.equal(armed.jobs[0].watch.nextPollAt,clock.now+INTERVAL_MS);
  const w2=browser(11),e2=engine(w2,clock);e2.key='fixture-service-key';w2.login();const resumed=new PortalFlow(e2,w2,{saved:armed,now:()=>clock.now});connectChanges(e2,w2,resumed);await resumed.tick();assert.equal(w2.posts(),0);
});
test('sold analysis keeps the live watch and watch=false does not start monitoring',async()=>{
  const clock={now:1000000},w=browser(11),e=engine(w,clock);e.key='fixture-service-key';const flow=new PortalFlow(e,w,{now:()=>clock.now});connectChanges(e,w,flow);await ready(flow,w);
  await flow.configure([config()]);await flow.tick();await flow.configure([config({mode:'sold',compare:false,watch:false})]);await flow.tick();
  assert.equal(flow.jobs.find(job=>job.id==='glove-live').phase,'watching');assert.equal(flow.jobs.find(job=>job.id==='glove-sold').phase,'market-ready');assert.equal(flow.jobs.find(job=>job.id==='glove-sold').watch,null);
});
test('portal keeps two users and their job/results APIs isolated with CSRF and Origin checks',async()=>{
  const dir=await mkdtemp(join(tmpdir(),'maple-portal-test-')),clock={now:1000000},keyFile=join(dir,'key');await writeFile(keyFile,'fixture-service-key');let app;const wires=[];
  try{
    app=await createPortal({port:0,dataDir:join(dir,'state'),keyFile,timerMs:0,now:()=>clock.now,browserFactory:()=>{const w=browser(wires.length+11);wires.push(w);return w;},engineFactory:w=>engine(w,clock)});
    const {port}=await app.listen(),base=`http://127.0.0.1:${port}`;
    const create=async()=>{const r=await fetch(base+'/api/session',{method:'POST',headers:{origin:base,'content-type':'application/json'},body:'{}'});return {r,cookie:r.headers.get('set-cookie')?.split(';')[0]};};
    const a=await create(),b=await create();assert.equal(a.r.status,200);assert.equal(b.r.status,200);assert.equal((await create()).r.status,429);
    const token=async cookie=>(await(await fetch(base+'/api/bootstrap',{headers:{cookie}})).json()).csrf;
    const at=await token(a.cookie),bt=await token(b.cookie),post=(path,cookie,csrf,data={},origin=base)=>fetch(base+path,{method:'POST',headers:{cookie,origin,'x-portal-token':csrf,'content-type':'application/json'},body:JSON.stringify(data)});
    assert.equal((await fetch(base+'/api/state')).status,401);assert.equal((await post('/api/jobs',a.cookie,bt,{jobs:[config()]})).status,403);assert.equal((await post('/api/stop',a.cookie,at,{},'http://evil.test')).status,403);
    assert.equal((await fetch(base+'/api/login/frame',{headers:{cookie:a.cookie}})).status,403);assert.equal((await post('/api/login/reload',a.cookie,at)).status,200);
    wires[0].login();wires[1].login();await app.tick();assert.equal(wires[0].posts(),0);assert.equal(wires[1].posts(),0);
    assert.equal((await post('/api/character/select',a.cookie,at,{characterId:110})).status,200);
    assert.equal((await post('/api/character/select',b.cookie,bt,{characterId:120})).status,200);
    assert.equal((await fetch(base+'/api/login/frame',{headers:{cookie:a.cookie,'x-portal-token':at}})).status,409);
    await post('/api/jobs',a.cookie,at,{jobs:[config({id:'private-a'})]});await post('/api/jobs',b.cookie,bt,{jobs:[config({id:'private-b'})]});await app.tick();
    assert.equal((await fetch(base+'/api/jobs/private-a-live/items',{headers:{cookie:b.cookie}})).status,404);
    const state=await(await fetch(base+'/api/state',{headers:{cookie:a.cookie}})).json();assert.equal(state.jobs[0].phase,'watching');assert.doesNotMatch(JSON.stringify(state),/fixture-service-key|tokenHash|accountId/);
    await post('/api/jobs/private-a-live/stop',a.cookie,at);clock.now+=INTERVAL_MS;await app.tick();assert.equal(wires[0].posts(),1);assert.equal(wires[1].posts(),2);
    for(const file of await readdir(join(dir,'state')))if(file.endsWith('.json')){const raw=await readFile(join(dir,'state',file),'utf8');assert.equal(raw.includes(a.cookie.split('=')[1]),false);assert.doesNotMatch(raw,/fixture-service-key/);}
  }finally{await app?.close();await rm(dir,{recursive:true,force:true});delete process.env.NEXON_DEVELOPER_KEY;}
});
test('session capacity reservation is atomic and destroying one engine keeps the shared service key',async () => {
  const dir=await mkdtemp(join(tmpdir(),'maple-store-test-'));
  try {
    const store=new PortalStore(dir,{limit:1}); await store.init();
    const outcomes=await Promise.allSettled([store.create(),store.create()]);
    assert.equal(outcomes.filter(x => x.status==='fulfilled').length,1);
    const created=outcomes.find(x => x.status==='fulfilled').value;
    const loaded=new PortalStore(dir); await loaded.init(); assert.equal(loaded.find(created.token).id,created.record.id);
    process.env.NEXON_DEVELOPER_KEY='fixture-service-key';
    const e=engine(browser(11),{now:0}); e.destroy(); assert.equal(process.env.NEXON_DEVELOPER_KEY,'fixture-service-key');
  } finally { delete process.env.NEXON_DEVELOPER_KEY; await rm(dir,{recursive:true,force:true}); }
});
test('Discord mode requires the internal key, binds a stable user to one record and rotates browser access',async()=>{
  const dir=await mkdtemp(join(tmpdir(),'maple-discord-test-'));let app;
  try{
    app=await createPortal({port:0,dataDir:dir,limit:2,timerMs:0,discordMode:true,internalKey:'fixture-internal-key-123456789012345',browserFactory:()=>browser(11),engineFactory:w=>engine(w,{now:0})});
    const {port}=await app.listen(),base=`http://127.0.0.1:${port}`,key='fixture-internal-key-123456789012345';
    const issue=(discordUserId,secret=key)=>fetch(base+'/internal/discord/session',{method:'POST',headers:{'content-type':'application/json','x-maple-internal-key':secret},body:JSON.stringify({discordUserId})});
    assert.equal((await fetch(base+'/api/session',{method:'POST',headers:{origin:base,'content-type':'application/json'},body:'{}'})).status,401);
    assert.equal((await issue('101','wrong')).status,403);
    const first=await(await issue('101')).json(),other=await(await issue('202')).json();
    assert.notEqual(first.token,other.token);assert.equal(app.store.records.size,2);
    assert.equal((await issue('303')).status,429);
    const renewed=await(await issue('101')).json();assert.notEqual(first.token,renewed.token);assert.equal(app.store.records.size,2);
    assert.equal((await fetch(base+'/api/state',{headers:{cookie:`maple_portal_session=${first.token}`}})).status,401);
    assert.equal((await fetch(base+'/api/session',{method:'POST',headers:{origin:base,'content-type':'application/json',cookie:`maple_portal_session=${renewed.token}`},body:'{}'})).status,200);
    assert.equal((await fetch(base+'/internal/discord/users/101/status',{headers:{'x-maple-internal-key':key}})).status,200);
    assert.equal((await fetch(base+'/internal/discord/users/202/status',{headers:{'x-maple-internal-key':key}})).status,200);
    assert.equal((await fetch(base+'/internal/discord/users/101/stop',{method:'POST',headers:{'content-type':'application/json','x-maple-internal-key':key},body:JSON.stringify({jobId:'missing-live'})})).status,404);
    const owned=app.store.findDiscordUser('202');
    owned.flow={version:2,configs:[{id:'owned',mode:'live',watch:true}],jobs:[{id:'owned-live',config:{id:'owned',mode:'live',keyword:'개인 장갑',watch:true},phase:'watching',stopped:false}]};
    await app.store.save(owned);
    const stop=(user)=>fetch(base+`/internal/discord/users/${user}/stop`,{method:'POST',headers:{'content-type':'application/json','x-maple-internal-key':key},body:JSON.stringify({jobId:'owned-live'})});
    assert.equal((await stop('101')).status,404);
    assert.equal(owned.flow.jobs[0].stopped,false);
    assert.equal((await stop('202')).status,200);
    assert.equal(owned.flow.jobs[0].stopped,true);
    assert.equal((await fetch(base+'/internal/discord/events',{headers:{'x-maple-internal-key':'wrong'}})).status,403);
    const firstRecord=app.store.findDiscordUser('101');
    app.store.captureDiscordNotices(firstRecord,{jobs:[{id:'glove-live',notices:[{foundAt:'2026-11-05T01:00:00Z',item:{id:'auction-1',name:'테스트 장갑',price:123456789}}]}]});
    await app.store.save(firstRecord);
    const events=await(await fetch(base+'/internal/discord/events',{headers:{'x-maple-internal-key':key}})).json();
    assert.equal(events.events.length,1);assert.equal(events.events[0].discordUserId,'101');
    assert.equal((await fetch(base+'/internal/discord/events/ack',{method:'POST',headers:{'content-type':'application/json','x-maple-internal-key':key},body:JSON.stringify({eventId:events.events[0].id,status:'delivered'})})).status,200);
    assert.equal((await(await fetch(base+'/internal/discord/events',{headers:{'x-maple-internal-key':key}})).json()).events.length,0);
    assert.equal(app.store.findDiscordUser('101').discordUserId,'101');
    const loaded=new PortalStore(dir,{limit:2});await loaded.init();assert.equal(loaded.findDiscordUser('101').id,app.store.findDiscordUser('101').id);
  }finally{await app?.close();await rm(dir,{recursive:true,force:true});}
});
test('a Nexon account already linked to another Discord owner is rejected before character selection',async()=>{
  const w=browser(11),e=engine(w,{now:0}),flow=new PortalFlow(e,w,{isOwnerAvailable:()=>false});
  w.login();await flow.tick();
  await assert.rejects(()=>flow.selectCharacter(110),error=>error.code==='ACCOUNT_LINKED');
  assert.equal(flow.engine.character,null);
});
test('Discord watch notices remain private and durable until delivery acknowledgement',async()=>{
  const dir=await mkdtemp(join(tmpdir(),'maple-outbox-test-'));
  try{
    const store=new PortalStore(dir,{limit:2});await store.init();
    const {record}=await store.issueDiscordSession('101');await store.issueDiscordSession('202');
    const flow={jobs:[{id:'glove-live',notices:[{foundAt:'2026-11-05T01:00:00Z',item:{id:'auction-1',name:'테스트 장갑',price:123456789}}]}]};
    store.captureDiscordNotices(record,flow);store.captureDiscordNotices(record,flow);await store.save(record);
    let pending=store.pendingDiscordNotices();assert.equal(pending.length,1);assert.equal(pending[0].discordUserId,'101');
    assert.equal(pending[0].price,123456789);assert.equal(pending[0].name,'테스트 장갑');
    assert.equal(await store.acknowledgeDiscordNotice(pending[0].id,'delivered'),true);
    assert.equal(store.pendingDiscordNotices().length,0);
    const loaded=new PortalStore(dir,{limit:2});await loaded.init();assert.equal(loaded.pendingDiscordNotices().length,0);
  }finally{await rm(dir,{recursive:true,force:true});}
});
test('login viewing excludes unrelated sites and API-key management pages',() => {
  assert.equal(isLoginSurface('https://nxlogin.nexon.com/'),true);
  assert.equal(isLoginSurface('https://auction.maplestory.nexon.com/'),true);
  assert.equal(isLoginSurface('https://openapi.nexon.com/my-app/'),false);
  assert.equal(isLoginSurface('https://nexon.com.evil.test/'),false);
  assert.equal(isLoginSurface('http://nxlogin.nexon.com/'),false);
});

test('NAVER sign-in permits only its exact HTTPS authentication origin and returns to Nexon',() => {
  assert.equal(isLoginSurface('https://nid.naver.com/oauth2.0/authorize?fixture=1'),true);
  assert.equal(isLoginSurface('https://nid.naver.com/nidlogin.login'),true);
  for(const url of ['http://nid.naver.com/','https://nid.naver.com.evil.test/','https://evil-nid.naver.com/',
    'https://nid.naver.com:444/','https://www.naver.com/','https://openapi.naver.com/']) assert.equal(isLoginSurface(url),false,url);
  const popup={url:()=> 'https://nid.naver.com/nidlogin.login',isClosed:()=>false};
  const nexon={url:()=> 'https://nxlogin.nexon.com/',isClosed:()=>false};
  const wire=new PortalBrowser();wire.context={pages:()=>[nexon,popup]};wire.authPage=popup;
  assert.equal(wire.surface(),popup);assert.equal(wire.loginState().origin,'https://nid.naver.com');
  popup.isClosed=()=>true;
  assert.equal(wire.surface(),nexon);assert.equal(wire.loginState().origin,'https://nxlogin.nexon.com');
});

test('blank, closed and unrelated popups cannot hide a usable official login page',() => {
  const official={url:()=> 'https://nxlogin.nexon.com/',isClosed:()=>false};
  const blank={url:()=> 'about:blank',isClosed:()=>false};
  const unrelated={url:()=> 'https://openapi.nexon.com/my-app/',isClosed:()=>false};
  const closed={url:()=> 'https://nxlogin.nexon.com/',isClosed:()=>true};
  const wire=new PortalBrowser();wire.context={pages:()=>[official,blank,unrelated,closed]};
  for(const p of [null,blank,unrelated,closed]) {
    wire.authPage=p;
    assert.equal(wire.surface(),official);
    assert.equal(wire.loginState().origin,'https://nxlogin.nexon.com');
  }
  wire.context={pages:()=>[blank,unrelated,closed]};wire.authPage=null;
  assert.throws(()=>wire.surface(),{code:'LOGIN_SURFACE_UNAVAILABLE'});
});

test('simultaneous frame requests share a capture and recover after capture failure',async () => {
  const page={url:()=> 'https://nxlogin.nexon.com/',isClosed:()=>false};
  const wire=new PortalBrowser();let captures=0,finish,fail=false;
  wire.context={pages:()=>[page],newCDPSession:async()=>({
    async send() { captures++; if(fail) throw new Error('fixture failure'); await new Promise(r=>{finish=r;});return {data:Buffer.from('fixture-image').toString('base64')}; },
    async detach() {},
  })};
  const first=wire.frame(),second=wire.frame();await new Promise(r=>setImmediate(r));
  assert.equal(captures,1);finish();
  const frames=await Promise.all([first,second]);assert.deepEqual(frames[0],frames[1]);
  fail=true;await assert.rejects(wire.frame(),{code:'LOGIN_FRAME_UNAVAILABLE'});
  fail=false;const recovered=wire.frame();await new Promise(r=>setImmediate(r));finish();
  assert.equal((await recovered).toString(),'fixture-image');
});

test('turning a running watch off cancels future polls and remains off after restore',async()=>{
  const clock={now:1000000},w=browser(11),e=engine(w,clock);e.key='fixture-service-key';
  const flow=new PortalFlow(e,w,{now:()=>clock.now});connectChanges(e,w,flow);await ready(flow,w);
  await flow.configure([config()]);await flow.tick();assert.equal(w.posts(),1);
  await flow.stop('glove-live');clock.now+=INTERVAL_MS*5;await flow.tick();assert.equal(w.posts(),1);
  assert.equal(flow.configs[0].watch,false);assert.equal(flow.jobs[0].config.watch,false);
  const saved=structuredClone(flow.saved),w2=browser(11),e2=engine(w2,clock);e2.key='fixture-service-key';w2.login();
  const restored=new PortalFlow(e2,w2,{saved,now:()=>clock.now});connectChanges(e2,w2,restored);await restored.tick();assert.equal(w2.posts(),0);
});
test('a new one-time live job preserves unrelated active watches',async()=>{
  const clock={now:1000000},w=browser(11),e=engine(w,clock);e.key='fixture-service-key';const flow=new PortalFlow(e,w,{now:()=>clock.now});connectChanges(e,w,flow);await ready(flow,w);
  await flow.configure([config()]);await flow.tick();await flow.configure([config({id:'other',watch:false})]);await flow.tick();
  assert.equal(flow.jobs.find(x=>x.id==='glove-live').phase,'watching');assert.equal(flow.jobs.find(x=>x.id==='other-live').watch,null);
});

test('equipment live and sold share a base ID, persist separately, and never auto replay after restore',async()=>{
 const clock={now:1000000},w=browser(11),e=engine(w,clock);const flow=new PortalFlow(e,w,{now:()=>clock.now});connectChanges(e,w,flow);await ready(flow,w);
 const configs=['live','sold'].map(mode=>config({id:'gear-pair',keyword:'가상 장갑',category:'ALL',budgetMode:'none',mode,watch:false,compare:false}));
 assert.equal(normalizeJobs(configs).length,2);assert.throws(()=>normalizeJobs([configs[0],configs[0]]),/중복/);
 await flow.configure(configs);await flow.tick();assert.equal(w.posts(),2);assert.equal(flow.jobs.find(x=>x.id==='gear-pair-live').phase,'complete');assert.equal(flow.jobs.find(x=>x.id==='gear-pair-sold').phase,'market-ready');
 const saved=structuredClone(flow.saved),w2=browser(11),e2=engine(w2,clock);w2.login();const restored=new PortalFlow(e2,w2,{saved,now:()=>clock.now});connectChanges(e2,w2,restored);await restored.tick();assert.equal(w2.posts(),0);assert.equal(restored.jobs.length,2);
});
test('disabled rows do not block submission; invalid enabled grades do',()=>{
 assert.doesNotThrow(()=>normalizeJobs([config({enabled:false,keyword:'',category:'bad',potentialGrade:'bad',potentialOptions:'bad',intervalMinutes:'bad'})]));
 assert.throws(()=>normalizeJobs([config({potentialGrade:'bad'})]));assert.throws(()=>normalizeJobs([config({mode:'bad'})]));
});

test('both retired search modes are rejected under the old ID or the item name',()=>{
 for(const mode of ['sold','live'])for(const field of [{id:'sol-fragment'},{keyword:'솔 에르다 조각'}])
   assert.throws(()=>normalizeJobs([config({...field,mode})]),/기능은 종료/);
});

test('HTTP preserves Ethernel live search and 14-day equipment history while refusing stale fragment submissions',async()=>{
 const dir=await mkdtemp(join(tmpdir(),'maple-jobs-regression-')),clock={now:1000000};let app;const wire=browser(11);
 try{
  app=await createPortal({port:0,dataDir:dir,timerMs:0,now:()=>clock.now,browserFactory:()=>wire,engineFactory:w=>engine(w,clock)});const {port}=await app.listen(),base=`http://127.0.0.1:${port}`;
  const create=await fetch(base+'/api/session',{method:'POST',headers:{origin:base,'content-type':'application/json'},body:'{}'}),cookie=create.headers.get('set-cookie').split(';')[0];const csrf=(await(await fetch(base+'/api/bootstrap',{headers:{cookie}})).json()).csrf;
  const post=data=>fetch(base+'/api/jobs',{method:'POST',headers:{cookie,origin:base,'x-portal-token':csrf,'content-type':'application/json'},body:JSON.stringify(data)});
  const catalog=await(await fetch(base+'/api/catalog?category=ARMOR_ARMOR_GLOVE&job=MAGE&q='+encodeURIComponent('에테르넬'))).json();const glove=catalog.items.find(x=>x.name==='에테르넬 메이지글러브');assert.ok(glove);
  const gradeOptions={potentialOptions:[{option:'criticalDamagePercent',minValue:15},{option:'intPercent',minValue:10}],additionalPotentialOptions:[{option:'intPercent',minValue:4},{option:'magicAttack',minValue:11}]};
  const r=await post({jobs:[config({id:'ethernel',itemId:glove.id,keyword:glove.name,jobClass:'MAGE',watch:false,compare:false,...gradeOptions}),config({id:'disabled-bad',enabled:false,itemId:'invalid',potentialGrade:'bad'})]});assert.equal(r.status,202,await r.text());
  wire.login();await app.tick();assert.equal(wire.posts(),0);
  assert.equal((await fetch(base+'/api/character/select',{method:'POST',headers:{cookie,origin:base,'x-portal-token':csrf,'content-type':'application/json'},body:JSON.stringify({characterId:110})})).status,200);
  await app.tick();assert.equal(wire.posts(),1);
  const obsolete=await post({jobs:[config({id:'sol-fragment',mode:'sold',keyword:'솔 에르다 조각'})]});
  assert.equal(obsolete.status,400);assert.match(await obsolete.text(),/기능은 종료/);await app.tick();assert.equal(wire.posts(),1);
  const sold=config({id:'ethernel',mode:'sold',itemId:glove.id,keyword:glove.name,compare:false,watch:false});
  const response=await post({jobs:[sold]});assert.equal(response.status,202,await response.text());await app.tick();assert.equal(wire.posts(),2);
  const state=await(await fetch(base+'/api/state',{headers:{cookie}})).json();assert.deepEqual(state.jobs.map(x=>x.id),['ethernel-live','ethernel-sold']);
  assert.equal(state.jobs[1].phase,'market-ready');assert.equal(state.jobs[1].result.market.daily.length,14);
  assert.equal((await post({jobs:[sold,sold]})).status,400);assert.equal(wire.posts(),2);
  assert.equal((await post({jobs:{}})).status,400);assert.equal(wire.posts(),2);
 }finally{await app?.close();await rm(dir,{recursive:true,force:true});}
});

test('deleting an armed watch removes its persisted history, future polls, and detail access',async()=>{
 const clock={now:1000000},w=browser(11),e=engine(w,clock);e.key='fixture-service-key';const flow=new PortalFlow(e,w,{now:()=>clock.now});connectChanges(e,w,flow);await ready(flow,w);await flow.configure([config()]);await flow.tick();assert.equal(w.posts(),1);
 await flow.remove('glove-live');clock.now+=INTERVAL_MS*5;await flow.tick();assert.equal(w.posts(),1);assert.equal(flow.jobs.length,0);assert.equal(flow.saved.jobs.length,0);assert.equal(flow.configs.length,0);assert.throws(()=>flow.itemDetail('glove-live','a'),/없는 작업/);await assert.rejects(()=>flow.retry('glove-live'),e=>e.status===404);
 const w2=browser(11),e2=engine(w2,clock);w2.login();const restored=new PortalFlow(e2,w2,{saved:structuredClone(flow.saved),now:()=>clock.now});connectChanges(e2,w2,restored);await restored.tick();assert.equal(w2.posts(),0);assert.equal(restored.jobs.length,0);
});
test('deleting during quota lookup prevents the search POST and completion cannot resurrect the record',async()=>{
 const clock={now:1000000},w=browser(11),e=engine(w,clock),flow=new PortalFlow(e,w,{now:()=>clock.now});connectChanges(e,w,flow);await ready(flow,w);await flow.tick();const original=w.request.bind(w);let release,entered;
 const started=new Promise(r=>entered=r);w.request=async cmd=>{if(new URL(cmd.url).pathname.endsWith('/daily-limit')){entered();await new Promise(r=>release=r);}return original(cmd);};
 await flow.configure([config({watch:false,compare:false})]);const pending=flow.tick();await started;await flow.remove('glove-live');release();await pending;assert.equal(w.posts(),0);assert.equal(flow.jobs.length,0);assert.equal(flow.saved.jobs.length,0);
});

test('deleting an in-flight search lets its response finish without reviving a watch or history',async()=>{
 const clock={now:1000000},w=browser(11),e=engine(w,clock),flow=new PortalFlow(e,w,{now:()=>clock.now});connectChanges(e,w,flow);await ready(flow,w);await flow.tick();const original=w.request.bind(w);let release,entered;
 const started=new Promise(r=>entered=r);w.request=async cmd=>{const reply=await original(cmd);if(cmd.method==='POST'){entered();await new Promise(r=>release=r);}return reply;};
 await flow.configure([config({compare:false})]);const pending=flow.tick();await started;await flow.remove('glove-live');release();await pending;clock.now+=INTERVAL_MS*2;await flow.tick();assert.equal(w.posts(),1);assert.equal(flow.jobs.length,0);assert.equal(flow.saved.jobs.length,0);
});

test('offline restart drops fragment watches, results and samples without activating a browser or touching equipment history',async()=>{
 const dir=await mkdtemp(join(tmpdir(),'maple-retired-fragment-'));let app;let activations=0;
 try{
  const store=new PortalStore(dir);await store.init();const {record,token}=await store.create();
  const live=config({id:'sol-fragment',keyword:'솔 에르다 조각',mode:'live'}),sold={...live,mode:'sold'},gear=config({mode:'sold',watch:false,compare:false});
  const gearJob={id:'glove-sold',config:gear,phase:'market-ready',result:{market:{sampleCount:12}}};
  record.flow={version:2,owner:'same-owner',characterId:110,configs:[live,sold,gear],jobs:[
    {id:'sol-fragment-live',config:live,phase:'watching',watch:{seen:[]}},
    {id:'sol-fragment-sold',config:sold,phase:'queued',archive:[{id:'sample'}]},gearJob]};
  await store.save(record);
  const legacy=(await store.create()).record;legacy.flow={owner:'old-owner',watch:{input:{keyword:'솔에르다 조각'}},plan:{budget:200}};await store.save(legacy);
  app=await createPortal({port:0,dataDir:dir,timerMs:0,browserFactory:()=>{activations++;return browser(11);}});await app.listen();await app.tick();
  assert.equal(activations,0);const result=app.store.find(token).flow;
  assert.equal(result.owner,'same-owner');assert.equal(result.characterId,110);assert.deepEqual(result.configs,[gear]);assert.deepEqual(result.jobs,[gearJob]);
  assert.deepEqual(app.store.records.get(legacy.id).flow,{owner:'old-owner',version:2,configs:[],jobs:[]});
  const onDisk=JSON.parse(await readFile(join(dir,record.id+'.json'),'utf8'));assert.deepEqual(onDisk.flow,result);
 }finally{await app?.close();await rm(dir,{recursive:true,force:true});}
});
test('clear finished leaves active watches intact',async()=>{
 const clock={now:1000000},w=browser(11),e=engine(w,clock);e.key='fixture-service-key';const f=new PortalFlow(e,w,{now:()=>clock.now});connectChanges(e,w,f);await ready(f,w);await f.configure([config(),config({id:'once',watch:false})]);await f.tick();assert.equal(await f.clearFinished(),1);assert.equal(f.jobs.length,1);assert.equal(f.jobs[0].phase,'watching');
});
test('item detail and delete HTTP routes enforce user ownership and CSRF; lists omit tooltip payload',async()=>{
 const dir=await mkdtemp(join(tmpdir(),'maple-details-http-')),clock={now:1000000};let app;const wires=[];
 try{app=await createPortal({port:0,dataDir:dir,timerMs:0,now:()=>clock.now,browserFactory:()=>{const w=browser(wires.length+11),base=w.request.bind(w);w.request=async cmd=>{const r=await base(cmd);if(cmd.method==='POST'){const x={_id:'detail-id',itemName:'장갑',pricePerItem:'20000000000',quantity:1,starforce:17,status:'ON_SALE',endDate:'2026-11-05T00:00:00Z',toolTip:{categories:['장갑'],reqJob:'마법사',stat:{int:120},baseStat:{int:40},itemCode:'private-payload'}};r.bodyText=JSON.stringify({searchKey:'detail-key',total:1,items:[x],page:1,totalPages:1,hasNext:false});}return r;};wires.push(w);return w;},engineFactory:w=>engine(w,clock)});
 const {port}=await app.listen(),base=`http://127.0.0.1:${port}`;const create=async()=>{const r=await fetch(base+'/api/session',{method:'POST',headers:{origin:base,'content-type':'application/json'},body:'{}'});const cookie=r.headers.get('set-cookie').split(';')[0];return {cookie,csrf:(await(await fetch(base+'/api/bootstrap',{headers:{cookie}})).json()).csrf};};const a=await create(),b=await create();
 const post=(path,data,who=a,csrf=who.csrf)=>fetch(base+path,{method:'POST',headers:{origin:base,cookie:who.cookie,'x-portal-token':csrf,'content-type':'application/json'},body:JSON.stringify(data)});
 assert.equal((await post('/api/jobs',{jobs:[config({compare:false,watch:false})]})).status,202);wires[0].login();await app.tick();assert.equal((await post('/api/character/select',{characterId:110})).status,200);await app.tick();
 const detailPath='/api/jobs/glove-live/item?itemId=detail-id';const list=await(await fetch(base+'/api/jobs/glove-live/items',{headers:{cookie:a.cookie}})).json();assert.equal(list.items[0].itemDetails,undefined);
 const detail=await(await fetch(base+detailPath,{headers:{cookie:a.cookie}})).json();assert.equal(detail.itemDetails.stats.int,120);assert.equal(JSON.stringify(detail).includes('private-payload'),false);assert.equal((await fetch(base+detailPath,{headers:{cookie:b.cookie}})).status,404);
 assert.equal((await post('/api/jobs/glove-live/delete',{},a,b.csrf)).status,403);assert.equal((await post('/api/jobs/glove-live/delete',{})).status,200);assert.equal((await fetch(base+detailPath,{headers:{cookie:a.cookie}})).status,404);assert.equal(wires[0].posts(),1);
 }finally{await app?.close();await rm(dir,{recursive:true,force:true});}
});
