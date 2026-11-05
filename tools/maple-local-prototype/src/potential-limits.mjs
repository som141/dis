// Auction search has minimum option sums only. Maximums are checked against
// the actual tooltip lines after the provider result pages have been fetched.
const names={
  bossDamagePercent:['보스 몬스터 데미지','보스 몬스터 공격 시 데미지'],
  ignoreMonsterDefense:['몬스터 방어율 무시'],damagePercent:['데미지'],
  physicalAttackPercent:['공격력'],magicAttackPercent:['마력'],
  allStatsPercent:['올스탯'],strPercent:['STR'],dexPercent:['DEX'],
  intPercent:['INT'],lukPercent:['LUK'],criticalPercent:['크리티컬 확률'],
  criticalDamagePercent:['크리티컬 데미지'],itemDropPercent:['아이템 획득 확률'],
  maxHpPercent:['최대 HP','MaxHP'],maxMpPercent:['최대 MP','MaxMP'],
  physicalAttack:['공격력'],magicAttack:['마력'],str:['STR'],dex:['DEX'],
  int:['INT'],luk:['LUK'],maxHp:['최대 HP','MaxHP'],maxMp:['최대 MP','MaxMP'],
  allStats:['올스탯'],statusResistance:['상태 이상 내성'],
  elementalResistance:['모든 속성 내성'],skillCooldownReduction:['스킬 재사용 대기시간'],
  mesosObtainedPercent:['메소 획득량'],
};
export const MAXIMUM_OPTION_KEYS=Object.freeze(Object.keys(names));
const normalize=s=>s.replace(/\s+/g,'').toLowerCase();
function targetUnit(key){return key==='skillCooldownReduction'?'초':key.endsWith('Percent')||key==='ignoreMonsterDefense'||key==='criticalPercent'?'%':'';}
export function potentialSum(section,key){
  if(!section||!Array.isArray(section.entries))return null;
  const aliases=names[key];if(!aliases)return null;
  const wanted=new Set(aliases.map(normalize)),unit=targetUnit(key);
  let sum=0;
  for(const entry of section.entries){
    if(typeof entry?.text!=='string')return null;
    const line=entry.text.trim();
    const match=/^(.+?)\s*([+\-])\s*([\d,]+(?:\.\d+)?)\s*(%|초)?(?:\s*증가)?$/.exec(line);
    if(match){
      if(wanted.has(normalize(match[1]))&&(match[4]||'')===unit){
        const value=Number(match[3].replaceAll(',',''));if(!Number.isFinite(value))return null;
        sum+=key==='skillCooldownReduction'?Math.abs(value):match[2]==='-'?-value:value;
      }
    }else if(aliases.some(name=>normalize(line).startsWith(normalize(name))))return null;
  }
  return sum;
}
export function applyPotentialMaximums(items,potentialMaximums=[],additionalPotentialMaximums=[]){
  const enabled=potentialMaximums.length+additionalPotentialMaximums.length>0;
  if(!enabled)return {items,summary:{enabled:false,before:items.length,matched:items.length,excluded:0,unknown:0}};
  const matched=[];let unknown=0,excluded=0;
  for(const item of items){
    let uncertain=false,tooHigh=false;
    for(const [section,limits] of [['potential',potentialMaximums],['additional',additionalPotentialMaximums]]){
      for(const {option,maxValue} of limits){
        const value=potentialSum(item.itemDetails?.[section],option);
        if(value===null)uncertain=true;
        else if(value>maxValue)tooHigh=true;
      }
    }
    if(tooHigh)excluded++;
    else if(uncertain)unknown++;
    else matched.push(item);
  }
  return {items:matched,summary:{enabled:true,before:items.length,matched:matched.length,excluded,unknown}};
}
