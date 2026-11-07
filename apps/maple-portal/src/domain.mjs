import {
  AuctionService, AuctionBridge, MapleAuctionApi, loadCharacterSnapshot,
  refreshCharacterSnapshot, clearNexonResponseCache,
  ARMOR_CATEGORY_LABELS, WEAPON_CATEGORY_LABELS, POTENTIAL_OPTION_LABELS,
} from '../dist/core.mjs';
import { marketHistory, tradeTimestamp } from './market-stats.mjs';
import { applyPotentialMaximums, MAXIMUM_OPTION_KEYS } from './potential-limits.mjs';

export const INTERVAL_MS = 30 * 60 * 1000;
export const DEFAULT_CHARACTER = '에반이였솜';
export const CATEGORIES = { ...ARMOR_CATEGORY_LABELS, ...WEAPON_CATEGORY_LABELS };
export class AppError extends Error {
  constructor(message, status = 400, code = 'INVALID_INPUT') { super(message); this.status = status; this.code = code; }
}
export function requireObject(value) {
  if (typeof value === 'string') {
    const match=value.match(/(?:API code |HTTP_|\bcode[^0-9]*)(404[012])/i);
    const code=/넥슨 오픈 API 응답 시간 초과/.test(value)?'NEXON_TIMEOUT':/결과가 너무 많/.test(value)||match?.[1]==='4040'?'TOO_MANY_RESULTS':match?.[1]==='4041'?'PROVIDER_THROTTLED':match?.[1]==='4042'?'QUOTA_EXHAUSTED':/429/.test(value)?'PROVIDER_THROTTLED':/401|403|세션|인증|로그인/.test(value)?'AUTH_EXPIRED':/400|조건|필터/.test(value)?'INVALID_QUERY':'PROVIDER_ERROR';
    throw new AppError({NEXON_TIMEOUT:'넥슨 캐릭터 정보 응답이 지연됐습니다. 잠시 후 다시 조회해 주세요.',TOO_MANY_RESULTS:'결과가 너무 많습니다. 아이템·가격·옵션 조건을 좁혀 주세요.',PROVIDER_THROTTLED:'경매장이 연속 조회를 제한했습니다. 잠시 후 직접 다시 조회하세요.',QUOTA_EXHAUSTED:'오늘 검색 한도를 모두 사용했습니다.',AUTH_EXPIRED:'공식 경매장 인증이 만료됐거나 변경됐습니다. 계정을 다시 연결하세요.',INVALID_QUERY:'경매장이 검색 조건을 거절했습니다. 부위와 옵션 조건을 확인하세요.',PROVIDER_ERROR:'경매장 응답을 확인하지 못했습니다. 자동으로 재검색하지 않습니다.'}[code],422,code);
  }
  if (!value || typeof value !== 'object') throw new AppError('제공자 응답 형식이 올바르지 않습니다.', 502, 'INVALID_RESPONSE');
  return value;
}
// Recognize scheduled work and submissions created by the retired fragment view.
export function isFragmentSearch(input) {
  return input?.id === 'sol-fragment' || (typeof input?.keyword === 'string' && input.keyword.replace(/\s/g, '') === '솔에르다조각');
}
export function parseQuery(input) {
  if (isFragmentSearch(input)) throw new AppError('솔 에르다 조각 시세 기능은 종료되었습니다. 장비를 선택해 주세요.');
  const keyword = typeof input.keyword === 'string' ? input.keyword.trim() : '';
  if (keyword.length > 80) throw new AppError('아이템 이름은 80자 이내로 입력하세요.');
  const category = input.category === 'ALL' ? undefined : input.category;
  if (category && !Object.hasOwn(CATEGORIES, category)) throw new AppError('목록에서 부위를 선택하세요.');
  if (!keyword && !category) throw new AppError('아이템 이름 또는 부위를 선택하세요.');
  const budget = Number(input.budget);
  if (!['none','custom'].includes(input.budgetMode) && (!Number.isFinite(budget) || budget <= 0 || !Number.isSafeInteger(Math.round(budget*1e8)))) throw new AppError('올바른 예산을 입력하세요.');
  if(input.bossDefenseRate!=null&&(!Number.isFinite(Number(input.bossDefenseRate))||Number(input.bossDefenseRate)<0||Number(input.bossDefenseRate)>1000))throw new AppError('대상 방어율은 0~1000%로 입력하세요.');
  const star = Number(input.starforceMin ?? 0), grade = Number(input.potentialGrade ?? 0);
  if (!Number.isInteger(star) || star < 0 || star > 30 || !Number.isInteger(grade) || grade < 0 || grade > 4) throw new AppError('스타포스·잠재 등급을 확인하세요.');
  const starMax = input.starforceMax === '' || input.starforceMax == null ? null : Number(input.starforceMax);
  if (starMax !== null && (!Number.isInteger(starMax) || starMax < star || starMax > 30)) throw new AppError('최대 스타포스는 최소 스타포스 이상, 30성 이하로 입력하세요.');
  const potentials=normalizeOptionRows(input.potentialOptions,input.option,input.optionMin),additionals=normalizeOptionRows(input.additionalPotentialOptions,input.additionalOption,input.additionalMin);
  parseMaximums(input,potentials,additionals);
  if(input.jobClass&&!['ALL','WARRIOR','MAGE','ARCHER','THIEF','PIRATE'].includes(input.jobClass))throw new AppError('직업군을 확인하세요.');

  const meso = Math.round(budget * 100_000_000), budgetMode=input.budgetMode || (input.hardCap ? 'max' : 'range');
  if (!['none','max','range','custom'].includes(budgetMode)) throw new AppError('예산 검색 방식을 확인하세요.');
  if(potentials.some(x=>x.option.endsWith('PerLevel')))throw new AppError('레벨당 스탯은 에디 잠재에서 선택하세요.');
  const additional = Number(input.additionalGrade ?? 0);
  if (!Number.isInteger(additional) || additional<0 || additional>4) throw new AppError('에디셔널 등급을 확인하세요.');
  if(input.potentialNone&&potentials.length||input.additionalNone&&additionals.length)throw new AppError('잠재 없음 등급에는 옵션 조건을 지정할 수 없습니다.');
  return {
    ...(keyword ? {keyword} : {}), ...(category ? {category} : {}),
    ...(budgetMode==='none' ? {} : priceBounds(input,meso,budgetMode)),
    ...(star>0?{starforceMin:star}:{}), ...(grade || input.potentialNone ? { potentialGrade: grade } : {}),
    ...(additional || input.additionalNone ? {additionalPotentialGrade:additional} : {}),
    ...(input.jobClass && input.jobClass!=='ALL' ? {jobClass:input.jobClass} : {}),
    ...(additionals.length?{additionalPotentialOptions:additionals,additionalPotentialSum:true}:{}),
    ...(potentials.length?{potentialOptions:potentials,potentialSum:true}:{}),
    exactMatch: input.exactMatch === true, myWorldOnly: input.myWorldOnly === true,
  };
}

