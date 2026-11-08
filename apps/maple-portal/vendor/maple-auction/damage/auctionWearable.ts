import type { AuctionItem } from '../auction/item.js';
import { getJobClass, type JobClass } from './stat/job.js';
import secondaryRules from './secondaryWeaponRules.json';

// Exact secondary-weapon names come from the equipment catalog and Nexon's
// official job tables. Broad tooltip labels do not encode their job rule.
const SECONDARY_JOBS: Record<string, string[]> = secondaryRules.jobsByName;
// The catalog records these six tradeable emblems by broad class only. Nexon's
// character introductions identify the class-specific equipment families.
// https://maplestory.nexon.com/News/Update/245 (Angelic Buster)
// https://maplestory.nexon.com/News/Update/264 (Xenon)
// https://archive.maplestory.nexon.com/News/Update/241 (Kaiser)
const EMBLEM_JOBS: Record<string, string[]> = {
  '드래곤 엠블렘': ['카이저'], '레서 드래곤 엠블렘': ['카이저'],
  '엔젤 엠블렘': ['엔젤릭버스터'], '레서 엔젤 엠블렘': ['엔젤릭버스터'],
  '하이브리드 하트': ['제논'], '실버 하이브리드 하트': ['제논'],
};
const ALL_JOBS = new Set(['공용', '전체', '전직업', '모든직업', '전체직업', '직업제한없음']);
const JOB_ALIASES: Record<string, string> = {
  '캐논 슈터': '캐논마스터', 캐논슈터: '캐논마스터',
  '데몬 슬레이어': '데몬슬레이어', '데몬 어벤져': '데몬어벤져',
  '아크메이지(불, 독)': '아크메이지(불,독)',
  '아크메이지(썬, 콜)': '아크메이지(썬,콜)',
};
const normalize = (value: string) => {
  const trimmed = value.trim();
  return Object.hasOwn(JOB_ALIASES, trimmed) ? JOB_ALIASES[trimmed] : trimmed;
};

export function secondaryWeaponJobs(itemName: string): readonly string[] | null {
  const name = itemName.trim().normalize('NFC');
  return Object.hasOwn(SECONDARY_JOBS, name) ? SECONDARY_JOBS[name] : null;
}

export function emblemJobs(itemName: string): readonly string[] | null {
  const name = itemName.trim().normalize('NFC');
  return Object.hasOwn(EMBLEM_JOBS, name) ? EMBLEM_JOBS[name] : null;
}

// Nexon update 614 names all five level-200 Mitra variants by job class.
// https://maplestory.nexon.com/News/Update/614
export function mitraEmblemClass(itemName: string): JobClass | null {
  const match = /^미트라의 분노\s*:\s*(전사|마법사|궁수|도적|해적)$/.exec(itemName.trim().normalize('NFC'));
  return match ? match[1] as JobClass : null;
}

export function auctionWearabilityError(item: Pick<AuctionItem, 'toolTip'> & Partial<Pick<AuctionItem, 'itemName'>>, job: string): string | null {
  const normalizedJob = normalize(job);
  const knownSecondaryJobs = secondaryWeaponJobs(item.itemName ?? item.toolTip?.itemName ?? '');
  if (knownSecondaryJobs) {
    return knownSecondaryJobs.includes(normalizedJob) ? null : `${knownSecondaryJobs.join('·')} 전용 보조무기입니다.`;
  }
  const knownEmblemJobs = emblemJobs(item.itemName ?? item.toolTip?.itemName ?? '');
  if (knownEmblemJobs) {
    return knownEmblemJobs.includes(normalizedJob) ? null : `${knownEmblemJobs.join('·')} 전용 엠블렘입니다.`;
  }
  const mitraClass = mitraEmblemClass(item.itemName ?? item.toolTip?.itemName ?? '');
  if (mitraClass) {
    const actualClass = getJobClass(normalizedJob);
    const xenonHybrid = normalizedJob === '제논' && (mitraClass === '도적' || mitraClass === '해적');
    return actualClass === mitraClass || xenonHybrid ? null : `${mitraClass} 직업군 전용 엠블렘입니다.`;
  }
  const reqJob = item.toolTip?.reqJob?.trim();
  if (reqJob && !ALL_JOBS.has(reqJob.replace(/\s/g, ''))) {
    // Commas inside job names (e.g. 아크메이지(불,독)) are not separators.
    const allowed = reqJob.split(/[,/·](?![^()]*\))/)
      .map(value => normalize(value.replace(/\s*전용$/, '')))
      .some(value => value === normalizedJob || value === getJobClass(normalizedJob));
    if(!allowed)return `요구 직업 ${reqJob.slice(0,40)}과 현재 직업 ${job.slice(0,40)}이 맞지 않습니다.`;
  }
  if ((item.toolTip?.unwearableJobNames ?? []).some(name => normalize(name) === normalizedJob)) return '경매장 정보에서 현재 직업을 착용 불가로 표시했습니다.';
  return null;
}

export function isAuctionItemWearable(item: Pick<AuctionItem, 'toolTip'> & Partial<Pick<AuctionItem, 'itemName'>>, job: string): boolean {
  return auctionWearabilityError(item, job) === null;
}
