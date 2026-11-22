import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync, readdirSync, lstatSync } from 'node:fs';
import { resolve, join, relative } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(fileURLToPath(new URL('../../', import.meta.url)));
const textFile = /\.(?:md|js|mjs|cjs|ts|java|html|css|json|map|ya?ml|properties|sh|svg|txt|example)$/i;
const placeholder = value => /^(?:replace[-_]|your[-_]|https:\/\/example\.invalid)/i.test(value);

export function secretCandidates(config) {
  return Object.fromEntries(Object.entries(config).filter(([name, value]) =>
    /TOKEN|SECRET|PASSWORD|(?:^|_)KEY(?:_|$)|WEBHOOK/.test(name) &&
    typeof value === 'string' && value.length >= 12 && !placeholder(value)));
}

export function collectTextFiles(directory) {
  if (!existsSync(directory)) return [];
  return readdirSync(directory, { withFileTypes: true }).flatMap(entry => {
    const path = join(directory, entry.name);
    if (entry.isSymbolicLink()) return [];
    if (entry.isDirectory()) return collectTextFiles(path);
    return entry.isFile() && textFile.test(entry.name) ? [path] : [];
  });
}

export function scanExposure(secrets, files) {
  const findings = [];
  for (const file of files) {
    const content = readFileSync(file, 'utf8');
    for (const [name, value] of Object.entries(secrets)) {
      if (content.includes(value)) findings.push({ name, file });
    }
  }
  return findings; // Do not return a matched secret or source line.
}

