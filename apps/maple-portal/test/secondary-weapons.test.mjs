import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {JOB_STAT,getJobClass,auctionWearabilityError,getAuctionEquipmentSlots,secondaryWeaponJobs,emblemJobs} from '../dist/core.mjs';
import {catalog as runtimeCatalog,findEquipment} from '../src/equipment-data.mjs';

const catalog=JSON.parse(readFileSync(new URL('../public/data/equipment.json',import.meta.url),'utf8'));
const secondary=catalog.items.filter(item=>item.category==='WEAPON_SUB');
const item=(name,reqJob='전체',categories=['하위 부위'])=>({itemName:name,toolTip:{reqJob,categories}});

test('every known job is recognized and broad tooltip job labels stay wearable',()=>{
  for(const job of Object.keys(JOB_STAT)){
    const jobClass=getJobClass(job);
    assert.ok(jobClass,`missing job class: ${job}`);
    for(const reqJob of [jobClass,job,'전체','공용','전 직업','모든 직업']){
      assert.equal(auctionWearabilityError(item('알 수 없는 일반 장비',reqJob),job),null,`${job}: ${reqJob}`);
    }
    assert.match(auctionWearabilityError({toolTip:{reqJob:'전체',unwearableJobNames:[job]}},job),/착용 불가/,job);
  }
});

test('official and catalog secondary rules cover every canonical supported job',()=>{
  const covered=new Set(Object.values(JSON.parse(readFileSync(new URL('../vendor/maple-auction/damage/secondaryWeaponRules.json',import.meta.url),'utf8')).jobsByName).flat());
  for(const job of Object.keys(JOB_STAT)){
    if(['캐논슈터','데몬 슬레이어','데몬 어벤져'].includes(job))continue;
    assert.ok(covered.has(job),`no secondary-weapon evidence for ${job}`);
  }
});

test('every catalog secondary weapon has an exact job rule and maps to the secondary slot',()=>{
  assert.ok(secondary.length>=200);
  for(const entry of secondary){
    const jobs=secondaryWeaponJobs(entry.name);
    assert.ok(jobs?.length,`${entry.name} (${entry.subtype})`);
    assert.deepEqual(getAuctionEquipmentSlots(item(entry.name,'전체',['무기',entry.subtype])),['보조무기'],entry.name);
    for(const job of jobs){
      // A generic tooltip label must not reject a verified job-specific item.
      assert.equal(auctionWearabilityError(item(entry.name,'전체'),job),null,`${entry.name}: ${job}`);
    }
    const stranger=jobs.includes('에반')?'히어로':'에반';
    assert.match(auctionWearabilityError(item(entry.name,'전체'),stranger),/전용 보조무기/,`${entry.name}: ${stranger}`);
  }
});

test('Astra shared and new-job weapons retain their own exact job sets',()=>{
  const cases=[
    ['아스트라 아케인 실드',['아크메이지(불,독)','아크메이지(썬,콜)','비숍']],
    ['아스트라 포스실드',['데몬슬레이어','데몬어벤져']],
    ['아스트라 아워글라스',['제로']],
    ['아스트라 마법깃펜',['레테']],
    ['드래곤마스터의 유산',['에반']],
  ];
  for(const [name,jobs] of cases){
    assert.deepEqual(new Set(secondaryWeaponJobs(name)),new Set(jobs),name);
    for(const job of jobs)assert.equal(auctionWearabilityError(item(name,'마법사'),job),null,`${name}: ${job}`);
    assert.deepEqual(getAuctionEquipmentSlots(item(name,'전체',['문서'])),['보조무기'],name);
  }
  assert.equal(auctionWearabilityError(item('드래곤마스터의 유산','마법사'),'비숍')?.includes('에반 전용'),true);
  assert.equal(auctionWearabilityError({itemName:'드래곤마스터의 유산',toolTip:{reqJob:'전체',unwearableJobNames:['에반']}},'에반'),null,'official exact-name rule resolves conflicting generic tooltip metadata');
  assert.equal(auctionWearabilityError(item('아스트라 림리스','전체'),'캐논슈터'),null);
  assert.equal(auctionWearabilityError(item('에레브의 광휘','전체'),'플레임위자드'),null);
  assert.match(auctionWearabilityError(item('에레브의 광휘','전체'),'에반'),/전용 보조무기/);
});

test('older secondary shields and medals appear under the secondary search category',()=>{
  for(const name of ['루인 포스실드','버츄스 메달','정의의 소울실드']){
    assert.equal(runtimeCatalog.items.find(entry=>entry.name===name)?.category,'WEAPON_SUB',name);
    assert.ok(findEquipment({category:'WEAPON_SUB',q:name}).items.some(entry=>entry.name===name),name);
  }
  assert.equal(runtimeCatalog.items.find(entry=>entry.name==='데이모스 세이지 실드')?.category,'ARMOR_ARMOR_SHIELD');
});

test('all catalog emblems map to their actual slot and owner job',()=>{
  const emblems=catalog.items.filter(entry=>entry.category==='ARMOR_ACCESSORY_EMBLEM');
  assert.equal(emblems.length,6);
  for(const entry of emblems){
    const jobs=emblemJobs(entry.name);
    assert.equal(jobs?.length,1,entry.name);
    assert.deepEqual(getAuctionEquipmentSlots(item(entry.name,'전체',['파워소스'])),['엠블렘'],entry.name);
    assert.equal(auctionWearabilityError(item(entry.name,'전체'),jobs[0]),null,entry.name);
    const stranger=jobs[0]==='카이저'?'엔젤릭버스터':'카이저';
    assert.match(auctionWearabilityError(item(entry.name,'전체'),stranger),/전용 엠블렘/,entry.name);
  }
});

test('unknown equipment still follows provider job restrictions and slot labels',()=>{
  assert.equal(secondaryWeaponJobs('미등록 보조무기'),null);
  assert.equal(secondaryWeaponJobs('__proto__'),null);
  assert.equal(emblemJobs('constructor'),null);
  assert.match(auctionWearabilityError(item('미등록 보조무기','전사'),'에반'),/요구 직업 전사/);
  assert.equal(auctionWearabilityError(item('미등록 보조무기','아크메이지(불,독), 비숍'),'아크메이지(불,독)'),null);
  assert.deepEqual(getAuctionEquipmentSlots(item('미등록 보조무기','전체',['보조무기','기타'])),['보조무기']);
  assert.deepEqual(getAuctionEquipmentSlots(item('미등록 방패','전체',['방패'])),['보조무기']);
  assert.equal(getAuctionEquipmentSlots(item('미등록 아이템','전체',['알 수 없는 부위'])),null);
  assert.equal(getAuctionEquipmentSlots(item('constructor','전체',['__proto__'])),null);
});