export function normalizeMaximumRows(rows){
  if(rows==null)return [];
  if(!Array.isArray(rows)||rows.length>8)throw new AppError('옵션 상한은 잠재·에디 각각 최대 8개까지 입력하세요.');
  const unique=new Map();
  for(const row of rows){
    if(!row||typeof row!=='object'||!MAXIMUM_OPTION_KEYS.includes(row.option))throw new AppError('상한을 확인할 수 있는 옵션을 목록에서 선택하세요.');
    if(row.maxValue===''||row.maxValue==null||typeof row.maxValue==='boolean')throw new AppError('옵션 상한을 입력하세요.');
    const max=Number(row.maxValue);
    if(!Number.isFinite(max)||max<0||max>10000)throw new AppError('옵션 상한은 0~10000의 숫자로 입력하세요.');
    unique.set(row.option,Math.min(max,unique.get(row.option)??Infinity));
  }
  return [...unique].map(([option,maxValue])=>({option,maxValue})).sort((a,b)=>a.option.localeCompare(b.option));
}
export function parseMaximums(input,potentials=normalizeOptionRows(input.potentialOptions,input.option,input.optionMin),additionals=normalizeOptionRows(input.additionalPotentialOptions,input.additionalOption,input.additionalMin)){
  const potentialMaximums=normalizeMaximumRows(input.potentialMaximums),additionalPotentialMaximums=normalizeMaximumRows(input.additionalPotentialMaximums);
  if(input.potentialNone&&potentialMaximums.length||input.additionalNone&&additionalPotentialMaximums.length)throw new AppError('잠재 없음 등급에는 옵션 상한을 지정할 수 없습니다.');
  for(const [minimums,maximums] of [[potentials,potentialMaximums],[additionals,additionalPotentialMaximums]])for(const max of maximums){
    const min=minimums.find(row=>row.option===max.option)?.minValue;
    if(min!=null&&min>max.maxValue)throw new AppError('같은 옵션의 최소치가 상한보다 큽니다.');
  }
  return {potentialMaximums,additionalPotentialMaximums};
}

