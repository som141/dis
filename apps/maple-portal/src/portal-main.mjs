import { createPortal } from './portal-server.mjs';

const port = Number(process.env.MAPLE_PORTAL_PORT || 8878);
const limit = Number(process.env.MAPLE_PORTAL_USERS || 2);
const database = process.env.MAPLE_POSTGRES_HOST ? {
  host:process.env.MAPLE_POSTGRES_HOST,
  port:Number(process.env.MAPLE_POSTGRES_PORT || 5432),
  database:process.env.MAPLE_POSTGRES_DB || 'stock',
  user:process.env.MAPLE_POSTGRES_USER || 'maple_app',
  password:process.env.MAPLE_POSTGRES_PASSWORD || '',
  application_name:'maple-portal',
} : null;
if(process.env.MAPLE_REQUIRE_POSTGRES==='true'&&!database)throw new Error('PostgreSQL is required for this deployment');
if(database&&!database.password)throw new Error('MAPLE_POSTGRES_PASSWORD is required');
if (!Number.isInteger(port) || port < 1024 || port > 65535 || !Number.isInteger(limit) || limit < 1 || limit > 1000) throw new Error('Invalid portal settings');
if(process.env.MAPLE_DISCORD_MODE==='true'&&(process.env.MAPLE_DISCORD_INTERNAL_KEY||'').length<32)throw new Error('MAPLE_DISCORD_INTERNAL_KEY must be at least 32 characters');
const app = await createPortal({ port, host:process.env.MAPLE_PORTAL_BIND || '127.0.0.1',
  origin:process.env.MAPLE_PORTAL_ORIGIN || `http://127.0.0.1:${port}`,
  dataDir:process.env.MAPLE_PORTAL_DATA || '/home/node/portal-data',
  keyFile:process.env.MAPLE_SERVICE_KEY_FILE, limit,
  discordMode:process.env.MAPLE_DISCORD_MODE==='true',internalKey:process.env.MAPLE_DISCORD_INTERNAL_KEY||'', database,
});
await app.listen();
console.log('Maple portal ready. Each visitor has a private login browser. Credentials and user input are never logged.');
for (const signal of ['SIGTERM','SIGINT']) process.once(signal,() => { void app.close().then(() => process.exit(0)); });
