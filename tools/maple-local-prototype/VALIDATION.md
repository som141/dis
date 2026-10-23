# 검증 기록 · 2026-10-23

클라우드 Linux의 Node.js 24.19.0, Chromium 151 및 도커의 Node.js 24.22.1, Chromium 155 환경에서 실행했다. Windows의 실제 실행·사용자 로그인 성공 기록은 아니다.

## 통과한 검사

- `npm test`: **18개 통과**. 예산 ±30%, 에반의 마력 축, 실제 장비 교체 계산 경로, 가성비, 미지원 점수, 새 검색 생성, 한도, 30분 감시, 기준선·중복 억제, 만료·중단, 연결/캐릭터 변경, 동시 실행 차단, 키 미노출. 도커 브라우저의 관찰 헤더만 전달, 계정 변경/401 시 POST 차단, 탭 변경 시 기존 연결 무효화, 프록시 유지도 확인했다.
- HTTP 및 WebSocket 경계: 외부 Origin·잘못된 Host·요청 토큰 누락 거절, 페어링한 연결만 수락, 검색·조회 경로만 허용.
- 확장 background 코드를 모의 Chrome API에서 실행해 `credentials: include`, Cookie/Authorization 헤더 미전달, 구매·임의 목적지 차단 확인.
- Chromium 웹 UI 흐름: 가상 계정에서 `에반이였솜` 선택 → API 키 입력 후 입력란 비우기 → 장비 표시 → 검색 → 모델 개선 10%·110점·0.4점/억 표시 → 감시 시작/종료.
- 위 브라우저 흐름은 실제 WebSocket 연결과 검색·계산 어댑터를 사용하되, 확장 peer와 넥슨 응답은 **명시적인 가상 fixture**로 대체했다. 외부 HTTP 호출·JavaScript 오류 없음. 390px 폭 확인.
- 정상 실행 모드 `http://127.0.0.1:8877`: health 200, 실제 확장·키·캐릭터 미연결 상태를 그대로 표시. 가상 결과를 넣지 않음.
- `npm audit --omit=dev`: 알려진 취약점 0건.
- 새 문서 로컬 링크, 확장 manifest, JavaScript 문법 확인.

## 도커 실행 검증

- Compose 프로젝트 `maple-prototype`에서 이미지를 빌드하고 컨테이너를 실제 실행했다. Chromium·Xvfb·noVNC·Node 앱이 실행되며 healthcheck가 통과했다.
- 원격 화면은 `127.0.0.1:17900`에만 공개했다. `/vnc.html` 응답과 WebSocket/RFB 연결, 비밀번호 인증 요구(type 2)를 확인했다. 원본 VNC/CDP 포트는 공개하지 않았다.
- 컨테이너 교체 후 브라우저/캐시 볼륨의 비밀이 아닌 테스트 파일이 유지되고, 임시 가상 API 키·선택 캐릭터·감시가 초기화되며 브라우저가 다시 열리는 것을 확인했다. 실제 로그인 쿠키의 유효 기간을 검증한 것은 아니다.
- 재생성된 컨테이너에서 남은 Chromium SingletonLock으로 실행이 멈추는 문제를 재현하고 수정했다. 고정 hostname과 전용 볼륨의 배타적 flock을 사용하며, 잠금을 확보한 뒤 오래된 프로세스 링크 세 개만 정리한다. 활성 프로필을 두 번째 프로세스가 잠그려는 시도는 거절됐다.
- 컨테이너의 실제 headed Chromium에서 `test/docker-browser-check.mjs` 실행 성공: 공식 페이지/API 응답만 가상 route로 대체하고 쿠키 포함 fetch → 가상 본인 캐릭터 선택 → 계산 키 입력란 삭제 → 장비 조회 → 매물 검색 → 110점·0.4점/억 → 감시 시작 → 가상 시계로 두 번의 30분 경과 → 새 매물 알림·중복 억제 → 감시 종료. 브라우저 JavaScript 오류 없음.
- 도커 UI는 확장 설치 안내 대신 서버 브라우저 로그인 안내를 표시한다. 원격 화면을 닫아도 서버가 감시하는 구조이며 감시/수신함은 여전히 메모리 기반이다.
- 실제 공식 경매장 최상위 페이지는 로딩됐다. 하지만 로그인 SDK `https://signin.nexon.com/sdk/inface.js`는 이 클라우드 프록시에서 CONNECT 403으로 실패했다. 로그인/OTP·인증된 실제 검색은 수행하지 않았다. 정책을 우회하거나 브라우저 TLS 검증을 끄지 않았다.
- 이미지 빌드 중 VFS 저장 방식에서 디스크 부족을 발견했다. 이 프로토타입의 중간 이미지/빌드 캐시를 정리하고 브라우저 설치 뒤의 앱 복사를 한 레이어로 합쳤다.

