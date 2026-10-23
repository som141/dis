import { test } from 'node:test';
import assert from 'node:assert/strict';
import { parseQuery, rankItems, PrototypeEngine, INTERVAL_MS } from '../src/domain.mjs';
import { getFinalDamageChangeRate } from '../dist/core.mjs';

const query = { keyword:'아케인셰이드 메이지글러브', category:'ARMOR_ARMOR_GLOVE', budget:30 };
function fixtureWire() {
  let remaining = 100, searches = 0, nextItems = [];
  const wire = { connected:true, epoch:1, scope() { return this; }, async request(cmd) {
    const url = new URL(cmd.url);
    let data;
    if (url.pathname === '/v1/accounts') data = { accounts:[{ accountId:111 }] };
    else if (url.pathname.endsWith('/characters')) data = { characters:url.pathname.includes('/gameWorlds/0/') ? [{ characterId:222, characterName:'에반이였솜', level:280 }] : [] };
    else if (url.pathname.endsWith('/daily-limit')) data = { search:{ remaining, limit:100 } };
    else if (cmd.method === 'POST') { ++searches; --remaining; data = { searchKey:`fixture-${searches}`, page:1, total:nextItems.length, totalPages:1, hasNext:false, items:nextItems }; }
    else data = { searchKey:'fixture-page', page:1, total:nextItems.length, totalPages:1, hasNext:false, items:nextItems };
    return { id:'fixture', ok:true, status:200, bodyText:JSON.stringify(data) };
  } };
  return { wire, setItems(items) { nextItems = items; }, setQuota(n) { remaining = n; }, searches:() => searches };
}
const item = id => ({ _id:id, itemName:'테스트 장갑', pricePerItem:'2500000000', quantity:1, starforce:17, status:'ON_SALE', endDate:'2026-11-01T00:00:00Z', wishlistCount:0 });
async function setup(clock = { now:1000000 }) {
  const f = fixtureWire();
  const engine = new PrototypeEngine(f.wire, { now:() => clock.now, loadSnapshot:async () => { throw new Error('fixture: no API key'); } });
  await engine.connect(); await engine.selectCharacter(222);
  return { ...f, engine, clock };
}