export function normalizeOptionRows(rows,legacyOption='',legacyMin=0){
  if(rows==null)rows=legacyOption?[{option:legacyOption,minValue:legacyMin??0}]:[];
  if(!Array.isArray(rows)||rows.length>8)throw new AppError('잠재 조건은 각 영역에 최대 8개까지 입력하세요.');
  const unique=new Map();
  for(const row of rows){
    if(!row||typeof row!=='object'||!Object.hasOwn(POTENTIAL_OPTION_LABELS,row.option))throw new AppError('목록에 있는 잠재 옵션을 선택하세요.');
    if(row.minValue===''||row.minValue==null||typeof row.minValue==='boolean')throw new AppError('옵션 최소치를 입력하세요.');
    const min=Number(row.minValue);
    if(!Number.isFinite(min)||min<0||min>10000)throw new AppError('옵션 최소치는 0~10000의 숫자로 입력하세요.');
    if(min>0)unique.set(row.option,Math.max(min,unique.get(row.option)||0));
  }
  return [...unique].map(([option,minValue])=>({option,minValue})).sort((a,b)=>a.option.localeCompare(b.option));
}
export function priceBounds(input,meso,mode) {
  const percent=Number(input.budgetPercent??30);
  if(!Number.isFinite(percent)||percent<0||percent>100)throw new AppError('예산 비율은 0~100%로 입력하세요.');
  const min=mode==='custom'?(input.budgetMin===''||input.budgetMin==null?undefined:Math.round(Number(input.budgetMin)*1e8)):mode==='max'?0:Math.ceil(meso*(1-percent/100));
  const max=mode==='custom'?(input.budgetMax===''||input.budgetMax==null?undefined:Math.round(Number(input.budgetMax)*1e8)):mode==='max'?meso:Math.floor(meso*(1+percent/100));
  for(const n of [min,max])if(n!==undefined&&(!Number.isSafeInteger(n)||n<0))throw new AppError('예산 범위를 확인하세요.');
  if(min!=null&&max!=null&&min>max)throw new AppError('최소 예산이 최대 예산보다 큽니다.');
  return {...(min!=null?{priceMin:min}:{}),...(max!=null?{priceMax:max}:{})};
}
export function rankItems(items = []) {
  return items.map(item => {
    const comparisons = Object.entries(item.finalDamageChangeRate ?? {}).filter(([, n]) => typeof n === 'number' && Number.isFinite(n));
    const best = comparisons.length ? comparisons.reduce((a, b) => b[1] > a[1] ? b : a) : null;
    const price = Number(item.price);
    return { ...item, comparison: best ? {
      slot: best[0], gainPercent: best[1], score: 100 + best[1],
      efficiency: price > 0 && Number.isFinite(price) ? best[1] / (price / 100_000_000) : null,
      model:'동일 조건의 장비 교체 기대 피해 계수',details:item.comparisonDetails?.[best[0]],
    } : null };
  }).sort((a, b) => {
    const x = a.comparison?.efficiency, y = b.comparison?.efficiency;
    if (x == null && y == null) return Number(a.price) - Number(b.price);
    if (x == null) return 1;
    if (y == null) return -1;
    return y - x;
  });
}