## 실제 계정에서 확인할 항목

- Windows의 `start-windows.cmd` 실행과 본인 Chrome의 unpacked 확장 설치.
- 본인 Linux 서버의 SSH 터널·원격 화면 비밀번호로 접속하고 공식 탭에서 로그인·OTP를 완료하는 흐름.
- 공식 경매장 로그인·OTP 이후 세션 쿠키를 사용한 검색 응답과 최신 내부 API 호환성.
- `에반이였솜`의 실제 장비·옵션과 모델 결과 대조.
- 30분 뒤 실제 신규 매물 발견, 일일 한도 차감, 세션 만료 후 재로그인.

클라우드 Chromium은 관리자 정책으로 unpacked 확장 설치를 거절했다. 정책을 변경하지 않았다. 따라서 실제 Chrome 확장 설치 성공이나 실제 넥슨 인증·검색 완료를 주장하지 않는다.

기존 Discord 음악·주식 봇, Linux 운영 서버, 배포 파이프라인은 변경하거나 실행하지 않았다.

## Windows Docker Desktop · 2026-10-23 19:04 KST

### 환경과 출발점

- 새 경로 `C:\Users\s0302\OneDrive\문서\Learn database\dis`에 지정 브랜치를 clone했다. 상위 작업 폴더의 기존 미커밋 파일은 그대로 두었다.
- 브랜치: `next-plan/maple-local-handoff`. 기준 커밋: `7b9282533c1c4efd145aba7eca41ed1c536a5813`.
- Windows 10 Education 22H2 / build 19045, Docker Desktop `4.94.0.241994`, Engine `29.8.2`, Compose `v5.5.1`, Linux/amd64, WSL2 kernel `6.6.87.2-microsoft-standard-WSL2`.
- 호스트 Node `v20.10.0`은 앱 요구 버전보다 낮다. 비밀번호 생성만 호스트에서 수행하고, 빌드·검사는 컨테이너 Node `v24.22.1`을 사용했다. Chromium `155.0.8059.79`, Playwright Core `1.64.1`.
- 실행 이미지: `sha256:5540a6d0b2eda9b69a56ee98d66d86b9a7cda7747d8e19c13d652186b0cb84ca`.

### 발견·수정한 Windows 시작 오류

