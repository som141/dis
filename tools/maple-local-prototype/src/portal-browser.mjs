import { chromium } from 'playwright-core';
import { mkdir, unlink } from 'node:fs/promises';
import { join } from 'node:path';
import { BrowserPageTransport, AUCTION_ORIGIN, browserProxy } from './browser-transport.mjs';

export function isLoginSurface(url) {
  try {
    const u = new URL(url);
    // Nexon's NAVER sign-in leaves nexon.com, then returns through its own
    // callback. Allow only NAVER's exact HTTPS authentication origin; other
    // NAVER services and lookalike domains are not login surfaces.
    return u.origin === 'https://nid.naver.com' ||
      u.protocol === 'https:' && (u.hostname === 'nexon.com' || u.hostname.endsWith('.nexon.com')) && u.hostname !== 'openapi.nexon.com';
  } catch { return false; }
}
export class PortalBrowser extends BrowserPageTransport {
  constructor({ configureContext } = {}) { super(); this.configureContext = configureContext; this.authPage = null; this.dialog = null; this.inputBusy = false; this.width = 1280; this.height = 900; this.captures = new WeakMap(); }
  async start(profileDir) {
    await mkdir(profileDir,{ recursive:true, mode:0o700 });
    // The portal container holds the exclusive volume flock before reaching here.
    for (const file of ['SingletonLock','SingletonSocket','SingletonCookie']) {
      await unlink(join(profileDir,file)).catch(e => { if (e.code !== 'ENOENT') throw e; });
    }
    const ctx = await chromium.launchPersistentContext(profileDir, {
      executablePath:'/usr/bin/chromium', headless:false, viewport:{ width:this.width, height:this.height },
      locale:'ko-KR', timezoneId:'Asia/Seoul', proxy:browserProxy(),
      handleSIGTERM:false, handleSIGINT:false, handleSIGHUP:false,
      args:['--disable-dev-shm-usage'],
    });
    this.context = ctx;
    await this.configureContext?.(ctx);
    ctx.on('close',() => { this.context = null; this.page = null; this.authPage = null; this.headers = {}; this.navigation = 'browser-closed'; this.invalidate(); });
    const observe = page => {
      this.observe(page);
      // Popups first appear as about:blank. Do not replace the usable login
      // surface until the new page actually reaches an allowed official site.
      if (isLoginSurface(page.url())) this.authPage = page;
      page.on('framenavigated',frame => { if (frame === page.mainFrame() && isLoginSurface(page.url())) this.authPage = page; });
      page.on('dialog',dialog => { this.dialog = dialog; });
      page.on('close',() => { if (this.authPage === page) this.authPage = ctx.pages().filter(p => isLoginSurface(p.url())).at(-1) || null; });
    };
    ctx.on('page',observe);
    for (const p of ctx.pages()) observe(p);
    const pages = ctx.pages(), first = pages[0] || await ctx.newPage();
    for (const p of pages.slice(1)) await p.close();
    await first.goto(AUCTION_ORIGIN,{ waitUntil:'domcontentloaded', timeout:45000 });
  }
  loginState() {
    const p = this.loginPage();
    let origin = null;
    if (p && !p.isClosed() && isLoginSurface(p.url())) origin = new URL(p.url()).origin;
    return { origin, width:this.width, height:this.height, dialog:this.dialog ? { type:this.dialog.type(), message:this.dialog.message().slice(0,800) } : null };
  }
  loginPage() {
    const usable = p => p && !p.isClosed() && isLoginSurface(p.url());
    if (!usable(this.authPage)) this.authPage = this.context?.pages().filter(usable).at(-1) || null;
    return this.authPage;
  }
  surface() {
    const p = this.loginPage();
    if (!p || p.isClosed() || !isLoginSurface(p.url())) throw Object.assign(new Error(),{ code:'LOGIN_SURFACE_UNAVAILABLE', status:409 });
    return p;
  }
  async frame() {
    const page = this.surface();
    let capture = this.captures.get(page);
    if (!capture) {
      capture = { session:this.context.newCDPSession(page), pending:null };
      this.captures.set(page,capture);
    }
    // One in-flight capture per page, even if the same visitor opens two tabs.
    // Capture the compositor directly: screenshots must not wait for web fonts,
    // animations or navigation, which otherwise delay every input refresh.
    if (!capture.pending) {
      capture.pending = (async () => {
        let timer;
        try {
          const session = await capture.session;
          const { data } = await Promise.race([
            session.send('Page.captureScreenshot',{ format:'jpeg', quality:65, fromSurface:true, captureBeyondViewport:false }),
            new Promise((_,reject) => { timer = setTimeout(() => reject(new Error('Frame timeout')),3000); }),
          ]);
          // A redirect/close may have replaced the page while capture ran.
          if (this.surface() !== page) throw new Error('Login page changed');
          return Buffer.from(data,'base64');
        } catch {
          this.captures.delete(page);
          void capture.session.then(s => s.detach()).catch(() => {});
          throw Object.assign(new Error(),{ code:'LOGIN_FRAME_UNAVAILABLE', status:503 });
        } finally { clearTimeout(timer); }
      })().finally(() => { capture.pending = null; });
    }
    return capture.pending;
  }
  async reloadLogin() {
    if (this.inputBusy || this.reloading) throw Object.assign(new Error(),{ code:'BUSY', status:409 });
    this.reloading = true;
    try {
      const page = this.loginPage() || await this.context.newPage();
      await page.goto(AUCTION_ORIGIN,{ waitUntil:'domcontentloaded', timeout:30000 });
    } catch {
      throw Object.assign(new Error(),{ code:'LOGIN_SURFACE_UNAVAILABLE', status:503 });
    } finally { this.reloading = false; }
  }
  async input(input) {
    if (this.inputBusy) throw Object.assign(new Error(),{ code:'BUSY', status:409 });
    const page = this.surface(); this.inputBusy = true;
    try {
      if (input.type === 'click' && Number.isFinite(input.x) && Number.isFinite(input.y) && input.x >= 0 && input.x < this.width && input.y >= 0 && input.y < this.height) {
        await page.mouse.click(input.x,input.y);
      } else if (input.type === 'text' && typeof input.text === 'string' && input.text.length > 0 && input.text.length <= 1024) {
        await page.keyboard.insertText(input.text);
      } else if (input.type === 'key' && typeof input.key === 'string' && /^(Tab|Shift\+Tab|Enter|Backspace|Delete|ArrowLeft|ArrowRight|ArrowUp|ArrowDown|Home|End|Escape|Control\+a)$/.test(input.key)) {
        await page.keyboard.press(input.key);
      } else if (input.type === 'wheel' && Number.isFinite(input.dy) && Math.abs(input.dy) <= 1500) {
        await page.mouse.wheel(0,input.dy);
      } else if (input.type === 'dialog' && typeof input.accept === 'boolean' && this.dialog) {
        const dialog = this.dialog; this.dialog = null;
        if (input.accept && dialog.type() !== 'prompt') await dialog.accept(); else await dialog.dismiss();
      } else throw Object.assign(new Error(),{ code:'INVALID_INPUT', status:400 });
    } finally { this.inputBusy = false; }
  }
}
