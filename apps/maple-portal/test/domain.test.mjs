import { test } from 'node:test';
import assert from 'node:assert/strict';
import { parseQuery, rankItems, topTenChanges, PrototypeEngine, INTERVAL_MS } from '../src/domain.mjs';
import { getFinalDamageChangeRate, getDamageBreakdown, compareRingActivation, JOB_STAT } from '../dist/core.mjs';

const query = { keyword:'아케인셰이드 메이지글러브', category:'ARMOR_ARMOR_GLOVE', budget:30 };
function fixtureWire() {
  let remaining = 100, searches = 0, nextItems = [];
  const wire = { connected:true, epoch:1, scope() { return this; }, async request(cmd) {
    const url = new URL(cmd.url);
    let data;
    if (url.pathname === '/v1/accounts') data = { accounts:[{ accountId:111 }] };
    else if (url.pathname.endsWith('/characters')) data = { characters:url.pathname.includes('/gameWorlds/0/') ? [{ characterId:222, characterName:'에반이였솜', level:280 }] : [] };
    else if (url.pathname.endsWith('/daily-limit')) data = { search:{ remaining, limit:100 } };
    else if (cmd.method === 'POST') { ++searches; --remaining; data = { searchKey:`fixture-${searches}`, page:1, total:nextItems.length, totalPages:1, hasNext:false, items:nextItems }; }
    else data = { searchKey:'fixture-page', page:1, total:nextItems.length, totalPages:1, hasNext:false, items:nextItems };
    return { id:'fixture', ok:true, status:200, bodyText:JSON.stringify(data) };
  } };
  return { wire, setItems(items) { nextItems = items; }, setQuota(n) { remaining = n; }, searches:() => searches };
}
const item = id => ({ _id:id, itemName:'테스트 장갑', pricePerItem:'2500000000', quantity:1, starforce:17, status:'ON_SALE', endDate:'2026-11-01T00:00:00Z', wishlistCount:0 });
async function setup(clock = { now:1000000 }) {
  const f = fixtureWire();
  const engine = new PrototypeEngine(f.wire, { now:() => clock.now, loadSnapshot:async () => { throw new Error('fixture: no API key'); } });
  await engine.connect(); await engine.selectCharacter(222);
  return { ...f, engine, clock };
}

