const $ = id => document.getElementById(id);
const esc = s => String(s ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const money = n => `${(Number(n) / 100000000).toLocaleString('ko-KR', { maximumFractionDigits:4 })}억`;
const date = n => n ? new Date(n).toLocaleString('ko-KR', { timeZone:'Asia/Seoul' }) : '-';
let bootstrap, current, pending = false, characterSignature = '', resultSignature = '', equipmentSignature = '';
async function request(path, body) {
  const res = await fetch(path, body === undefined ? {} : { method:'POST', headers:{ 'Content-Type':'application/json', 'X-Local-Token':bootstrap.csrf }, body:JSON.stringify(body), signal:AbortSignal.timeout(180000) });
  const data = await res.json();
  if (!res.ok || data.ok === false) throw new Error(data.error || `요청 실패 (${res.status})`);
  return data;
}
async function act(path, body, label) {
  if (pending) return;
  pending = true; $('error').hidden = true; $('progress').hidden = false; $('progress').textContent = label;
  document.querySelectorAll('button').forEach(b => b.disabled = true);
  try { const result = await request(path, body); await refresh(); return result; }
  catch (e) { $('error').textContent = e.message; $('error').hidden = false; }
  finally { pending = false; $('progress').hidden = true; document.querySelectorAll('button').forEach(b => b.disabled = false); }
}
function render(s) {
  current = s;
  const docker = bootstrap.mode === 'server-browser';
  $('connectionBadge').textContent = docker ? (s.connected ? '공식 탭 열림 · 인증 확인 필요' : '공식 탭 확인 필요') : (s.connected ? 'Chrome 연결됨' : '브라우저 연결 대기');
  $('keyInfo').textContent = s.keyConfigured ? '키 적용됨 · 서버 종료 시 삭제' : '키 미설정 · 매물 검색만 가능';
  $('characterInfo').textContent = s.character ? `${s.character.name} · ${s.character.world} · Lv.${s.character.level}` : '본인 캐릭터 선택 대기';
  $('connectionInfo').textContent = docker
    ? (s.browser?.navigation === 'site-unavailable' ? '공식 사이트에 연결하지 못했습니다. 원격 브라우저에서 네트워크 오류를 확인하세요.' : '탭 연결과 로그인 성공은 별개입니다. 연결 확인으로 본인 캐릭터가 조회되는지 확인하세요.')
    : (s.connected ? '확장이 연결됐습니다. 연결 확인으로 로그인 계정의 캐릭터를 불러오세요.' : '확장 연결코드와 공식 경매장 로그인 상태를 확인하세요.');
  const signature = JSON.stringify(s.characters);
  if (signature !== characterSignature) {
    characterSignature = signature;
    $('characters').innerHTML = '<option value="">캐릭터 선택</option>' + s.characters.map(c => `<option value="${esc(c.characterId)}">${esc(c.name)} · ${esc(c.world)} · Lv.${c.level}</option>`).join('');
    const preferred = s.characters.find(c => c.name === s.defaultCharacter);
    if (preferred) $('characters').value = preferred.characterId;
  }
  const equipSig = JSON.stringify(s.profile);
  if (equipSig !== equipmentSignature) {
    equipmentSignature = equipSig;
    $('equipmentList').innerHTML = s.profile ? `<p class="muted">${esc(s.profile.class)} · Lv.${esc(s.profile.level)} · 로컬 확인 ${esc(date(s.profileLoadedAt))}</p>` + s.profile.items.map(i => `<div class="equip"><strong>${esc(i.slot)}</strong> · ${esc(i.name)} · ${esc(i.star || 0)}성<br>${esc(i.pot)} / 에디 ${esc(i.add)}</div>`).join('') : '';
  }
  const resultSig = JSON.stringify(s.result);
  if (resultSig !== resultSignature) {
    resultSignature = resultSig;
    const r = s.result;
    $('resultCount').textContent = r ? `전체 ${r.total}건 · ${r.page}/${r.totalPages || 1}페이지` : '아직 검색하지 않았습니다.';
    $('resultInfo').textContent = r ? `${r.character} 기준 · ${money(r.query.priceMin)}~${money(r.query.priceMax)} · 검색 잔여 ${r.searchRemaining ?? '확인 필요'}회 · ${date(r.fetchedAt)} KST` : '';
    $('modelNote').hidden = !r?.finalDamageNote;
    $('modelNote').textContent = r?.finalDamageNote?.includes('넥슨 오픈 API 키') ? '계산용 키가 없어 교체 점수를 제공하지 않습니다. 로컬 API 키 설정 후 다시 검색하세요.' : (r?.finalDamageNote || '');
    $('results').innerHTML = r ? (r.items.length ? r.items.map(itemCard).join('') : '<div class="empty">조건에 맞는 매물이 없습니다. 검색 횟수는 사용될 수 있습니다.</div>') : '<div class="empty">Chrome 연결 후 본인 캐릭터로 첫 검색을 해 보세요.</div>';
  }
  const w = s.watch;
  $('watchState').textContent = w ? `${w.status} · 30분 간격 · 마지막 확인 ${date(w.lastCheckedAt)} · 다음 확인 ${date(w.nextPollAt)} · 종료 ${date(w.expiresAt)}${w.error ? ` · ${w.error}` : ''}` : '감시 없음 · Discord DM은 연결하지 않았습니다.';
  $('notices').innerHTML = s.notices.map(n => `<div class="alertitem"><strong>새로 확인한 매물 · ${esc(n.item.name)}</strong><br>${money(n.item.price)} · ${esc(date(n.foundAt))} KST${n.item.comparison ? ` · 모델 개선 ${n.item.comparison.gainPercent}%` : ''}</div>`).join('');
}
function itemCard(i) {
  const c = i.comparison;
  return `<article class="listing"><h3>${esc(i.name)}</h3><div class="price">${money(i.price)}</div><p class="muted">${esc(i.starforce)}성 · 수량 ${esc(i.quantity)} · ${esc(i.status === 'SOLD' ? '판매 완료' : '판매 중')}</p>${c ? `<p class="gain ${c.gainPercent < 0 ? 'negative' : ''}">${c.gainPercent >= 0 ? '+' : ''}${c.gainPercent.toFixed(2)}% · ${c.score.toFixed(2)}점</p><p class="muted">${esc(c.slot)} 교체 기준 · 1억당 개선 ${c.efficiency?.toFixed(3) ?? '-'}점</p>` : '<p class="notice">교체 점수 없음 · 키/직업/부위/착용 가능 여부를 확인하세요.</p>'}<p class="details">${esc(i.stat)}<br>잠재: ${esc(i.potential || '없음')}<br>에디: ${esc(i.additional || '없음')}<br>추옵: ${esc(i.exOption || '-')}<br>${esc(i.scroll)}<br>${esc(i.tradeDesc)}</p><p class="muted">매물 ID ${esc(i.id)}<br>${i.tradeDate ? '판매 완료' : '등록 종료'} ${esc(date(i.tradeDate || i.endDate))} KST</p></article>`;
}
async function refresh() { render(await request('/api/state')); }
function updateRange() {
  const b = Number($('budget').value);
  $('budgetRange').textContent = `검색 범위: ${(b * .7).toLocaleString('ko-KR', { maximumFractionDigits:4 })}억 ~ ${(b * ($('hardCap').checked ? 1 : 1.3)).toLocaleString('ko-KR', { maximumFractionDigits:4 })}억`;
}
$('copyPair').onclick = async () => { try { await navigator.clipboard.writeText(bootstrap.pairing); $('copyPair').textContent = '복사됨'; } catch { $('pairCode').type = 'text'; $('pairCode').select(); } };
$('showPair').onclick = () => { $('pairCode').type = $('pairCode').type === 'password' ? 'text' : 'password'; $('pairCode').select(); };
$('connect').onclick = () => act('/api/connect', {}, 'Chrome 로그인 계정의 캐릭터를 확인하고 있습니다…');
$('selectCharacter').onclick = () => act('/api/character', { characterId:Number($('characters').value) }, '검색 기준 캐릭터를 선택하고 있습니다…');
$('saveKey').onclick = () => { const key = $('apiKey').value.trim(); $('apiKey').value = ''; return act('/api/key', { key }, '계산용 키를 메모리에 적용하고 있습니다…'); };
$('clearKey').onclick = () => act('/api/key', { key:'' }, '키를 삭제하고 있습니다…');
$('equipment').onclick = () => act('/api/equipment', {}, '캐릭터 장비·스탯 조회 중입니다. 첫 조회는 약 30~60초 걸릴 수 있습니다…');
$('refreshEquipment').onclick = () => act('/api/equipment', { refresh:true }, '넥슨 캐릭터 데이터를 새로 갱신하고 있습니다…');
$('budget').oninput = updateRange; $('hardCap').onchange = updateRange;
$('searchForm').onsubmit = e => {
  e.preventDefault();
  return act('/api/search', { keyword:$('keyword').value, category:$('category').value, budget:Number($('budget').value),
    starforceMin:Number($('starforce').value), potentialGrade:Number($('grade').value), option:$('option').value,
    optionMin:Number($('optionMin').value), hardCap:$('hardCap').checked, exactMatch:$('exact').checked,
    myWorldOnly:$('myWorld').checked, sold:$('sold').checked }, '새 매물을 검색하고 교체 성능을 계산하고 있습니다…');
};
$('firstPage').onclick = () => act('/api/page', { page:1, limit:20, sort:$('sort').value }, '검색 결과 페이지를 확인하고 있습니다…');
$('nextPage').onclick = () => { if (!current?.result?.hasNext) { $('error').hidden = false; $('error').textContent = '다음 페이지가 없습니다.'; return; } return act('/api/page', { page:current.result.page + 1, limit:20, sort:$('sort').value }, '다음 페이지를 확인하고 있습니다…'); };
$('startWatch').onclick = () => act('/api/watch/start', { days:Number($('days').value), minGain:Number($('minGain').value) }, '기존 매물을 기준선으로 저장하고 있습니다…');
$('stopWatch').onclick = () => act('/api/watch/stop', {}, '감시를 종료하고 있습니다…');
try {
  bootstrap = await request('/api/bootstrap');
  $('pairCode').value = bootstrap.pairing || '';
  if (bootstrap.mode === 'server-browser') {
    $('extensionInstructions').hidden = true; $('dockerInstructions').hidden = false;
    $('connectTitle').textContent = '서버 브라우저 로그인';
    $('watchInstructions').textContent = '30분마다 서버에서 새 매물을 확인합니다. Windows의 원격 화면을 닫아도 서버·도커·브라우저가 계속 실행 중이면 감시합니다. 세션 만료 시 재로그인이 필요하며, 컨테이너 재시작 시 감시 설정은 사라집니다.';
    $('runtimeFooter').textContent = '개인 도커 프로토타입 · 로그인 정보는 서버 브라우저 프로필에 유지 · 검색·계산은 서버에서 처리';
  }
  $('category').innerHTML = Object.entries(bootstrap.categories).map(([value,label]) => `<option value="${esc(value)}">${esc(label)}</option>`).join('');
  $('category').value = 'ARMOR_ARMOR_GLOVE';
  await refresh();
  setInterval(() => { if (!pending) void refresh().catch(() => { $('connectionBadge').textContent = '로컬 서버 연결 끊김'; }); }, 5000);
} catch { $('error').hidden = false; $('error').textContent = '로컬 서버를 시작하고 이 창을 새로고침하세요.'; }
