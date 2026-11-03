import { chromium } from 'playwright-core';
import { allowedCommand } from './bridge.mjs';

export const AUCTION_ORIGIN = 'https://auction.maplestory.nexon.com';
const API_ORIGIN = 'https://api.mskr.nexon.com';
const CLIENT_HEADERS = ['x-platform', 'x-device-id', 'x-client-version'];
const failure = (code, error) => ({ id:'', ok:false, code, error });
const originOf = url => { try { return new URL(url).origin; } catch { return ''; } };

// Cookie/Authorization values never leave Chromium. Only these three non-secret
// client headers from normal official-site requests are kept in process memory.
export function pickClientHeaders(headers) {
  return Object.fromEntries(CLIENT_HEADERS.filter(k => typeof headers[k] === 'string').map(k => [k, headers[k]]));
}
export function browserProxy(env = process.env) {
  const value = env.HTTPS_PROXY || env.https_proxy || env.HTTP_PROXY || env.http_proxy;
  if (!value) return undefined;
  const u = new URL(value);
  return { server:`${u.protocol}//${u.host}`, ...(u.username ? { username:decodeURIComponent(u.username), password:decodeURIComponent(u.password) } : {}),
    bypass:[env.NO_PROXY || env.no_proxy || '', 'localhost', '127.0.0.1', '[::1]'].filter(Boolean).join(',') };
}

