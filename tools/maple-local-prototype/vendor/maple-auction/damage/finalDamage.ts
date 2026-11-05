import { JOB_STAT, type MainStat } from './stat/job.js';
import type { CharacterStats, StatBlock } from './stat-interface.js';

export interface CharacterDamageContext {
  job: string;
  level: number;
  stats: CharacterStats;
  bossDefenseRate?: number;
  observedFinalStats?: Record<string, number>;
  statusDamageUptime?: number;
}

interface AggregatedStats {
  flat: Record<MainStat, number>;
  flatNoPercent: Record<MainStat, number>;
  percent: Record<MainStat, number>;
  allStat: number;
  allStatPercent: number;
  attack: number;
  magic: number;
  attackPercent: number;
  magicPercent: number;
  damage: number;
  bossDamage: number;
  statusDamage: number;
  ignoreDefense: number[];
  finalDamage: number[];
  criticalRate: number;
  criticalDamage: number;
  hp: number;
  hpNoPercent: number;
  hpPercent: number;
}

const MAIN_STATS: MainStat[] = ['STR', 'DEX', 'INT', 'LUK'];
const SUB_STAT: Record<MainStat, MainStat> = {
  STR: 'DEX',
  DEX: 'STR',
  INT: 'LUK',
  LUK: 'DEX',
};
const DOUBLE_SUB_STAT_JOBS = new Set(['카데나', '듀얼블레이드', '섀도어']);
const BOSS_DEFENSE_RATE = 3.8;

const STAT_KEYS = new Set<keyof StatBlock>([
  'STR',
  'DEX',
  'INT',
  'LUK',
  'STR미적용',
  'DEX미적용',
  'INT미적용',
  'LUK미적용',
  'STR퍼',
  'DEX퍼',
  'INT퍼',
  'LUK퍼',
  '올스탯',
  '올스탯미적용',
  '올스탯퍼',
  '레벨당STR',
  '레벨당DEX',
  '레벨당INT',
  '레벨당LUK',
  '공격력',
  '마력',
  '공격력퍼',
  '마력퍼',
  '데미지',
  '보공',
  '추가뎀',
  '방무',
  '최종뎀',
  '크확',
  '크뎀',
  'HP',
  'HP미적용',
  'HP퍼',
  '쿨감',
]);

function emptyAggregatedStats(): AggregatedStats {
  return {
    flat: { STR: 0, DEX: 0, INT: 0, LUK: 0 },
    flatNoPercent: { STR: 0, DEX: 0, INT: 0, LUK: 0 },
    percent: { STR: 0, DEX: 0, INT: 0, LUK: 0 },
    allStat: 0,
    allStatPercent: 0,
    attack: 0,
    magic: 0,
    attackPercent: 0,
    magicPercent: 0,
    damage: 0,
    bossDamage: 0,
    statusDamage: 0,
    ignoreDefense: [],
    finalDamage: [],
    criticalRate: 0,
    criticalDamage: 0,
    hp: 0,
    hpNoPercent: 0,
    hpPercent: 0,
  };
}

function number(block: StatBlock, key: keyof StatBlock): number {
  const value = block[key];
  if(value!==undefined&&(typeof value!=='number'||!Number.isFinite(value)))throw new Error('유효하지 않은 스탯 수치입니다.');
  return typeof value === 'number' && Number.isFinite(value) ? value : 0;
}

