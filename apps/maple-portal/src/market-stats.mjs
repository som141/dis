const DAY = 86400000, KST = 9 * 3600000;
export const koreanDay = value => new Date(value + KST).toISOString().slice(0,10);
export function tradeTimestamp(value) {
  if (typeof value !== 'string' || !value.trim()) return NaN;
  let text = value.trim();
  if (/^\d{4}-\d{2}-\d{2}$/.test(text)) text += 'T00:00:00+09:00';
  else if (/^\d{4}-\d{2}-\d{2}[ T]\d{2}:\d{2}/.test(text) && !/(Z|[+-]\d{2}:?\d{2})$/i.test(text)) text = text.replace(' ','T') + '+09:00';
  return Date.parse(text);
}
function quantile(sorted,p) {
  const at=(sorted.length-1)*p,lo=Math.floor(at),hi=Math.ceil(at);
  return sorted[lo]+(sorted[hi]-sorted[lo])*(at-lo);
}
export function priceDistribution(values) {
  if (!values.length) return null;
  const sorted=[...values].sort((a,b)=>a-b),q1=quantile(sorted,.25),median=quantile(sorted,.5),q3=quantile(sorted,.75),iqr=q3-q1;
  const lower=q1-1.5*iqr,upper=q3+1.5*iqr,outliers=sorted.filter(n=>n<lower || n>upper);
  return { count:sorted.length,mean:sorted.reduce((sum,n)=>sum+n,0)/sorted.length,min:sorted[0],q1,median,q3,max:sorted.at(-1),
    whiskerLow:sorted.find(n=>n>=lower),whiskerHigh:sorted.findLast(n=>n<=upper),
    outlierCount:outliers.length,outliers:outliers.filter((_,i)=>i%Math.max(1,Math.ceil(outliers.length/30))===0) };
}

export function marketHistory(items,{now=Date.now(),days=14,coverage}={}) {
  const last=koreanDay(now),start=Date.parse(last+'T00:00:00+09:00')-(days-1)*DAY,end=start+days*DAY;
  const buckets=Array.from({length:days},(_,i)=>({date:koreanDay(start+i*DAY),prices:[]}));
  let missingTradeDate=0,invalidPrice=0,outsideWindow=0,nonSales=0;const prices=[];
  for (const item of items) {
    if(item.status!=='SOLD'){nonSales++;continue;}
    const stamp=tradeTimestamp(item.tradeDate),price=Number(item.price);
    if (!Number.isFinite(stamp)) {missingTradeDate++;continue;}
    if (stamp<start || stamp>=end || stamp>now) {outsideWindow++;continue;}
    if (!Number.isFinite(price) || price<=0) {invalidPrice++;continue;}
    buckets[Math.floor((stamp-start)/DAY)].prices.push(price);prices.push(price);
  }
  return { from:koreanDay(start),to:last,timezone:'Asia/Seoul',days:days,
    daily:buckets.map(({date,prices})=>({date,stats:priceDistribution(prices)})),
    overall:priceDistribution(prices),missingTradeDate,invalidPrice,outsideWindow,nonSales,
    sampleCount:prices.length,scannedCount:items.length,coverage,
    basis:'판매 완료 시각(tradeDate) · 개당 가격 · 거래 매물 1건당 1표본',
    note:'오늘을 포함한 최근 14일(KST). 박스는 Q1~Q3, 중앙선은 중앙값, 수염은 1.5×IQR 범위 내 최저·최고가. 거래일을 알 수 없는 매물은 제외합니다.' };
}