test('30억 ±30% and hard cap use meso, option rows and exact category', () => {
  const q = parseQuery({ ...query, option:'intPercent', optionMin:21 });
  assert.equal(q.priceMin, 2100000000); assert.equal(q.priceMax, 3900000000);
  assert.deepEqual(q.potentialOptions, [{ option:'intPercent', minValue:21 }]);
  assert.equal(parseQuery({ ...query, hardCap:true }).priceMax, 3000000000);
  assert.throws(() => parseQuery({ ...query, budget:Infinity }));
  assert.throws(() => parseQuery({ ...query, category:'__proto__' }));
  assert.throws(() => parseQuery({ ...query, option:'unknown' }));
});
test('Evan uses magic attack, and unchanged equipment yields zero improvement', () => {
  const before = { 기본:{ INT:100, LUK:25, 마력:100, 공격력:100, 방무:[90] }, AP:{} };
  const context = { job:'에반', level:280, stats:before };
  assert.equal(getFinalDamageChangeRate(context, before), 0);
  assert.equal(getFinalDamageChangeRate(context, { ...before, 기본:{ ...before.기본, 마력:110 } }), 10);
  assert.equal(getFinalDamageChangeRate(context, { ...before, 기본:{ ...before.기본, 공격력:999 } }), 0);
});
test('cost effectiveness uses incremental gain; unsupported is not zero', () => {
  const result = rankItems([
    { id:'unknown', price:1 },
    { id:'expensive', price:4000000000, finalDamageChangeRate:{ 장갑:4 } },
    { id:'better', price:2000000000, finalDamageChangeRate:{ 장갑:3 } },
    { id:'loss', price:1000000000, finalDamageChangeRate:{ 장갑:-2 } },
  ]);
  assert.equal(result[0].id, 'better'); assert.equal(result[0].comparison.efficiency, .15);
  assert.equal(result[0].comparison.score, 103); assert.equal(result.at(-1).comparison, null);
});
test('real auction adapter discovers owned character and fresh searches consume quota', async () => {
  const { engine, searches } = await setup();
  assert.equal(engine.character.name, '에반이였솜');
  assert.equal((await engine.search(query)).searchRemaining, 99);
  assert.equal((await engine.search(query)).searchRemaining, 98);
  assert.equal(searches(), 2);
  await assert.rejects(() => engine.selectCharacter(999), /본인 캐릭터/);
});
test('auction tooltip → actual Evan equipment replacement → gain and efficiency', async () => {
  const f = fixtureWire();
  const engine = new PrototypeEngine(f.wire, { loadSnapshot:async () => ({
    name:'에반이였솜', job:'에반', level:280, equipment:{ item_equipment:[] },
    stats:{ 기본:{ 마력:100, 방무:[100] }, AP:{ INT:1000, LUK:100 }, 장비:{ 장갑:{ name:'현재 장갑', stat:{ INT:100, 마력:10 } } }, 세트효과:{} },
  }) });
  engine.setKey('fixture-only-test-key');
  try {
    await engine.connect(); await engine.selectCharacter(222);
    f.setItems([{ ...item('better-glove'), toolTip:{ categories:['방어구', '장갑'], reqJob:'마법사', stat:{ int:100, mad:21 }, upgradeInfo:{} } }]);
    const result = await engine.search(query);
    assert.equal(result.items[0].comparison.slot, '장갑');
    assert.equal(result.items[0].comparison.gainPercent, 10);
    assert.equal(result.items[0].comparison.score, 110);
    assert.equal(result.items[0].comparison.efficiency, .4);
  } finally { engine.destroy(); }
});
test('quota exhaustion and unknown quota prevent a search POST', async () => {
  const { engine, setQuota, searches } = await setup();
  setQuota(0); await assert.rejects(() => engine.search(query), e => e.code === 'QUOTA_EXHAUSTED');
  setQuota(undefined); await assert.rejects(() => engine.search(query), e => e.code === 'QUOTA_UNKNOWN');
  assert.equal(searches(), 0);
});
test('30-minute watch suppresses baseline, fresh POST, deduplication and stop', async () => {
  const { engine, setItems, clock, searches } = await setup();
  setItems([item('old')]); await engine.search(query); engine.startWatch({ days:1 });
  assert.equal(engine.watch.intervalMs, 1800000); assert.equal(engine.notices.length, 0);
  await engine.tick(); assert.equal(searches(), 1);
  setItems([item('old'), item('new')]); clock.now += INTERVAL_MS; await engine.tick();
  assert.equal(searches(), 2); assert.equal(engine.notices.length, 1); assert.equal(engine.notices[0].item.id, 'new');
  clock.now += INTERVAL_MS; await engine.tick(); assert.equal(engine.notices.length, 1);
  engine.stopWatch(); clock.now += INTERVAL_MS; await engine.tick(); assert.equal(searches(), 3);
});
test('expired, partial coverage and changed character do not keep watching', async () => {
  const { engine, clock, searches, wire } = await setup();
  await engine.search(query); engine.result.total = 20;
  assert.throws(() => engine.startWatch(), /한 페이지/);
  engine.result.total = 0; engine.startWatch(); clock.now += 86400000; await engine.tick();
  assert.equal(engine.watch.status, '만료'); assert.equal(searches(), 1);
  engine.startWatch(); ++wire.epoch; engine.connectionChanged();
  assert.equal(engine.character, null); assert.equal(engine.result, null); assert.equal(engine.watch.status, '인증 필요');
  clock.now += INTERVAL_MS; await engine.tick(); assert.equal(searches(), 1);
});
test('watch quota errors wait without immediate retry and long calls do not overlap', async () => {
  const { engine, clock, setQuota, searches } = await setup();
  await engine.search(query); engine.startWatch(); setQuota(0); clock.now += INTERVAL_MS;
  await engine.tick(); assert.equal(engine.watch.status, '한도 대기'); assert.equal(searches(), 1);
  let release;
  const running = engine.run(() => new Promise(r => { release = r; }));
  await assert.rejects(() => engine.run(async () => {}), e => e.code === 'BUSY');
  release(); await running;
});
test('keys are never returned in state and clearing key invalidates calculations', async () => {
  const { engine } = await setup();
  const key = 'fixture-only-test-key';
  engine.setKey(key); assert.equal(engine.state().keyConfigured, true);
  assert.equal(JSON.stringify(engine.state()).includes(key), false);
  engine.setKey(''); assert.equal(engine.state().keyConfigured, false);
  await assert.rejects(() => engine.equipment(), e => e.code === 'NO_KEY');
});
