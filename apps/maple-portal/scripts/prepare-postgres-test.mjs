import pg from 'pg';
import { readFile } from 'node:fs/promises';

const url=process.env.MAPLE_TEST_DATABASE_URL;
if(!url)throw new Error('MAPLE_TEST_DATABASE_URL is required');
const client=new pg.Client({connectionString:url});
await client.connect();
try{
  await client.query('CREATE SCHEMA maple');
  const migration=await readFile(new URL('../../../ops/maple-db/migrations/V1__portal_state.sql',import.meta.url),'utf8');
  await client.query(migration);
}finally{await client.end();}
