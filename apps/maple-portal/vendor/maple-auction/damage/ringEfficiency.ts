import type { CharacterSnapshot } from '../characterSnapshot.js';
import type { CharacterStats, StatBlock } from './stat-interface.js';
import { getAnchoredDamageBreakdown, getDamageBreakdown } from './finalDamage.js';

export type RingChoice = 'none' | 'restraint' | 'continuous' | 'critical' | 'risk' | 'weapon-s' | 'weapon-d' | 'weapon-i' | 'weapon-l';
export const RING_CHOICES: Record<Exclude<RingChoice, 'none'>, string> = {
  restraint:'리스트레인트 링', continuous:'컨티뉴어스 링', critical:'크리데미지 링', risk:'리스크테이커 링',
  'weapon-s':'웨폰퍼프 - S링', 'weapon-d':'웨폰퍼프 - D링',
  'weapon-i':'웨폰퍼프 - I링', 'weapon-l':'웨폰퍼프 - L링',
};

// 2026-03-19 official update 799 gives these exact level-four effects.
// Other levels and ring types require their own official effect values.
const RING_SOURCE = 'https://gi.maplestory.nexon.com/Update/799';
const WEAPON_STAT: Record<string, keyof StatBlock> = {
  'weapon-s':'STR', 'weapon-d':'DEX', 'weapon-i':'INT', 'weapon-l':'LUK',
};

function weaponBase(character: CharacterSnapshot, replacementBase?: number): number {
  if (replacementBase !== undefined) return replacementBase;
  const weapons = character.equipment.item_equipment.filter(item => item.item_equipment_slot === '무기');
  if (weapons.length !== 1) throw new Error('주무기 기준값을 하나로 확인할 수 없습니다.');
  const base = weapons[0].item_base_option;
  const value = Math.max(Number(base?.attack_power), Number(base?.magic_power));
  if (!Number.isFinite(value) || value <= 0) throw new Error('주무기 기본 공마가 없습니다.');
  return value;
}

function withRing(stats: CharacterStats, choice: RingChoice, character: CharacterSnapshot, replacementBase?: number): CharacterStats {
  let bonus: StatBlock;
  if (choice === 'restraint') bonus = { 공격력퍼:85, 마력퍼:85 };
  else if (choice === 'continuous') bonus = { 공격력퍼:14, 마력퍼:14, 보공:54 };
  else if (choice === 'critical') bonus = { 크뎀:20 };
  else if (choice === 'risk') bonus = { 공격력퍼:30, 마력퍼:30 };
  else if (WEAPON_STAT[choice]) bonus = { [WEAPON_STAT[choice]]:Math.floor(weaponBase(character, replacementBase) * 2.6) } as StatBlock;
  else throw new Error('지원하는 반지 종류를 선택해 주세요.');
  return { ...stats, 반지발동효과: { 선택반지:bonus } } as CharacterStats;
}

export function compareRingActivation(character: CharacterSnapshot, changed: CharacterStats, choice: RingChoice, level: number, replacementBase?: number, bossDefenseRate=380, statusDamageUptime=0) {
  if (!(choice in RING_CHOICES) || (choice === 'restraint' || choice === 'continuous' ? level !== 6 : level !== 4)) throw new Error('이 반지·레벨의 검증된 발동 효과량이 없습니다.');
  const context = { ...character, bossDefenseRate, statusDamageUptime };
  const breakdown = character.observedFinalStats ? getAnchoredDamageBreakdown : getDamageBreakdown;
  const beforeOff = breakdown(context, character.stats).coefficient;
  const afterOff = breakdown(context, changed).coefficient;
  const beforeOn = breakdown(context, withRing(character.stats, choice, character)).coefficient;
  const afterOn = breakdown(context, withRing(changed, choice, character, replacementBase)).coefficient;
  if (![beforeOff, afterOff, beforeOn, afterOn].every(value => Number.isFinite(value) && value > 0)) throw new Error('반지 비교 계수가 0 이하이거나 유효하지 않습니다.');
  const percent = (a:number,b:number) => 100 * (b / a - 1);
  return {
    name:RING_CHOICES[choice as Exclude<RingChoice,'none'>], level,
    currentActivation:percent(beforeOff,beforeOn), candidateActivation:percent(afterOff,afterOn),
    equipmentOff:percent(beforeOff,afterOff), equipmentOn:percent(beforeOn,afterOn),
    source:choice === 'continuous' ? [RING_SOURCE,'https://maplestory.nexon.com/news/update/767?page=4'] : [RING_SOURCE],
    condition:choice === 'risk' ? '피격되지 않은 동안 발동이 유지된 상태' : choice === 'restraint' ? '링 영역 안에서 발동한 순간' : choice === 'continuous' ? '링 버프가 활성인 순간' : '스킬 효과가 발동한 순간',
  };
}
