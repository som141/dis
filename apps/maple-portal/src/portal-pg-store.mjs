import pg from 'pg';
import { PortalStore, withoutFragmentWork } from './portal-store.mjs';

const { Client } = pg;
const expiresInThirtyDays = () => new Date(Date.now() + 30 * 86400_000).toISOString();
const json = value => JSON.stringify(value ?? {});
const validId = value => typeof value === 'string' && /^[a-f0-9]{32}$/.test(value);

function workspaceRows(flow) {
  if (flow?.version !== 2) return [];
  const rows = [{ key:'active', characterId:flow.characterId ?? null, active:true,
    configs:flow.configs || [], jobs:flow.jobs || [] }];
  for (const [id, workspace] of Object.entries(flow.characterWorkspaces || {})) {
    if (!/^\d+$/.test(id)) throw new Error('Invalid character workspace');
    rows.push({ key:`character:${id}`, characterId:Number(id), active:false,
      configs:workspace.configs || [], jobs:workspace.jobs || [] });
  }
  return rows;
}

export class PostgresPortalStore extends PortalStore {
  constructor(root, { limit=2, database, client }={}) {
    super(root,{limit});
    this.client=client || new Client(database);
    this.ownsClient=!client;
    this.transactionQueue=Promise.resolve();
    this.locked=false;
  }

  async init() {
    if(this.ownsClient)await this.client.connect();
    // Browser profiles and in-memory jobs require one portal process per database.
    const lock=await this.client.query('SELECT pg_try_advisory_lock(1313096784, 8878) AS acquired');
    if(!lock.rows[0]?.acquired)throw new Error('Another Maple portal is already using this database');
    this.locked=true;
    if(this.ownsClient)this.client.on('error',()=>{ process.exitCode=1; process.kill(process.pid,'SIGTERM'); });

    const accounts=await this.client.query('SELECT * FROM maple.portal_account ORDER BY created_at, id');
    const workspace=await this.client.query('SELECT * FROM maple.character_workspace ORDER BY portal_id, is_active DESC, workspace_key');
    const configs=await this.client.query('SELECT * FROM maple.search_config ORDER BY portal_id, workspace_key, position');
    const jobs=await this.client.query('SELECT * FROM maple.search_job ORDER BY portal_id, workspace_key, position');
    const seen=await this.client.query('SELECT * FROM maple.watch_seen_listing ORDER BY portal_id, workspace_key, job_id');
    const outbox=await this.client.query('SELECT * FROM maple.notification_outbox ORDER BY found_at, event_id');

    const workspaces=new Map();
    for(const row of workspace.rows)workspaces.set(`${row.portal_id}|${row.workspace_key}`,{configs:[],jobs:[]});
    for(const row of configs.rows)workspaces.get(`${row.portal_id}|${row.workspace_key}`)?.configs.push(row.payload);
    for(const row of jobs.rows)workspaces.get(`${row.portal_id}|${row.workspace_key}`)?.jobs.push(row.payload);
    const jobsByKey=new Map();
    for(const row of jobs.rows)jobsByKey.set(`${row.portal_id}|${row.workspace_key}|${row.job_id}`,row.payload);
    for(const row of seen.rows){
      const job=jobsByKey.get(`${row.portal_id}|${row.workspace_key}|${row.job_id}`);
      if(job?.watch){job.watch.seen ||= [];job.watch.seen.push(row.item_id);}
    }

    for(const row of accounts.rows){
      const flow=row.legacy_flow ?? {version:2,owner:row.owner_hash,characterId:row.character_id == null ? null : Number(row.character_id),
        characterWorkspaces:{},configs:[],jobs:[]};
      if(row.legacy_flow == null){
        for(const w of workspace.rows.filter(w=>w.portal_id===row.id)){
          const value=workspaces.get(`${row.id}|${w.workspace_key}`);
          if(w.is_active){flow.configs=value.configs;flow.jobs=value.jobs;}
          else flow.characterWorkspaces[w.workspace_key.slice(10)]=value;
        }
      }
      const record={id:row.id,tokenHash:row.token_hash,discordUserId:row.discord_user_id || undefined,
        createdAt:row.created_at.toISOString(),sessionExpiresAt:row.session_expires_at.toISOString(),flow:withoutFragmentWork(flow),notificationOutbox:[]};
      this.records.set(record.id,record);
    }
    for(const event of outbox.rows){
      const record=this.records.get(event.portal_id);
      record?.notificationOutbox.push({id:event.event_id,discordUserId:event.discord_user_id,jobId:event.job_id,
        itemId:event.item_id,name:event.name,price:event.price == null ? null : Number(event.price),
        foundAt:event.found_at.toISOString(),status:event.status,attempts:event.attempts,
        ...(event.ack_at?{ackAt:event.ack_at.toISOString()}:{})});
    }
    if(this.records.size>this.limit)throw new Error('Portal user limit is lower than the stored user count');
  }