function addBlock(
  target: AggregatedStats,
  block: StatBlock,
  level: number
): void {
  for (const stat of MAIN_STATS) {
    target.flat[stat] += number(block, stat);
    target.flat[stat] += Math.floor(level / 9) * number(block, `레벨당${stat}`);
    target.flatNoPercent[stat] += number(block, `${stat}미적용`);
    target.flatNoPercent[stat] += number(block, '올스탯미적용');
    target.percent[stat] += number(block, `${stat}퍼`);
  }

  target.allStat += number(block, '올스탯');
  target.allStatPercent += number(block, '올스탯퍼');
  target.attack += number(block, '공격력');
  target.magic += number(block, '마력');
  target.attackPercent += number(block, '공격력퍼');
  target.magicPercent += number(block, '마력퍼');
  target.damage += number(block, '데미지');
  target.bossDamage += number(block, '보공');
  target.statusDamage += number(block, '추가뎀');
  target.criticalRate += number(block, '크확');
  target.criticalDamage += number(block, '크뎀');
  target.hp += number(block, 'HP');
  target.hpNoPercent += number(block, 'HP미적용');
  target.hpPercent += number(block, 'HP퍼');
  if((block.방무??[]).some(n=>!Number.isFinite(n)||n<0||n>100)||(block.최종뎀??[]).some(n=>!Number.isFinite(n)||n<=-100))throw new Error('방무 또는 최종 데미지 수치가 유효하지 않습니다.');
  target.ignoreDefense.push(...(block.방무 ?? []));
  target.finalDamage.push(...(block.최종뎀 ?? []));
}

function isStatBlock(value: Record<string, unknown>): boolean {
  const keys = Object.keys(value);
  return (
    keys.length > 0 &&
    keys.every((key) => STAT_KEYS.has(key as keyof StatBlock))
  );
}

function collectBlocks(
  value: unknown,
  target: AggregatedStats,
  level: number
): void {
  if (value === null || typeof value !== 'object' || Array.isArray(value))
    return;

  const record = value as Record<string, unknown>;
  if (isStatBlock(record)) {
    addBlock(target, record as StatBlock, level);
    return;
  }

  for (const child of Object.values(record)) {
    collectBlocks(child, target, level);
  }
}

function aggregateStats(stats: CharacterStats, level: number): AggregatedStats {
  const result = emptyAggregatedStats();
  collectBlocks(stats, result, level);

  const mapleWarrior = stats.메이플용사 ?? 0;
  if (mapleWarrior !== 0) {
    for (const stat of MAIN_STATS) {
      result.flat[stat] += Math.floor(
        ((stats.AP[stat] ?? 0) * mapleWarrior) / 100
      );
    }
  }

  return result;
}

function finalStat(stats: AggregatedStats, stat: MainStat): number {
  return (
    Math.floor(
      (stats.flat[stat] + stats.allStat) *
        (1 + (stats.percent[stat] + stats.allStatPercent) / 100)
    ) + stats.flatNoPercent[stat]
  );
}

function finalHp(stats: AggregatedStats): number {
  return Math.floor(stats.hp * (1 + stats.hpPercent / 100)) + stats.hpNoPercent;
}

function statFactor(job: string, stats: AggregatedStats): number {
  const jobStat = JOB_STAT[job];
  if (!jobStat) throw new Error(`지원하지 않는 직업: ${job}`);

  if (jobStat === 'deven') {
    return 4 * Math.floor(finalHp(stats) / 3.5) + finalStat(stats, 'STR');
  }
  if (jobStat === 'xenon') {
    return (
      4 *
      (finalStat(stats, 'STR') +
        finalStat(stats, 'DEX') +
        finalStat(stats, 'LUK'))
    );
  }

  const secondary = SUB_STAT[jobStat];
  const secondSecondary = DOUBLE_SUB_STAT_JOBS.has(job) ? 'STR' : null;
  return (
    4 * finalStat(stats, jobStat) +
    finalStat(stats, secondary) +
    (secondSecondary ? finalStat(stats, secondSecondary) : 0)
  );
}

