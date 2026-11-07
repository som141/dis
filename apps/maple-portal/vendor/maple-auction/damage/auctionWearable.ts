import type { AuctionItem } from '../auction/item.js';
import { getJobClass } from './stat/job.js';

// The tooltip can describe a whole job class or a specific job. A few older
// secondary weapons show a generic class label despite a narrower game rule.
// Nexon's update 794 lists these four documents as Evan secondary weapons:
// https://maplestory.nexon.com/News/Update/794
const EVAN_DOCUMENTS = new Set([
  '드래곤마스터의 유산', '이볼빙 드래곤마스터의 유산',
  '블랙 드래곤마스터의 유산', '메이플 트레져의 유산',
]);
const ALL_JOBS = new Set(['공용', '전체', '전직업', '모든 직업']);

export function auctionWearabilityError(item: Pick<AuctionItem, 'toolTip'> & Partial<Pick<AuctionItem, 'itemName'>>, job: string): string | null {
  if (EVAN_DOCUMENTS.has(item.itemName ?? '')) return job === '에반' ? null : '에반 전용 보조무기입니다.';
  const reqJob = item.toolTip?.reqJob?.trim();
  if (reqJob && !ALL_JOBS.has(reqJob)) {
    const allowed = reqJob.split(/[,/·]/).map(value=>value.trim().replace(/\s*전용$/, '')).some(value=>value===job||value===getJobClass(job));
    if(!allowed)return `요구 직업 ${reqJob.slice(0,40)}과 현재 직업 ${job.slice(0,40)}이 맞지 않습니다.`;
  }
  if ((item.toolTip?.unwearableJobNames ?? []).includes(job)) return '경매장 정보에서 현재 직업을 착용 불가로 표시했습니다.';
  return null;
}

export function isAuctionItemWearable(item: Pick<AuctionItem, 'toolTip'> & Partial<Pick<AuctionItem, 'itemName'>>, job: string): boolean {
  return auctionWearabilityError(item, job) === null;
}
