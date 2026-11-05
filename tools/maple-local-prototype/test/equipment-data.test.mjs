import {buildCreateBody} from '../dist/core.mjs';
import {test} from 'node:test';
import assert from 'node:assert/strict';
import {findEquipment,catalog} from '../src/equipment-data.mjs';
import {parseQuery,normalizeOptionRows} from '../src/domain.mjs';
test('mage hats include the requested Ethernel hat and exclude warrior-only hats',()=>{
  const result=findEquipment({category:'ARMOR_ARMOR_CAP',job:'MAGE',q:'에테르넬'});
  assert.ok(result.items.some(x=>x.name==='에테르넬 메이지햇'&&x.level===250));
  assert.ok(result.items.every(x=>x.category==='ARMOR_ARMOR_CAP'&&(x.jobs.includes('ALL')||x.jobs.includes('MAGE'))));
  assert.equal(catalog.complete,false);
  assert.ok(findEquipment({category:'ARMOR_ARMOR_CAP',q:'ㅇㅌㄹㄴ'}).total>0);
});
test('explicit budget bounds, ratio, no upper bound and grade-none survive parsing',()=>{
  const base={keyword:'장비',budget:200,category:'ALL'};
  assert.deepEqual({...parseQuery({...base,budgetMode:'custom',budgetMin:120,budgetMax:200})}.priceMin,12000000000);
  assert.equal(parseQuery({...base,budgetMode:'range',budgetPercent:10}).priceMax,22000000000);
  assert.equal(parseQuery({...base,budgetMode:'custom',budgetMin:120,budgetMax:''}).priceMax,undefined);
  assert.throws(()=>parseQuery({...base,budgetMode:'custom',budgetMin:201,budgetMax:200}));
  assert.equal(parseQuery({...base,potentialNone:true}).potentialGrade,0);
  assert.equal(parseQuery({...base,additionalGrade:4,additionalOption:'magicAttack',additionalMin:12}).additionalPotentialOptions[0].minValue,12);
});
test('official Ethernel names are never lost by the historical metadata join',async()=>{
  const {readFile}=await import('node:fs/promises');const official=JSON.parse(await readFile(new URL('../public/data/official-snippets.json',import.meta.url),'utf8'));
  const names=new Set(catalog.items.map(x=>x.name));for(const row of official.filter(x=>x.nameOrigin.startsWith('에테르넬 ')))assert.ok(names.has(row.nameOrigin),row.nameOrigin);
  for(const [job,family] of [['WARRIOR','나이트'],['MAGE','메이지'],['ARCHER','아처'],['THIEF','시프'],['PIRATE','파이렛']])for(const [suffix,category] of [['글러브','ARMOR_ARMOR_GLOVE'],['슈즈','ARMOR_ARMOR_SHOES'],['케이프','ARMOR_ARMOR_CAPE']]){
    const result=findEquipment({job,category,q:'에테르넬'});assert.ok(result.items.some(x=>x.name===`에테르넬 ${family}${suffix}`&&x.level===250));
  }
  assert.equal(new Set(catalog.items.map(x=>x.id)).size,catalog.items.length);
  const all=[];let offset=0;for(;;){const page=findEquipment({category:'ARMOR_ARMOR_CAP',offset});all.push(...page.items);offset+=page.items.length;if(offset>=page.total)break;assert.ok(page.items.length);}
  assert.equal(all.length,findEquipment({category:'ARMOR_ARMOR_CAP'}).total);assert.equal(new Set(all.map(x=>x.id)).size,all.length);
});
test('multiple minimums canonicalize duplicates, reject invalid input, and keep main/additional independent',()=>{
 const q=parseQuery({keyword:'장갑',category:'ARMOR_ARMOR_GLOVE',budget:200,potentialOptions:[{option:'criticalDamagePercent',minValue:15},{option:'intPercent',minValue:10}],additionalPotentialOptions:[{option:'intPercent',minValue:4},{option:'magicAttack',minValue:11}]});
 assert.deepEqual(q.potentialOptions,[{option:'criticalDamagePercent',minValue:15},{option:'intPercent',minValue:10}]);assert.equal(q.additionalPotentialOptions.find(x=>x.option==='magicAttack').minValue,11);
 assert.deepEqual(normalizeOptionRows([{option:'intPercent',minValue:10},{option:'intPercent',minValue:7}]),[{option:'intPercent',minValue:10}]);
 for(const minValue of [-1,NaN,Infinity,'',null,true,'abc'])assert.throws(()=>normalizeOptionRows([{option:'intPercent',minValue}]));
 assert.throws(()=>normalizeOptionRows([{option:'__proto__',minValue:1}]));assert.throws(()=>normalizeOptionRows({}));
 assert.throws(()=>parseQuery({keyword:'a',budget:1,potentialNone:true,potentialOptions:[{option:'intPercent',minValue:1}]}));
 assert.throws(()=>parseQuery({keyword:'a',budget:1,potentialOptions:[{option:'intPerLevel',minValue:1}]}));
});

test('UI job groups map to the official auction enum, including the formerly rejected mage search',()=>{
 const id={worldId:0,accountId:1,characterId:2};
 for(const [input,expected] of [['MAGE','WIZARD'],['WARRIOR','FIGHTER'],['ARCHER','ARCHER'],['THIEF','THIEF'],['PIRATE','PIRATE']]){
  const q=parseQuery({keyword:'에테르넬 메이지글러브',category:'ARMOR_ARMOR_GLOVE',budget:200,budgetMode:'custom',budgetMin:200,budgetMax:700,jobClass:input,potentialGrade:4,starforceMin:17,potentialOptions:[{option:'criticalDamagePercent',minValue:15}]});
  const b=buildCreateBody(q,id);assert.equal(b.filters.itemCategory.itemJobCategory,expected);assert.equal(b.filters.price.max,'70000000000');assert.equal(b.filters.enhancementOption.potentialOptionSum.criticalDamagePercent,15);
 }
 assert.throws(()=>buildCreateBody({jobClass:'UNKNOWN'},id));
});
