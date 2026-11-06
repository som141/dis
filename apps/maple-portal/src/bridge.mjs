import { randomUUID, timingSafeEqual } from 'node:crypto';
import { WebSocket, WebSocketServer } from 'ws';

const ALLOWED_GET = [
  /^\/v1\/accounts$/,
  /^\/v1\/accounts\/\d+\/gameWorlds\/\d+\/(characters|balance)$/,
  /^\/v1\/market\/web\/daily-limit$/,
  /^\/v1\/market\/web\/items\/searches\/(sold\/)?[^/]+\/tool-tip$/,
];
export function allowedCommand(cmd) {
  try {
    const u = new URL(cmd.url);
    if (u.origin !== 'https://api.mskr.nexon.com' || u.username || u.password) return false;
    if (cmd.method === 'GET') return ALLOWED_GET.some(p => p.test(u.pathname));
    return cmd.method === 'POST' && /^\/v1\/market\/web\/items\/searches\/(sold\/)?tool-tip$/.test(u.pathname);
  } catch { return false; }
}

export class BrowserBridge {
  constructor(server, token) {
    this.token = token;
    this.socket = null;
    this.epoch = 0;
    this.pending = new Map();
    this.onChange = () => {};
    this.wss = new WebSocketServer({ noServer: true, maxPayload: 12 * 1024 * 1024 });
    server.on('upgrade', (req, socket, head) => {
      const url = new URL(req.url, 'http://127.0.0.1');
      const candidate = Buffer.from(url.searchParams.get('token') || '');
      const expected = Buffer.from(token);
      if (url.pathname !== '/bridge' || !/^chrome-extension:\/\/[a-p]{32}$/.test(req.headers.origin || '') ||
          candidate.length !== expected.length || !timingSafeEqual(candidate, expected) ||
          !/^(127\.0\.0\.1|localhost):\d+$/.test(req.headers.host || '')) {
        socket.end('HTTP/1.1 403 Forbidden\r\nConnection: close\r\n\r\n');
        return;
      }
      // Exactly one paired Chrome profile. A new connection invalidates the old identity.
      this.wss.handleUpgrade(req, socket, head, ws => this.attach(ws));
    });
  }
  get connected() { return this.socket?.readyState === WebSocket.OPEN; }
  attach(ws) {
    this.drop();
    this.socket = ws;
    ++this.epoch;
    this.onChange();
    ws.on('error', () => ws.close());
    ws.on('message', raw => {
      if (this.socket !== ws) return;
      let msg;
      try { msg = JSON.parse(String(raw)); } catch { return; }
      const p = this.pending.get(msg.id);
      if (!p || typeof msg.ok !== 'boolean') return;
      this.pending.delete(msg.id);
      clearTimeout(p.timer);
      p.resolve(msg);
    });
    ws.on('close', () => { if (this.socket === ws) this.drop(); });
  }
  drop() {
    const old = this.socket;
    this.socket = null;
    ++this.epoch;
    for (const [id, p] of this.pending) {
      clearTimeout(p.timer);
      p.resolve({ id, ok: false, code: 'DISCONNECTED', error: '브라우저 연결이 끊겼습니다.' });
    }
    this.pending.clear();
    old?.close();
    this.onChange();
  }
  scope() {
    const epoch = this.epoch, bridge = this;
    return {
      get connected() { return epoch === bridge.epoch && bridge.connected; },
      request: (cmd, ms) => bridge.request(cmd, ms, epoch),
    };
  }
  request(cmd, timeoutMs = 20_000, epoch = this.epoch) {
    if (epoch !== this.epoch || !this.connected) return Promise.resolve({ id: '', ok: false, code: 'DISCONNECTED', error: 'Chrome 확장에서 로컬 연결을 확인해 주세요.' });
    if (!allowedCommand(cmd)) return Promise.resolve({ id: '', ok: false, code: 'FORBIDDEN_URL', error: '공식 경매장 탭에서 로그인하거나 새로고침해 주세요. 로컬 도구는 검색·조회만 허용합니다.' });
    const id = randomUUID();
    return new Promise(resolve => {
      const timer = setTimeout(() => {
        this.pending.delete(id);
        resolve({ id, ok: false, code: 'TIMEOUT', error: '브라우저 요청 시간이 초과되었습니다. 자동으로 검색을 반복하지 않습니다.' });
      }, timeoutMs);
      this.pending.set(id, { resolve, timer });
      try { this.socket.send(JSON.stringify({ ...cmd, id, type: 'fetch' })); }
      catch { clearTimeout(timer); this.pending.delete(id); resolve({ id, ok: false, code: 'DISCONNECTED', error: '브라우저 연결이 끊겼습니다.' }); }
    });
  }
  async close() { this.drop(); await new Promise(resolve => this.wss.close(resolve)); }
}