- 시스템 Git의 `core.autocrlf=true`로 두 컨테이너 `.sh` 파일이 CRLF로 checkout됐다. 최초 이미지가 `/app/docker/entrypoint.sh: 2: set: Illegal option -`로 재시작하는 것을 재현했다. Node 24 기본 이미지에서 같은 파일을 `/bin/sh`로 실행해 같은 오류를 확인했다.
- 루트 `.gitattributes`의 기존 `* text=auto`를 유지하면서 `tools/maple-local-prototype/docker/*.sh text eol=lf`를 추가하고 두 작업 파일을 LF로 정규화했다. 스크립트의 Git 내용 변경은 없다. 근거: [Git의 text/eol 속성](https://git-scm.com/docs/gitattributes).
- 재빌드 후 컨테이너 `healthy`, `RestartCount=0`, 두 스크립트 `sh -n` 통과. `127.0.0.1:17900 → 7900`만 공개하며 앱의 8877·VNC/CDP 포트는 호스트에 공개하지 않았다.
- `LOCAL_WORKFLOW.md`에 호스트 Node가 낮을 때의 Node 24 컨테이너 검사 명령을 추가했다. 실제로 실행한 명령이며 `node_modules`와 `dist`는 임시 볼륨으로 격리했다.

### 실제 실행 확인과 가상 검사

| 검사 | Windows PC에서 얻은 결과 | 구분 |
| --- | --- | --- |
| Compose 빌드·시작 | Linux 컨테이너 healthy, CPU 2 / RAM 3GiB / shm 1GiB | 실제 인프라 |
| 앱 `/healthz` | HTTP 200, `ok:true`, `mode:server-browser` | 실제 앱 |
| noVNC | 로컬 원격 화면 열림, 비밀번호 인증 요구 확인 | 실제 UI·인증 전 |
| `npm ci --ignore-scripts && npm test` | Node 24 컨테이너에서 18개 통과, 설치 audit 0건 | 가상 단위·통합 |
| `test/docker-browser-check.mjs` | 실제 headed Chromium에서 캐릭터 선택·키 입력란 삭제·장비·검색·110점·0.4점/억·감시/중복 억제 통과 | **외부 응답은 전부 fixture, 30분은 가상 시계** |
| Compose 설정·Git diff | `docker compose config --quiet`, `git diff --check` 통과 | 설정·정적 확인 |

가상 검사는 별도 임시 브라우저 프로필과 앱 포트 8879에서 수행했다. 정상 앱 8877에는 가상 캐릭터·키·매물·감시를 넣지 않았다. 정상 앱은 캐릭터 0명, 키 미설정, 감시 없음으로 시작했다.

### 외부 접속·참고 자료

- 로컬 Docker의 Node HTTPS 요청으로 `https://signin.nexon.com/sdk/inface.js`는 HTTP 200을 확인했다. 기존 클라우드 프록시 CONNECT 403은 이 요청에서 재현되지 않았다. **로그인 성공 증거는 아니다.**
- 같은 일반 HTTP 요청의 경매장 최상위 페이지는 HTTP 403이었다. 이를 인증된 브라우저/API의 403으로 해석하지 않는다. 실제 Chromium은 실행 중이며 로그인·탐색 상태에서 사용자 입력을 기다린다. TLS 검증을 끄거나 브라우저/사이트 보호를 우회하지 않았다.
- `REFERENCES.md`의 [maple-auction-mcp api.ts](https://github.com/oyc0401/maple-auction-mcp/blob/d01119598479225946c3a7defa9be6f2f1b93295/server/src/auction/api.ts)·mapping.ts, [maple-auction browser.ts](https://github.com/lilllee/maple-auction/blob/3ae0bf31b8ff762739e5af3db60484d861d9232f/src/nexon/browser.ts)·watch/loop.ts를 읽었다. 두 저장소의 고정 커밋과 최신 HEAD 비교는 모두 `identical`, 추가 커밋 0개였다. 외부 코드를 새로 가져오지 않았고 기존 MIT 출처 기록을 유지했다.
- [Docker Windows 공식 문서](https://docs.docker.com/desktop/setup/install/windows-install/), [Playwright persistent context 문서](https://playwright.dev/docs/api/class-browsertype#browser-type-launch-persistent-context), [Nexon 공개 API 문서](https://openapi.nexon.com/ko/game/maplestory/)·[가이드](https://openapi.nexon.com/ko/guide/)·[FAQ](https://openapi.nexon.com/ko/support/faq/)를 확인했다. 공개 [캐릭터 YAML](https://openapi.nexon.com/static/api/maplestory/14_ko_script20261022040014.yaml)은 로컬 HTTPS에서 200이며 해당 파일에서 경매장 경로는 발견되지 않았다. 공개 캐릭터 API 키와 웹 경매장 로그인은 별개로 유지한다.

### 실제 계정 검증 상태

| 단계 | 현재 상태 |
| --- | --- |
| 공식 로그인·OTP | 사용자 직접 입력 대기. 성공 미확인 |
| 본인 캐릭터 목록 → 에반이였솜 | 인증된 응답 미확인. 임의 ID나 fixture로 대체하지 않음 |
| 공개 API 키·실제 착용 장비 | 사용자 키 입력 대기. 직업·월드·원본 데이터 갱신 시점 대조 미완료 |
| 실제 매물·잔여 한도 | 실제 검색 POST 0회. 잔여량·가격·옵션·공식 화면 일치 미검증 |
| 실제 교체 점수·가성비 | 미검증. 독립 모델이며 환산주스탯 템환산과 동일하지 않음 |
| 실제 30분 감시 | 미시작. 실제 회차 시각·한도 차감·신규 0건/신규 알림 여부를 아직 기록할 수 없음 |

사용자에게 noVNC 비밀번호 파일을 직접 확인하고 공식 탭에서 로그인·OTP, 앱 화면에서 API 키 입력을 완료하도록 안내했다. 비밀번호 파일 내용·키·OTP·쿠키를 읽거나 출력하지 않았다. 비밀번호 파일의 Git 제외도 확인했다.

컨테이너는 사용자 입력을 위해 실행 상태로 유지한다. 입력 완료 후 인증된 캐릭터 응답 확인 → 에반이였솜 선택 → 장비 조회 → 잔여량 확인 후 소수 실제 검색 → 공식 매물/모델 대조 → 한 페이지 조건의 실제 30분 회차 관측 순으로 이어간다. `main` 병합·push·운영 배포는 수행하지 않았다.

## Windows 재개 · 2026-10-23 21:01~21:06 KST

- 재개 시 Docker Desktop 엔진이 종료돼 있었다. 기존 컨테이너와 볼륨을 보존한 채 Desktop을 시작하고 `docker compose up -d --no-build`로 재개했다. 같은 이미지에서 `healthy`를 다시 확인했다. 브라우저 프로필의 사용자 로그인 상태는 재개 후 공식 화면에서 사용할 수 있었지만, 이를 장기 세션 수명 보장으로 해석하지 않는다. 메모리 키·캐릭터·감시는 초기화된 상태였다.
- **실제 계정 P0 성공:** 공식 경매장 구매 화면에 에반이였솜이 표시된 뒤 `/api/connect`가 HTTP 200으로 캐릭터 35명을 반환했다. 본인 목록의 **에반이였솜 · 스카니아 · Lv.288**을 `/api/character`로 선택했고 HTTP 200을 확인했다. 가상 응답이나 임의 캐릭터 ID는 사용하지 않았다. 계정 ID·인증 값은 기록하지 않았다.
- 앞선 공식 캐릭터 선택/로그인 진행 중 연결 시도 1회는 `/api/connect` HTTP 422, `PROVIDER_ERROR`, 내부 `NETWORK`로 실패했다. 공식 구매 화면 진입 후 재연결은 성공했다. 실제 검색 POST를 재시도한 것이 아니며 이 구간의 실제 검색 POST는 0회다. 실행 절차에 공식 캐릭터 선택 후 구매 화면까지 진입하는 안내를 보완했다. 인증 완료 전의 탭 열림을 성공으로 취급하지 않는다.
- 공개 API 키가 아직 없어 실제 장비·교체 계산은 대기 상태다. 사용자의 요청으로 공식 Open API 애플리케이션 등록 화면을 열고, 메이플스토리 / 개발 단계 / 서비스명 입력 / 사용자 직접 약관 동의·등록 / 키 직접 복사·앱 메모리에 적용 절차를 안내했다. 키가 표시되는 상세 화면을 읽거나 캡처하지 않는다. 근거: [공식 사전 준비 안내](https://openapi.nexon.com/ko/guide/prepare-in-advance/).
- **P1 실제 매물·한도, P2 실제 장비/계산, P3 실제 30분 감시는 아직 미검증**이다. 앞 절의 키 입력 대기 상태는 이 시점에도 유지되며, 위의 실제 계정 조회 성공은 앞 절 P0 대기 상태를 갱신한다. 기존 18개 테스트와 가상 Chromium 검사는 이전 절의 결과이며 실제 계정 성공 근거와 별개다.
