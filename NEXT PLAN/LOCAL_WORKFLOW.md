# Windows 로컬 실행과 실증 순서

로그인 이후 자동 실행 포털은 [PORTAL.md](../tools/maple-local-prototype/PORTAL.md)를 먼저 따른다. 포트 18877에서 실행하며 일반 사용자 키/VNC 설정을 요구하지 않는다. 아래는 기존 개인 도구를 직접 실증할 때의 별도 절차다.

## 1. GitHub에서 받기

PowerShell에서 작업할 폴더로 이동한 뒤 저장소 전체를 받는다.

```powershell
git clone --branch next-plan/maple-local-handoff https://github.com/som141/dis.git
cd dis
git status --short
git branch --show-current
git rev-parse HEAD
```

이미 저장소가 있으면 먼저 미커밋 작업을 확인한다. 작업이 있으면 강제 checkout/reset/clean 대신 별도 경로에 clone하거나 worktree를 만든다. 문서가 안 보이면 main 대신 위 브랜치를 받았는지 확인한다.

Codex 앱에서 `dis`를 로컬 프로젝트로 열고 [LOCAL_PROMPT.md](LOCAL_PROMPT.md)의 프롬프트를 전달한다.

## 2. 실행 전 확인

```powershell
docker version
docker compose version
docker info --format '{{.OSType}}'
node --version
```

Docker Desktop이 실행 중이고 OS 유형이 `linux`여야 한다. 호스트 Node는 22 이상이면 비밀번호 생성과 테스트에 사용할 수 있다. Node가 없어도 아래 대체 명령으로 도커를 시작할 수 있다. 최초 이미지 다운로드·빌드 공간과 메모리를 확인한다. 컨테이너 설정은 CPU 2개/메모리 최대 3GB다.

## 3. 도커 시작

```powershell
cd tools\maple-local-prototype
node docker/setup.mjs
docker compose up -d --build
docker compose ps
```

호스트 Node가 없으면 `node docker/setup.mjs` 한 줄만 다음으로 대체한다. Docker가 공유할 수 있는 로컬 폴더에서 실행한다.

```powershell
docker run --rm --mount "type=bind,source=$($PWD.Path),target=/work" --workdir /work node:24-bookworm-slim node docker/setup.mjs
```

개인 PC에서는 `compose.cloud.yaml`을 추가하지 않는다. Docker Desktop이 빌드·실행하므로 처음에는 브라우저 설치에 시간이 걸릴 수 있다.

컨테이너가 `healthy`가 되면 브라우저에서 다음 주소를 연다.

http://127.0.0.1:17900/vnc.html?autoconnect=1&resize=scale

같은 Windows PC에서 실행하므로 SSH 터널은 필요 없다. 원격 화면 비밀번호는 `.docker-secrets/vnc-password` 파일에서 **사용자가 직접 확인**한다. 로컬 Codex가 비밀번호를 채팅·로그·URL에 출력하지 않게 한다.

## 4. 실제 계정 연결

1. 원격 Chromium에서 공식 경매장 탭을 연다.
2. 사용자가 공식 로그인·OTP·추가 인증을 직접 완료한다.
   - 공식 사이트가 캐릭터 선택 화면을 표시하면 **에반이였솜을 선택해 경매장 구매 화면까지 진입**한다. 이 단계에서 공식 검색 버튼을 누를 필요는 없다. 로그인만 마친 상태에서는 경매장 세션이 아직 없어 프로토타입 계정 조회가 실패할 수 있다.
3. 경매장 세션이 준비되면 앱이 자동으로 본인 캐릭터 목록을 조회한다. **연결 다시 확인 · 내 캐릭터 불러오기** 버튼은 수동 확인용으로 남아 있다.
4. 본인 목록의 **에반이였솜** 한 명이 일치하면 자동 선택한다. 이름이 없거나 중복되면 직접 확인하도록 표시하며 다른 캐릭터로 대체하지 않는다. 연결 과정은 조회만 수행하고 검색 횟수를 사용하거나 감시를 시작하지 않는다.
5. 계산할 경우 사용자가 앱 화면의 비밀번호 입력란에 공개 캐릭터 API 키를 직접 넣고 **메모리에 적용**한다.
6. 착용 장비 조회 후 실제 장비·직업·월드·데이터 갱신 시점을 확인한다.

