import type { EquipmentSlot } from './equipmentSlot.js';
import { SET_DB } from './setDb.js';
import {
  calculateSetEffects,
  countEquipmentSets,
  normalizeSetName,
  setOfItem,
  isLuckyItemName,
} from './setSimulation.js';
import type { CharacterStats, StatBlock } from './stat-interface.js';

export interface EquipmentReplacement {
  slot: EquipmentSlot;
  name: string;
  stat: StatBlock;
}

export function getStatsAfterEquipmentReplacement(
  current: CharacterStats,
  replacement: EquipmentReplacement,
  definitions:Record<string,Record<number,StatBlock>>={},
  observedCounts:Record<string,number>={}
): CharacterStats {
  const 장비 = {
    ...current.장비,
    [replacement.slot]: {
      name: replacement.name,
      stat: replacement.stat,
    },
  };
  const itemNames = Object.values(장비).map((item) => item.name);
  const knownSets=Object.keys(definitions);
  const counts = countEquipmentSets(itemNames,knownSets);
  const beforeCounts = countEquipmentSets(Object.values(current.장비).map(item=>item.name),knownSets);
  const oldName=current.장비[replacement.slot]?.name??'';
  const unresolvedSetMembership=Object.keys(current.세트효과??{}).some(name=>{
    const set=normalizeSetName(name);
    return !SET_DB[set]&&(!Number.isInteger(observedCounts[set])||observedCounts[set]!==beforeCounts[set]);
  });
  // The Open API lists active set bonuses, but does not list each set's item
  // members. An unchanged item cannot change any active set, even if that set
  // is absent from our historical set table. A changed unidentified item can.
  if(oldName && oldName!==replacement.name && !setOfItem(oldName,knownSets) && unresolvedSetMembership) {
    throw new Error('기존 장비가 소속된 세트를 확인할 수 없어 세트 변화를 계산할 수 없습니다.');
  }
  if(oldName!==replacement.name&&(isLuckyItemName(oldName)||isLuckyItemName(replacement.name))&&unresolvedSetMembership) {
    throw new Error('럭키 아이템이 미지원 세트에 미치는 영향을 확인할 수 없습니다.');
  }
  // The API's total_set_count is authoritative. It also captures currently
  // active lucky membership or members with names that do not share a prefix.
  // Keep that offset while changing a member we can identify by name.
  for(const [set,actual] of Object.entries(observedCounts)){
    if(!Number.isInteger(actual)||actual<0)continue;
    const offset=actual-(beforeCounts[set]??0);
    beforeCounts[set]=actual;
    counts[set]=(counts[set]??0)+offset;
    if(counts[set]<0)throw new Error('교체 후 세트 수를 확인할 수 없습니다: '+set);
  }
  const simulated = calculateSetEffects(counts),effects:Record<string,StatBlock>={};
  const currentSets=new Set(Object.keys(current.세트효과??{}).map(normalizeSetName));
  for(const [set,tiers] of Object.entries(definitions)){
    // Use the observed API bonus verbatim when this set's count did not move.
    // A partial/malformed full-tier list must not block an unrelated swap.
    if(currentSets.has(set)&&(beforeCounts[set]??0)===(counts[set]??0))continue;
    if((counts[set]??0)===0){delete simulated[set];continue;}
    if(!Object.keys(tiers).length)throw new Error('새로운 세트 단계의 공식 옵션 자료가 없습니다: '+set);
    const sum:Record<string,number|number[]>={};
    for(const [required,block] of Object.entries(tiers))if(Number(required)<=counts[set])for(const [key,value] of Object.entries(block)){
      if(Array.isArray(value))sum[key]=[...(sum[key] as number[]??[]),...value];else sum[key]=(sum[key] as number??0)+(value as number);
    }
    simulated[set]=sum as StatBlock;
  }
  for(const [name,block] of Object.entries(current.세트효과??{})){
    const set=normalizeSetName(name);
    if((beforeCounts[set]??0)===(counts[set]??0)){effects[name]=block;delete simulated[set];}
    else if(!SET_DB[set]&&!definitions[set])throw new Error('세트 변화의 옵션 자료가 없습니다: '+set);
  }

  return {
    ...current,
    장비,
    세트효과: {
      ...effects,
      ...simulated,
    },
  };
}
