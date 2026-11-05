# 현재 구현과 검증 범위

2026-10-24 기준. 아래 항목은 같은 브랜치의 소스와 함께 읽는다.

로그인 이후 자동 실행을 위한 [로컬 서비스 포털](../tools/maple-local-prototype/PORTAL.md)을 추가했다. `http://127.0.0.1:18877/`에서 사용자별 공식 로그인 → 최고 레벨 본인 캐릭터·장비 자동 조회 → 장갑 검색·계산 → 30분 감시를 진행한다. 기존 17900 개인 도구와 별도 프로젝트·볼륨을 사용하며 일반 사용자 API 키 입력과 VNC 비밀번호가 없다. 실제 새 포털의 전체 계정 검증은 운영자 서비스 키·사용자 로그인 입력을 기다린다.

## 실제 실행 구조

```mermaid
flowchart LR
    User[Windows 사용자] --> Desktop[Docker Desktop / Linux 컨테이너]
    Desktop --> VNC[noVNC · localhost:17900]
    VNC --> Browser[화면 있는 Chromium · 공식 수동 로그인]
    UI[프로토타입 웹 화면] --> API[Node · localhost:8877]
    API --> Engine[검색 · 교체 계산 · 30분 감시]
    Engine --> Bridge[BrowserPageTransport]
    Bridge -->|공식 페이지 내부 fetch| Browser
    Browser -->|브라우저 세션| Auction[웹 경매장]
    Engine -->|별도 API 키| Character[공개 캐릭터 API]
    Engine --> Inbox[웹 수신함]
```

UI의 8877 포트는 컨테이너 내부에서만 사용한다. Windows 사용자는 17900 원격 화면 안에서 해당 UI를 조작한다. 개인 PC의 브라우저에 확장을 설치하는 방식은 별도 실행 모드로 남아 있다.

## 코드 위치

| 파일 | 역할 |
| --- | --- |
| [compose.yaml](../tools/maple-local-prototype/compose.yaml) | 독립 Compose 프로젝트, localhost 원격 화면, 프로필/캐시 볼륨, CPU·메모리 설정 |
| [Dockerfile](../tools/maple-local-prototype/Dockerfile) · [docker/](../tools/maple-local-prototype/docker/) | Node·Chromium·Xvfb·noVNC 실행, 비밀번호 파일, healthcheck, 프로필 잠금 |
| [server.mjs](../tools/maple-local-prototype/src/server.mjs) | HTTP API, Host·Origin·CSRF 확인, 메모리 키 입력 |
| [docker-server.mjs](../tools/maple-local-prototype/src/docker-server.mjs) | 도커 서버와 브라우저 시작 |
| [auto-connect.mjs](../tools/maple-local-prototype/src/auto-connect.mjs) | 수동 로그인 후 세션 확인·본인 캐릭터 조회·에반이였솜 자동 선택. 조회만 수행하고 검색/감시는 시작하지 않음 |
| [portal-server.mjs](../tools/maple-local-prototype/src/portal-server.mjs) · [portal-browser.mjs](../tools/maple-local-prototype/src/portal-browser.mjs) | 사용자별 전용 브라우저·연결 쿠키·CSRF·공식 로그인 화면/입력 전달 |
| [portal-flow.mjs](../tools/maple-local-prototype/src/portal-flow.mjs) · [portal-store.mjs](../tools/maple-local-prototype/src/portal-store.mjs) | 로그인 이후 장비/검색/계산/감시 자동 실행·사용자별 체크포인트·계정 확인 후 복원 |
| [browser-transport.mjs](../tools/maple-local-prototype/src/browser-transport.mjs) | 공식 탭 안에서 조회·검색, 관찰한 클라이언트 헤더, 계정 변경·401 처리 |
| [domain.mjs](../tools/maple-local-prototype/src/domain.mjs) | 본인 캐릭터 선택, ±30% 가격, 점수/가성비, 한도, 감시·중복 억제 |
| [bridge.mjs](../tools/maple-local-prototype/src/bridge.mjs) | 조회·검색 URL 허용 목록과 별도 Windows 확장 연결 |
| [public/](../tools/maple-local-prototype/public/) | 검색·계산·감시 UI |
| [vendor/UPSTREAM.md](../tools/maple-local-prototype/vendor/UPSTREAM.md) | MIT 공개 구현의 출처·고정 커밋·수정 사항 |
| [test/](../tools/maple-local-prototype/test/) | 단위·통합 테스트, 가상 데이터 전용 브라우저 검증 |

## 데이터와 인증