test('30억 ±30% and hard cap use meso, option rows and exact category', () => {
  const q = parseQuery({ ...query, option:'intPercent', optionMin:21 });
  assert.equal(q.priceMin, 2100000000); assert.equal(q.priceMax, 3900000000);
  assert.deepEqual(q.potentialOptions, [{ option:'intPercent', minValue:21 }]);
  assert.equal(parseQuery({ ...query, hardCap:true }).priceMax, 3000000000);
  assert.throws(() => parseQuery({ ...query, budget:Infinity }));
  assert.throws(() => parseQuery({ ...query, category:'__proto__' }));
  assert.throws(() => parseQuery({ ...query, option:'unknown' }));
});
test('Evan uses magic attack, and unchanged equipment yields zero improvement', () => {
  const before = { 기본:{ INT:100, LUK:25, 마력:100, 공격력:100, 방무:[90] }, AP:{} };
  const context = { job:'에반', level:280, stats:before };
  assert.equal(getFinalDamageChangeRate(context, before), 0);
  assert.equal(getFinalDamageChangeRate(context, { ...before, 기본:{ ...before.기본, 마력:110 } }), 10);
  assert.equal(getFinalDamageChangeRate(context, { ...before, 기본:{ ...before.기본, 공격력:999 } }), 0);
});
test('alternate official job labels keep their own stat family',()=>{
  const stats={기본:{공격력:100,방무:[100]},AP:{STR:1000,DEX:100}};
  const canonical=getDamageBreakdown({job:'캐논마스터',level:280,stats},stats).coefficient;
  const alias=getDamageBreakdown({job:'캐논슈터',level:280,stats},stats).coefficient;
  assert.equal(alias,canonical);
});
test('every ordinary job uses a finite fixed-crit ring comparison with its own stat family',()=>{
  const jobs=Object.entries(JOB_STAT).filter(([,stat])=>!['xenon','deven'].includes(stat));
  assert.ok(jobs.length>=46);
  for(const [job] of jobs){
    const stats={기본:{STR:1000,DEX:1000,INT:1000,LUK:1000,공격력:100,마력:100,방무:[100]},AP:{}};
    const character={job,level:280,equipment:{item_equipment:[]},stats};
    const report=compareRingActivation(character,stats,'critical',4);
    assert.ok(Number.isFinite(report.currentActivation),job);
    assert.ok(Math.abs(report.equipmentOff)<1e-9,job);
  }
});
test('ordinary jobs produce finite equipment gain for their own attack type',()=>{
  for(const [job,main] of Object.entries(JOB_STAT)){
    if(['xenon','deven'].includes(main))continue;
    const useMagic=main==='INT',attack=useMagic?'마력':'공격력';
    const stats={기본:{STR:1000,DEX:1000,INT:1000,LUK:1000,공격력:100,마력:100,방무:[100]},AP:{}};
    const changed={...stats,장비:{장갑:{name:'후보',stat:{[attack]:10}}}};
    const gain=getFinalDamageChangeRate({job,level:280,stats},changed);
    assert.ok(Number.isFinite(gain)&&gain>0,`${job}: ${gain}`);
  }
});
test('level-four ring activation compares both equipment states with one fixed character and ring', () => {
  const character={job:'에반',level:280,equipment:{item_equipment:[{item_equipment_slot:'무기',item_base_option:{attack_power:'200',magic_power:'300'}}]},
    stats:{기본:{마력:100,방무:[100]},AP:{INT:1000,LUK:100},장비:{장갑:{name:'현재 장갑',stat:{INT:100,마력:10}}},세트효과:{}}};
  const changed={...character.stats,장비:{장갑:{name:'새 장갑',stat:{INT:100,마력:21}}}};
  const critical=compareRingActivation(character,changed,'critical',4);
  for(const value of [critical.currentActivation,critical.candidateActivation])assert.ok(Math.abs(value-20)<1e-9);
  for(const value of [critical.equipmentOff,critical.equipmentOn])assert.ok(Math.abs(value-10)<1e-9);
  const risk=compareRingActivation(character,changed,'risk',4);
  assert.ok(risk.currentActivation>0);assert.ok(risk.equipmentOn>0);
  const restraint=compareRingActivation(character,changed,'restraint',6);
  assert.ok(restraint.currentActivation>0);
  const continuous=compareRingActivation(character,changed,'continuous',6);
  assert.ok(continuous.currentActivation>0);
  assert.equal(continuous.name,'컨티뉴어스 링');
  assert.throws(()=>compareRingActivation(character,changed,'restraint',4),/검증된/);
  const weapon=compareRingActivation(character,changed,'weapon-i',4);
  assert.ok(weapon.currentActivation>0);assert.equal(weapon.name,'웨폰퍼프 - I링');
  assert.throws(()=>compareRingActivation(character,changed,'weapon-i',3),/검증된/);
  assert.throws(()=>compareRingActivation({...character,equipment:{item_equipment:[]}},changed,'weapon-i',4),/주무기/);
});
test('cost effectiveness uses incremental gain; unsupported is not zero', () => {
  const result = rankItems([
    { id:'unknown', price:1 },
    { id:'expensive', price:4000000000, finalDamageChangeRate:{ 장갑:4 } },
    { id:'better', price:2000000000, finalDamageChangeRate:{ 장갑:3 } },
    { id:'loss', price:1000000000, finalDamageChangeRate:{ 장갑:-2 } },
  ]);
  assert.equal(result[0].id, 'better'); assert.equal(result[0].comparison.efficiency, .15);
  assert.equal(result[0].comparison.score, 103); assert.equal(result.at(-1).comparison, null);
});
test('maximum starforce is validated and filtered after a single provider search', async()=>{
  assert.throws(()=>parseQuery({...query,starforceMin:17,starforceMax:16}),/최대 스타포스/);
  assert.throws(()=>parseQuery({...query,starforceMax:31}),/최대 스타포스/);
  const {engine,setItems,searches}=await setup();
  setItems([{...item('under'),starforce:16},{...item('equal'),starforce:17},{...item('over'),starforce:18},{...item('unknown'),starforce:null}]);
  const result=await engine.search({...query,compare:false,starforceMax:17});
  assert.deepEqual(result.items.map(x=>x.id),['under','equal']);
  assert.deepEqual(result.starforceFilter,{enabled:true,max:17,excluded:2});
  assert.equal(searches(),1);
});
test('only changes within ranked top ten create alerts and an old watch is baselined',()=>{
  const rows=Array.from({length:11},(_,i)=>({id:`item-${i}`,comparison:{gainPercent:2}}));
  assert.equal(topTenChanges(undefined,rows).changes.length,0);
  assert.equal(topTenChanges(rows.slice(0,10).map(x=>x.id),rows).changes.length,0);
  const reordered=[rows[10],...rows.slice(0,10)];
  const result=topTenChanges(rows.slice(0,10).map(x=>x.id),reordered);
  assert.equal(result.changes[0].item.id,'item-10');
  assert.equal(result.changes[0].rank,1);
  assert.equal(result.changes[0].previousRank,null);
  assert.equal(result.changes.length,10);
  assert.equal(topTenChanges(rows.slice(0,10).map(x=>x.id),reordered,3).changes.length,0);
});
test('real auction adapter discovers owned character and fresh searches consume quota', async () => {
  const { engine, searches } = await setup();
  assert.equal(engine.character.name, '에반이였솜');
  assert.equal((await engine.search(query)).searchRemaining, 99);
  assert.equal((await engine.search(query)).searchRemaining, 98);
  assert.equal(searches(), 2);
  await assert.rejects(() => engine.selectCharacter(999), /본인 캐릭터/);
});
test('auction tooltip → actual Evan equipment replacement → gain and efficiency', async () => {
  const f = fixtureWire();
  const engine = new PrototypeEngine(f.wire, { loadSnapshot:async () => ({
    name:'에반이였솜', job:'에반', level:280, equipment:{ item_equipment:[] },
    stats:{ 기본:{ 마력:100, 방무:[100] }, AP:{ INT:1000, LUK:100 }, 장비:{ 장갑:{ name:'현재 장갑', stat:{ INT:100, 마력:10 } } }, 세트효과:{} },
  }) });
  engine.setKey('fixture-only-test-key');
  try {
    await engine.connect(); await engine.selectCharacter(222);
    f.setItems([{ ...item('better-glove'), toolTip:{ categories:['방어구', '장갑'], reqJob:'마법사', stat:{ int:100, mad:21 }, upgradeInfo:{} } }]);
    const result = await engine.search(query);
    assert.equal(result.items[0].comparison.slot, '장갑');
    assert.equal(result.items[0].comparison.gainPercent, 10);
    assert.equal(result.items[0].comparison.score, 110);
    assert.equal(result.items[0].comparison.efficiency, .4);
    const withRing=await engine.search({...query,ringChoice:'critical',ringLevel:4,ringBaselineOff:true});
    assert.equal(withRing.items[0].comparison.details.ring,undefined,'retired ring controls must not affect new searches');
  } finally { engine.destroy(); }
});
test('Evan document secondary equipment can be compared with an empty slot',async()=>{
  const f=fixtureWire();
  const e=new PrototypeEngine(f.wire,{loadSnapshot:async()=>({name:'에반이였솜',job:'에반',level:280,
    equipment:{item_equipment:[]},stats:{기본:{마력:100,방무:[100]},AP:{INT:1000,LUK:100},장비:{},세트효과:{}}})});
  e.setKey('fixture-key');
  try{
    await e.connect();await e.selectCharacter(222);
    f.setItems([{...item('dragon-book'),itemName:'드래곤마스터의 유산',toolTip:{categories:['서적'],reqJob:'에반',reqLevel:100,stat:{int:10,mad:21}}}]);
    const result=await e.search({...query,keyword:'드래곤마스터의 유산',category:'WEAPON_SUB'});
    assert.equal(result.items[0].comparison.slot,'보조무기');
    assert.ok(result.items[0].comparison.gainPercent>0);
  }finally{e.destroy();}
});
test('Angelic Buster emblem uses the emblem slot despite a power-source tooltip',async()=>{
  const f=fixtureWire();
  const e=new PrototypeEngine(f.wire,{loadSnapshot:async()=>({name:'테스트캐릭터',job:'엔젤릭버스터',level:280,
    equipment:{item_equipment:[]},stats:{기본:{공격력:100,방무:[100]},AP:{DEX:1000,STR:100},장비:{},세트효과:{}}})});
  e.setKey('fixture-key');
  try{
    await e.connect();await e.selectCharacter(222);
    f.setItems([{...item('angel-emblem'),itemName:'엔젤 엠블렘',toolTip:{categories:['파워소스'],reqJob:'해적',reqLevel:100,stat:{dex:10,pad:21}}}]);
    const result=await e.search({...query,keyword:'엔젤 엠블렘',category:'ARMOR_ACCESSORY_EMBLEM'});
    assert.equal(result.items[0].comparison.slot,'엠블렘');
    assert.ok(result.items[0].comparison.gainPercent>0);
  }finally{e.destroy();}
});
test('Zero Astra Hourglass does not report a false generic secondary gain',async()=>{
  const f=fixtureWire();
  const e=new PrototypeEngine(f.wire,{loadSnapshot:async()=>({name:'제로캐릭터',job:'제로',level:280,
    equipment:{item_equipment:[]},stats:{기본:{공격력:100,방무:[100]},AP:{STR:1000,DEX:100},장비:{},세트효과:{}}})});
  e.setKey('fixture-key');
  try{
    await e.connect();await e.selectCharacter(222);
    f.setItems([{...item('zero-hourglass'),itemName:'아스트라 아워글라스',toolTip:{categories:['보조무기'],reqJob:'전체',reqLevel:200,stat:{str:10,pad:21}}}]);
    const result=await e.search({...query,keyword:'아스트라 아워글라스',category:'WEAPON_SUB'});
    assert.equal(result.items[0].comparison,null);
    assert.match(JSON.stringify(result.items[0].comparisonErrors),/대검·태도/);
  }finally{e.destroy();}
});
test('quota exhaustion and unknown quota prevent a search POST', async () => {
  const { engine, setQuota, searches } = await setup();
  setQuota(0); await assert.rejects(() => engine.search(query), e => e.code === 'QUOTA_EXHAUSTED');
  setQuota(undefined); await assert.rejects(() => engine.search(query), e => e.code === 'QUOTA_UNKNOWN');
  assert.equal(searches(), 0);
});
test('30-minute watch suppresses baseline, fresh POST, deduplication and stop', async () => {
  const { engine, setItems, clock, searches } = await setup();
  setItems([item('old')]); await engine.search(query); engine.startWatch({ days:1 });
  assert.equal(engine.watch.intervalMs, 1800000); assert.equal(engine.notices.length, 0);
  await engine.tick(); assert.equal(searches(), 1);
  setItems([item('old'), item('new')]); clock.now += INTERVAL_MS; await engine.tick();
  assert.equal(searches(), 2); assert.equal(engine.notices.length, 1); assert.equal(engine.notices[0].item.id, 'new');
  clock.now += INTERVAL_MS; await engine.tick(); assert.equal(engine.notices.length, 1);
  engine.stopWatch(); clock.now += INTERVAL_MS; await engine.tick(); assert.equal(searches(), 3);
});