export class BrowserPageTransport {
  constructor() { this.context = null; this.page = null; this.epoch = 0; this.headers = {}; this.accounts = null; this.onChange = () => {}; this.navigation = 'starting'; }
  get connected() { return !!this.page && !this.page.isClosed() && originOf(this.page.url()) === AUCTION_ORIGIN; }
  status() { return { running:!!this.context, pageReady:this.connected, clientReady:CLIENT_HEADERS.every(k => !!this.headers[k]), navigation:this.navigation }; }
  invalidate() { ++this.epoch; this.accounts = null; this.onChange(); }
  scope() {
    const epoch = this.epoch, wire = this;
    return { get connected() { return epoch === wire.epoch && wire.connected; }, request:(cmd, ms) => wire.request(cmd, ms, epoch) };
  }
  observe(page) {
    page.on('framenavigated', frame => {
      if (frame !== page.mainFrame()) return;
      if (originOf(page.url()) === AUCTION_ORIGIN) {
        this.page = page; this.headers = {}; this.navigation = 'official-site'; this.invalidate();
      } else if (this.page === page) {
        this.page = null; this.headers = {}; this.navigation = 'login-or-navigation'; this.invalidate();
      }
    });
    page.on('close', () => {
      if (this.page === page) { this.page = null; this.headers = {}; this.navigation = 'tab-closed'; this.invalidate(); }
    });
    page.on('request', req => {
      if (page === this.page && originOf(req.url()) === API_ORIGIN && originOf(page.url()) === AUCTION_ORIGIN) {
        this.headers = { ...this.headers, ...pickClientHeaders(req.headers()) };
      }
    });
  }
  async start(localUrl, { profileDir, executablePath = '/usr/bin/chromium' } = {}) {
    // The browser is a visible, dedicated profile. No remote debugging port,
    // extension, cookie export, login automation, stealth or TLS bypass is used.
    const context = await chromium.launchPersistentContext(profileDir, {
      executablePath, headless:false, viewport:null, locale:'ko-KR', timezoneId:'Asia/Seoul',
      handleSIGTERM:false, handleSIGINT:false, handleSIGHUP:false,
      proxy:browserProxy(), args:['--start-maximized', '--disable-dev-shm-usage'],
    });
    this.context = context;
    context.on('close', () => { this.context = null; this.page = null; this.headers = {}; this.navigation = 'browser-closed'; this.invalidate(); });
    context.on('page', page => this.observe(page));
    // Do not restore old tabs with query strings or open automatic API searches.
    const pages = context.pages();
    const appPage = pages[0] || await context.newPage();
    if (pages.length) this.observe(appPage);
    for (const page of pages.slice(1)) await page.close();
    await appPage.goto(localUrl);
    const auction = await context.newPage();
    try { await auction.goto(AUCTION_ORIGIN, { waitUntil:'domcontentloaded', timeout:45000 }); }
    catch { this.navigation = 'site-unavailable'; }
    await appPage.bringToFront();
  }
  async fetchInPage(cmd, timeoutMs) {
    const headers = { accept:'application/json, text/plain, */*', ...this.headers, ...(cmd.body != null ? { 'content-type':'application/json' } : {}) };
    return this.page.evaluate(async ({ url, method, headers, body, timeoutMs }) => {
      const res = await fetch(url, { method, headers, body, credentials:'include', redirect:'error', signal:AbortSignal.timeout(timeoutMs) });
      const bodyText = await res.text();
      if (bodyText.length > 10 * 1024 * 1024) throw new Error('Response too large');
      return { id:'', ok:res.ok, status:res.status, bodyText, ...(!res.ok ? { code:'HTTP_ERROR', error:`HTTP ${res.status}` } : {}) };
    }, { url:cmd.url, method:cmd.method, headers, body:cmd.body, timeoutMs:Math.min(timeoutMs, 18000) });
  }
  async request(cmd, timeoutMs = 20000, epoch = this.epoch) {
    if (!allowedCommand(cmd)) return failure('FORBIDDEN_URL', '조회·검색 경로만 허용합니다.');
    if (epoch !== this.epoch || !this.connected) return failure('DISCONNECTED', '원격 브라우저의 공식 경매장 탭을 확인하고 다시 연결하세요.');
    if (!CLIENT_HEADERS.every(k => !!this.headers[k])) return failure('NO_CLIENT_HEADERS', '공식 경매장에서 로그인·추가 인증을 완료하고 페이지를 새로고침하세요.');
    try {
      // Recheck the account before every search POST. Switching login in another
      // tab must not let an existing watch search as a different account.
      const isAccounts = new URL(cmd.url).pathname === '/v1/accounts';
      if (cmd.method === 'POST' && !this.accounts) return failure('SESSION_REQUIRED', '연결 확인으로 본인 캐릭터를 다시 불러오세요.');
      if (cmd.method === 'POST') {
        const check = await this.request({ url:API_ORIGIN + '/v1/accounts', method:'GET' }, timeoutMs, epoch);
        if (!check.ok) return check;
      }
      if (epoch !== this.epoch) return failure('SESSION_CHANGED', '로그인 계정이 변경되어 감시를 중단했습니다. 다시 연결하세요.');
      const reply = await this.fetchInPage(cmd, timeoutMs);
      if (epoch !== this.epoch) return failure('SESSION_CHANGED', '브라우저 연결이 변경되었습니다. 다시 연결하세요.');
      if ([401,403].includes(reply.status)) { this.invalidate(); return { ...reply, bodyText:undefined, error:'공식 경매장 인증을 확인한 뒤 다시 연결하세요.' }; }
      if (reply.ok && isAccounts) {
        const data = JSON.parse(reply.bodyText);
        if (!Array.isArray(data.accounts) || !data.accounts.length) return failure('NO_ACCOUNTS', '공식 경매장에서 본인 계정을 확인하세요.');
        const ids = data.accounts.map(a => String(a.accountId)).sort().join(',');
        if (this.accounts && ids !== this.accounts) { this.invalidate(); return failure('SESSION_CHANGED', '로그인 계정이 변경되었습니다. 캐릭터를 다시 선택하세요.'); }
        this.accounts = ids;
      }
      return reply;
    } catch { return failure('NETWORK', '브라우저 요청에 실패했습니다. 공식 탭의 로그인·네트워크 상태를 확인하세요. 검색을 자동 재시도하지 않습니다.'); }
  }
  async close() { await this.context?.close(); }
}
