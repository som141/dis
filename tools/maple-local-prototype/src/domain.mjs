import {
  AuctionService, AuctionBridge, MapleAuctionApi, loadCharacterSnapshot,
  refreshCharacterSnapshot, clearNexonResponseCache,
  ARMOR_CATEGORY_LABELS, WEAPON_CATEGORY_LABELS,
} from '../dist/core.mjs';

export const INTERVAL_MS = 30 * 60 * 1000;
export const DEFAULT_CHARACTER = '에반이였솜';
export const CATEGORIES = { ...ARMOR_CATEGORY_LABELS, ...WEAPON_CATEGORY_LABELS };
export class AppError extends Error {
  constructor(message, status = 400, code = 'INVALID_INPUT') { super(message); this.status = status; this.code = code; }
}
export function requireObject(value) {
  if (typeof value === 'string') throw new AppError(value, 422, 'PROVIDER_ERROR');
  if (!value || typeof value !== 'object') throw new AppError('제공자 응답 형식이 올바르지 않습니다.', 502, 'INVALID_RESPONSE');
  return value;
}
export function parseQuery(input) {
  const keyword = typeof input.keyword === 'string' ? input.keyword.trim() : '';
  if (!keyword || keyword.length > 80) throw new AppError('아이템 이름을 1~80자로 입력하세요.');
  if (!Object.hasOwn(CATEGORIES, input.category)) throw new AppError('목록에서 부위를 선택하세요.');
  const budget = Number(input.budget);
  if (!Number.isFinite(budget) || budget <= 0 || budget > 10000) throw new AppError('예산은 0 초과 10,000억 이하로 입력하세요.');
  const star = Number(input.starforceMin ?? 0), grade = Number(input.potentialGrade ?? 0);
  if (!Number.isInteger(star) || star < 0 || star > 30 || !Number.isInteger(grade) || grade < 0 || grade > 4) throw new AppError('스타포스·잠재 등급을 확인하세요.');
  const option = input.option || '', allowed = ['intPercent', 'magicAttackPercent', 'bossDamagePercent', 'ignoreMonsterDefense', 'criticalDamagePercent', 'strPercent', 'dexPercent', 'lukPercent', 'physicalAttackPercent', 'allStatsPercent'];
  const min = Number(input.optionMin ?? 0);
  if ((option && !allowed.includes(option)) || !Number.isFinite(min) || min < 0 || min > 1000) throw new AppError('옵션 조건을 확인하세요.');
  const meso = Math.round(budget * 100_000_000);
  return {
    keyword, category: input.category,
    priceMin: Math.ceil(meso * 7 / 10), priceMax: Math.floor(meso * (input.hardCap === true ? 1 : 1.3)),
    starforceMin: star, ...(grade ? { potentialGrade: grade } : {}),
    ...(option && min > 0 ? { potentialOptions: [{ option, minValue: min }], potentialSum: true } : {}),
    exactMatch: input.exactMatch === true, myWorldOnly: input.myWorldOnly === true,
  };
}
export function rankItems(items = []) {
  return items.map(item => {
    const comparisons = Object.entries(item.finalDamageChangeRate ?? {}).filter(([, n]) => typeof n === 'number' && Number.isFinite(n));
    const best = comparisons.length ? comparisons.reduce((a, b) => b[1] > a[1] ? b : a) : null;
    const price = Number(item.price);
    return { ...item, comparison: best ? {
      slot: best[0], gainPercent: best[1], score: 100 + best[1],
      efficiency: price > 0 && Number.isFinite(price) ? best[1] / (price / 100_000_000) : null,
      model: '독립 장비 교체 모델 · 보스 방어율 380%',
    } : null };
  }).sort((a, b) => {
    const x = a.comparison?.efficiency, y = b.comparison?.efficiency;
    if (x == null && y == null) return Number(a.price) - Number(b.price);
    if (x == null) return 1;
    if (y == null) return -1;
    return y - x;
  });
}

