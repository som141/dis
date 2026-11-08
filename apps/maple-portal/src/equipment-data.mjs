import {join} from 'node:path';
import {fileURLToPath} from 'node:url';
import {readFile,mkdir,writeFile} from 'node:fs/promises';
import {officialItemIdFromIcon} from './official-item-icon.mjs';
const dataRoot=new URL('../public/data/',import.meta.url);
export const catalog=JSON.parse(await readFile(new URL('equipment.json',dataRoot),'utf8'));
const mitra=JSON.parse(await readFile(new URL('mitra-emblems.json',dataRoot),'utf8'));
const officialNames=JSON.parse(await readFile(new URL('official-snippets.json',dataRoot),'utf8'));
const catalogNames=new Set(catalog.items.map(item=>item.name));
for(const item of mitra.items)if(!catalogNames.has(item.name))catalog.items.push({...item,metadataSource:mitra.source});
// Older metadata classifies several job-specific secondary shields and medals
// as armor/accessories. The exact names are cross-checked against Nexon's
// secondary-weapon table; ordinary shared shields keep their original class.
const secondaryRules=JSON.parse(await readFile(new URL('../vendor/maple-auction/damage/secondaryWeaponRules.json',import.meta.url),'utf8')).jobsByName;
for(const item of catalog.items){
  if(Object.hasOwn(secondaryRules,item.name)&&['ARMOR_ARMOR_SHIELD','ARMOR_ACCESSORY_MEDAL'].includes(item.category))item.category='WEAPON_SUB';
}
// The official autocomplete contains current names and icons, but no level,
// job, cash status or tradability. Infer only the slot family from the item ID
// when historical classified IDs agree. These are search suggestions, not
// verified catalog records, and cannot silently set requirement metadata.
const categoryVotes=new Map();
for(const item of catalog.items){
  const id=Number(item.id);
  if(!Number.isInteger(id)||id<1_000_000||id>=2_000_000)continue;
  for(const prefix of [String(id).slice(0,3),String(id).slice(0,4)]){
    if(!categoryVotes.has(prefix))categoryVotes.set(prefix,new Set());
    categoryVotes.get(prefix).add(item.category);
  }
}
const categoryForId=id=>{
  if(!Number.isInteger(id)||id<1_000_000||id>=2_000_000)return null;
  const value=String(id);
  for(const prefix of [value.slice(0,4),value.slice(0,3)]){
    const vote=categoryVotes.get(prefix);
    if(vote?.size===1)return [...vote][0];
  }
  return null;
};
const knownNames=new Set(catalog.items.map(item=>item.name));
const nameOnly=officialNames.flatMap(row=>{
  const name=row.nameOrigin;
  if(!name||knownNames.has(name))return [];
  const itemId=officialItemIdFromIcon(row.thumbnail);
  const category=categoryForId(itemId);
  return category?[{id:`official-name-${row.encryptedItemId}`,name,icon:row.thumbnail,category,level:null,jobs:[],itemId,metadataStatus:'name-only'}]:[];
});
const uniqueNameOnly=[...new Map(nameOnly.map(item=>[item.name,item])).values()];
catalog.metadataVersion+=' + official Mitra emblems + current autocomplete names';
catalog.notice='분류·레벨이 확인된 장비는 KMS 389과 공식 추가 자료 기준입니다. 이름 검색에 나오는 공식 최신 후보는 부위만 추정하며 직업·레벨·거래 가능 여부를 직접 확인해야 합니다.';
const initials='ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ';
const initial=s=>[...s].map(c=>{const n=c.charCodeAt(0)-44032;return n>=0&&n<11172?initials[Math.floor(n/588)]:c;}).join('');
export function findEquipment({category='ALL',job='ALL',q='',offset=0}={}){
  const query=q.replace(/\s/g,'').toLowerCase();
  const matches=x=>(category==='ALL'||x.category===category||x.category.startsWith(category+'_'))&&(job==='ALL'||x.metadataStatus==='name-only'||x.jobs.includes('ALL')||x.jobs.includes(job))&&(!query||x.name.replace(/\s/g,'').toLowerCase().includes(query)||initial(x.name).includes(query));
  const rows=catalog.items.filter(matches);
  if(query.length>=2)rows.push(...uniqueNameOnly.filter(matches));
  return {...catalog,items:rows.slice(Math.max(0,offset),Math.max(0,offset)+120),total:rows.length,nameOnlyCount:uniqueNameOnly.length};
}
const parts={WEAPON:1,WEAPON_SUB:3,ARMOR_ACCESSORY_EMBLEM:2,ARMOR_ARMOR_SHIELD:5,ARMOR_ARMOR_CAP:6,ARMOR_ARMOR_COAT:7,ARMOR_ARMOR_LONGCOAT:8,ARMOR_ARMOR_PANTS:9,ARMOR_ARMOR_SHOES:10,ARMOR_ARMOR_GLOVE:11,ARMOR_ARMOR_CAPE:12,ARMOR_ACCESSORY_BELT:13,ARMOR_ACCESSORY_SHOULDER:14,ARMOR_ACCESSORY_FACE:15,ARMOR_ACCESSORY_EYE:16,ARMOR_ACCESSORY_EAR:17,ARMOR_ACCESSORY_RING:18,ARMOR_ACCESSORY_PENDANT:19,ARMOR_ETC_MACHINE_HEART:20};
const requests=new Map();
const cacheRoot=process.env.MAPLE_PORTAL_DATA?join(process.env.MAPLE_PORTAL_DATA,'rules-cache'):fileURLToPath(new URL('rules/',dataRoot));
export async function potentialRules({category,grade,level,system,subtype}){
  const part=['Soul Ring','Force Shield'].includes(subtype)?4:subtype==='Shield'?5:parts[category]??(category?.startsWith('WEAPON_')?1:null);
  if(!part||![1,2,3,4].includes(Number(grade))||!Number.isInteger(Number(level))||level<0||level>250||!['main','additional'].includes(system)) throw new Error('부위·등급·장비 레벨(0~250)을 선택하세요.');
  const id=`${system}-${part}-${grade}-${level}`;
  if(requests.has(id))return requests.get(id);
  const promise=(async()=>{
    const file=join(cacheRoot,id+'.json');
    try{const cached=JSON.parse(await readFile(file,'utf8'));if(Date.now()-Date.parse(cached.fetchedAt)<86400000)return cached;}catch{}
    const source=`https://maplestory.nexon.com/Guide/OtherProbability/cube/${system==='main'?'black':'addi'}`;
    const r=await fetch('https://maplestory.nexon.com/Guide/OtherProbability/cube/GetSearchProbList',{method:'POST',headers:{'content-type':'application/x-www-form-urlencoded','Referer':source,'X-Requested-With':'XMLHttpRequest','User-Agent':'Mozilla/5.0'},body:new URLSearchParams({nCubeItemID:system==='main'?'5062010':'5062500',nGrade:String(grade),nPartsType:String(part),nReqLev:String(level)}),signal:AbortSignal.timeout(15000)});
    const html=await r.text();const options=[];let line=0;
    for(const match of html.matchAll(/<tr\b[^>]*>([\s\S]*?)<\/tr>/g)){
      const cells=[...match[1].matchAll(/<t[dh]\b[^>]*>([\s\S]*?)<\/t[dh]>/g)].map(m=>m[1].replace(/<[^>]*>/g,'').replace(/&lt;/g,'<').replace(/&gt;/g,'>').replace(/&amp;/g,'&').replace(/\s+/g,' ').trim());
      if(cells[0]?.includes('번째 옵션')){line++;continue;}
      if(line&&cells.length>=2&&/^\d/.test(cells[1]))options.push({text:cells[0],line,probability:cells[1]});
    }
    if(!r.ok||!options.length)throw new Error('공식 옵션표를 불러오지 못했습니다. 잠시 후 다시 시도하세요.');
    const result={source,system,part,grade:Number(grade),level:Number(level),fetchedAt:new Date().toISOString(),options};
    try{await mkdir(cacheRoot,{recursive:true});await writeFile(file,JSON.stringify(result));}catch{}return result;
  })();requests.set(id,promise);try{return await promise;}catch(e){requests.delete(id);throw e;}
}
