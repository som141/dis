const NS='http://www.w3.org/2000/svg';
export function formatPrice(value) {
  const n=Number(value);if(value==null||!Number.isFinite(n)) return '—';
  if(n>=1e8) return (n/1e8).toLocaleString('ko-KR',{maximumFractionDigits:2})+'억';
  if(n>=1e4) return (n/1e4).toLocaleString('ko-KR',{maximumFractionDigits:1})+'만';
  return n.toLocaleString('ko-KR',{maximumFractionDigits:0})+' 메소';
}
function svg(tag,attrs={},text) {
  const node=document.createElementNS(NS,tag);
  for(const [key,value] of Object.entries(attrs)) node.setAttribute(key,String(value));
  if(text!=null) node.textContent=text;return node;
}
export function renderMarketChart(root,report,{boxes=true,median=true,log=false,onDay=()=>{}}={}) {
  root.replaceChildren();
  if(!report?.daily?.some(day=>day.stats)) return;
  const width=1000,height=350,left=76,right=28,top=27,bottom=55,plotWidth=width-left-right,plotHeight=height-top-bottom;
  const values=report.daily.filter(day=>day.stats).flatMap(day=>[day.stats.min,day.stats.max]);
  let low=Math.min(...values),high=Math.max(...values);if(low===high){low=Math.max(1,low*.9);high=high*1.1+1;}
  const scale=log?value=>Math.log10(Math.max(1,value)):value=>value;
  let min=scale(low),max=scale(high),pad=(max-min)*.09;min-=pad;max+=pad;
  if(!log) min=Math.max(0,min);
  const y=value=>top+(max-scale(value))/(max-min)*plotHeight;
  const x=index=>left+(index+.5)*plotWidth/report.daily.length;
  const canvas=svg('svg',{viewBox:`0 0 ${width} ${height}`,role:'group','aria-label':`${report.from}부터 ${report.to}까지 날짜별 거래 가격 박스플롯과 중앙값 추이`});
  canvas.append(svg('title',{},`최근 2주 거래 가격: 날짜별 중앙값과 가격 분포`));
  for(let i=0;i<=5;i++) {
    const value=min+(max-min)*i/5,actual=log?10**value:value,yy=top+plotHeight-plotHeight*i/5;
    canvas.append(svg('line',{x1:left,y1:yy,x2:width-right,y2:yy,stroke:'#dfd2b9','stroke-dasharray':'3 5'}));
    canvas.append(svg('text',{x:left-11,y:yy+4,fill:'#817057','text-anchor':'end','font-size':11},formatPrice(actual)));
  }
  let medianPath='',previous=false;
  report.daily.forEach((day,index)=>{
    if(!day.stats){previous=false;return;}
    medianPath+=`${previous?' L':' M'}${x(index)} ${y(day.stats.median)}`;previous=true;
  });
  if(median) canvas.append(svg('path',{d:medianPath,fill:'none',stroke:'#b76c2c','stroke-width':2,opacity:.8}));
  report.daily.forEach((day,index)=>{
    const xx=x(index),s=day.stats,barWidth=Math.min(29,plotWidth/report.daily.length*.44);
    const description=s?`${day.date} · ${s.count}건 · 중앙값 ${formatPrice(s.median)} · Q1 ${formatPrice(s.q1)} · Q3 ${formatPrice(s.q3)} · 최저 ${formatPrice(s.min)} · 최고 ${formatPrice(s.max)} · 이상치 ${s.outlierCount}건`:`${day.date} · 거래 기록 없음`;
    const group=svg('g',{tabindex:0,role:'button','aria-label':description});
    group.append(svg('title',{},description));
    group.append(svg('rect',{x:xx-plotWidth/report.daily.length/2,y:top,width:plotWidth/report.daily.length,height:plotHeight+38,fill:'transparent'}));
    if(s&&boxes) {
      group.append(svg('line',{x1:xx,y1:y(s.whiskerHigh),x2:xx,y2:y(s.whiskerLow),stroke:'#85734f','stroke-width':1.3}));
      for(const value of [s.whiskerHigh,s.whiskerLow]) group.append(svg('line',{x1:xx-barWidth*.3,y1:y(value),x2:xx+barWidth*.3,y2:y(value),stroke:'#85734f','stroke-width':1.3}));
      group.append(svg('rect',{x:xx-barWidth/2,y:y(s.q3),width:barWidth,height:Math.max(2,y(s.q1)-y(s.q3)),fill:'#dfbd7466',stroke:'#a0844b',rx:2}));
      group.append(svg('line',{x1:xx-barWidth/2,y1:y(s.median),x2:xx+barWidth/2,y2:y(s.median),stroke:'#b76c2c','stroke-width':2.5}));
      for(const value of s.outliers) group.append(svg('circle',{cx:xx,cy:y(value),r:2,fill:'#9d8260',opacity:.6}));
    }
    if(s&&median) group.append(svg('circle',{cx:xx,cy:y(s.median),r:3.5,fill:'#b76c2c',stroke:'#fff8e9','stroke-width':1.5}));
    group.append(svg('text',{x:xx,y:height-29,fill:s?'#624d35':'#a28e72','text-anchor':'middle','font-size':11},day.date.slice(5).replace('-','/')));
    group.append(svg('text',{x:xx,y:height-12,fill:'#8d775d','text-anchor':'middle','font-size':10},s?`${s.count}건`:'거래 없음'));
    const select=()=>onDay(day,description);group.addEventListener('pointerenter',select);group.addEventListener('focus',select);group.addEventListener('click',select);
    group.addEventListener('keydown',event=>{
      if(event.key==='Enter'||event.key===' '){event.preventDefault();select();}
      if(event.key==='ArrowRight'||event.key==='ArrowLeft'){event.preventDefault();const groups=canvas.querySelectorAll('[role=button]');groups[(index+(event.key==='ArrowRight'?1:-1)+groups.length)%groups.length].focus();}
    });
    canvas.append(group);
  });
  root.append(canvas);
}