앱이 열렸거나 브라우저가 연결됐다는 표시만으로 넥슨 로그인 성공을 선언하지 않는다. 인증된 계정/캐릭터 응답이 완료 기준이다.

자동 연결은 Docker 서버에서 동작하므로 AI를 계속 실행할 필요가 없다. 미인증 상태에서는 조회 재시도 간격을 5초부터 최대 60초까지 늘린다. 세션 만료 뒤에는 사용자가 공식 화면에서 재로그인하며, 기존 감시는 자동으로 재개하지 않는다. 공식 탭은 열어 둔다. HTTP 426은 반복 재시도하지 않고 버전 확인 상태로 멈춘다.

## 5. 검색·계산 확인

기본 입력은 장갑 / 아케인셰이드 메이지글러브 / 30억 / 17성 / 레전드리 / INT 21%다. 실제 캐릭터·시장에 맞춰 사용자가 조정한다.

- 검색 범위가 21억~39억인지, 월드·부위·이름·옵션이 요청과 일치하는지 확인한다.
- 실제 표시된 매물의 가격·잠재·추옵 등을 공식 탭과 대조한다.
- 실제 착용 장비 대비 점수·개선율·가성비 계산을 확인한다. 지원하지 않는 비교를 0점으로 채우지 않는다.
- 실제 검색은 일일 잔여량을 확인하면서 소수 회만 실행한다. 부하 테스트나 무한 재시도로 한도를 소비하지 않는다.
- 오류가 나면 상태 코드·정제한 오류 코드·요청 경로·단계를 기록한다. 쿠키·Authorization·키·OTP·원본 프로필은 기록하지 않는다.

## 6. 감시 확인

전체 결과가 한 페이지에 들어오는 판매 중 조건에서 감시를 시작한다. 최초 결과를 알림으로 쏟아내지 않는지, 30분 후 새 검색이 한 번 생성되는지, 새 매물만 남기는지 확인한다. 새 매물이 없으면 **조회 성공·신규 0건**으로 기록하며 알림 성공을 꾸미지 않는다.

정기 검색은 조건 하나당 하루 48회이며 최초/수동 검색이 추가된다. 정확한 잔여량은 제공자 응답을 따른다. 현재 알림은 웹 수신함이며 Discord DM은 없다.

Windows PC·Docker Desktop이 꺼지거나 절전하면 감시를 유지할 수 없다. 브라우저 세션이 만료되면 다시 로그인한다. 컨테이너 재시작 시 키와 감시는 재설정한다.

## 7. 검사와 기록

호스트 Node 22 이상이 있으면 다음을 실행한다.

```powershell
npm ci --ignore-scripts
npm test
```

호스트 Node가 22 미만이거나 없으면 프로토타입 폴더에서 Node 24 컨테이너로 검사한다. 아래 명령은 의존성과 빌드 결과를 임시 볼륨에 두므로 Windows용 `node_modules`와 섞이지 않는다.

```powershell
$prototypePath = $PWD.Path
docker run --rm --mount "type=bind,source=$prototypePath,target=/work" --mount type=volume,target=/work/node_modules --mount type=volume,target=/work/dist --workdir /work node:24-bookworm-slim sh -c 'npm ci --ignore-scripts && npm test'
```

검사 코드와 기대 범위는 [VALIDATION.md](../tools/maple-local-prototype/VALIDATION.md)를 읽는다. 브라우저 fixture는 실제 로그인 검사와 별개다. 가상 시계 테스트를 실제 30분 운영 관측으로 쓰지 않는다.

결과에는 환경·커밋·실제 실행 버전·성공 단계·실패 단계·한도·다음 조치를 남긴다. 키/프로필/로그는 `.gitignore`에서 제외하고, 공유할 로그는 최소 필드만 추려 기록한다.

중지는 `docker compose down`이다. `down -v`는 로그인 프로필도 삭제하므로 단순 중지에 사용하지 않는다.
