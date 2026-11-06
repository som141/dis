import { existsSync, readFileSync, readdirSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root=resolve(fileURLToPath(new URL('../../',import.meta.url)));
const archive=join(root,'docs/archive/maple-local-handoff-2026-10');
const files=[
  'README.md','docs/README.md','docs/operations/MAPLE_DISCORD_SETUP.md',
  'docs/operations/MAPLE_SECURITY_PREFLIGHT.md','docs/reference/MAPLE_PORTAL_SCHEMA.md',
  'apps/README.md','apps/maple-portal/README.md','apps/maple-portal/PORTAL.md',
  ...readdirSync(archive).filter(name=>name.endsWith('.md')).map(name=>`docs/archive/maple-local-handoff-2026-10/${name}`),
];
let missing=0;
for(const relative of files){
  const source=join(root,relative),text=readFileSync(source,'utf8');
  for(const match of text.matchAll(/\]\((?:<([^>]+)>|([^\s)]+))(?:\s+"[^"]*")?\)/g)){
    const target=(match[1]||match[2]).split('#')[0].split('?')[0];
    if(!target||/^[a-z][a-z0-9+.-]*:/i.test(target))continue;
    if(!existsSync(resolve(dirname(source),decodeURIComponent(target)))){
      console.error(`${relative}: missing ${target}`);missing++;
    }
  }
}
console.log(`Checked ${files.length} Markdown files; broken local links: ${missing}`);
if(missing)process.exitCode=1;
