import {test} from 'node:test';
import assert from 'node:assert/strict';
import {getDamageBreakdown,getAnchoredDamageBreakdown,getFinalDamageChangeRate,getStatsAfterEquipmentReplacement,auditDamageBaseline,responseCacheFresh,RESPONSE_CACHE_TTL_MS,readCachedResponse,writeCachedResponse} from '../dist/core.mjs';
const close=(a,b)=>assert.ok(Math.abs(a-b)<1e-8,`${a} != ${b}`);
const context=()=>({job:'에반',level:288,bossDefenseRate:380,stats:{기본:{마력:100,크확:100,크뎀:35,방무:[90]},AP:{INT:1000,LUK:100,STR:4,DEX:4},장비:{장갑:{name:'가상 장갑',stat:{INT:100,INT퍼:20,마력:10,마력퍼:10,크뎀:8}}},세트효과:{}},observedFinalStats:{STR:20,DEX:20,INT:5000,LUK:200,마력:1000,공격력:10,'크리티컬 확률':100,'크리티컬 데미지':60,'데미지':30,'보스 몬스터 데미지':200,'방어율 무시':95,'최종 데미지':10}});
test('API-anchored baseline equals actual current stats despite missing non-equipment effects',()=>{
 const c=context(),d=getAnchoredDamageBreakdown(c,c.stats);
 assert.equal(d.finalStats.INT,5000);assert.equal(d.finalStats.LUK,200);assert.equal(d.factors.attack,1000);
 close(d.factors.stat,20200);close(d.factors.critical,1.95);close(d.factors.defense,.81);close(d.factors.damage,3.3);close(d.factors.finalDamage,1.1);
 assert.equal(getFinalDamageChangeRate(c,c.stats),0);assert.equal(auditDamageBaseline(c).verified,false);
});
test('equipment delta follows independent hand calculation and keeps % and fixed stats distinct',()=>{
 const c=context(),changed=structuredClone(c.stats);changed.장비.장갑.stat.INT+=10;changed.장비.장갑.stat.마력+=10;changed.장비.장갑.stat.크뎀+=8;
 const d=getAnchoredDamageBreakdown(c,changed);assert.equal(d.finalStats.INT,5012);assert.equal(d.factors.attack,1011);close(d.factors.critical,2.03);
 const expected=Math.round(((20248/20200)*(1011/1000)*(2.03/1.95)-1)*10000)/100;
 assert.equal(getFinalDamageChangeRate(c,changed),expected);
});
test('critical chance is fixed at 100 percent for every comparison',()=>{
 const c=context();delete c.observedFinalStats;
 for(const [chance,factor] of [[0,1.43],[50,1.43],[100,1.43],[140,1.43]]){c.stats.기본.크확=chance;close(getDamageBreakdown(c,c.stats).factors.critical,factor);}
});
test('IED and final damage are multiplicative; defense zero baseline is rejected',()=>{
 const c=context();delete c.observedFinalStats;c.stats.기본.방무=[50,50];c.stats.기본.최종뎀=[20,30];c.bossDefenseRate=300;
 const d=getDamageBreakdown(c,c.stats);close(d.factors.defense,.25);close(d.factors.finalDamage,1.56);
 c.bossDefenseRate=400;assert.throws(()=>getFinalDamageChangeRate(c,c.stats),/변경 전 데미지/);
});
test('API IED delta removes and adds a source multiplicatively rather than subtracting percentages',()=>{
 const c=context();c.stats.장비.장갑.stat.방무=[10];const changed=structuredClone(c.stats);changed.장비.장갑.stat.방무=[20];
 const d=getAnchoredDamageBreakdown(c,changed);close(d.finalStats['방어율 무시'],100*(1-.05*.8/.9));
});
test('fixed symbol stats remain outside percentage changes',()=>{
 const c=context();c.stats.심볼={INT미적용:1000};const changed=structuredClone(c.stats);changed.장비.장갑.stat.INT퍼+=10;
 assert.equal(getAnchoredDamageBreakdown(c,changed).finalStats.INT,5333);
});
test('unchanged set counts preserve actual API effects instead of replacing them with a partial DB',()=>{
 const c=context();c.stats.장비.장갑.name='아케인셰이드 메이지글러브';c.stats.세트효과={'아케인셰이드 세트(마법사)':{마력:123,크뎀:7}};
 const changed=getStatsAfterEquipmentReplacement(c.stats,{slot:'장갑',name:'아케인셰이드 메이지글러브',stat:c.stats.장비.장갑.stat});
 assert.deepEqual(changed.세트효과,c.stats.세트효과);assert.equal(getFinalDamageChangeRate(c,changed),0);
});
test('missing/nonfinite baselines and special job formulae cannot produce plausible bogus numbers',()=>{
 const c=context();c.observedFinalStats.INT=NaN;assert.throws(()=>getAnchoredDamageBreakdown(c,c.stats));
 c.observedFinalStats.INT=5000;c.job='데몬어벤져';assert.throws(()=>getAnchoredDamageBreakdown(c,c.stats),/특수 스탯/);
 c.job='에반';c.bossDefenseRate=-1;assert.throws(()=>getAnchoredDamageBreakdown(c,c.stats));
 c.bossDefenseRate=380;c.stats.기본.마력=Infinity;assert.throws(()=>getAnchoredDamageBreakdown(c,c.stats));
});
test('character cache expires after fifteen minutes, including memory hits; malformed times expire',()=>{
 const now=10000000;assert.ok(responseCacheFresh(now-1000,now));assert.equal(responseCacheFresh(now-RESPONSE_CACHE_TTL_MS,now),false);assert.equal(responseCacheFresh(NaN,now),false);assert.equal(responseCacheFresh(now+600000,now),false);
});

