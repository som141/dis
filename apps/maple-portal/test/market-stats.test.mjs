import { test } from 'node:test';
import assert from 'node:assert/strict';
import { marketHistory, priceDistribution, tradeTimestamp } from '../src/market-stats.mjs';
test('boxplot uses interpolated quartiles, IQR whiskers and a true median',()=>{
  const stats=priceDistribution([1,2,3,4,100]);assert.equal(stats.median,3);assert.equal(stats.q1,2);assert.equal(stats.q3,4);assert.equal(stats.whiskerHigh,4);assert.deepEqual(stats.outliers,[100]);assert.equal(priceDistribution([]),null);
});
test('fourteen KST calendar days use sale timestamps and never infer dates from endDate',()=>{
  const now=Date.parse('2026-11-04T03:00:00Z');
  const items=[{status:'SOLD',tradeDate:'2026-10-21T15:00:00Z',price:100},{status:'SOLD',tradeDate:'2026-11-04T11:00:00',price:300},{status:'SOLD',tradeDate:'2026-10-21T14:59:59Z',price:1},{status:'SOLD',endDate:'2026-11-03T00:00:00+09:00',price:500},{status:'SOLD',tradeDate:'2026-11-04T10:00:00+09:00',price:0},{status:'ON_SALE',tradeDate:'2026-11-04T10:00:00+09:00',price:999}];
  const report=marketHistory(items,{now});assert.equal(report.from,'2026-10-22');assert.equal(report.to,'2026-11-04');assert.equal(report.daily.length,14);assert.equal(report.sampleCount,2);assert.equal(report.overall.median,200);assert.equal(report.missingTradeDate,1);assert.equal(report.outsideWindow,1);assert.equal(report.invalidPrice,1);assert.equal(report.nonSales,1);assert.equal(report.daily[1].stats,null);
  assert.equal(tradeTimestamp('2026-11-04T11:00:00'),Date.parse('2026-11-04T02:00:00Z'));
});
