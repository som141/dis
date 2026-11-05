import {test} from 'node:test';
import assert from 'node:assert/strict';
import {parseQuery,parseMaximums,normalizeMaximumRows,PrototypeEngine} from '../src/domain.mjs';
import {normalizeJobs,PortalFlow} from '../src/portal-flow.mjs';
import {potentialSum,applyPotentialMaximums} from '../src/potential-limits.mjs';

const base={keyword:'에테르넬 메이지글러브',category:'ARMOR_ARMOR_GLOVE',budget:200};
const entry=(...lines)=>({grade:4,entries:lines.map(text=>({text}))});
const sample=(id,potential,additional,powerDiff)=>({id,price:10000000000,powerDiff,itemDetails:{potential,additional}});

test('upper bounds are canonicalized, checked against minimums, and never forwarded as auction filters',()=>{
  const input={...base,potentialOptions:[{option:'intPercent',minValue:10}],potentialMaximums:[{option:'intPercent',maxValue:25},{option:'intPercent',maxValue:20}],additionalPotentialMaximums:[{option:'magicAttack',maxValue:11}]};
  assert.deepEqual(parseMaximums(input).potentialMaximums,[{option:'intPercent',maxValue:20}]);
  assert.equal(Object.hasOwn(parseQuery(input),'potentialMaximums'),false);
  const job=normalizeJobs([{id:'limit',enabled:true,mode:'live',...input}])[0];
  assert.deepEqual(job.potentialMaximums,[{option:'intPercent',maxValue:20}]);
  for(const maxValue of ['',null,true,-1,Infinity,10001,'abc'])assert.throws(()=>normalizeMaximumRows([{option:'intPercent',maxValue}]));
  assert.throws(()=>normalizeMaximumRows([{option:'hasteSkill',maxValue:1}]));
  assert.throws(()=>parseQuery({...input,potentialMaximums:[{option:'intPercent',maxValue:9}]}),/최소치가 상한/);
  assert.throws(()=>parseQuery({...base,potentialNone:true,potentialMaximums:[{option:'intPercent',maxValue:0}]}),/잠재 없음/);
});

test('tooltip option sums separate percentage and flat, main and additional, and exclude unknowns',()=>{
  const main=entry('크리티컬 데미지 +8%','크리티컬 데미지 +8%','INT +10%');
  const additional=entry('INT +4%','마력 +11');
  assert.equal(potentialSum(main,'criticalDamagePercent'),16);
  assert.equal(potentialSum(main,'intPercent'),10);
  assert.equal(potentialSum(additional,'magicAttack'),11);
  assert.equal(potentialSum(additional,'magicAttackPercent'),0);
  assert.equal(potentialSum(entry('스킬 재사용 대기시간 -2초'),'skillCooldownReduction'),2);
  assert.equal(potentialSum(undefined,'intPercent'),null);
  assert.equal(potentialSum(entry('INT 증가'),'intPercent'),null);
  const records=[sample('pass',main,additional,10),sample('too-high',entry('크리티컬 데미지 +24%'),additional,100),sample('unknown',undefined,additional,50)];
  const filtered=applyPotentialMaximums(records,[{option:'criticalDamagePercent',maxValue:20}],[{option:'magicAttack',maxValue:11}]);
  assert.deepEqual(filtered.items.map(x=>x.id),['pass']);
  assert.deepEqual(filtered.summary,{enabled:true,before:3,matched:1,excluded:1,unknown:1});
});

test('combat power sorting uses available provider deltas and puts missing deltas last',()=>{
  const flow=new PortalFlow({}, {}, {});
  flow.jobs=[{id:'test-live',engine:{result:{items:[sample('low',entry(),entry(),5),sample('unknown',entry(),entry(),undefined),sample('high',entry(),entry(),50)]}}}];
  assert.deepEqual(flow.items('test-live',{sort:'power-desc'}).items.map(x=>x.id),['high','low','unknown']);
  assert.deepEqual(flow.items('test-live',{sort:'default'}).items.map(x=>x.id),['low','unknown','high']);
});

test('search filters only fetched results and retains raw coverage counts',async()=>{
  let posts=0,body;
  const wire={connected:true,epoch:1,scope(){return this;},async request(cmd){
    const path=new URL(cmd.url).pathname;let data;
    if(path==='/v1/accounts')data={accounts:[{accountId:1}]};
    else if(path.endsWith('/characters'))data={characters:path.includes('/gameWorlds/0/')?[{characterId:2,characterName:'테스트',level:280}]:[]};
    else if(path.endsWith('/daily-limit'))data={search:{limit:100,remaining:100-posts}};
    else if(cmd.method==='POST'){posts++;body=cmd.body;data={searchKey:'fixture',page:1,total:2,totalPages:1,hasNext:false,items:[
      {_id:'one',itemName:'장갑',pricePerItem:'10000000000',quantity:1,status:'ON_SALE',starforce:17,endDate:'2026-11-05',wishlistCount:0,attackPowerDiff:5,toolTip:{upgradeInfo:{potential:entry('INT +10%')}}},
      {_id:'two',itemName:'장갑',pricePerItem:'20000000000',quantity:1,status:'ON_SALE',starforce:17,endDate:'2026-11-05',wishlistCount:0,attackPowerDiff:100,toolTip:{upgradeInfo:{potential:entry('INT +30%')}}},
    ]};}else throw Error('unexpected request');
    return {id:'fixture',ok:true,status:200,bodyText:JSON.stringify(data)};
  }};
  const engine=new PrototypeEngine(wire,{manageKeyEnvironment:false,loadSnapshot:async()=>{throw Error('unused');}});
  await engine.connect();await engine.selectCharacter(2);
  const result=await engine.search({...base,potentialMaximums:[{option:'intPercent',maxValue:20}]});
  assert.equal(posts,1);assert.equal(result.collection.loaded,2);assert.equal(result.total,2);
  assert.deepEqual(result.items.map(x=>x.id),['one']);
  assert.deepEqual(result.localFilter,{enabled:true,before:2,matched:1,excluded:1,unknown:0});
  assert.doesNotMatch(JSON.stringify(body),/maxValue|potentialMaximums/);
  engine.destroy();
});