  saveNow(record) {
    const run=this.transactionQueue.catch(()=>{}).then(()=>this.writeRecord(record));
    this.transactionQueue=run;
    return run;
  }

  async writeRecord(record) {
    if(!validId(record.id)||!superValidHash(record.tokenHash))throw new Error('Invalid portal record');
    const flow=record.flow || {};
    const current=flow.version===2;
    record.sessionExpiresAt ||= expiresInThirtyDays();
    await this.client.query('BEGIN');
    try{
      await this.client.query(`INSERT INTO maple.portal_account
        (id,token_hash,discord_user_id,owner_hash,character_id,flow_version,legacy_flow,created_at,session_expires_at)
        VALUES ($1,$2,$3,$4,$5,$6,$7::jsonb,$8,$9)
        ON CONFLICT (id) DO UPDATE SET token_hash=EXCLUDED.token_hash,discord_user_id=EXCLUDED.discord_user_id,
          owner_hash=EXCLUDED.owner_hash,character_id=EXCLUDED.character_id,flow_version=EXCLUDED.flow_version,
          legacy_flow=EXCLUDED.legacy_flow,session_expires_at=EXCLUDED.session_expires_at,updated_at=now()`,
        [record.id,record.tokenHash,record.discordUserId || null,current?flow.owner || null:null,
          current?flow.characterId || null:null,current?2:0,current?null:json(flow),record.createdAt,record.sessionExpiresAt]);
      await this.client.query('DELETE FROM maple.character_workspace WHERE portal_id=$1',[record.id]);
      for(const workspace of workspaceRows(flow)){
        await this.client.query(`INSERT INTO maple.character_workspace(portal_id,workspace_key,character_id,is_active)
          VALUES($1,$2,$3,$4)`,[record.id,workspace.key,workspace.characterId,workspace.active]);
        for(const [position,config] of workspace.configs.entries()){
          await this.client.query(`INSERT INTO maple.search_config(portal_id,workspace_key,config_id,mode,position,payload)
            VALUES($1,$2,$3,$4,$5,$6::jsonb)`,[record.id,workspace.key,config.id,config.mode,position,json(config)]);
        }
        for(const [position,job] of workspace.jobs.entries()){
          const payload=structuredClone(job),seen=Array.isArray(payload.watch?.seen)?payload.watch.seen:[];
          if(payload.watch)delete payload.watch.seen;
          await this.client.query(`INSERT INTO maple.search_job(portal_id,workspace_key,job_id,position,phase,stopped,payload)
            VALUES($1,$2,$3,$4,$5,$6,$7::jsonb)`,[record.id,workspace.key,job.id,position,job.phase || 'queued',job.stopped===true,json(payload)]);
          if(seen.length)await this.client.query(`INSERT INTO maple.watch_seen_listing(portal_id,workspace_key,job_id,item_id)
            SELECT $1,$2,$3,value FROM jsonb_array_elements_text($4::jsonb) AS value
            ON CONFLICT DO NOTHING`,[record.id,workspace.key,job.id,JSON.stringify([...new Set(seen.map(String))])]);
        }
      }
      await this.client.query('DELETE FROM maple.notification_outbox WHERE portal_id=$1',[record.id]);
      for(const event of record.notificationOutbox || []){
        await this.client.query(`INSERT INTO maple.notification_outbox
          (event_id,portal_id,discord_user_id,job_id,item_id,name,price,found_at,status,attempts,ack_at)
          VALUES($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11)`,
          [event.id,record.id,event.discordUserId,event.jobId,event.itemId,event.name,event.price,
            event.foundAt,event.status,event.attempts || 0,event.ackAt || null]);
      }
      await this.client.query('COMMIT');
    }catch(error){await this.client.query('ROLLBACK');throw error;}
  }

  async audit(record,eventType,details={}){
    if(!/^[a-z_]{3,50}$/.test(eventType))throw new Error('Invalid audit event type');
    const run=this.transactionQueue.catch(()=>{}).then(()=>this.client.query(`INSERT INTO maple.audit_event(portal_id,discord_user_id,event_type,details)
      VALUES($1,$2,$3,$4::jsonb)`,[record?.id || null,record?.discordUserId || null,eventType,json(details)]));
    this.transactionQueue=run;
    await run;
  }

  async close(){
    await this.transactionQueue.catch(()=>{});
    if(this.locked){await this.client.query('SELECT pg_advisory_unlock(1313096784, 8878)');this.locked=false;}
    if(this.ownsClient)await this.client.end();
  }
}

function superValidHash(value){return typeof value==='string'&&/^[a-f0-9]{64}$/.test(value);}