test('portal completes up to sixty results by GET and keeps watch baselines without extra search POSTs',async () => {
  const f=fixtureWire(),clock={now:1000000},request=f.wire.request.bind(f.wire);
  let gets=0,failPage=false;
  f.wire.request=async cmd=>{
    const url=new URL(cmd.url);
    if(cmd.method==='GET' && url.pathname.includes('/searches/')) {
      gets++;assert.equal(url.searchParams.get('limit'),'60');
      if(failPage) return {ok:false,status:404,code:'HTTP_ERROR',error:'fixture expired page'};
    }
    const reply=await request(cmd);
    if(url.pathname.includes('/searches/')) {
      const data=JSON.parse(reply.bodyText);
      if(cmd.method==='POST') {data.items=data.items.slice(0,10);data.hasNext=data.total>10;}
      else data.searchKey=url.pathname.split('/').at(-2);
      reply.bodyText=JSON.stringify(data);
    }
    return reply;
  };
  const engine=new PrototypeEngine(f.wire,{now:()=>clock.now,completePageLimit:60,loadSnapshot:async()=>{throw new Error('fixture: no key');}});
  await engine.connect();await engine.selectCharacter(222);
  f.setItems(Array.from({length:25},(_,i)=>item(`item-${i}`)));
  const result=await engine.search({...query,budget:200});
  assert.equal(result.items.length,25);assert.equal(result.total,25);assert.equal(result.searchRemaining,99);
  assert.equal(result.query.priceMin,14000000000);assert.equal(result.query.priceMax,26000000000);
  engine.startWatch();assert.equal(engine.watch.seen.size,25);assert.equal(engine.notices.length,0);
  f.setItems(Array.from({length:26},(_,i)=>item(`item-${i}`)));clock.now+=INTERVAL_MS;await engine.tick();
  assert.equal(f.searches(),2);assert.equal(gets,2);assert.equal(engine.notices.length,0,'outside top ten does not notify');
  failPage=true;clock.now+=INTERVAL_MS;await engine.tick();
  assert.equal(f.searches(),3,'a failed GET must not recreate the charged search');
  await engine.tick();assert.equal(f.searches(),3,'no immediate POST retry after failure');
  failPage=false;f.setItems(Array.from({length:61},(_,i)=>item(`item-${i}`)));
  await engine.search({...query,budget:200});assert.equal(gets,4,'retrieve the configured scope without requesting a second page');
  assert.throws(()=>engine.startWatch(),/일부 결과/);
});
test('expired, partial coverage and changed character do not keep watching', async () => {
  const { engine, clock, searches, wire } = await setup();
  await engine.search(query); engine.result.total = 20; engine.result.collection.complete=false;
  assert.throws(() => engine.startWatch(), /일부 결과/);
  engine.result.total = 0; engine.startWatch(); clock.now += 86400000; await engine.tick();
  assert.equal(engine.watch.status, '만료'); assert.equal(searches(), 1);
  engine.startWatch(); ++wire.epoch; engine.connectionChanged();
  assert.equal(engine.character, null); assert.equal(engine.result, null); assert.equal(engine.watch.status, '인증 필요');
  clock.now += INTERVAL_MS; await engine.tick(); assert.equal(searches(), 1);
});
test('watch quota errors wait without immediate retry and long calls do not overlap', async () => {
  const { engine, clock, setQuota, searches } = await setup();
  await engine.search(query); engine.startWatch(); setQuota(0); clock.now += INTERVAL_MS;
  await engine.tick(); assert.equal(engine.watch.status, '한도 대기'); assert.equal(searches(), 1);
  let release;
  const running = engine.run(() => new Promise(r => { release = r; }));
  await assert.rejects(() => engine.run(async () => {}), e => e.code === 'BUSY');
  release(); await running;
});
test('keys are never returned in state and clearing key invalidates calculations', async () => {
  const { engine } = await setup();
  const key = 'fixture-only-test-key';
  engine.setKey(key); assert.equal(engine.state().keyConfigured, true);
  assert.equal(JSON.stringify(engine.state()).includes(key), false);
  engine.setKey(''); assert.equal(engine.state().keyConfigured, false);
  await assert.rejects(() => engine.equipment(), e => e.code === 'NO_KEY');
});

