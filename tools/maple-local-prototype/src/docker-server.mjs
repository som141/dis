import { mkdir } from 'node:fs/promises';
import { createApp } from './server.mjs';
import { BrowserPageTransport } from './browser-transport.mjs';

const profileDir = process.env.MAPLE_BROWSER_PROFILE || '/home/node/browser-profile';
await mkdir(profileDir, { recursive:true });
const wire = new BrowserPageTransport();
const app = createApp({ mode:'server-browser', wireFactory:() => wire });
await app.listen();
for (const signal of ['SIGINT','SIGTERM']) process.once(signal, () => { void app.close().then(() => process.exit(0)); });
try {
  await wire.start('http://127.0.0.1:8877', { profileDir });
  console.log('Maple Docker desktop ready. Complete Nexon login manually in the official browser tab.');
} catch {
  console.error('Browser startup failed. Check desktop services; no credentials or provider responses are logged.');
  await app.close(); process.exit(1);
}