test('cache reads expire both in-memory and persisted responses',async()=>{
 const {mkdtemp,rm}=await import('node:fs/promises');const {tmpdir}=await import('node:os');const {join}=await import('node:path');
 const dir=await mkdtemp(join(tmpdir(),'maple-cache-regression-')),oldDirectory=process.env.MAPLE_AUCTION_DATA_DIR,originalNow=Date.now;let now=originalNow();
 process.env.MAPLE_AUCTION_DATA_DIR=dir;Date.now=()=>now;
 try{await writeCachedResponse('https://fixture.invalid/stat',{value:1});assert.equal((await readCachedResponse('https://fixture.invalid/stat')).data.value,1);now+=RESPONSE_CACHE_TTL_MS;assert.equal(await readCachedResponse('https://fixture.invalid/stat'),null);}finally{Date.now=originalNow;if(oldDirectory===undefined)delete process.env.MAPLE_AUCTION_DATA_DIR;else process.env.MAPLE_AUCTION_DATA_DIR=oldDirectory;await rm(dir,{recursive:true,force:true});}
});
test('set count changes use official API full definitions rather than stale static amounts',()=>{
 const stats={기본:{},AP:{},장비:{모자:{name:'에테르넬 메이지햇',stat:{}},상의:{name:'에테르넬 메이지로브',stat:{}},장갑:{name:'기존 장갑',stat:{}}},세트효과:{'에테르넬 세트(마법사)':{마력:41}}};
 const after=getStatsAfterEquipmentReplacement(stats,{slot:'장갑',name:'에테르넬 메이지글러브',stat:{}},{에테르넬:{2:{마력:41},3:{마력:42,방무:[10]}}});
 assert.equal(after.세트효과.에테르넬.마력,83);assert.deepEqual(after.세트효과.에테르넬.방무,[10]);assert.equal(after.세트효과['에테르넬 세트(마법사)'],undefined);
});

test('status damage is an explicit target assumption and preserves the API baseline',()=>{
 const c=context();c.observedFinalStats['상태이상 추가 데미지']=20;const off=getAnchoredDamageBreakdown(c,c.stats);close(off.factors.damage,3.3);
 c.statusDamageUptime=1;close(getAnchoredDamageBreakdown(c,c.stats).factors.damage,3.5);assert.equal(getFinalDamageChangeRate(c,c.stats),0);
});

test('critical-rate-only equipment changes have zero gain under the fixed 100% assumption',()=>{
 const c=context();c.observedFinalStats['크리티컬 확률']=30;const changed=structuredClone(c.stats);changed.장비.장갑.stat.크확=50;
 assert.equal(getFinalDamageChangeRate(c,changed),0);close(getAnchoredDamageBreakdown(c,c.stats).factors.critical,1.95);assert.equal(getAnchoredDamageBreakdown(c,c.stats).finalStats['크리티컬 확률'],100);
});
