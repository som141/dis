# DIS 메이플 마켓 포털

Discord `/maple connect`로 연결하는 메이플 경매장 검색·장비 비교·감시 서비스다. Spring Gateway가 Discord OAuth와 DM을 처리하고, 이 Node 앱은 사용자별 Chromium 세션과 검색을 처리한다. 운영 상태는 기존 DIS PostgreSQL 인스턴스의 `maple` 스키마에 저장한다.

## 사용

1. Discord에서 `/maple connect`를 실행하고 공식 Discord 인증을 완료한다.
2. 포털에 표시된 공식 넥슨 로그인 화면에서 본인 계정으로 로그인하고 캐릭터를 고른다.
3. 아이템·예산·잠재 조건을 선택해 한 번 조회하거나 감시를 시작한다. 로그인만으로 경매장 검색 요청을 보내지 않는다.
4. 목록에서 상세 스탯과 가격·비교 결과를 확인한다. 감시한 검색의 가성비 상위 10위에 신규 진입하거나 순위가 바뀐 매물만 본인에게 DM으로 보낸다.

화면의 세부 옵션과 계산 범위는 [포털 사용법](PORTAL.md), 데이터 구조는 [PostgreSQL 스키마](../../docs/reference/MAPLE_PORTAL_SCHEMA.md)를 따른다. 성능 비교는 [독립 데미지 모델](DAMAGE_MODEL.md)이며 환산주스탯 결과를 보장하지 않는다.

## 장비 도감과 최신 이름

- `npm run catalog:refresh`는 넥슨 공식 경매장 자동완성 이름·아이콘 목록을 내려받아 구조와 미트라·에테르넬 이름을 검사한 뒤 갱신한다. 2026-11-08 확인 결과 공식 이름 24,219개였고 저장된 파일과 같았다.
- 부위·요구 레벨·직업이 검증된 도감은 KMS 389 분류 자료에 공식 업데이트로 확인한 에테르넬 15종과 미트라의 분노 5종을 보충한다. 미트라는 엠블렘이며 전사·마법사·궁수·도적·해적을 각각 선택할 수 있다.
- 도감에 없는 공식 이름은 이름을 2글자 이상 입력하면 아이콘의 아이템 ID로 부위를 **추정**해 검색 후보로 보여준다. 후보에는 캐시 장비나 거래 불가 장비가 섞일 수 있다. 후보를 선택한 경우 요구 레벨을 직접 확인해 입력해야 하며, 직업·거래 가능 여부도 경매장 결과로 확인해야 한다. 이 후보에는 자동으로 검증된 장비 ID나 요구 레벨을 부여하지 않는다.
- 현재 게임 클라이언트 1.2.420의 전체 장비 메타데이터 공개본은 확보하지 못했다. 따라서 모든 장비의 부위·레벨·착용 조건을 최신 버전으로 전수 검증했다고 표시하지 않는다.

## 실행 경로

- DIS 전체 서비스: 저장소 루트의 [Compose](../../docker-compose.yml)에 `maple` 프로필을 사용한다. 설정·백업·배포 순서는 [운영 가이드](../../docs/operations/MAPLE_DISCORD_SETUP.md)를 따른다.
- 단독 로컬 체험: [compose.portal.yaml](compose.portal.yaml)로 실행한다. 이 모드는 기존 JSON 체크포인트를 사용하며 Discord와 운영 DB에 연결하지 않는다.
- 이전 개인용 Chrome 확장/VNC 방식: [별도 실행 안내](LOCAL_LEGACY.md)와 [Docker 안내](DOCKER.md)에 기록을 보존했다. 운영 포털의 사용 절차가 아니다.

## 저장·보안 경계

- 운영 PostgreSQL의 `maple` 스키마에는 Discord 사용자 ID, 쿠키의 SHA-256 해시, 캐릭터별 검색 조건·작업, 감시 중복 방지 ID, DM 발송함과 감사 이벤트가 들어간다. 평문 포털 토큰과 넥슨 로그인 암호는 저장하지 않는다.
- 사용자별 Chromium 프로필과 공식 로그인 쿠키는 `maple-portal-data` 볼륨에 남는다. DB 이전은 이 브라우저 프로필을 다른 사용자에게 재사용하지 않는다.
- 단일 포털 인스턴스가 PostgreSQL advisory lock을 보유한다. 사용자 수 상한은 `MAPLE_PORTAL_USERS`로 정하며, 현재 구성은 여러 포털 복제본을 동시에 실행하는 구조가 아니다.
- 운영 배포에서는 `MAPLE_REQUIRE_POSTGRES=true`로 PostgreSQL 없이 시작하지 못하게 한다. 기존 JSON은 이전기가 읽기만 하고, 이전 후 원본은 백업으로 보존한다.

## 개발 검증

```bash
cd apps/maple-portal
npm ci --ignore-scripts
npm test
```

PostgreSQL 통합 검증은 [CI 워크플로](../../.github/workflows/maple-postgres-test.yml)가 임시 PostgreSQL 16에서 스키마·JSON 이전·재시작 복원을 검사한다. 로컬 Docker가 없으면 해당 통합 테스트는 건너뛰며 통과로 기록하지 않는다.
