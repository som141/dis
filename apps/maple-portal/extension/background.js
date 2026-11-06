let socket;
const getPaths = [/^\/v1\/accounts$/, /^\/v1\/accounts\/\d+\/gameWorlds\/\d+\/(characters|balance)$/, /^\/v1\/market\/web\/daily-limit$/, /^\/v1\/market\/web\/items\/searches\/(sold\/)?[^/]+\/tool-tip$/];
function allowed(cmd) {
  try {
    const u = new URL(cmd.url);
    if (u.origin !== 'https://api.mskr.nexon.com' || u.username || u.password) return false;
    return cmd.method === 'GET' ? getPaths.some(p => p.test(u.pathname)) : cmd.method === 'POST' && /^\/v1\/market\/web\/items\/searches\/(sold\/)?tool-tip$/.test(u.pathname);
  } catch { return false; }
}
function send(ws, data) { if (ws.readyState === WebSocket.OPEN) ws.send(JSON.stringify(data)); }
async function execute(cmd, ws) {
  if (cmd.type !== 'fetch' || !allowed(cmd)) { send(ws, { id: cmd.id, ok: false, code: 'FORBIDDEN_URL', error: '검색·조회 외 요청은 허용하지 않습니다.' }); return; }
  try {
    const headers = {};
    for (const [k, v] of Object.entries(cmd.headers || {})) {
      if (['accept', 'content-type', 'x-platform', 'x-device-id', 'x-client-version'].includes(k.toLowerCase())) headers[k] = v;
    }
    const res = await fetch(cmd.url, { method: cmd.method, headers, credentials: 'include', body: cmd.body,
      redirect: 'error', signal: AbortSignal.timeout(18000) });
    const bodyText = await res.text();
    if (bodyText.length > 10 * 1024 * 1024) throw new Error('응답 크기 초과');
    send(ws, res.ok ? { id: cmd.id, ok: true, status: res.status, bodyText } : { id: cmd.id, ok: false, status: res.status, code: 'HTTP_ERROR', error: `HTTP ${res.status}`, bodyText });
  } catch { send(ws, { id: cmd.id, ok: false, code: 'NETWORK', error: '브라우저 요청에 실패했습니다. 공식 경매장 탭을 확인하세요.' }); }
}
async function connect() {
  if (socket && [WebSocket.OPEN, WebSocket.CONNECTING].includes(socket.readyState)) return;
  const { pairing, port = 8877 } = await chrome.storage.session.get(['pairing', 'port']);
  if (!/^[a-f0-9]{64}$/.test(pairing || '')) return;
  const ws = new WebSocket(`ws://127.0.0.1:${port}/bridge?token=${pairing}`);
  let heartbeat;
  socket = ws;
  ws.onopen = () => { chrome.action.setBadgeText({ text: 'ON' }); heartbeat = setInterval(() => send(ws, { type: 'ping' }), 20000); };
  ws.onmessage = ev => { try { void execute(JSON.parse(ev.data), ws); } catch {} };
  ws.onclose = () => { clearInterval(heartbeat); if (socket === ws) socket = null; chrome.action.setBadgeText({ text: '' }); };
  ws.onerror = () => ws.close();
}
chrome.runtime.onMessage.addListener((msg, sender, respond) => {
  if (sender.id !== chrome.runtime.id) return;
  if (msg.type === 'pair') {
    if (!/^[a-f0-9]{64}$/.test(msg.pairing || '') || !Number.isInteger(msg.port) || msg.port < 1024 || msg.port > 65535) { respond({ ok: false }); return; }
    void chrome.storage.session.set({ pairing: msg.pairing, port: msg.port }).then(() => { socket?.close(); socket = null; return connect(); }).then(() => respond({ ok: true }));
    return true;
  }
  if (msg.type === 'status') respond({ connected: socket?.readyState === WebSocket.OPEN });
  if (msg.type === 'disconnect') { void chrome.storage.session.clear(); socket?.close(); socket = null; respond({ ok: true }); }
});
chrome.alarms.create('reconnect', { periodInMinutes: 0.5 });
chrome.alarms.onAlarm.addListener(() => { void connect(); });
chrome.runtime.onStartup.addListener(() => { void connect(); });
void connect();
