import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync, readdirSync } from 'node:fs';
import { resolve, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const root=resolve(fileURLToPath(new URL('../../',import.meta.url)));
const findings=[];
const fail=(name,detail)=>findings.push({priority:'P0',name,detail});
const note=(name,detail)=>findings.push({priority:'INFO',name,detail});

const history=execFileSync('git',['log','--all','--full-history','--name-only','--pretty=format:',
  '--','.env','.env.*','**/.env','**/.env.*'],{cwd:root,encoding:'utf8'});
const trackedSecrets=history.split(/\r?\n/).map(s=>s.trim()).filter(s=>s && !/\.env\.example$/i.test(s));
if(trackedSecrets.length)fail('Git 비밀 파일 이력',`${new Set(trackedSecrets).size}개 경로 확인 필요`);
else note('Git 비밀 파일 이력','이름 기준 .env 파일 이력 없음');

const envPath=join(root,'.env');
const config={};
if(existsSync(envPath))for(const line of readFileSync(envPath,'utf8').split(/\r?\n/)){
  const match=/^([A-Z][A-Z0-9_]*)=(.*)$/.exec(line.trim());
  if(match)config[match[1]]=match[2].replace(/^(['"])(.*)\1$/,'$2');
}
if(config.MAPLE_DISCORD_ENABLED?.toLowerCase()==='true'&&!config.MAPLE_POSTGRES_PASSWORD)
  fail('운영 DB 비밀번호','MAPLE_POSTGRES_PASSWORD 설정 필요');

const assets=[join(root,'apps/maple-portal/public'),join(root,'apps/maple-portal/dist')];
const content=[];
for(const dir of assets)if(existsSync(dir))for(const entry of readdirSync(dir,{withFileTypes:true})){
  if(entry.isFile()&&/\.(?:js|mjs|html|css|json)$/.test(entry.name))content.push(readFileSync(join(dir,entry.name),'utf8'));
}
const serviceKeyPath=join(root,'apps/maple-portal/.docker-secrets/nexon-service-key');
const secrets={...Object.fromEntries(Object.entries(config).filter(([name])=>/TOKEN|SECRET|PASSWORD|SIGNING_KEY|INTERNAL_KEY/.test(name)))};
if(existsSync(serviceKeyPath))secrets.NEXON_SERVICE_KEY=readFileSync(serviceKeyPath,'utf8').trim();
for(const [name,value] of Object.entries(secrets)){
  if(value?.length>=12 && content.some(text=>text.includes(value)))fail('클라이언트 비밀값 유출',`${name} 값이 공개 파일에 포함됨`);
}
if(!Object.keys(secrets).length)note('클라이언트 비밀값 유출','로컬 시크릿이 없어 값 대조 생략');
else note('클라이언트 비밀값 유출','보유 시크릿과 공개 파일 대조 완료');

const nginx=readFileSync(join(root,'ops/maple-edge/nginx.conf'),'utf8');
if(!nginx.includes('access_log /var/log/nginx/access.log maple_safe;')||!nginx.includes('$request_method $uri $server_protocol'))
  fail('OAuth URL 접근 로그','쿼리 문자열 제외 로그 형식 확인 필요');
else note('OAuth URL 접근 로그','Nginx 접근 로그에 쿼리 문자열 제외');

for(const finding of findings)console.log(`${finding.priority} ${finding.name}: ${finding.detail}`);
if(findings.some(finding=>finding.priority==='P0'))process.exitCode=1;