test('multiple potential minimums reach the actual charged POST as independent sum filters',async()=>{
 const f=fixtureWire(),base=f.wire.request.bind(f.wire),posted=[];
 f.wire.request=async cmd=>{if(cmd.method==='POST')posted.push(JSON.parse(cmd.body));return base(cmd);};
 const e=new PrototypeEngine(f.wire,{loadSnapshot:async()=>{throw new Error('not used');}});await e.connect();await e.selectCharacter(222);
 await e.search({...query,compare:false,jobClass:'MAGE',potentialOptions:[{option:'criticalDamagePercent',minValue:15},{option:'intPercent',minValue:10}],additionalPotentialOptions:[{option:'intPercent',minValue:4},{option:'magicAttack',minValue:11}]});
 assert.equal(posted.length,1);assert.equal(posted[0].filters.itemCategory.itemJobCategory,'WIZARD');assert.deepEqual(posted[0].filters.enhancementOption.potentialOptionSum,{criticalDamagePercent:15,intPercent:10});assert.deepEqual(posted[0].filters.enhancementOption.additionalPotentialOptionSum,{intPercent:4,magicAttack:11});assert.equal(posted[0].filters.enhancementOption.potentialOptions,undefined);
});
test('a malformed candidate cannot suppress valid comparisons in the same response',async()=>{
 const f=fixtureWire();const e=new PrototypeEngine(f.wire,{loadSnapshot:async()=>({name:'에반이였솜',job:'에반',level:280,equipment:{item_equipment:[]},stats:{기본:{마력:100,방무:[100]},AP:{INT:1000,LUK:100},장비:{장갑:{name:'현재 장갑',stat:{INT:100,마력:10}}},세트효과:{}}})});
 e.setKey('fixture-key');try{await e.connect();await e.selectCharacter(222);f.setItems([{...item('bad'),toolTip:{categories:['장갑'],reqJob:'마법사',stat:{int:'invalid'}}},{...item('good'),toolTip:{categories:['장갑'],reqJob:'마법사',stat:{int:100,mad:21}}}]);const r=await e.search(query);assert.equal(r.items.find(x=>x.id==='good').comparison.gainPercent,10);assert.equal(r.items.find(x=>x.id==='bad').comparison,null);assert.ok(r.items.find(x=>x.id==='bad').comparisonErrors);}finally{e.destroy();}
});