- 웹 경매장: 원격 Chromium의 공식 페이지에서 사용자가 로그인·OTP를 직접 완료한다. 쿠키를 추출해 Node로 전달하지 않는다.
- 캐릭터 계산: 사용자가 발급한 공개 캐릭터 API 키를 앱 화면에 입력한다. 경매장 인증과 별개다. 키 없이 검색만 가능하다.
- 메모리: API 키·선택 캐릭터·검색 결과·감시·수신함. 재시작하면 초기화한다.
- 볼륨: 공식 로그인 프로필과 공개 캐릭터 데이터 캐시. 세션 유효 기간은 제공자 정책에 따른다.
- 기본 캐릭터: **에반이였솜**, 에반은 마력 축으로 계산한다. 실제 계정 목록에서 조회된 경우에만 선택한다.
- Docker 모드는 경매장 세션이 준비되면 위 본인 캐릭터를 자동 선택한다. 키 없이 연결·매물 검색이 가능하고, AI는 실행 요건이 아니다. 세션 만료 시 사용자가 재로그인하며 감시는 직접 다시 설정한다.

## 구현된 검색·계산·감시

- 아이템 이름 한 개, 부위, 기준 예산, 스타포스·잠재 등급·옵션 조건.
- 예산 B의 70~130%. 30억이면 21억~39억이며 예산 초과 제외를 선택할 수 있다.
- 현재 장비 100점, 교체 점수 `100 + 모델 개선율(%)`, 가성비 `개선율 / 가격(억)`.
- 미지원 비교는 점수를 비워 두며 전체 시장 최고 매물로 표시하지 않는다. 현재 조회 페이지 안에서 정렬한다.
- 단일 감시, 기간 1/3/7일, 30분 간격. 최초 결과는 기준선, 이후 새로운 매물만 웹 수신함에 남긴다.
- 전체 결과가 한 페이지에 담기는 조건만 감시한다. 검색 잔여량을 확인하지 못하거나 소진되면 새 검색을 하지 않는다.
- 계정·브라우저 연결 변경 시 기존 캐릭터/감시를 무효화한다. 자동 구매·판매는 없다.

## 확인한 것과 남은 것

| 항목 | 상태 |
| --- | --- |
| `npm test` | 31개 통과: 기존 24개와 포털 자동 실행·두 사용자 격리·재시작/재연결·검색 의도 기록·운영자 키 수명·접속 제한 7개 |
| 도커 빌드·실행·원격 화면·health | Codex Linux와 Windows Docker Desktop에서 확인 |
| 실제 headed Chromium에서 캐릭터→계산→검색→감시 | API 응답을 가상 route로 대체하여 통과 |
| 30분 감시·중복 억제 | 가상 시계로 확인. 실제 30분 운영 성공 기록은 아님 |
| 컨테이너 교체와 프로필/캐시 볼륨 유지 | 비밀이 아닌 테스트 파일로 확인. 메모리 가상 키 삭제 확인 |
| 프로필 잠금 오류 | 재현·수정. 배타적 flock 후 오래된 Singleton 링크만 정리 |
| 실제 본인 캐릭터 조회 | Windows에서 35명 조회 및 에반이였솜 · 스카니아 · Lv.288 선택 성공. 당시 수동 연결 경로 |
| 로그인 후 자동 연결 | Windows 실제 계정에서 버튼 조작 없이 35명 조회·에반이였솜 자동 선택 성공. 이후 공식 로그인 화면으로 이동했으며 세션 종료 원인은 미확정 |
| 실제 검색·한도 | 판매 중 검색 3회 HTTP 200, 각각 0건, 잔여량 99→98→97. 매물 가격·옵션 대조는 미완료 |
| 실제 장비 정확도·실제 30분 회차 | 키 미설정으로 실제 장비/계산 미완료. 세션 이탈로 실제 감시는 시작되지 않음 |
| Discord 계정 연결·명령·DM·감시 DB 복원 | 미구현 |
| 여러 사용자의 로그인만으로 후속 작업 실행 | 별도 로컬 포털 구현. 독립 Chromium 두 개의 가상 로그인 → 장비/계산/검색/감시 통과. 새 포털의 실제 계정 전체 검증은 대기 |
| 포털 감시·수신함 복원 | 사용자별 JSON 체크포인트, 같은 계정/캐릭터일 때 복원. 가상 검사 통과. 운영 DB·Discord 신원 복구와 별개 |

검증 당시 도커는 Node 24.22.1 / Chromium 155였다. 앞으로 빌드되는 OS 패키지와 브라우저 버전은 달라질 수 있으므로 로컬 결과에 실제 버전을 남긴다.
