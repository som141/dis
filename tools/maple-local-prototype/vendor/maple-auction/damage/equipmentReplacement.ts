import type { EquipmentSlot } from './equipmentSlot.js';
import { SET_DB } from './setDb.js';
import {
  calculateSetEffects,
  countEquipmentSets,
  normalizeSetName,
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
  definitions:Record<string,Record<number,StatBlock>>={}
): CharacterStats {
  if (!current.장비?.[replacement.slot]) {
    throw new Error(`교체할 장비 슬롯을 찾을 수 없습니다: ${replacement.slot}`);
  }

  const 장비 = {
    ...current.장비,
    [replacement.slot]: {
      name: replacement.name,
      stat: replacement.stat,
    },
  };
  const itemNames = Object.values(장비).map((item) => item.name);
  const counts = countEquipmentSets(itemNames);
  const beforeCounts = countEquipmentSets(Object.values(current.장비).map(item=>item.name));
  const simulated = calculateSetEffects(counts),effects:Record<string,StatBlock>={};
  for(const [set,tiers] of Object.entries(definitions)){
    if((counts[set]??0)===0){delete simulated[set];continue;}
    if(counts[set]>Math.max(...Object.keys(tiers).map(Number)))throw new Error('새로운 세트 단계의 공식 옵션 자료가 없습니다: '+set);
    const sum:Record<string,number|number[]>={};
    for(const [required,block] of Object.entries(tiers))if(Number(required)<=counts[set])for(const [key,value] of Object.entries(block)){
      if(Array.isArray(value))sum[key]=[...(sum[key] as number[]??[]),...value];else sum[key]=(sum[key] as number??0)+(value as number);
    }
    simulated[set]=sum as StatBlock;
  }
  for(const [name,block] of Object.entries(current.세트효과??{})){
    const set=normalizeSetName(name);
    if((beforeCounts[set]??0)===(counts[set]??0)){effects[name]=block;delete simulated[set];}
    else if(!SET_DB[set])throw new Error('세트 변화의 옵션 자료가 없습니다: '+set);
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
