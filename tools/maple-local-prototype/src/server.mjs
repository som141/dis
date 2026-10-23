import http from 'node:http';
import { readFile } from 'node:fs/promises';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { resolve } from 'node:path';
import { randomBytes, timingSafeEqual } from 'node:crypto';
import { spawn } from 'node:child_process';
import { BrowserBridge } from './bridge.mjs';
import { AppError, PrototypeEngine, CATEGORIES } from './domain.mjs';

const root = fileURLToPath(new URL('../', import.meta.url));
// Keep this prototype's public-character cache separate from other Maple tools.
process.env.MAPLE_AUCTION_DATA_DIR ||= resolve(root, '.data');
const equal = (a, b) => { const x = Buffer.from(a || ''), y = Buffer.from(b || ''); return x.length === y.length && timingSafeEqual(x, y); };
async function readBody(req) {
  let size = 0, parts = [];
  for await (const part of req) { size += part.length; if (size > 32 * 1024) throw new AppError('입력 내용이 너무 큽니다.', 413); parts.push(part); }
  try { return JSON.parse(Buffer.concat(parts).toString('utf8') || '{}'); }
  catch { throw new AppError('JSON 입력을 확인하세요.'); }
}
export function createApp({ port = 8877, engineFactory = wire => new PrototypeEngine(wire), wireFactory, mode = 'extension' } = {}) {
  const csrf = randomBytes(32).toString('hex'), pairing = randomBytes(32).toString('hex');
  let engine, bridge;
  const server = http.createServer(async (req, res) => {
    const host = req.headers.host || '', address = server.address();
    const currentPort = typeof address === 'object' && address ? address.port : port;
    const validHosts = [`127.0.0.1:${currentPort}`, `localhost:${currentPort}`];
    res.setHeader('Cache-Control', 'no-store');
    res.setHeader('X-Content-Type-Options', 'nosniff');
    res.setHeader('Referrer-Policy', 'no-referrer');
    res.setHeader('Content-Security-Policy', "default-src 'self'; script-src 'self'; style-src 'self'; connect-src 'self'; img-src 'self' data:; frame-ancestors 'none'; base-uri 'none'; form-action 'self'");
    const json = (status, body) => { res.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8' }); res.end(JSON.stringify(body)); };
    try {
      if (!validHosts.includes(host)) throw new AppError('로컬 주소로 접속하세요.', 403, 'INVALID_HOST');
      const url = new URL(req.url, `http://${host}`);
      if (req.method === 'GET' && url.pathname === '/api/bootstrap') return json(200, { csrf, ...(mode === 'extension' ? { pairing } : {}), mode, port: currentPort, categories: CATEGORIES });
      if (req.method === 'GET' && url.pathname === '/api/state') return json(200, { ...engine.state(), browser: bridge.status?.() });
      if (req.method === 'GET' && url.pathname === '/healthz') {
        const ready = mode !== 'server-browser' || !!bridge.status?.().running;
        return json(ready ? 200 : 503, { ok: ready, mode });
      }
      if (req.method === 'POST' && url.pathname.startsWith('/api/')) {
        if (!validHosts.some(h => req.headers.origin === `http://${h}`) || !equal(req.headers['x-local-token'], csrf)) throw new AppError('이 창을 새로고침한 뒤 다시 시도하세요.', 403, 'INVALID_ORIGIN');
        if (!String(req.headers['content-type'] || '').startsWith('application/json')) throw new AppError('JSON 요청만 허용합니다.', 415);
        const body = await readBody(req);
        if (!body || typeof body !== 'object' || Array.isArray(body)) throw new AppError('입력 형식을 확인하세요.');
        const result = await engine.run(async () => {
          switch (url.pathname) {
            case '/api/key': return engine.setKey(body.key);
            case '/api/connect': return engine.connect();
            case '/api/character': return engine.selectCharacter(body.characterId);
            case '/api/equipment': return engine.equipment(body.refresh === true);
            case '/api/search': return engine.search(body);
            case '/api/page': return engine.page(body);
            case '/api/watch/start': return engine.startWatch(body);
            case '/api/watch/stop': return engine.stopWatch();
            default: throw new AppError('없는 기능입니다.', 404);
          }
        });
        return json(200, { ok: true, result });
      }
      const assets = { '/': ['public/index.html', 'text/html; charset=utf-8'], '/app.js': ['public/app.js', 'text/javascript; charset=utf-8'], '/style.css': ['public/style.css', 'text/css; charset=utf-8'] };
      if (req.method === 'GET' && assets[url.pathname]) {
        const [path, type] = assets[url.pathname];
        res.writeHead(200, { 'Content-Type': type }); res.end(await readFile(resolve(root, path))); return;
      }
      throw new AppError('없는 경로입니다.', 404);
    } catch (e) {
      // Never log request bodies, provider errors, session data, URLs with pairing tokens, or keys.
      const message = e instanceof AppError ? e.message : '처리 중 오류가 발생했습니다. 연결 상태를 확인한 뒤 다시 시도하세요.';
      const safe = engine?.key ? message.split(engine.key).join('[가림]') : message;
      json(e instanceof AppError ? e.status : 500, { ok: false, error: safe, code: e.code || 'INTERNAL_ERROR' });
    }
  });
  bridge = wireFactory ? wireFactory() : new BrowserBridge(server, pairing);
  engine = engineFactory(bridge);
  bridge.onChange = () => engine.connectionChanged();
  const timer = setInterval(() => { void engine.tick().catch(() => {}); }, 1000);
  timer.unref();
  return {
    server, bridge, engine,
    async listen() { await new Promise((ok, fail) => { server.once('error', fail); server.listen(port, '127.0.0.1', ok); }); return server.address(); },
    async close() { clearInterval(timer); await bridge.close(); engine.destroy(); await new Promise(ok => server.close(ok)); },
  };
}
if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  const requestedPort = Number(process.env.MAPLE_LOCAL_PORT || 8877);
  if (!Number.isInteger(requestedPort) || requestedPort < 1024 || requestedPort > 65535) throw new Error('Invalid MAPLE_LOCAL_PORT');
  const app = createApp({ port: requestedPort });
  const address = await app.listen();
  console.log(`Maple local prototype: http://127.0.0.1:${address.port}`);
  console.log('Login in your own Chrome. Ctrl+C stops local watches. No production bot is started.');
  if (process.platform === 'win32' && process.argv.includes('--open')) {
    const browser = spawn('cmd.exe', ['/c', 'start', `http://127.0.0.1:${address.port}`], { windowsHide:true, stdio:'ignore' });
    browser.on('error', () => {});
  }
  for (const signal of ['SIGINT', 'SIGTERM']) process.once(signal, () => { void app.close().then(() => process.exit(0)); });
}
