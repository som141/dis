import { mkdir, open } from 'node:fs/promises';
import { randomBytes } from 'node:crypto';
const directory = new URL('../.docker-secrets/', import.meta.url);
await mkdir(directory, { recursive:true, mode:0o700 });
try {
  const file = await open(new URL('vnc-password', directory), 'wx', 0o600);
  // VNC authentication uses eight characters. SSH/loopback protects transport.
  await file.writeFile(randomBytes(6).toString('base64') + '\n'); await file.close();
  console.log('Desktop password created in .docker-secrets/vnc-password (Git/build excluded).');
} catch (e) { if (e.code !== 'EEXIST') throw e; console.log('Existing desktop password preserved.'); }