export class PrototypeEngine {
  constructor(wire, { now = Date.now, loadSnapshot = loadCharacterSnapshot, refreshSnapshot = refreshCharacterSnapshot } = {}) {
    this.wire = wire; this.now = now; this.loadSnapshot = loadSnapshot; this.refreshSnapshot = refreshSnapshot;
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
    if (key) process.env.NEXON_DEVELOPER_KEY = key;
    else delete process.env.NEXON_DEVELOPER_KEY;
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
    const query = parseQuery(input), epoch = this.wire.epoch, svc = this.service;
    const quota = await this.quota(); this.guard(epoch);
    if (quota.remaining <= 0) throw new AppError('오늘 검색 한도를 모두 사용했습니다. 초기화 후 다시 시도하세요.', 429, 'QUOTA_EXHAUSTED');
    svc.clearSearchCache(); // Fresh POST: never assume old handles include new listings.
    const summary = requireObject(await svc.search(query, input.sold === true));
    this.guard(epoch);
    if (!Array.isArray(summary.items) || !summary.searchKey) throw new AppError('검색 결과 형식이 달라졌습니다.', 502, 'INVALID_RESPONSE');
    const result = { ...summary, items: rankItems(summary.items), query,
      character: this.character.name, characterId: this.character.characterId, sold: input.sold === true,
      fetchedAt: new Date(this.now()).toISOString(), model: '독립 장비 교체 모델 · 환산주스탯 수치 아님',
      coverage: '조회한 페이지의 매물만 비교합니다. 전체 매물 최고 순위를 의미하지 않습니다.' };
    this.result = result;
    this.lastInput = { keyword: query.keyword, category: query.category, budget: Number(input.budget),
      starforceMin: query.starforceMin, potentialGrade: Number(input.potentialGrade || 0),
      option: input.option || '', optionMin: Number(input.optionMin || 0),
      hardCap: input.hardCap === true, exactMatch: query.exactMatch, myWorldOnly: query.myWorldOnly, sold: input.sold === true };
    if (!fromWatch && this.watch && !['종료', '만료'].includes(this.watch.status)) this.watch.status = '검색 변경 · 재확인 필요';
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
  startWatch({ days = 1, minGain = 0 } = {}) {
    if (!this.result || this.result.sold || this.result.characterId !== this.character?.characterId) throw new AppError('판매 중 매물을 검색한 뒤 감시를 시작하세요.');
    if (this.result.total > this.result.items.length) throw new AppError('현재 프로토타입 감시는 한 페이지에 모두 담기는 검색만 지원합니다. 이름·옵션·가격을 좁혀 다시 검색하세요.');
    if (![1, 3, 7].includes(Number(days)) || !Number.isFinite(Number(minGain)) || Number(minGain) < 0) throw new AppError('기간·개선율을 확인하세요.');
    if (Number(minGain) > 0 && !this.result.items.some(i => i.comparison)) throw new AppError('개선율 조건을 쓰려면 계산 가능한 결과가 필요합니다. 스펙 조건만 감시하려면 0으로 설정하세요.');
    if (!this.lastInput) throw new AppError('다시 검색한 뒤 감시를 시작하세요.');
    this.watch = { status: '감시 중', intervalMs: INTERVAL_MS, startedAt: this.now(), expiresAt: this.now() + Number(days) * 86400000,
      nextPollAt: this.now() + INTERVAL_MS, lastCheckedAt: this.result.fetchedAt, characterId: this.character.characterId,
      input: { ...this.lastInput }, minGain: Number(minGain), seen: new Set(this.result.items.map(i => i.id)), error: null };
    return this.watchView();
  }
  stopWatch() { if (this.watch) this.watch.status = '종료'; return this.watchView(); }
  watchView() { if (!this.watch) return null; const { seen, input, ...view } = this.watch; return { ...view, seenCount: seen.size }; }
  async tick() {
    const w = this.watch;
    if (!w || w.status !== '감시 중' || this.busy) return;
    if (this.now() >= w.expiresAt) { w.status = '만료'; return; }
    if (this.now() < w.nextPollAt) return;
    await this.run(async () => {
      try {
        if (this.character?.characterId !== w.characterId) throw new AppError('캐릭터를 다시 확인하세요.', 409, 'CONNECTION_CHANGED');
        const result = await this.search(w.input, { fromWatch: true });
        if (this.watch !== w || w.status !== '감시 중' || this.now() >= w.expiresAt) return;
        if (result.total > result.items.length) { w.status = '검색 범위 초과 · 조건 조정 필요'; return; }
        for (const item of result.items) {
          if (!w.seen.has(item.id) && (w.minGain === 0 || item.comparison?.gainPercent >= w.minGain)) {
            this.notices.unshift({ item, foundAt: new Date(this.now()).toISOString(), character: result.character });
          }
          w.seen.add(item.id);
        }
        this.notices = this.notices.slice(0, 100);
        w.lastCheckedAt = result.fetchedAt; w.error = null;
      } catch (e) {
        w.error = e.code || 'PROVIDER_ERROR';
        // Do not retry a POST immediately: even a timeout may have consumed quota.
        if (e.code === 'QUOTA_EXHAUSTED') w.status = '한도 대기';
        else if (!this.wire.connected || e.code === 'CONNECTION_CHANGED') w.status = '인증 필요';
      } finally { w.nextPollAt = this.now() + INTERVAL_MS; }
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
