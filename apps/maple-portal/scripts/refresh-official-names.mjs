import {readFile,writeFile} from 'node:fs/promises';
import {createHash} from 'node:crypto';

const source='https://mskr-web.dn.nexoncdn.co.kr/autocomplete/snippets.json';
const target=new URL('../public/data/official-snippets.json',import.meta.url);
const response=await fetch(source,{signal:AbortSignal.timeout(30_000)});
if(!response.ok)throw new Error(`Official autocomplete request failed: HTTP ${response.status}`);
const bytes=Buffer.from(await response.arrayBuffer());
const entries=JSON.parse(bytes.toString('utf8'));
if(!Array.isArray(entries)||entries.length<20_000)throw new Error('Official autocomplete is unexpectedly small');
const names=new Set();
for(const entry of entries){
  if(typeof entry?.nameOrigin!=='string'||typeof entry.thumbnail!=='string'||typeof entry.encryptedItemId!=='string')throw new Error('Unexpected official autocomplete row');
  names.add(entry.nameOrigin);
}
for(const required of ['미트라의 분노 : 전사','미트라의 분노 : 마법사','미트라의 분노 : 궁수','미트라의 분노 : 도적','미트라의 분노 : 해적','에테르넬 메이지글러브']){
  if(!names.has(required))throw new Error(`Expected equipment is missing from official autocomplete: ${required}`);
}
const previous=await readFile(target);
if(!bytes.equals(previous))await writeFile(target,bytes);
const hash=createHash('sha256').update(bytes).digest('hex');
console.log(JSON.stringify({source,count:entries.length,sha256:hash,changed:!bytes.equals(previous)}));