export function topTenChanges(previousIds, rankedItems, minGain = 0) {
  const current = rankedItems.slice(0, 10);
  const next = current.map(item => item.id);
  if (!Array.isArray(previousIds)) return { next, changes: [] };
  const before = new Map(previousIds.map((id, index) => [id, index + 1]));
  const changes = current.flatMap((item, index) => {
    const previousRank = before.get(item.id) ?? null;
    const rank = index + 1;
    if (previousRank === rank || (minGain > 0 && !(item.comparison?.gainPercent >= minGain))) return [];
    return [{ item, rank, previousRank }];
  });
  return { next, changes };
}

export class PrototypeEngine {
  constructor(wire, { now = Date.now, loadSnapshot = loadCharacterSnapshot, refreshSnapshot = refreshCharacterSnapshot, manageKeyEnvironment = true, completePageLimit = 0 } = {}) {
    this.wire = wire; this.now = now; this.loadSnapshot = loadSnapshot; this.refreshSnapshot = refreshSnapshot;
    this.manageKeyEnvironment = manageKeyEnvironment;
    this.completePageLimit = Number.isInteger(completePageLimit) && completePageLimit>0 ? Math.min(10000,completePageLimit) : 0;
    this.collection = null; this.cancelRequested = false;
    this.busy = false; this.key = process.env.NEXON_DEVELOPER_KEY || ''; this.characters = []; this.character = null;
    this.profile = null; this.profileAt = null; this.result = null; this.watch = null; this.notices = [];
    this.resetService();
  }
  resetService() {
    this.api = new MapleAuctionApi(new AuctionBridge(this.wire.scope()));
    // Selecting a character uses the normal cache; a full refresh is an explicit UI action.
    this.service = new AuctionService(this.api, this.loadSnapshot, this.loadSnapshot);
  }
  connectionChanged() {
    this.resetService(); this.characters = []; this.character = null; this.profile = null; this.profileAt = null; this.result = null; this.notices = []; this.lastInput = null;
    if (this.watch && !['종료', '만료'].includes(this.watch.status)) this.watch.status = '인증 필요';
  }
  guard(epoch) {
    if (epoch !== this.wire.epoch || !this.wire.connected) throw new AppError('브라우저 연결이 변경되었습니다. 연결 확인 후 다시 선택하세요.', 409, 'CONNECTION_CHANGED');
  }
  async run(fn) {
    if (this.busy) throw new AppError('이전 조회가 진행 중입니다. 완료 후 다시 시도하세요.', 409, 'BUSY');
    this.busy = true;
    try { return await fn(); } finally { this.busy = false; }
  }
  setKey(key) {
    if (typeof key !== 'string' || (key && !/^[A-Za-z0-9._-]{10,1024}$/.test(key))) throw new AppError('키 형식을 확인하세요.');
    this.key = key;
    if (this.manageKeyEnvironment) {
      if (key) process.env.NEXON_DEVELOPER_KEY = key;
      else delete process.env.NEXON_DEVELOPER_KEY;
    }
    clearNexonResponseCache(); this.profile = null; this.profileAt = null; this.result = null;
    if (this.watch && !['종료', '만료'].includes(this.watch.status)) this.watch.status = '설정 변경 · 재확인 필요';
    return { configured: !!key };
  }
  async connect() {
    if (!this.wire.connected) throw new AppError('브라우저 연결을 확인하세요. 도커에서는 공식 경매장 탭, Windows에서는 동봉된 확장과 연결코드를 확인하세요.', 409, 'NO_BROWSER');
    this.resetService();
    const epoch = this.wire.epoch, svc = this.service;
    const characters = requireObject(await svc.listCharacters());
    this.guard(epoch);
    if (!Array.isArray(characters)) throw new AppError('캐릭터 목록을 읽지 못했습니다.', 502);
    this.characters = characters;
    this.character = null; this.profile = null; this.result = null;
    if (this.watch && !['종료', '만료'].includes(this.watch.status)) this.watch.status = '캐릭터 재확인 필요';
    return { characters };
  }
  async selectCharacter(id) {
    const c = this.characters.find(c => c.characterId === Number(id));
    if (!c) throw new AppError('연결 확인으로 불러온 본인 캐릭터를 선택하세요.');
    const epoch = this.wire.epoch;
    const result = requireObject(await this.service.setCharacter(c.name, c.characterId));
    this.guard(epoch);
    if (!result.switched) throw new AppError('캐릭터 선택에 실패했습니다.', 422);
    this.character = c; this.profile = null; this.profileAt = null; this.result = null;
    if (this.watch && !['종료', '만료'].includes(this.watch.status)) this.watch.status = '캐릭터 변경 · 재확인 필요';
    return result;
  }
  async equipment(refresh = false) {
    if (!this.key) throw new AppError('계산용 넥슨 Open API 키를 로컬 설정에 입력하세요. 키 없이 매물 검색은 가능합니다.', 422, 'NO_KEY');
    if (!this.character) throw new AppError('먼저 본인 캐릭터를 선택하세요.');
    const epoch = this.wire.epoch, svc = this.service, name = this.character.name;
    if (refresh) await this.refreshSnapshot(name);
    const result = requireObject(await svc.userEquip(name));
    this.guard(epoch); this.profile = result; this.profileAt = new Date(this.now()).toISOString();
    return { ...result, loadedAt: this.profileAt };
  }
  async quota() {
    const reply = await this.api.getDailyLimit();
    if (!reply.ok) throw new AppError('검색 잔여량을 확인하지 못했습니다. 공식 경매장 로그인 상태를 확인하세요.', 422, 'QUOTA_UNKNOWN');
    const data = reply.data?.search;
    if (!data || !Number.isFinite(data.remaining)) throw new AppError('검색 한도 응답 형식이 달라졌습니다.', 502, 'QUOTA_UNKNOWN');
    return data;
  }
  async search(input, { fromWatch = false } = {}) {
    if (!this.character) throw new AppError('연결 확인 후 본인 캐릭터를 선택하세요.');
    this.collection=null;this.cancelRequested=false;
    const query = parseQuery(input), maximums=parseMaximums(input),epoch = this.wire.epoch, svc = this.service;
    const quota = await this.quota(); this.guard(epoch);
    if(this.cancelRequested)throw new AppError('조회가 중지되었습니다.',409,'CANCELLED');
    if (quota.remaining <= 0) throw new AppError('오늘 검색 한도를 모두 사용했습니다. 초기화 후 다시 시도하세요.', 429, 'QUOTA_EXHAUSTED');
    svc.setComparisonContext?.({bossDefenseRate:Number(input.bossDefenseRate??380),statusDamageUptime:1,ringChoice:'none'});
    svc.clearSearchCache(); // Fresh POST: never assume old handles include new listings.
    const sold = input.sold === true;
    const compare = input.compare !== false && !sold;
    let summary = requireObject(await svc.search(query,sold,{compare}));
    this.guard(epoch);
    if (!Array.isArray(summary.items) || !summary.searchKey || !Number.isSafeInteger(summary.total) || summary.total<0) throw new AppError('검색 결과 형식이 달라졌습니다.', 502, 'INVALID_RESPONSE');
    const target=Math.min(Number(summary.total)||0,this.completePageLimit||summary.items.length),seen=new Map();
    let failed=null;
    this.collection={status:'loading',loaded:0,total:summary.total,target,page:0,pages:Math.ceil(target/60),capped:summary.total>10000};
    try {
      if (summary.total>summary.items.length && this.completePageLimit) {
        for (let number=1;number<=Math.ceil(target/60);number++) {
          if(this.cancelRequested) throw new AppError('조회가 중지되었습니다.',409,'CANCELLED');
          this.guard(epoch);
          const page=requireObject(await svc.getPage(summary.searchKey,number,60,'PRICE_PER_ITEM_ASC',{allowRecreate:false,compare}));
          this.guard(epoch);
          if(!Array.isArray(page.items)) throw new AppError('검색 결과 형식을 확인해 주세요.',502,'INVALID_RESPONSE');
          if(page.searchKey && page.searchKey!==summary.searchKey) throw new AppError('검색 연결이 변경되었습니다.',409,'CONNECTION_CHANGED');
          for (const item of page.items) {
            if (item.id && !seen.has(item.id) && seen.size < target) seen.set(item.id, item);
          }
          this.collection={...this.collection,loaded:seen.size,page:number};
          if(page.items.length===0 || page.hasNext===false) break;
          if(number<Math.ceil(target/60)) await new Promise(r=>setTimeout(r,120));
        }
      } else for(const item of summary.items) if(item.id) seen.set(item.id,item);
      if(this.cancelRequested) throw new AppError('조회가 중지되었습니다.',409,'CANCELLED');
      if(seen.size<target) throw new AppError('일부 페이지를 불러오지 못했습니다. 현재 결과는 부분 조회입니다.',502,'INCOMPLETE_RESULTS');
    } catch(e) {failed=e;}
    this.collection={...this.collection,status:failed ? 'incomplete' : 'complete',loaded:seen.size,complete:!failed && seen.size>=Math.min(summary.total,10000),error:failed?.code||null};
    const filtered=applyPotentialMaximums([...seen.values()],maximums.potentialMaximums,maximums.additionalPotentialMaximums);
    const starMax=input.starforceMax===''||input.starforceMax==null?null:Number(input.starforceMax);
    const allItems=starMax===null?filtered.items:filtered.items.filter(item=>item.starforce!=null&&item.starforce!==''&&Number.isInteger(Number(item.starforce))&&Number(item.starforce)<=starMax);
    const starforceFilter={enabled:starMax!==null,max:starMax,excluded:filtered.items.length-allItems.length};
    const market = sold ? marketHistory(allItems, {now:this.now(), coverage:this.collection}) : null;
    const marketItems=sold ? allItems.filter(item=>{const stamp=tradeTimestamp(item.tradeDate);return item.status==='SOLD' && Number.isFinite(Number(item.price)) && Number(item.price)>0 && Number.isFinite(stamp) && stamp<=this.now() && market.from<=new Date(stamp+9*3600000).toISOString().slice(0,10) && new Date(stamp+9*3600000).toISOString().slice(0,10)<=market.to;}) : allItems;
    const result = { ...summary, items:sold ? marketItems.sort((a,b)=>tradeTimestamp(b.tradeDate)-tradeTimestamp(a.tradeDate)) : rankItems(allItems),query,collection:this.collection,market,localFilter:filtered.summary,starforceFilter,
      character: this.character.name, characterId: this.character.characterId, sold: input.sold === true,
      fetchedAt: new Date(this.now()).toISOString(), model: '독립 장비 교체 모델 · 환산주스탯 수치 아님',
      coverage:(this.collection.capped ? '제공자 조회 상한인 10,000건 범위입니다. 전체 결과를 포함하지 않습니다.' : failed ? '페이지 조회가 완료되지 않은 부분 결과입니다.' : '검색 결과 페이지를 모두 조회했습니다.') };
    this.result = result;
    this.lastInput = structuredClone(input);
    if (!fromWatch && this.watch && !['종료', '만료'].includes(this.watch.status)) this.watch.status = '검색 변경 · 재확인 필요';
    if(failed) throw failed;
    return result;
  }
  async page(input) {
    if (!this.result) throw new AppError('먼저 검색하세요.');
    const page = Number(input.page), limit = Number(input.limit || 20);
    const sorts = ['PRICE_PER_ITEM_ASC', 'PRICE_DESC', 'ATTACK_POWER_DESC', 'END_DATE_ASC', 'REGISTER_DATE_DESC', 'ITEM_NAME_ASC'];
    if (!Number.isInteger(page) || page < 1 || ![20, 40, 60].includes(limit) || !sorts.includes(input.sort)) throw new AppError('페이지·정렬 조건을 확인하세요.');
    const epoch = this.wire.epoch, previous = this.result;
    const summary = requireObject(await this.service.getPage(previous.searchKey, page, limit, input.sort));
    this.guard(epoch);
    this.result = { ...previous, ...summary, items: rankItems(summary.items), fetchedAt: new Date(this.now()).toISOString() };
    return this.result;
  }
  startWatch({ days = 1, minGain = 0, intervalMinutes = 30 } = {}) {
    if (!this.result || this.result.sold || this.result.characterId !== this.character?.characterId) throw new AppError('판매 중 매물을 검색한 뒤 감시를 시작하세요.');
    if (this.result.total>this.result.items.length && !this.result.collection?.complete) throw new AppError('일부 결과가 누락되어 감시를 시작할 수 없습니다.',409,'INCOMPLETE_RESULTS');
    if (!Number.isInteger(Number(days)) || Number(days)<0 || Number(days)>365 || !Number.isFinite(Number(minGain)) || Number(minGain)<0 || !Number.isInteger(Number(intervalMinutes)) || Number(intervalMinutes)<1 || Number(intervalMinutes)>1440) throw new AppError('기간·개선율·조회 간격을 확인하세요.');
    if (Number(minGain) > 0 && !this.result.items.some(i => i.comparison)) throw new AppError('개선율 조건을 쓰려면 계산 가능한 결과가 필요합니다. 스펙 조건만 감시하려면 0으로 설정하세요.');
    if (!this.lastInput) throw new AppError('다시 검색한 뒤 감시를 시작하세요.');
    const intervalMs=Number(intervalMinutes)*60000;
    this.watch = { status:'감시 중',intervalMs,startedAt:this.now(),expiresAt:Number(days)===0 ? null : this.now()+Number(days)*86400000,
      nextPollAt:this.now()+intervalMs,lastCheckedAt:this.result.fetchedAt,characterId:this.character.characterId,
      input: { ...this.lastInput }, minGain: Number(minGain), seen: new Set(this.result.items.map(i => i.id)), top10:this.result.items.slice(0,10).map(i=>i.id), error: null };
    return this.watchView();
  }
  stopWatch() { if (this.watch) this.watch.status = '종료'; return this.watchView(); }
  watchView() { if (!this.watch) return null; const { seen, input, ...view } = this.watch; return { ...view, seenCount: seen.size }; }
  async tick() {
    const w = this.watch;
    if (!w || w.status !== '감시 중' || this.busy) return;
    if (w.expiresAt!=null && this.now() >= w.expiresAt) { w.status = '만료'; return; }
    if (this.now() < w.nextPollAt) return;
    await this.run(async () => {
      try {
        if (this.character?.characterId !== w.characterId) throw new AppError('캐릭터를 다시 확인하세요.', 409, 'CONNECTION_CHANGED');
        const result = await this.search(w.input, { fromWatch: true });
        if (this.watch !== w || w.status !== '감시 중' || (w.expiresAt!=null && this.now() >= w.expiresAt)) return;
        if (result.total>result.items.length && !result.collection?.complete) { w.status='일부 결과 누락 · 재확인 필요';return; }
        const ranking=topTenChanges(w.top10,result.items,w.minGain);
        for (const change of ranking.changes) this.notices.unshift({ ...change, foundAt:new Date(this.now()).toISOString(), character:result.character });
        w.top10=ranking.next;
        for (const item of result.items) w.seen.add(item.id);
        this.notices = this.notices.slice(0, 100);
        w.lastCheckedAt = result.fetchedAt; w.error = null;
      } catch (e) {
        w.error = e.code || 'PROVIDER_ERROR';
        // Do not retry a POST immediately: even a timeout may have consumed quota.
        if (e.code === 'QUOTA_EXHAUSTED') w.status = '한도 대기';
        else if (!this.wire.connected || e.code === 'CONNECTION_CHANGED') w.status = '인증 필요';
      } finally { w.nextPollAt=this.now()+w.intervalMs; }
    });
  }
  state() {
    return { connected: this.wire.connected, busy: this.busy, keyConfigured: !!this.key,
      defaultCharacter: DEFAULT_CHARACTER, characters: this.characters, character: this.character,
      profile: this.profile, profileLoadedAt: this.profileAt, result: this.result,
      watch: this.watchView(), notices: this.notices, intervalMs: INTERVAL_MS };
  }
  destroy() { this.setKey(''); }
}