export function getDamageBreakdown(
  context: CharacterDamageContext,
  stats: CharacterStats
) {
  const aggregated = aggregateStats(stats, context.level);
  const jobStat = JOB_STAT[context.job];
  if (!jobStat) throw new Error(`지원하지 않는 직업: ${context.job}`);

  const useMagic = jobStat === 'INT';
  const attack = Math.floor(
    (useMagic ? aggregated.magic : aggregated.attack) *
      (1 +
        (useMagic ? aggregated.magicPercent : aggregated.attackPercent) / 100)
  );
  const damageFactor =
    1 +
    (aggregated.damage + aggregated.bossDamage + aggregated.statusDamage*(context.statusDamageUptime??0)) / 100;
  const criticalDamage =
    aggregated.criticalDamage +
    (100 * (stats.크리티컬리인포스 ?? 0)) / 100;
  const criticalFactor = 1 + criticalDamage / 100; // User assumption: effective critical chance is fixed at 100%.
  const ignoreDefenseRemain = aggregated.ignoreDefense.reduce(
    (remain, value) => remain * (1 - value / 100),
    1
  );
  const defenseFactor = Math.max(
    0,
    1 - ((context.bossDefenseRate ?? 380) / 100) * ignoreDefenseRemain
  );
  const finalDamageFactor = aggregated.finalDamage.reduce(
    (factor, value) => factor * (1 + value / 100),
    1
  );

  if(!Number.isFinite(context.bossDefenseRate??380)||(context.bossDefenseRate??380)<0||(context.bossDefenseRate??380)>1000)throw new Error('대상 방어율은 0~1000% 범위여야 합니다.');
  const stat = statFactor(context.job, aggregated);
  const coefficient = stat * attack * damageFactor * criticalFactor * defenseFactor * finalDamageFactor;
  const finalStats: Record<string,number> = {STR:finalStat(aggregated,'STR'),DEX:finalStat(aggregated,'DEX'),INT:finalStat(aggregated,'INT'),LUK:finalStat(aggregated,'LUK'),
    '공격력':Math.floor(aggregated.attack*(1+aggregated.attackPercent/100)), '마력':Math.floor(aggregated.magic*(1+aggregated.magicPercent/100)),
    '크리티컬 확률':100, '크리티컬 데미지':aggregated.criticalDamage-35,
    '데미지':aggregated.damage,'보스 몬스터 데미지':aggregated.bossDamage,'방어율 무시':100*(1-ignoreDefenseRemain),'최종 데미지':100*(finalDamageFactor-1)};
  return {components:aggregated,coefficient,factors:{stat,attack,damage:damageFactor,critical:criticalFactor,defense:defenseFactor,finalDamage:finalDamageFactor},finalStats};
}

function roundRate(value: number): number {
  return Math.round(value * 100) / 100 || 0;
}

export function getFinalDamageChangeRate(
  character: CharacterDamageContext,
  changedStats: CharacterStats
): number {
  if(['xenon','deven'].includes(JOB_STAT[character.job]))throw new Error('제논·데몬어벤져의 특수 스탯 환산은 현재 지원하지 않습니다.');
  const before = (character.observedFinalStats?getAnchoredDamageBreakdown(character,character.stats):getDamageBreakdown(character, character.stats)).coefficient;
  if (!Number.isFinite(before) || before <= 0) {
    throw new Error('변경 전 데미지가 0 이하라 증감률을 계산할 수 없습니다.');
  }

  const after = (character.observedFinalStats?getAnchoredDamageBreakdown(character,changedStats):getDamageBreakdown(character, changedStats)).coefficient;
  if (!Number.isFinite(after) || after < 0) {
    throw new Error('변경 후 데미지를 계산할 수 없습니다.');
  }

  return roundRate((after / before - 1) * 100);
}

export function auditDamageBaseline(context: CharacterDamageContext) {
  const model=getDamageBreakdown(context,context.stats);
  if(!context.observedFinalStats)return {verified:false,issues:['API 기준 스탯 없음'],checks:[]};
  const relevant=JOB_STAT[context.job]==='INT'?['INT','LUK','마력']:JOB_STAT[context.job]==='STR'?['STR','DEX','공격력']:JOB_STAT[context.job]==='DEX'?['DEX','STR','공격력']:['LUK','DEX','공격력'];
  const keys=[...relevant,'크리티컬 확률','크리티컬 데미지','데미지','보스 몬스터 데미지','방어율 무시','최종 데미지'];
  const checks=keys.map(key=>{const observed=context.observedFinalStats![key],modeled=model.finalStats[key],tolerance=['STR','DEX','INT','LUK','마력','공격력'].includes(key)?1:0.02;return {key,observed,modeled,ok:Number.isFinite(observed)&&Number.isFinite(modeled)&&Math.abs(observed-modeled)<=tolerance};});
  return {verified:checks.every(x=>x.ok),issues:checks.filter(x=>!x.ok).map(x=>x.key),checks};
}

