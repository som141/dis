import { readdir, readFile } from 'node:fs/promises';
import { join } from 'node:path';
import { PostgresPortalStore } from '../src/portal-pg-store.mjs';
import { withoutFragmentWork } from '../src/portal-store.mjs';

const root=process.env.MAPLE_PORTAL_DATA || '/home/node/portal-data';
const password=process.env.MAPLE_POSTGRES_PASSWORD;
if(!password)throw new Error('MAPLE_POSTGRES_PASSWORD is required');
const store=new PostgresPortalStore(root,{limit:Number(process.env.MAPLE_PORTAL_USERS || 2),database:{
  host:process.env.MAPLE_POSTGRES_HOST || 'postgres',port:Number(process.env.MAPLE_POSTGRES_PORT || 5432),
  database:process.env.MAPLE_POSTGRES_DB || 'stock',user:process.env.MAPLE_POSTGRES_USER || 'maple_app',password,
  application_name:'maple-json-import',
}});
await store.init();
let imported=0,skipped=0;
try{
  const files=(await readdir(root)).filter(name=>/^[a-f0-9]{32}\.json$/.test(name)).sort();
  const missing=files.filter(file=>!store.records.has(file.slice(0,-5)));
  if(store.records.size+missing.length>store.limit)throw new Error('Stored users exceed MAPLE_PORTAL_USERS; increase capacity before migration');
  for(const file of files){
    const record=JSON.parse(await readFile(join(root,file),'utf8'));
    if(record.id !== file.slice(0,-5) || !/^[a-f0-9]{64}$/.test(record.tokenHash))throw new Error('Invalid legacy checkpoint');
    if(store.records.has(record.id)){skipped++;continue;}
    record.flow=withoutFragmentWork(record.flow);
    record.sessionExpiresAt ||= new Date(Date.parse(record.createdAt)+30*86400_000).toISOString();
    if(!Number.isFinite(Date.parse(record.sessionExpiresAt)))throw new Error('Invalid legacy session timestamp');
    await store.save(record);
    store.records.set(record.id,record);
    imported++;
  }
  // These counts contain no user or account identifiers.
  console.log(JSON.stringify({imported,skipped,storedUsers:store.records.size,
    storedJobs:[...store.records.values()].reduce((n,r)=>n+(r.flow?.jobs?.length || 0),0),
    pendingNotices:store.pendingDiscordNotices(Number.MAX_SAFE_INTEGER).length}));
}finally{await store.close();}
