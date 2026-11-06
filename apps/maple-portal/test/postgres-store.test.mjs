import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, writeFile, rm } from 'node:fs/promises';
import { join } from 'node:path';
import { tmpdir } from 'node:os';
import { spawnSync } from 'node:child_process';
import { randomBytes } from 'node:crypto';
import { PostgresPortalStore } from '../src/portal-pg-store.mjs';

const connectionString=process.env.MAPLE_TEST_DATABASE_URL;
const database={connectionString};
const createStore=()=>new PostgresPortalStore('',{limit:10,database});
const id=()=>randomBytes(16).toString('hex');
const hash=()=>randomBytes(32).toString('hex');

test('PostgreSQL restores isolated character workspaces, seen listings, outbox and session expiry',{
  skip:!connectionString,
},async()=>{
  const store=createStore();await store.init();
  const first=await store.issueDiscordSession('101');
  const second=await store.issueDiscordSession('202');
  const owner=hash();
  first.record.flow={version:2,owner,characterId:110,characterWorkspaces:{111:{configs:[{id:'saved',mode:'sold'}],jobs:[]}},
    configs:[{id:'glove',mode:'live',budget:200}],
    jobs:[{id:'glove-live',config:{id:'glove',mode:'live'},phase:'watching',stopped:false,
      watch:{status:'감시 중',seen:['item-a','item-b']},notices:[]}]};
  first.record.notificationOutbox=[{id:hash(),discordUserId:'101',jobId:'glove-live',itemId:'item-b',name:'장갑',price:100,
    foundAt:new Date().toISOString(),status:'pending'}];
  await store.save(first.record);
  second.record.flow={version:2,owner:hash(),characterId:220,characterWorkspaces:{},configs:[],jobs:[]};
  await store.save(second.record);
  await store.audit(first.record,'search_configured',{count:1});
  await store.close();

  const restored=createStore();await restored.init();
  try{
    assert.equal(restored.find(first.token)?.id,first.record.id);
    assert.equal(restored.find(second.token)?.id,second.record.id);
    assert.equal(restored.find('wrong'),null);
    const a=restored.findDiscordUser('101'),b=restored.findDiscordUser('202');
    assert.deepEqual(a.flow.jobs[0].watch.seen,['item-a','item-b']);
    assert.deepEqual(a.flow.characterWorkspaces['111'].configs,[{id:'saved',mode:'sold'}]);
    assert.equal(b.flow.jobs.length,0);
    assert.equal(restored.pendingDiscordNotices().length,1);
    assert.equal(await restored.acknowledgeDiscordNotice(a.notificationOutbox[0].id,'delivered'),true);
    a.sessionExpiresAt=new Date(Date.now()-1000).toISOString();await restored.save(a);
    assert.equal(restored.find(first.token),null);
    const rotated=await restored.issueDiscordSession('101');
    assert.equal(restored.find(first.token),null);
    assert.equal(restored.find(rotated.token)?.id,a.id);
    assert.equal(restored.pendingDiscordNotices().length,0);
  }finally{await restored.close();}
});

test('legacy JSON importer is idempotent and keeps its source file unchanged',{skip:!connectionString},async()=>{
  const root=await mkdtemp(join(tmpdir(),'maple-pg-import-'));
  const record={id:id(),tokenHash:hash(),discordUserId:'303',createdAt:new Date().toISOString(),
    flow:{version:2,owner:hash(),characterId:330,characterWorkspaces:{},configs:[],jobs:[]},notificationOutbox:[]};
  const source=JSON.stringify(record);
  await writeFile(join(root,`${record.id}.json`),source);
  const url=new URL(connectionString);
  const env={...process.env,MAPLE_PORTAL_DATA:root,MAPLE_PORTAL_USERS:'10',MAPLE_POSTGRES_HOST:url.hostname,
    MAPLE_POSTGRES_PORT:url.port,MAPLE_POSTGRES_DB:url.pathname.slice(1),MAPLE_POSTGRES_USER:decodeURIComponent(url.username),
    MAPLE_POSTGRES_PASSWORD:decodeURIComponent(url.password)};
  try{
    const run=()=>spawnSync(process.execPath,['scripts/import-portal-json.mjs'],{cwd:new URL('../',import.meta.url),env,encoding:'utf8'});
    const one=run();assert.equal(one.status,0,one.stderr);assert.equal(JSON.parse(one.stdout).imported,1);
    const two=run();assert.equal(two.status,0,two.stderr);assert.equal(JSON.parse(two.stdout).skipped,1);
    const restored=createStore();await restored.init();
    try{assert.equal(restored.findDiscordUser('303').flow.characterId,330);}
    finally{await restored.close();}
    assert.equal(await (await import('node:fs/promises')).readFile(join(root,`${record.id}.json`),'utf8'),source);
  }finally{await rm(root,{recursive:true,force:true});}
});