// API aggregate values anchor the baseline; only the equipment/set delta is applied.
// Percent-free symbol/union stats remain outside percentage multiplication.
export function getAnchoredDamageBreakdown(context: CharacterDamageContext, changed: CharacterStats) {
  if(['xenon','deven'].includes(JOB_STAT[context.job]))throw new Error('제논·데몬어벤져의 특수 스탯 환산은 현재 지원하지 않습니다.');
  const current=getDamageBreakdown(context,context.stats),after=getDamageBreakdown(context,changed);
  const observed=context.observedFinalStats;
  if(!observed)throw new Error('캐릭터 API 기준 스탯이 없습니다.');
  const a=current.components,b=after.components;
  const read=(key:string)=>{if(!Number.isFinite(observed[key]))throw new Error('캐릭터 기준 스탯 누락: '+key);return observed[key];};
  const boundedFloor=(value:number)=>Math.floor(value+1e-7);
  const result={} as Record<MainStat,number>;
  for(const stat of MAIN_STATS){
    const factor=1+(a.percent[stat]+a.allStatPercent)/100,newFactor=1+(b.percent[stat]+b.allStatPercent)/100;
    if(factor<=0||newFactor<=0)throw new Error('스탯 배율을 계산할 수 없습니다.');
    const base=(read(stat)-a.flatNoPercent[stat])/factor;
    result[stat]=boundedFloor((base+(b.flat[stat]+b.allStat-a.flat[stat]-a.allStat))*newFactor)+b.flatNoPercent[stat];
  }
  const jobStat=JOB_STAT[context.job] as MainStat;if(!MAIN_STATS.includes(jobStat))throw new Error('지원하지 않는 직업입니다.');
  const magic=jobStat==='INT',attackKey=magic?'마력':'공격력',pct=magic?a.magicPercent:a.attackPercent,newPct=magic?b.magicPercent:b.attackPercent;
  if(pct<=-100||newPct<=-100)throw new Error('공격력 배율을 계산할 수 없습니다.');
  const attack=boundedFloor((read(attackKey)/(1+pct/100)+((magic?b.magic:b.attack)-(magic?a.magic:a.attack)))*(1+newPct/100));
  const critRate=100; // Same fixed assumption before and after replacement.
  const critDamage=read('크리티컬 데미지')+b.criticalDamage-a.criticalDamage;
  const critical=1+critRate/100*(35+critDamage)/100;
  const damage=1+(read('데미지')+b.damage-a.damage+read('보스 몬스터 데미지')+b.bossDamage-a.bossDamage+((Number.isFinite(observed['상태이상 추가 데미지'])?observed['상태이상 추가 데미지']:a.statusDamage)+b.statusDamage-a.statusDamage)*(context.statusDamageUptime??0))/100;
  const remain=(rows:number[])=>rows.reduce((v,n)=>v*(1-n/100),1);
  const beforeRemain=remain(a.ignoreDefense),afterRemain=remain(b.ignoreDefense);
  if(beforeRemain===0&&afterRemain!==0)throw new Error('방무 100% 효과 제거 후의 방어율을 복원할 수 없습니다.');
  const iedRemain=beforeRemain===0?0:(1-read('방어율 무시')/100)*afterRemain/beforeRemain;
  const defense=Math.max(0,1-(context.bossDefenseRate??380)/100*iedRemain);
  const finalDamage=(1+read('최종 데미지')/100)*after.factors.finalDamage/current.factors.finalDamage;
  const stat=4*result[jobStat]+result[SUB_STAT[jobStat]]+(DOUBLE_SUB_STAT_JOBS.has(context.job)?result.STR:0);
  return {coefficient:stat*attack*damage*critical*defense*finalDamage,factors:{stat,attack,damage,critical,defense,finalDamage},finalStats:{...result,[attackKey]:attack,'크리티컬 확률':critRate,'크리티컬 데미지':critDamage,'방어율 무시':100*(1-iedRemain)}};
}