test('provider failures keep actionable safe codes and never leak raw responses or retry POST',async()=>{
 for(const [status,code,expected] of [[401,0,'AUTH_EXPIRED'],[422,4040,'TOO_MANY_RESULTS'],[429,4041,'PROVIDER_THROTTLED'],[429,4042,'QUOTA_EXHAUSTED'],[400,9999,'INVALID_QUERY']]){
  const f=fixtureWire(),original=f.wire.request.bind(f.wire);let posts=0;
  f.wire.request=async cmd=>{if(cmd.method==='POST'){posts++;return {ok:false,status,code:'HTTP_ERROR',error:'private-provider-marker',bodyText:JSON.stringify({code})};}return original(cmd);};
  const e=new PrototypeEngine(f.wire);await e.connect();await e.selectCharacter(222);await assert.rejects(()=>e.search({...query,compare:false}),error=>error.code===expected&&!error.message.includes('private-provider-marker'));
  await e.tick();assert.equal(posts,1);
 }
});

test('retired fragment work is refused before checking quota or searching', async () => {
  const {engine,wire,searches}=await setup();
  wire.request=async()=>{assert.fail('Retired work must not contact the auction');};
  for (const input of [{id:'sol-fragment', sold:true}, {keyword:'솔에르다 조각'}, {keyword:'솔 에르다 조각',sold:true}])
    await assert.rejects(()=>engine.search({...query,...input}), /기능은 종료/);
  assert.equal(searches(),0);
});

