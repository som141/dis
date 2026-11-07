import {openItemDetails,closeDeletedItem} from './item-details.js';
import {enhanceEquipment} from './equipment-ui.js';
import {renderMarketChart,formatPrice} from './market-chart.js';
const $=id=>document.getElementById(id),show=(id,yes)=>{$(id).hidden=!yes;};
const time=value=>value==null?'—':new Date(value).toLocaleString('ko-KR',{timeZone:'Asia/Seoul'});
const el=(tag,text,className)=>{const node=document.createElement(tag);if(text!=null)node.textContent=text;if(className)node.className=className;return node;};
let csrf,state,syncing=false,framing=false,inputQueue=Promise.resolve(),frameTimer,frameGeneration=0,frameFailures=0,reloadPending=false,wheelDelta=0,wheelTimer;
let categories={},draftHydrated=false,draftDirty=false,selectedJob=null,listPage=1,listPages=0,detailKey='',cardsKey='',itemsLoading=false,itemsReloadPending=false;
const phases={queued:'조회 대기',login:'로그인 대기',ready:'조건을 선택해 주세요',equipment:'장비 조회 중',searching:'매물 수집 중',complete:'조회 완료','market-ready':'시세 조회 완료',watching:'자동 감시 중',paused:'중지됨',stopping:'중지 중',expired:'감시 기간 종료','watch-paused':'감시 확인 필요','check-required':'조회 확인 필요','operator-setup':'성능 비교 설정 필요','account-changed':'연결 계정 확인 필요','character-unavailable':'캐릭터를 선택해 주세요','provider-update':'연결 모듈 점검 필요'};
function message(text){$('message').textContent=text;show('message',!!text);}
async function api(path,data){
  const response=await fetch(path,{method:data===undefined?'GET':'POST',headers:{...(csrf?{'x-portal-token':csrf}:{}),...(data!==undefined?{'content-type':'application/json'}:{})},...(data!==undefined?{body:JSON.stringify(data)}:{}),signal:AbortSignal.timeout(40000)});
  const result=await response.json();if(!response.ok){const error=new Error(result.error||'요청에 실패했습니다.');error.code=result.code;throw error;}return result;
}
function field(row,key){return row.querySelector(`[data-field="${key}"]`);}
function chooseOptions(select,entries,value){select.replaceChildren(...entries.map(([key,label])=>{const option=el('option',label);option.value=key;return option;}));select.value=String(value);}
function currentMode(){return document.querySelector('input[name=mode]:checked').value;}
function addRow(config={}){
  const row=el('div',null,'query-row');row.dataset.id=config.id||crypto.randomUUID();
  row.innerHTML='<div class="query-main"><label><span class="field-label">이번 검색에 포함</span><input data-field="enabled" type="checkbox" aria-label="이번 검색에 이 아이템 포함" checked></label><label><span class="field-label">아이템 이름</span><input data-field="keyword" maxlength="80" placeholder="예: 에테르넬 메이지글러브" aria-label="아이템 이름" list="equipmentNames"></label><label><span class="field-label">부위</span><select data-field="category" aria-label="아이템 부위"></select></label><label><span class="field-label">가격 조건</span><select data-field="budgetMode" aria-label="가격 조건"><option value="max">예산 이하</option><option value="range">예산 ±30%</option><option value="none">제한 없음</option></select></label><label><span class="field-label">예산 · 억 메소</span><input data-field="budget" type="number" min="0.0001" step="0.0001" aria-label="기준 예산 억 메소" value="200"></label><label><span class="field-label">감시</span><input data-field="watch" type="checkbox" aria-label="이 아이템 자동 감시"></label><button class="row-remove" type="button" aria-label="이 아이템 삭제">×</button></div><p class="row-price-note"></p><details class="row-filters"><summary>강화 · 잠재 · 월드 조건</summary><div class="filter-grid"><label>최소 스타포스<input data-field="starforceMin" type="number" min="0" max="30" value="0"></label><label>최대 스타포스<input data-field="starforceMax" type="number" min="0" max="30" placeholder="제한 없음"></label><label>잠재 등급<select data-field="potentialGrade"></select></label><label>에디셔널 등급<select data-field="additionalGrade"></select></label></div><div class="filter-checks"><label class="check"><input data-field="exactMatch" type="checkbox" checked>이름 정확히 일치</label><label class="check"><input data-field="myWorldOnly" type="checkbox" checked>내 월드만</label><label class="check"><input data-field="compare" type="checkbox">내 장비 성능 비교</label></div></details>';
  chooseOptions(field(row,'category'),[['ALL','전체 부위'],...Object.entries(categories)],config.category||'ALL');
  const grades=[['0','지정 안 함'],['1','레어'],['2','에픽'],['3','유니크'],['4','레전드리']];
  for(const key of ['potentialGrade','additionalGrade'])chooseOptions(field(row,key),grades,config[key]||0);
  for(const key of ['keyword','budget','budgetMode','starforceMin','starforceMax'])if(config[key]!=null)field(row,key).value=config[key];
  for(const key of ['enabled','watch','exactMatch','myWorldOnly','compare'])if(config[key]!=null)field(row,key).checked=!!config[key];
  if(config.compare==null)field(row,'compare').checked=$('compareAll').checked;
  row.querySelector('.row-remove').onclick=async()=>{try{const id=row.dataset.id+'-live';if(state?.jobs.some(j=>j.id===id&&j.config.watch))await api(`/api/jobs/${id}/stop`,{});row.remove();draftDirty=true;updateBuilder();}catch(e){message(e.message);}};
  row.addEventListener('input',()=>{draftDirty=true;updateBuilder();});row.addEventListener('change',()=>{draftDirty=true;updateBuilder();});
  $('queryRows').append(row);enhanceEquipment(row,config,api,()=>{draftDirty=true;updateBuilder();},async()=>{const id=row.dataset.id+'-live';if(state?.jobs.some(j=>j.id===id)){await api(`/api/jobs/${id}/stop`,{});await sync();}});updateBuilder();return row;
}
function collectRows(){
  return [...$('queryRows').children].map(row=>{
    const value={id:row.dataset.id,mode:currentMode(),intervalMinutes:Number($('intervalMinutes').value),days:Number($('watchDays').value),minGain:0};
    for(const input of row.querySelectorAll('[data-field]')) value[input.dataset.field]=input.type==='checkbox'?input.checked:input.value;
    value.itemId=row.equipmentId||'';value.potentialOptions=row.potentialOptions||[];value.additionalPotentialOptions=row.additionalPotentialOptions||[];value.potentialMaximums=row.potentialMaximums||[];value.additionalPotentialMaximums=row.additionalPotentialMaximums||[];value.potentialNone=value.potentialGrade==='none';value.additionalNone=value.additionalGrade==='none';if(value.potentialNone)value.potentialGrade=0;if(value.additionalNone)value.additionalGrade=0;
    if(value.mode==='sold'){value.watch=false;value.compare=false;}return value;
  });
}
function updateBuilder(){
  const sold=currentMode()==='sold';let selected=0,watches=0;
  for(const row of $('queryRows').children){
    const enabled=field(row,'enabled').checked;if(enabled)selected++;
    field(row,'budget').disabled=field(row,'budgetMode').value==='none';field(row,'watch').disabled=sold;field(row,'compare').disabled=sold;
    if(enabled&&field(row,'watch').checked&&!sold)watches++;
    const budget=Number(field(row,'budget').value),mode=field(row,'budgetMode').value;
    for(const key of ['budgetMin','budgetMax','budgetPercent','budget'])if(field(row,key))field(row,key).parentElement.hidden=key==='budget'?['custom','none'].includes(mode):key==='budgetPercent'?mode!=='range':mode!=='custom';
    row.querySelector('.row-price-note').textContent=mode==='none'?'가격 제한 없이 조회':mode==='custom'?`직접 범위: ${field(row,'budgetMin')?.value||'하한 없음'} ~ ${field(row,'budgetMax')?.value||'상한 없음'}억`:mode==='max'?`검색 가격: ${budget.toLocaleString('ko-KR')}억 이하`:`검색 가격: ${(budget*(1-Number(field(row,'budgetPercent')?.value??30)/100)).toLocaleString('ko-KR')}~${(budget*(1+Number(field(row,'budgetPercent')?.value??30)/100)).toLocaleString('ko-KR')}억`;
  }
  show('watchOptions',!sold&&watches>0);show('marketHelp',sold);$('compareAll').disabled=sold;
  const interval=Number($('intervalMinutes').value)||30;
  $('quotaEstimate').textContent=`선택한 감시 ${watches}개 · 정기 검색 약 ${Math.ceil(1440/interval)*watches}회/일 + 최초·수동 검색. 경매장 잔여 횟수가 부족하면 중지됩니다.`;
  $('selectionSummary').textContent=`${selected}개 항목 선택${!sold?` · 자동 감시 ${watches}개`: ' · 최근 14일 판매 완료 거래'}`;
  $('runSearch').textContent=sold?'선택 항목 2주 시세 조회':watches?'조회 후 감시 시작':'한 번 조회';
}
function hydrate(configs){
  if(draftHydrated||draftDirty)return;draftHydrated=true;
  if(!configs?.length)return;
  const preferredMode=configs[0].mode;const byId=new Map();for(const c of configs)if(!byId.has(c.id)||c.mode===preferredMode)byId.set(c.id,c);configs=[...byId.values()];
  $('queryRows').replaceChildren();for(const config of configs)addRow(config);
  document.querySelector(`input[name=mode][value="${configs[0].mode==='sold'?'sold':'live'}"]`).checked=true;
  $('intervalMinutes').value=configs[0].intervalMinutes||30;$('watchDays').value=configs[0].days??0;
  $('savedHint').textContent='저장된 조건을 불러왔습니다. 선택·예산·감시 여부를 확인하고 실행하세요.';updateBuilder();
}
function priceRange(query){return query?.priceMax==null?'가격 제한 없음':`${formatPrice(query.priceMin)} ~ ${formatPrice(query.priceMax)}`;}
function renderCards(jobs){
  const signature=JSON.stringify(jobs.map(job=>[job.id,job.phase,job.error,job.progress,job.result?.fetchedAt,job.watch?.nextPollAt,job.notices.length,selectedJob]));
  if(signature===cardsKey)return;cardsKey=signature;$('jobCards').replaceChildren();
  for(const job of jobs){
    const card=el('article',null,'job-card'+(selectedJob===job.id?' active':'')),top=el('div',null,'job-top'),name=el('div');
    name.append(el('div',job.config.mode==='sold'?'14 DAYS / SOLD':'LIVE / MARKET','job-mode'),el('div',job.config.keyword||categories[job.config.category]||'전체 부위','job-name'));top.append(name,el('span',phases[job.phase]||job.phase,'job-state'));card.append(top);
    const mode=job.config.budgetMode,range=mode==='none'?'가격 제한 없음':mode==='max'?`${job.config.budget}억 이하`:mode==='custom'?`${job.config.budgetMin||0}~${job.config.budgetMax||'상한 없음'}억`:`${job.config.budget}억 ±${job.config.budgetPercent??30}%`;
    card.append(el('p',`${range} · ${categories[job.config.category]||'전체 부위'}${job.config.watch?` · ${job.config.intervalMinutes}분 감시`:''}`,'job-meta'));
    if(job.progress){const p=el('progress');p.max=job.progress.target||1;p.value=job.progress.loaded;card.append(p,el('p',`${job.progress.loaded.toLocaleString()} / ${job.progress.target.toLocaleString()}건 · ${job.progress.page} / ${job.progress.pages}페이지`,'progress-text'));}
    else if(job.result)card.append(el('p',`${job.result.loadedCount.toLocaleString()}건 조회 · ${time(job.result.fetchedAt)}`,'progress-text'));
    if(job.watch&&job.phase==='watching')card.append(el('p',`다음 확인 ${time(job.watch.nextPollAt)} · 상위 10위 변동 알림 ${job.notices.length}건`,'progress-text'));
    if(job.error)card.append(el('p',job.error,'job-error'));
    const actions=el('div',null,'job-actions'),view=el('button',job.config.mode==='sold'?'시세 보기':'결과 보기');view.type='button';view.disabled=!job.result;view.onclick=()=>selectJob(job.id);actions.append(view);
    if(!job.stopped&&(['queued','equipment','searching','watching','login','operator-setup'].includes(job.phase)||job.config.watch&&!['expired','account-changed'].includes(job.phase))){const stop=el('button','중지');stop.type='button';stop.onclick=()=>jobAction(job.id,'stop');actions.append(stop);}
    else {const retry=el('button','다시 검색 · 1회');retry.type='button';retry.onclick=()=>jobAction(job.id,'retry');actions.append(retry);}
    const remove=el('button','삭제');remove.type='button';remove.onclick=()=>jobAction(job.id,'delete');actions.append(remove);
    card.append(actions);$('jobCards').append(card);
  }
}
async function jobAction(id,action){try{await api(`/api/jobs/${id}/${action}`,{});await sync();}catch(error){message(error.message);}}
function render(s){
  if(!!state?.login!==!!s.login||state?.login?.origin!==s.login?.origin)resetFrame();
  if(state?.character?.id!==s.character?.id){draftDirty=false;draftHydrated=false;selectedJob=null;detailKey='';cardsKey='';$('queryRows').replaceChildren();}
  state=s;
  categories=s.categories||categories;hydrate(s.configs);
  if(!$('queryRows').children.length)addRow({keyword:'',category:'ARMOR_ARMOR_CAP',jobClass:'MAGE',budget:200,budgetMode:'max',watch:false,compare:true});
  const characters=s.characters||[],selector=$('characterSelect'),previous=selector.value;
  const signature=characters.map(c=>c.id+':'+c.name+':'+c.world+':'+c.level).join('|');
  if(selector.dataset.signature!==signature){selector.replaceChildren(...characters.map(c=>new Option(`${c.name} · ${c.world} · Lv.${c.level}`,c.id)));selector.dataset.signature=signature;selector.value=characters.some(c=>String(c.id)===previous)?previous:String(s.character?.id||characters[0]?.id||'');}
  show('characterChooser',characters.length>0);$('chooseCharacter').textContent=s.character?'캐릭터 변경':'이 캐릭터로 연결';$('chooseCharacter').disabled=!characters.length||String(s.character?.id)===selector.value;
  $('character').textContent=s.character?`${s.character.name} · ${s.character.world} · Lv.${s.character.level}`:'연결할 계정을 선택하세요';
  $('connectionBadge').textContent=s.character?'캐릭터 연결됨':'로그인 대기';$('connectionBadge').classList.toggle('connected',!!s.character);
  $('statusTitle').textContent=s.starting?'개인 브라우저 준비 중':s.browserFailed?'브라우저 연결 확인 필요':phases[s.phase]||'연결 확인 중';
  $('statusDetail').textContent=s.browserFailed?'로그인 화면 다시 열기를 눌러 연결 상태를 확인하세요.':s.character?'검색 조건을 고른 뒤 실행하세요. 이미 실행한 감시는 포털 탭을 닫아도 이어집니다.':characters.length?'조회할 캐릭터를 목록에서 선택해 주세요.':'공식 화면에서 로그인과 추가 인증을 완료해 주세요.';
  show('reconnect',!!s.character||s.jobs.some(j=>j.error));
  $('begin').disabled=!!s.character||characters.length>0;$('begin').textContent=s.character?'연결됨':characters.length?'캐릭터 선택 중':'넥슨 계정 연결';
  show('login',!!s.login&&!characters.length);$('officialOrigin').textContent=s.login?.origin||'';$('loginTitle').textContent=s.login?.origin==='https://nid.naver.com'?'네이버 공식 로그인':'넥슨 공식 로그인';
  show('dialog',!!s.login?.dialog);$('dialogMessage').textContent=s.login?.dialog?.message||'';

  show('emptyJobs',!s.jobs.length);show('stopAll',s.jobs.some(job=>!job.stopped&&(job.config.watch||['searching','queued','equipment','operator-setup'].includes(job.phase))));
  if (!s.jobs.some(job => job.id === selectedJob)) selectedJob = s.jobs[0]?.id ?? null;
  renderCards(s.jobs);renderDetail();
}
async function sync(){if(syncing||!csrf||document.hidden)return;syncing=true;try{render(await api('/api/state'));}catch(error){message(error.message);}finally{syncing=false;}}
function selectJob(id){selectedJob=id;listPage=1;detailKey='';cardsKey='';renderCards(state.jobs);renderDetail();$('detailSection').scrollIntoView({behavior:'smooth',block:'start'});}
function renderDetail(){
  const job=state?.jobs.find(job=>job.id===selectedJob),result=job?.result;show('detailSection',!!result);if(!result)return;
  $('detailMode').textContent=job.config.mode==='sold'?'03 / MARKET HISTORY · 14 DAYS':'03 / LIVE LISTINGS';
  $('detailTitle').textContent=job.config.keyword||categories[job.config.category]||'전체 부위';
  const sold=job.config.mode==='sold',loaded=result.collection?.loaded??result.loadedCount;
  $('resultSort').querySelector('[value="default"]').textContent=sold?'판매 완료일 최신 순':job.config.compare?'가성비 높은 순':'기본 정렬 · 가격 낮은 순';
  $('resultSummary').textContent=`${priceRange(result.query)} · 전체 ${result.total.toLocaleString()}건 중 ${loaded.toLocaleString()}건 조회${result.localFilter?.enabled?` · 상한 조건 통과 ${result.localFilter.matched.toLocaleString()}건 · 초과 ${result.localFilter.excluded.toLocaleString()}건 · 옵션 확인 불가 ${result.localFilter.unknown.toLocaleString()}건`:''}${sold?` · 최근 2주 유효 거래 ${result.market?.sampleCount||0}건`:''} · 잔여 검색 ${result.searchRemaining??'확인 필요'}회 · ${time(result.fetchedAt)}`;
  const incomplete=result.collection?.complete===false,capped=result.collection?.capped;
  show('coverageNote',incomplete||capped||!job.hasItems||!!result.finalDamageNote||!!result.localFilter?.enabled||!!result.starforceFilter?.enabled);$('coverageNote').textContent=[incomplete?'페이지 조회가 완료되지 않은 부분 결과입니다. 전체 매물·시세를 대표하지 않습니다.':'',capped?'경매장 조회 상한인 최대 10,000건만 포함됩니다. 일부 가격대의 표본일 수 있어 전체 시세를 대표하지 않습니다.':'',!job.hasItems?'저장된 집계를 표시하고 있습니다. 개별 거래 목록과 CSV는 다시 조회하면 사용할 수 있습니다.':'',result.localFilter?.enabled?'옵션 상한은 조회한 매물에만 적용했습니다. 상세 잠재 옵션을 확인할 수 없는 매물은 결과에서 제외했습니다.':'',result.starforceFilter?.enabled?`최대 ${result.starforceFilter.max}성은 조회 후 적용했습니다. 상한 초과 또는 스타포스 확인 불가 ${result.starforceFilter.excluded}건을 제외했습니다.`:'',result.finalDamageNote||''].filter(Boolean).join(' ');
  show('downloadCsv',job.hasItems);$('downloadCsv').href=`/api/jobs/${job.id}/csv`;$('downloadCsv').textContent=sold?'거래 데이터 CSV':'매물 데이터 CSV';
  for(const value of ['gain-desc','power-desc'])$('resultSort').querySelector(`[value="${value}"]`).disabled=sold;
  if(sold&&['gain-desc','power-desc'].includes($('resultSort').value))$('resultSort').value='default';
  show('marketSection',sold&&!!result.market);$('lastColumnTitle').textContent=sold?'판매 완료일':'교체 성능';$('tableTitle').textContent=sold?'최근 2주 거래 내역':'조회 매물';
  const key=job.id+'|'+result.fetchedAt+'|'+job.hasItems;
  if(key!==detailKey){detailKey=key;listPage=1;if(result.market)renderMarket(result.market);void loadItems();}
  show('watchSection',!!job.watch||job.notices.length>0);
  if(job.watch)$('watchSummary').textContent=`${job.watch.status} · ${job.config.intervalMinutes}분 간격 · 다음 ${time(job.watch.nextPollAt)} · 종료 ${job.watch.expiresAt==null?'직접 종료할 때까지':time(job.watch.expiresAt)}`;
  const noticesKey=job.id+'|'+JSON.stringify(job.notices.map(notice=>[notice.item.id,notice.foundAt]));
  if($('notices').dataset.key!==noticesKey){$('notices').dataset.key=noticesKey;$('notices').replaceChildren(...job.notices.map(notice=>el('div',`상위 10위 ${notice.previousRank?`${notice.previousRank}→${notice.rank}위`:`신규 ${notice.rank}위`} · ${notice.item.name} · ${formatPrice(notice.item.price)} · ${time(notice.foundAt)}`,'notice')));if(!job.notices.length)$('notices').textContent='상위 10위 순위 변동·신규 진입이 없습니다. 최초 결과는 알림 기준선으로 사용합니다.';}
}
function renderMarket(report){
  $('marketMetrics').replaceChildren();
  const metrics=[['2주 중앙값',formatPrice(report.overall?.median),'개당 가격'],['중간 50% 가격대',report.overall?`${formatPrice(report.overall.q1)} ~ ${formatPrice(report.overall.q3)}`:'—','Q1 ~ Q3'],['유효 거래',report.sampleCount.toLocaleString()+'건','거래 매물 1건당 1표본'],['분석 기간',report.from.slice(5)+' ~ '+report.to.slice(5),'KST · 오늘 포함 14일']];
  for(const [label,value,note] of metrics){const card=el('div',null,'metric');card.append(el('small',label),el('strong',value),el('span',note));$('marketMetrics').append(card);}
  show('chartEmpty',!report.sampleCount);show('marketChart',!!report.sampleCount);
  $('marketNote').textContent=`${report.basis}. ${report.note} 거래일 없음 ${report.missingTradeDate}건 · 유효하지 않은 가격 ${report.invalidPrice}건 · 기간 밖 ${report.outsideWindow}건 · 판매 완료 상태 아님 ${report.nonSales||0}건 제외.`;
  $('dailyTable').replaceChildren(...report.daily.map(day=>{const row=el('tr'),values=[day.date,day.stats?.count||0,...['min','q1','median','q3','max'].map(key=>formatPrice(day.stats?.[key]))];for(const value of values)row.append(el('td',value));return row;}));
  drawChart();
}
function drawChart(){const report=state?.jobs.find(job=>job.id===selectedJob)?.result?.market;if(!report)return;renderMarketChart($('marketChart'),report,{boxes:$('showBoxes').checked,median:$('showMedian').checked,log:$('logScale').checked,onDay:(_,text)=>{$('dayDetails').textContent=text;}});}
async function loadItems(){
  if(itemsLoading){itemsReloadPending=true;return;}if(!selectedJob)return;itemsLoading=true;const id=selectedJob,requestedPage=listPage,sort=$('resultSort').value,size=$('pageSize').value;
  try{const data=await api(`/api/jobs/${id}/items?page=${requestedPage}&size=${size}&sort=${sort}`);if(id!==selectedJob||requestedPage!==listPage||sort!==$('resultSort').value||size!==$('pageSize').value){itemsReloadPending=true;return;}
    listPages=data.pages;$('listings').replaceChildren();const sold=state.jobs.find(job=>job.id===id)?.config.mode==='sold';
    for(const item of data.items){const row=el('tr'),main=el('td'),layout=el('div',null,'listing-compact');if(item.icon){const icon=el('img');icon.src=item.icon;icon.alt='';icon.loading='lazy';icon.width=42;icon.height=42;layout.append(icon);}const text=el('div');text.append(el('div',item.name||'매물','listing-name'));const badges=el('div',null,'listing-badges');for(const [label,grade] of [['잠재',item.potentialGrade],['에디',item.additionalGrade]])if(grade!=null)badges.append(el('span',`${label} ${['없음','레어','에픽','유니크','레전드리'][grade]||'미제공'}`,'grade-badge grade-'+grade));text.append(badges);if(item.potentialBrief)text.append(el('div','잠재 '+item.potentialBrief,'listing-options'));if(item.additionalBrief)text.append(el('div','에디 '+item.additionalBrief,'listing-options'));layout.append(text);main.append(layout);
      const price=el('td',formatPrice(item.price),'price');price.title=Number(item.price).toLocaleString('ko-KR')+' 메소';row.append(main,price,el('td',item.starforce!=null?`${item.starforce}성`:'—'));const last=el('td');if(sold)last.append(el('small',time(item.tradeDate)));else{if(item.comparison)last.append(el('div',`${item.comparison.gainPercent>0?'+':''}${item.comparison.gainPercent.toFixed(2)}%`,item.comparison.gainPercent>=0?'gain-positive':'gain-negative'));else{const reasons=Object.values(item.comparisonErrors||{});const label=el('small',reasons.length?'비교 미지원 · '+reasons.join(' · '):'비교 미선택');last.append(label);}if(Number.isFinite(item.powerDiff))last.append(el('small',`경매장 전투력 참고치 ${item.powerDiff>=0?'+':''}${item.powerDiff.toLocaleString('ko-KR')}`));}
      const detail=el('button','자세히 보기','quiet');detail.type='button';detail.onclick=()=>openItemDetails(id,item.id,api);last.append(detail);row.append(last);$('listings').append(row);
    }
    if(!data.items.length){const row=el('tr'),cell=el('td',data.available?'조건에 맞는 거래·매물이 없습니다.':'개별 거래 내역을 다시 조회해 주세요.');cell.colSpan=4;row.append(cell);$('listings').append(row);}
    $('pageInfo').textContent=`${data.pages?requestedPage:0} / ${data.pages} 페이지 · ${data.total.toLocaleString()}건`;$('prevPage').disabled=requestedPage<=1;$('nextPage').disabled=requestedPage>=data.pages;
  }catch(error){message(error.message);}finally{itemsLoading=false;if(itemsReloadPending||id!==selectedJob||requestedPage!==listPage){itemsReloadPending=false;void loadItems();}}
}
function resetFrame(){++frameGeneration;frameFailures=0;$('screen').hidden=true;$('screenPlaceholder').hidden=false;$('screenPlaceholder').textContent='공식 로그인 화면에 연결하고 있습니다…';$('loginLoading').textContent='개인 로그인 화면을 준비하고 있습니다…';}
async function frame(){
  if(framing)return;if(!state?.login||state.characters?.length||!csrf||document.hidden||state.login.dialog||reloadPending){scheduleFrame(800);return;}framing=true;const generation=frameGeneration;
  try{const response=await fetch('/api/login/frame',{headers:{'x-portal-token':csrf},signal:AbortSignal.timeout(4500)});if(!response.ok)throw new Error();const bitmap=await createImageBitmap(await response.blob());try{if(generation!==frameGeneration||!state?.login)return;$('screen').getContext('2d').drawImage(bitmap,0,0,1280,900);$('screen').hidden=false;$('screenPlaceholder').hidden=true;$('loginLoading').textContent='로그인 화면 연결됨';frameFailures=0;}finally{bitmap.close();}}
  catch{if(generation===frameGeneration&&state?.login){++frameFailures;$('loginLoading').textContent='로그인 화면 연결이 지연되고 있습니다. 다시 연결하고 있습니다…';if(frameFailures>=3||$('screen').hidden){$('screen').hidden=true;$('screenPlaceholder').hidden=false;$('screenPlaceholder').textContent='화면을 불러오지 못했습니다. 계속되면 “로그인 화면 다시 열기”를 눌러 주세요.';}}}
  finally{framing=false;scheduleFrame(frameFailures?Math.min(2000,frameFailures*400):150);}
}
function scheduleFrame(delay=150){clearTimeout(frameTimer);frameTimer=setTimeout(frame,delay);}
function send(input){inputQueue=inputQueue.then(async()=>{await api('/api/login/input',input);scheduleFrame(0);}).catch(error=>message(error.message));}
$('screen').addEventListener('click',event=>{const r=$('screen').getBoundingClientRect();send({type:'click',x:(event.clientX-r.left)*1280/r.width,y:(event.clientY-r.top)*900/r.height});$('keyboard').focus({preventScroll:true});});
$('screen').addEventListener('wheel',event=>{event.preventDefault();wheelDelta=Math.max(-1500,Math.min(1500,wheelDelta+event.deltaY));if(!wheelTimer)wheelTimer=setTimeout(()=>{const dy=wheelDelta;wheelDelta=0;wheelTimer=null;if(dy)send({type:'wheel',dy});},40);},{passive:false});
$('keyboard').addEventListener('keydown',event=>{if(event.isComposing)return;if(event.ctrlKey&&event.key.toLowerCase()==='a'){event.preventDefault();send({type:'key',key:'Control+a'});return;}if(['Tab','Enter','Backspace','Delete','ArrowLeft','ArrowRight','ArrowUp','ArrowDown','Home','End','Escape'].includes(event.key)){event.preventDefault();send({type:'key',key:event.shiftKey&&event.key==='Tab'?'Shift+Tab':event.key});}});
function flush(){const value=$('keyboard').value;$('keyboard').value='';if(value)send({type:'text',text:value});}
$('keyboard').addEventListener('input',event=>{if(!event.isComposing)flush();});$('keyboard').addEventListener('compositionend',flush);$('keyboard').addEventListener('paste',event=>{event.preventDefault();const text=event.clipboardData.getData('text');if(text)send({type:'text',text:text.slice(0,1024)});});
$('dialogAccept').onclick=()=>send({type:'dialog',accept:true});$('dialogDismiss').onclick=()=>send({type:'dialog',accept:false});
$('reloadLogin').onclick=async()=>{if(reloadPending)return;reloadPending=true;$('reloadLogin').disabled=true;resetFrame();try{await inputQueue;await api('/api/login/reload',{});await sync();}catch(error){message(error.message);}finally{reloadPending=false;$('reloadLogin').disabled=false;scheduleFrame(0);}};
async function connect(){await api('/api/session',{});csrf=(await api('/api/bootstrap')).csrf;await sync();}
$('reconnect').onclick=async()=>{try{await api('/api/reconnect',{});await sync();}catch(e){message(e.message);}};
$('begin').onclick=async()=>{$('begin').disabled=true;try{await connect();$('login').scrollIntoView({behavior:'smooth'});}catch(error){message(error.message);$('begin').disabled=false;}};
$('characterSelect').onchange=()=>{$('chooseCharacter').disabled=String(state?.character?.id)===$('characterSelect').value;};
$('chooseCharacter').onclick=async()=>{const button=$('chooseCharacter');button.disabled=true;try{await api('/api/character/select',{characterId:Number($('characterSelect').value)});await sync();}catch(error){message(error.message);}finally{button.disabled=false;}};
$('addItem').onclick=()=>{draftDirty=true;const row=addRow();field(row,'keyword').focus();};
for(const input of document.querySelectorAll('input[name=mode]'))input.onchange=()=>{draftDirty=true;updateBuilder();};
$('compareAll').onchange=()=>{draftDirty=true;for(const row of $('queryRows').children)field(row,'compare').checked=$('compareAll').checked;};
$('intervalMinutes').oninput=updateBuilder;$('watchDays').oninput=updateBuilder;
$('searchForm').onsubmit=async event=>{event.preventDefault();message('');$('runSearch').disabled=true;try{const jobs=collectRows();if(!jobs.some(job=>job.enabled))throw new Error('검색할 항목을 선택해 주세요.');if(!csrf)await connect();await api('/api/jobs',{jobs});draftDirty=false;draftHydrated=true;const first=jobs.find(job=>job.enabled);selectedJob=first.id+'-'+first.mode;detailKey='';await sync();$('jobsSection').scrollIntoView({behavior:'smooth'});}catch(error){message(error.message);}finally{$('runSearch').disabled=false;}};
$('clearFinished').onclick=async()=>{try{await api('/api/jobs/clear-finished',{});await sync();}catch(e){message(e.message);}};
$('stopAll').onclick=async()=>{try{await api('/api/stop',{});await sync();}catch(error){message(error.message);}};
$('prevPage').onclick=()=>{if(listPage>1){listPage--;void loadItems();}};$('nextPage').onclick=()=>{if(listPage<listPages){listPage++;void loadItems();}};
$('resultSort').onchange=$('pageSize').onchange=()=>{listPage=1;void loadItems();};
for(const id of ['showBoxes','showMedian','logScale'])$(id).onchange=drawChart;
try{categories=(await api('/api/catalog')).categories;}catch{categories={ARMOR_ARMOR_GLOVE:'장갑',WEAPON:'무기',ARMOR_ARMOR_CAP:'모자',ARMOR_ARMOR_COAT:'상의',ARMOR_ARMOR_PANTS:'하의',ARMOR_ACCESSORY_RING:'반지',ARMOR_ACCESSORY_PENDANT:'펜던트'};}
addRow({keyword:'',category:'ARMOR_ARMOR_CAP',jobClass:'MAGE',budget:200,budgetMode:'max',watch:false,compare:true});
try{const response=await fetch('/api/bootstrap');if(response.ok){csrf=(await response.json()).csrf;await sync();}}catch{message('로컬 프로그램 연결을 확인해 주세요.');}
document.addEventListener('visibilitychange',()=>{if(!document.hidden){void sync();scheduleFrame(0);}});setInterval(()=>{if(!document.hidden)void sync();},1500);scheduleFrame(0);