export function runPreflight({ repoRoot = root, envFile = join(repoRoot, '.env'), production = false } = {}) {
  const findings = [];
  const add = (priority, name, detail) => findings.push({ priority, name, detail });
  const git = args => execFileSync('git', args, { cwd: repoRoot, encoding: 'utf8' });
  const history = git(['log', '--all', '--full-history', '--name-only', '--pretty=format:', '--',
    '.env', '.env.*', '**/.env', '**/.env.*', '*.env', '**/*.env']);
  const secretPaths = new Set(history.split(/\r?\n/).filter(s => s && !/(?:\.env\.example|\.example\.env)$/i.test(s)));
  add(secretPaths.size ? 'P0' : 'PASS', 'Git 비밀 파일명 이력', secretPaths.size ? `${secretPaths.size}개 경로 확인 필요` : '.env 계열 파일명 이력 없음 (전체 콘텐츠 비밀 탐지는 아님)');
  const config = {};
  if (existsSync(envFile)) {
    for (const line of readFileSync(envFile, 'utf8').split(/\r?\n/)) {
      const match = /^([A-Z][A-Z0-9_]*)=(.*)$/.exec(line.trim());
      if (match) config[match[1]] = match[2].replace(/^(['"])(.*)\1$/, '$2');
    }
  } else add(production ? 'P0' : 'SKIP', '설정 파일', '선택한 환경 파일 없음');
  if (production) {
    for (const name of ['INFRA_BIND', 'OBSERVABILITY_BIND', 'MAPLE_EDGE_BIND']) {
      if (config[name] && !['127.0.0.1', '::1'].includes(config[name])) add('P0', '관리 포트 바인딩', `${name}: loopback 아닌 설정`);
    }
    const passwordNames = ['RABBITMQ_PASSWORD', 'POSTGRES_PASSWORD'];
    if (config.OBSERVABILITY_ENABLED?.toLowerCase() === 'true') passwordNames.push('GRAFANA_ADMIN_PASSWORD');
    if (config.MAPLE_DISCORD_ENABLED?.toLowerCase() === 'true') passwordNames.push('MAPLE_POSTGRES_PASSWORD');
    for (const name of passwordNames) {
      if (!config[name] || config[name].length < 16 || placeholder(config[name])) add('P0', '운영 비밀번호', `${name}: 별도 임의 값 16자 이상 필요`);
    }
    if (config.GRAFANA_ANONYMOUS_ENABLED?.toLowerCase() === 'true') add('P0', '대시보드 인증', 'Grafana 익명 접근 활성화');
  }
  if (config.MAPLE_DISCORD_ENABLED?.toLowerCase() === 'true') {
    if (!config.MAPLE_POSTGRES_PASSWORD) add('P0', '운영 DB 비밀번호', 'MAPLE_POSTGRES_PASSWORD 설정 필요');
    if (production) {
      for (const name of ['MAPLE_LINK_SIGNING_KEY', 'MAPLE_DISCORD_INTERNAL_KEY']) {
        if (!config[name] || config[name].length < 32 || placeholder(config[name])) add('P0', '메이플 내부 인증', `${name}: 별도 임의 값 32자 이상 필요`);
      }
      if (config.MAPLE_LINK_SIGNING_KEY && config.MAPLE_LINK_SIGNING_KEY === config.MAPLE_DISCORD_INTERNAL_KEY) add('P0', '메이플 내부 인증', '서명 키와 내부 키가 같음');
      if (!/^https:\/\/[^/?#]+\/?$/.test(config.MAPLE_PUBLIC_BASE_URL || '')) add('P0', '공개 원점', 'HTTPS 원점 설정 필요');
      if (config.MAPLE_DISCORD_REDIRECT_URI !== `${(config.MAPLE_PUBLIC_BASE_URL || '').replace(/\/$/, '')}/maple/oauth/callback`) add('P0', 'OAuth callback', '공개 원점과 callback 경로 불일치');
    }
  }
  const publicDir = join(repoRoot, 'apps/maple-portal/public');
  const distDir = join(repoRoot, 'apps/maple-portal/dist');
  const distFiles = collectTextFiles(distDir);
  if (!distFiles.length) add('SKIP', '빌드 번들', 'dist 없음: npm run build 후 다시 확인');
  const tracked = git(['ls-files', '-z']).split('\0').filter(name => textFile.test(name))
    .map(name => join(repoRoot, name)).filter(path => existsSync(path) && !lstatSync(path).isSymbolicLink());
  const files = [...new Set([...collectTextFiles(publicDir), ...distFiles, ...tracked])];
  const secrets = secretCandidates(config);
  const serviceKey = join(repoRoot, 'apps/maple-portal/.docker-secrets/nexon-service-key');
  if (existsSync(serviceKey)) {
    const value = readFileSync(serviceKey, 'utf8').trim();
    if (value.length >= 12 && !placeholder(value)) secrets.NEXON_SERVICE_KEY = value;
  }
  if (!Object.keys(secrets).length) add('SKIP', '보유 비밀값 대조', '대조 가능한 비밀값 없음');
  else {
    const exposures = scanExposure(secrets, files);
    for (const exposure of exposures) add('P0', '보유 비밀값 노출', `${exposure.name}: ${relative(repoRoot, exposure.file)}`);
    if (!exposures.length) add('PASS', '보유 비밀값 대조', `공개 번들·Git 추적 텍스트 ${files.length}개에 일치 없음 (미보유 키·과거 콘텐츠 제외)`);
  }
  const nginx = readFileSync(join(repoRoot, 'ops/maple-edge/nginx.conf'), 'utf8');
  const safeAccess = nginx.includes('access_log /var/log/nginx/access.log maple_safe;') && nginx.includes('$request_method $uri $server_protocol');
  add(safeAccess ? 'PASS' : 'P0', 'OAuth 접근 로그 설정', safeAccess ? 'Nginx access log는 query 제외. error log·외부 터널 로그는 별도 점검' : '쿼리 제외 access log 형식 확인 필요');
  add('INFO', '검사 범위', '운영 서버·방화벽·실계정 인증·전체 Git 콘텐츠 이력의 보안 검증을 대신하지 않음');
  return findings;
}

export function main() {
  const envIndex = process.argv.indexOf('--env-file');
  if (envIndex !== -1 && !process.argv[envIndex + 1]) throw new Error('--env-file requires a path');
  const findings = runPreflight({ envFile: envIndex === -1 ? undefined : resolve(process.argv[envIndex + 1]), production: process.argv.includes('--production') });
  for (const finding of findings) console.log(`${finding.priority} ${finding.name}: ${finding.detail}`);
  if (findings.some(finding => finding.priority === 'P0')) process.exitCode = 1;
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) main();
