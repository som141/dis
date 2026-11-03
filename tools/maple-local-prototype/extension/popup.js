const $ = id => document.getElementById(id);
$('connect').onclick = async () => {
  const result = await chrome.runtime.sendMessage({ type: 'pair', pairing: $('pairing').value.trim(), port: Number($('port').value) });
  $('pairing').value = '';
  $('status').textContent = result?.ok ? '연결 요청 완료. 로컬 웹에서 연결 확인을 누르세요.' : '연결코드·포트를 확인하세요.';
};
$('login').onclick = () => chrome.tabs.create({ url: 'https://auction.maplestory.nexon.com/' });
$('disconnect').onclick = async () => { await chrome.runtime.sendMessage({ type: 'disconnect' }); $('status').textContent = '연결 해제됨'; };
chrome.runtime.sendMessage({ type: 'status' }).then(r => { $('status').textContent = r?.connected ? '로컬 서버 연결됨' : '로컬 서버 연결 대기'; });
