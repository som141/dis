import { mkdir, readFile, writeFile, rename, readdir, unlink } from 'node:fs/promises';
import { join } from 'node:path';
import { randomBytes, createHash } from 'node:crypto';

import { isFragmentSearch } from './domain.mjs';

// Migrate while offline, before deciding which users need a watch browser.
export function withoutFragmentWork(flow) {
  if (!flow) return flow;
  if (flow.version !== 2) {
    if (!isFragmentSearch(flow.watch?.input)) return flow;
    const { watch, plan, ...rest } = flow;
    return { ...rest, version:2, configs:[], jobs:[] };
  }
  const configs = (flow.configs || []).filter(config => !isFragmentSearch(config));
  const jobs = (flow.jobs || []).filter(job => !isFragmentSearch(job.config));
  return configs.length === (flow.configs || []).length && jobs.length === (flow.jobs || []).length
    ? flow : { ...flow, configs, jobs };
}

export const tokenHash = token => createHash('sha256').update(token).digest('hex');
const expiresInThirtyDays = () => new Date(Date.now() + 30 * 86400_000).toISOString();
const validId = id => typeof id === 'string' && /^[a-f0-9]{32}$/.test(id);
const validDiscordId = id => typeof id === 'string' && /^\d{1,22}$/.test(id);
export class PortalStore {
  constructor(root, { limit = 2 } = {}) { this.root = root; this.limit = limit; this.records = new Map(); this.issuance = Promise.resolve(); this.saveQueues=new Map(); }
  async init() {
    await mkdir(this.root, { recursive:true, mode:0o700 });
    for (const file of await readdir(this.root)) {
      if (!/^[a-f0-9]{32}\.json$/.test(file)) continue;
      const record = JSON.parse(await readFile(join(this.root,file),'utf8'));
      if (!validId(record.id) || file !== `${record.id}.json` || !/^[a-f0-9]{64}$/.test(record.tokenHash) || (record.discordUserId != null && !validDiscordId(record.discordUserId))) throw new Error('Invalid portal checkpoint');
      const cleaned = withoutFragmentWork(record.flow);
      if (cleaned !== record.flow) { record.flow = cleaned; await this.save(record); }
      this.records.set(record.id,record);
    }
  }
  async create() {
    if (this.records.size >= this.limit) throw Object.assign(new Error(), { code:'CAPACITY', status:429 });
    const token = randomBytes(32).toString('hex');
    const record = { id:randomBytes(16).toString('hex'), tokenHash:tokenHash(token), createdAt:new Date().toISOString(), sessionExpiresAt:expiresInThirtyDays(), flow:{} };
    // Reserve synchronously so simultaneous creates cannot exceed the limit.
    this.records.set(record.id,record);
    try { await this.save(record); } catch(e) { this.records.delete(record.id); throw e; }
    return { token, record };
  }
  find(token) {
    if (!/^[a-f0-9]{64}$/.test(token || '')) return null;
    const hash = tokenHash(token);
    return [...this.records.values()].find(r => r.tokenHash === hash &&
      Date.parse(r.sessionExpiresAt || r.createdAt) + (r.sessionExpiresAt ? 0 : 30 * 86400_000) > Date.now()) || null;
  }
  findDiscordUser(discordUserId) {
    if (!validDiscordId(discordUserId)) return null;
    return [...this.records.values()].find(record => record.discordUserId === discordUserId) || null;
  }
  issueDiscordSession(discordUserId) {
    const run = this.issuance.then(() => this.issueDiscordSessionLocked(discordUserId));
    this.issuance = run.catch(() => {});
    return run;
  }
  async issueDiscordSessionLocked(discordUserId) {
    if (!validDiscordId(discordUserId)) throw new Error('Invalid Discord user ID');
    let record = this.findDiscordUser(discordUserId);
    const token = randomBytes(32).toString('hex');
    if (!record) {
      if (this.records.size >= this.limit) throw Object.assign(new Error(), { code:'CAPACITY', status:429 });
      record = { id:randomBytes(16).toString('hex'), tokenHash:tokenHash(token), discordUserId, createdAt:new Date().toISOString(), sessionExpiresAt:expiresInThirtyDays(), flow:{} };
      this.records.set(record.id,record);
      try { await this.save(record); } catch(e) { this.records.delete(record.id); throw e; }
    } else {
      const previous = record.tokenHash;
      const previousExpiry = record.sessionExpiresAt;
      record.tokenHash = tokenHash(token);
      record.sessionExpiresAt = expiresInThirtyDays();
      try { await this.save(record); } catch(e) { record.tokenHash = previous; record.sessionExpiresAt = previousExpiry; throw e; }
    }
    return {token,record};
  }
  captureDiscordNotices(record,flow) {
    if(!record.discordUserId)return;
    const outbox=record.notificationOutbox??=[];
    const known=new Set(outbox.map(event=>event.id));
    for(const job of flow.jobs||[])for(const notice of job.notices||[]){
      const item=notice.item;
      if(typeof item?.id!=='string'||typeof notice.foundAt!=='string')continue;
      const id=createHash('sha256').update([record.id,job.id,item.id,notice.foundAt].join('|')).digest('hex');
      if(known.has(id))continue;
      known.add(id);
      outbox.push({id,discordUserId:record.discordUserId,jobId:job.id,itemId:item.id,
        name:String(item.name||'매물').slice(0,100),price:Number.isFinite(Number(item.price))?Number(item.price):null,
        foundAt:notice.foundAt,status:'pending'});
    }
    // Keep pending notices across job deletion; old completed entries can be pruned.
    if(outbox.length>2000)record.notificationOutbox=outbox.filter(event=>event.status==='pending').concat(outbox.filter(event=>event.status!=='pending').slice(-500));
  }
  pendingDiscordNotices(limit=50) {
    return [...this.records.values()].flatMap(record=>record.notificationOutbox||[])
      .filter(event=>event.status==='pending').sort((a,b)=>a.foundAt.localeCompare(b.foundAt)).slice(0,limit)
      .map(({id,discordUserId,jobId,itemId,name,price,foundAt})=>({id,discordUserId,jobId,itemId,name,price,foundAt}));
  }
  async acknowledgeDiscordNotice(id,status) {
    if(!/^[a-f0-9]{64}$/.test(id)||!['delivered','unavailable','retry'].includes(status))return false;
    for(const record of this.records.values()){
      const event=(record.notificationOutbox||[]).find(event=>event.id===id);
      if(!event)continue;
      if(event.status!=='pending')return true;
      if(status==='retry'){
        event.attempts=(event.attempts||0)+1;
        if(event.attempts>=3)event.status='unavailable';
      }else event.status=status;
      event.ackAt=new Date().toISOString();await this.save(record);return true;
    }
    return false;
  }
  save(record) {
    const previous=this.saveQueues.get(record.id)||Promise.resolve();
    const run=previous.catch(()=>{}).then(()=>this.saveNow(record));
    this.saveQueues.set(record.id,run);
    void run.finally(()=>{if(this.saveQueues.get(record.id)===run)this.saveQueues.delete(record.id);}).catch(()=>{});
    return run;
  }
  async saveNow(record) {
    if (!validId(record.id)) throw new Error('Invalid portal ID');
    const target = join(this.root,`${record.id}.json`), temp = `${target}.${randomBytes(4).toString('hex')}.tmp`;
    try{await writeFile(temp,JSON.stringify(record),{ mode:0o600 });await rename(temp,target);}
    finally{await unlink(temp).catch(error=>{if(error.code!=='ENOENT')throw error;});}
  }
}
