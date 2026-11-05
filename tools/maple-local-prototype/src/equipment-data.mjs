import {join} from 'node:path';
import {fileURLToPath} from 'node:url';
import {readFile,mkdir,writeFile} from 'node:fs/promises';
const dataRoot=new URL('../public/data/',import.meta.url);
export const catalog=JSON.parse(await readFile(new URL('equipment.json',dataRoot),'utf8'));
const initials='ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ';
const initial=s=>[...s].map(c=>{const n=c.charCodeAt(0)-44032;return n>=0&&n<11172?initials[Math.floor(n/588)]:c;}).join('');
export function findEquipment({category='ALL',job='ALL',q='',offset=0}={}){
  const query=q.replace(/\s/g,'').toLowerCase();
  const rows=catalog.items.filter(x=>(category==='ALL'||x.category===category||x.category.startsWith(category+'_'))&&(job==='ALL'||x.jobs.includes('ALL')||x.jobs.includes(job))&&(!query||x.name.replace(/\s/g,'').toLowerCase().includes(query)||initial(x.name).includes(query)));
  return {...catalog,items:rows.slice(Math.max(0,offset),Math.max(0,offset)+120),total:rows.length};
}
const parts={WEAPON:1,WEAPON_SUB:3,ARMOR_ACCESSORY_EMBLEM:2,ARMOR_ARMOR_SHIELD:5,ARMOR_ARMOR_CAP:6,ARMOR_ARMOR_COAT:7,ARMOR_ARMOR_LONGCOAT:8,ARMOR_ARMOR_PANTS:9,ARMOR_ARMOR_SHOES:10,ARMOR_ARMOR_GLOVE:11,ARMOR_ARMOR_CAPE:12,ARMOR_ACCESSORY_BELT:13,ARMOR_ACCESSORY_SHOULDER:14,ARMOR_ACCESSORY_FACE:15,ARMOR_ACCESSORY_EYE:16,ARMOR_ACCESSORY_EAR:17,ARMOR_ACCESSORY_RING:18,ARMOR_ACCESSORY_PENDANT:19,ARMOR_ETC_MACHINE_HEART:20};
const requests=new Map();
const cacheRoot=process.env.MAPLE_PORTAL_DATA?join(process.env.MAPLE_PORTAL_DATA,'rules-cache'):fileURLToPath(new URL('rules/',dataRoot));
export async function potentialRules({category,grade,level,system,subtype}){
  const part=['Soul Ring','Force Shield'].includes(subtype)?4:parts[category]??(category?.startsWith('WEAPON_')?1:null);
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
