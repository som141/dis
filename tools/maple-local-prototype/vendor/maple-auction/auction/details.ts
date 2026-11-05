import type {AuctionItem} from './item.js';

// Whitelist display fields. Never forward itemCode, identity, session or arbitrary
// provider objects to the browser. Missing fields stay missing rather than zero.
const scalar=(value:unknown)=>typeof value==='number'&&Number.isFinite(value)?value:undefined;
const str=(value:unknown)=>typeof value==='string'?value:undefined;
const statKeys=new Set('reduceReq incReq arc aut str dex int luk all mhp mmp hpr mpr mdf pad mad pdd speed jump dam bdr imdr addExpr expRate srExpRate srMesoRate srDropRate pqExpRate chuc craft'.split(' '));
const stats=(value:any)=>value&&typeof value==='object'?Object.fromEntries(Object.entries(value).filter(([key,v])=>statKeys.has(key)&&typeof v==='number'&&Number.isFinite(v))):undefined;
const lines=(value:any)=>Array.isArray(value)?value.filter(x=>typeof x==='string'):undefined;
function potential(value:any){return value&&typeof value==='object'?{grade:scalar(value.grade),entries:Array.isArray(value.entries)?value.entries.filter((x:any)=>typeof x?.text==='string').map((x:any)=>({text:x.text,grade:scalar(x.grade)})):[]}:undefined;}
export function officialIcon(value:unknown){
  if(typeof value!=='string')return undefined;
  try{const u=new URL(value);return !u.username&&!u.password&&((u.origin==='https://avatar.maplestory.nexon.com'&&u.pathname.startsWith('/ItemIcon/'))||(u.origin==='https://mskr-web.dn.nexoncdn.co.kr'&&/^\/gamefiles\/icon\/[a-f0-9]{32}\.png$/i.test(u.pathname)))?u.href:undefined;}catch{return undefined;}
}
export function buildItemDetails(item:AuctionItem){
  const t=(item.toolTip??{}) as any,u=t.upgradeInfo??{};
  return {
    icon:officialIcon(t.itemIcon?.fallBackUrl??(item as any).itemIcon?.fallBackUrl),
    level:scalar(t.reqLevel),job:str(t.reqJob),categories:lines(t.categories),description:str(t.itemDesc),
    starforce:(Number.isInteger(u.starForce?.current??item.starforce)&&(u.starForce?.current??item.starforce)>=0&&(u.starForce?.current??item.starforce)<=30)?u.starForce?.current??item.starforce:undefined,starforceMax:scalar(u.starForce?.max),
    scroll:u.scroll?{current:scalar(u.scroll.current),remaining:scalar(u.scroll.remaining),failure:scalar(u.scroll.failure),max:scalar(u.scroll.max)}:undefined,
    stats:stats(t.stat),components:{base:stats(t.baseStat),starforce:stats(t.starforceStat),scroll:stats(t.upgradeStat),extra:stats(t.exOptionStat),temporary:stats(t.timeLimitedStat)},
    potential:potential(u.potential),additional:potential(u.additionalPotential),
    extraEntries:Array.isArray(u.exOption?.entries)?u.exOption.entries.filter((x:any)=>typeof x?.text==='string').map((x:any)=>({text:x.text,grade:scalar(x.grade)})):undefined,
    trade:lines(t.tradeDesc??t.tradeDescs),timeLimit:str(t.timeLimit??t.expireDesc),
    soul:t.soulWeapon?{name:str(t.soulWeapon.soulName),option:str(t.soulWeapon.optionText)}:undefined,
    exceptional:Array.isArray(t.exceptionalUpgrade?.entries)?t.exceptionalUpgrade.entries.filter((x:any)=>typeof x?.text==='string').map((x:any)=>x.text):undefined,
  };
}
export function optionBrief(value:any){
  if(!Array.isArray(value?.entries))return undefined;
  const short:Record<string,string>={'크리티컬 데미지':'크뎀','보스 몬스터 데미지':'보공','보스 몬스터 공격 시 데미지':'보공','몬스터 방어율 무시':'방무','최대 HP':'HP','최대 MP':'MP'};
  const totals=new Map<string,number>(),other:string[]=[];
  for(const row of value.entries){
    const m=typeof row.text==='string'?/^(.+?)\s*:?\s*\+\s*(\d+(?:\.\d+)?)\s*(%)?$/.exec(row.text):null;
    if(m){const name=short[m[1].trim()]??m[1].trim();if(name==='방무'){other.push(`방무 ${m[2]}${m[3]||''}`);continue;}const key=name+(m[3]?'%':'');totals.set(key,(totals.get(key)||0)+Number(m[2]));}
    else if(typeof row.text==='string')other.push(row.text);
  }
  return [...totals].sort(([a],[b])=>((a.startsWith('크뎀')?0:a.includes('%')?1:2)-(b.startsWith('크뎀')?0:b.includes('%')?1:2))).map(([key,value])=>`${key.replace(/%$/,'')} ${Number(value.toFixed(2))}${key.endsWith('%')?'%':''}`).concat(other).slice(0,3).join(' · ');
}
