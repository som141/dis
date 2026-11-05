import {findEquipment,potentialRules,catalog} from './equipment-data.mjs';
import {POTENTIAL_OPTION_LABELS} from '../dist/core.mjs';
import {MAXIMUM_OPTION_KEYS} from './potential-limits.mjs';
import http from 'node:http';
import { readFile, mkdir } from 'node:fs/promises';
import { resolve, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { randomBytes, timingSafeEqual } from 'node:crypto';
import { PrototypeEngine, AppError, CATEGORIES } from './domain.mjs';
import { PortalStore } from './portal-store.mjs';
import { PortalFlow } from './portal-flow.mjs';
import { PortalBrowser } from './portal-browser.mjs';

const root = fileURLToPath(new URL('../',import.meta.url));
const same = (a,b) => typeof a === 'string' && typeof b === 'string' && a.length === b.length && timingSafeEqual(Buffer.from(a),Buffer.from(b));
const cookieName = 'maple_portal_session';
function sessionToken(req) {
  const matches = String(req.headers.cookie || '').split(';').map(s => s.trim()).filter(s => s.startsWith(cookieName+'='));
  return matches.length === 1 ? matches[0].slice(cookieName.length+1) : '';
}
async function body(req) {
  let size = 0; const chunks = [];
  for await (const chunk of req) { size += chunk.length; if (size > 65536) throw Object.assign(new Error(),{ status:413 }); chunks.push(chunk); }
  const data = JSON.parse(Buffer.concat(chunks).toString('utf8') || '{}');
  if (!data || typeof data !== 'object' || Array.isArray(data)) throw new Error();
  return data;
}
const safeErrors = {
  CAPACITY:'현재 연결 가능한 사용자 수가 가득 찼습니다.', UNAUTHENTICATED:'계정을 연결해 주세요.',
  FORBIDDEN:'이 화면을 새로고침한 뒤 다시 시도해 주세요.', BUSY:'이전 작업을 처리하고 있습니다.',
  LOGIN_SURFACE_UNAVAILABLE:'공식 로그인 화면을 준비하고 있습니다.',
  LOGIN_FRAME_UNAVAILABLE:'로그인 화면을 불러오지 못했습니다. 잠시 후 다시 연결합니다.',
  LOGIN_FINISHED:'로그인이 완료되어 자동 작업을 진행하고 있습니다.',
  ACCOUNT_CHANGED:'계정이 변경되어 이전 작업을 중지했습니다.',
};
export async function createPortal({ port = 8878, host = '127.0.0.1', origin, dataDir, limit = 2, discordMode = false, internalKey = '',
  browserFactory = () => new PortalBrowser(), engineFactory = wire => new PrototypeEngine(wire,{ manageKeyEnvironment:false, completePageLimit:10000 }),
  keyFile, now = Date.now, timerMs = 1000 } = {}) {
  const store = new PortalStore(dataDir,{ limit }); await store.init();
  const sessions = new Map(),accountReservations=new Map(); let closing = false, currentOrigin = origin, key = process.env.NEXON_DEVELOPER_KEY || '';
  let serviceKeyStatus = key ? 'configured' : 'missing';
  async function activate(record) {
    if (sessions.has(record.id)) return sessions.get(record.id);
    const wire = browserFactory(record), engine = engineFactory(wire);
    engine.key = key;
    const flow = new PortalFlow(engine,wire,{ now, saved:record.flow, save:async saved => { record.flow = saved; store.captureDiscordNotices(record,saved); await store.save(record); },
      isOwnerAvailable:owner=>{
        if(!discordMode)return true;
        const reserved=accountReservations.get(owner);
        if(reserved&&reserved!==record.id)return false;
        if([...store.records.values()].some(other=>other.id!==record.id&&other.discordUserId&&other.flow?.owner===owner))return false;
        accountReservations.set(owner,record.id);
        return true;
      } });
    wire.onChange = () => { engine.connectionChanged();flow.connectionChanged(); };
    const session = { wire, engine, flow, csrf:randomBytes(32).toString('hex'), starting:true, failed:false };
    sessions.set(record.id,session);
    session.ready = wire.start(join(dataDir,'profiles',record.id)).then(() => { session.starting = false; }).catch(() => { session.starting = false; session.failed = true; });
    return session;
  }
  async function refreshKey() {
    if (!keyFile) return;
    let next = '';
    try {
      next = (await readFile(keyFile,'utf8')).trim();
      serviceKeyStatus = next ? /^[A-Za-z0-9._-]{10,1024}$/.test(next) ? 'configured' : 'invalid' : 'missing';
    } catch { serviceKeyStatus = 'unreadable'; }
    // An operator configuration error must never stop browser login processing.
    if (serviceKeyStatus !== 'configured') next = '';
    if (next !== key) {
      key = next;
      if (key) process.env.NEXON_DEVELOPER_KEY = key; else delete process.env.NEXON_DEVELOPER_KEY;
      for (const s of sessions.values()) s.engine.key = key;
    }
  }
  await refreshKey();
  const server = http.createServer(async (req,res) => {
    res.setHeader('Cache-Control','no-store'); res.setHeader('X-Content-Type-Options','nosniff');
    res.setHeader('Referrer-Policy','no-referrer');
    res.setHeader('Content-Security-Policy',"default-src 'self'; script-src 'self'; style-src 'self'; connect-src 'self'; img-src 'self' blob: https://avatar.maplestory.nexon.com https://mskr-web.dn.nexoncdn.co.kr; frame-ancestors 'none'; base-uri 'none'; form-action 'self'");
    const json = (status,value) => { res.writeHead(status,{ 'Content-Type':'application/json; charset=utf-8' }); res.end(JSON.stringify(value)); };
    try {
      const url = new URL(req.url,'http://localhost');
      if (url.pathname === '/healthz' && req.method === 'GET' && /^(127\.0\.0\.1|localhost):\d+$/.test(req.headers.host || '')) return json(200,{ ok:!closing, mode:'isolated-portal' });
      if(closing) return json(503,{ok:false,code:'BUSY',error:'프로그램을 다시 시작하고 있습니다. 잠시 후 새로고침해 주세요.'});
      if (discordMode && url.pathname.startsWith('/internal/discord/')) {
        if (!internalKey || !same(req.headers['x-maple-internal-key'],internalKey)) throw Object.assign(new Error(),{code:'FORBIDDEN',status:403});
        if(url.pathname==='/internal/discord/events'&&req.method==='GET')return json(200,{events:store.pendingDiscordNotices()});
        if(url.pathname==='/internal/discord/events/ack'&&req.method==='POST'){
          const data=await body(req);
          if(!await store.acknowledgeDiscordNotice(data.eventId,data.status))throw new AppError('알림을 찾지 못했습니다.',404);
          return json(200,{ok:true});
        }
        if (url.pathname==='/internal/discord/session'&&req.method==='POST') {
          const {discordUserId}=await body(req),issued=await store.issueDiscordSession(discordUserId);
          return json(200,{token:issued.token});
        }
        const match=/^\/internal\/discord\/users\/(\d{1,22})\/(status|stop)$/.exec(url.pathname);
        if(match){
          const [,discordUserId,action]=match,record=store.findDiscordUser(discordUserId);
          if(!record&&action==='status'&&req.method==='GET')return json(200,{linked:false,jobs:[]});
          if(!record)throw Object.assign(new Error(),{status:404});
          const active=sessions.get(record.id),flow=active?.flow,stored=record.flow||{};
          if(action==='status'&&req.method==='GET'){
            const jobs=flow?.jobs||stored.jobs||[];
            return json(200,{linked:true,character:flow?.engine.character?.name||null,jobs:jobs.map(job=>({id:job.id,name:job.config?.keyword||'',phase:job.phase,watch:job.config?.watch===true&&!job.stopped}))});
          }
          if(action==='stop'&&req.method==='POST'){
            const {jobId}=await body(req);
            if(typeof jobId!=='string'||!/^[a-zA-Z0-9_-]{1,70}$/.test(jobId))throw new AppError('작업 ID를 확인하세요.');
            if(flow)await flow.stop(jobId);
            else{
              const job=(stored.jobs||[]).find(job=>job.id===jobId);
              if(!job)throw new AppError('검색 항목을 찾지 못했습니다.',404);
              job.config.watch=false;job.stopped=true;job.phase='paused';
              const config=(stored.configs||[]).find(row=>row.id===job.config.id&&row.mode===job.config.mode);
              if(config)config.watch=false;
              await store.save(record);
            }
            return json(200,{ok:true});
          }
        }
        throw Object.assign(new Error(),{status:404});
      }
      if (req.headers.host !== new URL(currentOrigin).host) throw Object.assign(new Error(),{ code:'FORBIDDEN', status:403 });
      const mutating = req.method !== 'GET';
      if (mutating && (req.method !== 'POST' || req.headers.origin !== currentOrigin || !String(req.headers['content-type']).startsWith('application/json'))) throw Object.assign(new Error(),{ code:'FORBIDDEN', status:403 });
      if(url.pathname==='/api/catalog'&&req.method==='GET') return json(200,{categories:CATEGORIES,optionLabels:POTENTIAL_OPTION_LABELS,maximumOptionKeys:MAXIMUM_OPTION_KEYS,...findEquipment(Object.fromEntries(url.searchParams))});
      if(url.pathname==='/api/potential-rules'&&req.method==='GET'){try{return json(200,await potentialRules(Object.fromEntries(url.searchParams)));}catch(e){return json(422,{error:e.message});}}
      if (url.pathname === '/api/session' && req.method === 'POST') {
        const existing = store.find(sessionToken(req));
        if(discordMode&&!existing)throw Object.assign(new Error(),{code:'UNAUTHENTICATED',status:401});
        const created = existing ? null : await store.create();
        const record = existing || created.record;
        if (created) res.setHeader('Set-Cookie',`${cookieName}=${created.token}; HttpOnly; SameSite=Strict; Path=/; Max-Age=2592000${currentOrigin.startsWith('https:') ? '; Secure' : ''}`);
        await activate(record); return json(200,{ ok:true });
      }
      if (url.pathname.startsWith('/api/')) {
        const record = store.find(sessionToken(req));
        if (!record) throw Object.assign(new Error(),{ code:'UNAUTHENTICATED', status:401 });
        const s = await activate(record);
        if (url.pathname === '/api/bootstrap' && req.method === 'GET') return json(200,{ csrf:s.csrf });
        if (mutating || url.pathname.startsWith('/api/login/')) {
          if (!same(req.headers['x-portal-token'],s.csrf)) throw Object.assign(new Error(),{ code:'FORBIDDEN', status:403 });
        }
        const loginAllowed = ['login','character-unavailable'].includes(s.flow.phase);
        if (url.pathname === '/api/state' && req.method === 'GET') return json(200,{ ...s.flow.state(), starting:s.starting, browserFailed:s.failed, serviceKeyStatus,
          connection:s.wire.status(), autoConnection:s.flow.auto.state(), login:loginAllowed ? s.wire.loginState() : null });
        if(url.pathname==='/api/character/select'&&req.method==='POST'){
          const data=await body(req);await s.flow.selectCharacter(data.characterId);return json(200,{ok:true});
        }
        if (url.pathname.startsWith('/api/login/') && !loginAllowed) throw Object.assign(new Error(),{ code:'LOGIN_FINISHED', status:409 });
        if (url.pathname === '/api/login/frame' && req.method === 'GET') {
          const frame = await s.wire.frame();
          res.writeHead(200,{ 'Content-Type':'image/jpeg' }); res.end(frame); return;
        }
        if (url.pathname === '/api/login/reload' && req.method === 'POST') { await s.ready; await s.wire.reloadLogin(); return json(200,{ ok:true }); }
        if (url.pathname === '/api/login/input' && req.method === 'POST') { await s.wire.input(await body(req)); return json(200,{ ok:true }); }
        if (url.pathname === '/api/jobs' && req.method === 'POST') { const data=await body(req);if(!Array.isArray(data.jobs))throw new AppError('검색 조건 목록을 확인하세요.');for(const job of data.jobs||[]){
          if(!job||typeof job!=='object'||Array.isArray(job))throw new AppError('검색 조건 형식이 잘못되었습니다.');
          if(job.enabled===false)continue;
          if(job.itemId){const item=catalog.items.find(x=>x.id===job.itemId);if(!item||item.name!==job.keyword||item.category!==job.category)throw new AppError('아이템과 부위를 다시 선택하세요.');if(job.jobClass&&job.jobClass!=='ALL'&&!item.jobs.includes('ALL')&&!item.jobs.includes(job.jobClass))throw new AppError('직업군과 아이템이 맞지 않습니다.');job.equipmentLevel=item.level;}

        }await s.flow.configure(data.jobs);return json(202,{ok:true}); }
        const jobRoute=/^\/api\/jobs\/([a-zA-Z0-9_-]{1,70})\/(items|item|csv|stop|retry|delete)$/.exec(url.pathname);
        if(jobRoute) {
          const [,id,action]=jobRoute;
          if(action==='items'&&req.method==='GET') return json(200,s.flow.items(id,{page:url.searchParams.get('page')||1,size:url.searchParams.get('size')||50,sort:url.searchParams.get('sort')||'default'}));
          if(action==='item'&&req.method==='GET')return json(200,s.flow.itemDetail(id,url.searchParams.get('itemId')));
          if(action==='delete'&&req.method==='POST'){await s.flow.remove(id);return json(200,{ok:true});}
          if(action==='stop'&&req.method==='POST') {await s.flow.stop(id);return json(200,{ok:true});}
          if(action==='retry'&&req.method==='POST') {await s.flow.retry(id);return json(202,{ok:true});}
          if(action==='csv'&&req.method==='GET') {
            s.flow.items(id);
            const job=s.flow.jobs.find(job=>job.id===id),items=job.engine?.result?.items;
            if(!items) throw new AppError('조회 결과를 다시 불러와 주세요.',409,'NO_RESULTS');
            const cell=value=>{let text=String(value??'');if(/^[=+\-@\t\r]/.test(text))text="'"+text;return '"'+text.replaceAll('"','""')+'"';};
            const columns=['id','name','price','quantity','starforce','potential','additional','exOption','tradeDate','status'];
            const csv='\uFEFF'+[columns,...items.map(item=>columns.map(key=>item[key]))].map(row=>row.map(cell).join(',')).join('\r\n');
            res.writeHead(200,{'Content-Type':'text/csv; charset=utf-8','Content-Disposition':`attachment; filename="maple-${id}.csv"`});res.end(csv);return;
          }
        }
        if(url.pathname==='/api/jobs/clear-finished'&&req.method==='POST')return json(200,{ok:true,removed:await s.flow.clearFinished()});
        if(url.pathname==='/api/reconnect'&&req.method==='POST'){await s.flow.stop();s.engine.connectionChanged();s.flow.connectionChanged();s.flow.phase='login';await s.wire.reloadLogin();return json(200,{ok:true});}
        if (url.pathname === '/api/stop' && req.method === 'POST') { await s.flow.stop(); return json(200,{ ok:true }); }
        if (url.pathname === '/api/retry' && req.method === 'POST') { await s.flow.retry(); return json(200,{ ok:true }); }
        throw Object.assign(new Error(),{ status:404 });
      }
      const assets = { '/':['public/portal.html','text/html; charset=utf-8'], '/portal.js':['public/portal.js','text/javascript; charset=utf-8'], '/equipment-ui.js':['public/equipment-ui.js','text/javascript; charset=utf-8'], '/item-details.js':['public/item-details.js','text/javascript; charset=utf-8'], '/market-chart.js':['public/market-chart.js','text/javascript; charset=utf-8'], '/portal.css':['public/portal.css','text/css; charset=utf-8'] };
      if (req.method === 'GET' && assets[url.pathname]) {
        const [file,type] = assets[url.pathname]; res.writeHead(200,{ 'Content-Type':type }); res.end(await readFile(resolve(root,file))); return;
      }
      throw Object.assign(new Error(),{ status:404 });
    } catch(e) {
      // Never log URLs, user input, frames, cookies, keys or provider bodies.
      const code = Object.hasOwn(safeErrors,e.code || '') ? e.code : e instanceof AppError ? e.code : 'REQUEST_FAILED';
      json(e.status || (e.code === 'BUSY' ? 409 : 400),{ ok:false, code, error:safeErrors[code] || (e instanceof AppError&&e.code!=='PROVIDER_ERROR'?e.message:'요청을 처리하지 못했습니다. 잠시 후 다시 확인해 주세요.') });
    }
  });
  let pumping = null, lastKeyRead = 0;
  function tick() {
    if (closing) return Promise.resolve();
    if (pumping) return pumping;
    pumping = (async () => {
      if (now()-lastKeyRead >= 5000) { await refreshKey(); lastKeyRead = now(); }
      await Promise.all([...sessions.values()].filter(s => !s.starting && !s.failed).map(s => s.flow.tick()));
    })().finally(() => { pumping = null; });
    return pumping;
  }
  const timer = timerMs ? setInterval(() => { void tick().catch(() => {}); },timerMs) : null;
  timer?.unref();
  return { server, store, sessions, tick,
    async listen() {
      await mkdir(join(dataDir,'profiles'),{ recursive:true, mode:0o700 });
      await new Promise((ok,fail) => { server.once('error',fail); server.listen(port,host,ok); });
      currentOrigin ||= `http://127.0.0.1:${server.address().port}`;
      for (const record of store.records.values()) if ((record.flow?.watch && !record.flow.stopped) || record.flow?.jobs?.some(job=>job.watch&&!job.stopped)) await activate(record);
      return server.address();
    },
    async close() {
      closing = true; clearInterval(timer);
      for(const s of sessions.values()) {s.flow.closing=true;for(const job of s.flow.jobs) if(job.engine) job.engine.cancelRequested=true;}
      await pumping?.catch(() => {});
      await Promise.all([...sessions.values()].map(async s => { await s.ready; await s.wire.close(); s.engine.destroy(); }));
      await new Promise(ok => server.close(ok));
    },
  };
}