test('decent skills are ignored on either side; remaining potential lines and conditional guards still apply', async () => {
  const f=fixtureWire();
  const e=new PrototypeEngine(f.wire,{manageKeyEnvironment:false,loadSnapshot:async()=>({
    name:'에반이였솜',job:'에반',level:280,equipment:{item_equipment:[{
      item_equipment_slot:'장갑',item_equipment_part:'장갑',potential_option_1:'<쓸만한 헤이스트> 스킬 사용 가능',
      additional_potential_option_1:'<쓸만한 미스틱 도어> 스킬 사용 가능',
    }]},stats:{기본:{마력:100,방무:[100]},AP:{INT:1000,LUK:100},장비:{장갑:{name:'현재 장갑',stat:{INT:100,마력:10}}},세트효과:{}},
  })});
  const candidate=(id,mad,main=[],additional=[])=>({...item(id),toolTip:{categories:['장갑'],reqJob:'마법사',stat:{int:100,mad},
    upgradeInfo:{potential:{entries:main.map(text=>({text}))},additionalPotential:{entries:additional.map(text=>({text}))}}}});
  e.setKey('fixture-key');
  try{
    await e.connect();await e.selectCharacter(222);
    f.setItems([candidate('only-skill-change',10),candidate('gain',10,['<쓸만한 샤프 아이즈> 스킬 사용 가능','마력 +5'],['마력 +6','<쓸만한윈드부스터> 스킬 사용 가능']),
      candidate('same-gain-without-skills',10,['마력 +5'],['마력 +6']),candidate('loss',0,['<쓸만한 헤이스트> 스킬 사용 가능']),
      candidate('unmodeled',10,['<쓸만한 헤이스트> 스킬 사용 가능','모든 스킬의 레벨 +1']),
      candidate('cooldown',10,[],['스킬 재사용 대기시간 -2초'])]);
    const items=Object.fromEntries((await e.search(query)).items.map(x=>[x.id,x]));
    assert.equal(items['only-skill-change'].comparison.gainPercent,0);
    assert.equal(items.gain.comparison.gainPercent,10);
    assert.equal(items.gain.comparison.gainPercent,items['same-gain-without-skills'].comparison.gainPercent);
    assert.deepEqual(items.gain.comparison.details.ignoredSkills,['쓸만한 헤이스트','쓸만한 미스틱 도어','쓸만한 샤프 아이즈','쓸만한 윈드 부스터']);
    assert.ok(items.loss.comparison.gainPercent<0);
    assert.equal(items.unmodeled.comparison,null);assert.match(items.unmodeled.comparisonErrors['장갑'],/스킬 레벨/);
    assert.equal(items.cooldown.comparison,null);assert.match(items.cooldown.comparisonErrors['장갑'],/쿨감/);
  }finally{e.destroy();}
});
