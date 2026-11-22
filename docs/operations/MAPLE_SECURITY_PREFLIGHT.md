# 메이플 포털 배포 전 보안 확인

저장소 루트에서 `node ops/maple-db/preflight.mjs`를 실행한다. 이 명령은 [공통 preflight](../../ops/security/preflight.mjs)를 실행한다. 비밀값을 출력하지 않고 `.env` 계열 파일명 이력, 보유 키와 공개 번들·Git 추적 텍스트의 일치 여부, Nginx access log 설정을 확인한다. 중첩 번들과 source map도 검사한다. 미보유 키·과거 파일 콘텐츠·이미지 안의 비밀값은 이 검사로 보장하지 않는다. `SKIP`은 통과가 아니다. 운영 설정은 `node ops/security/preflight.mjs --production --env-file <환경파일>`로 별도 검사한다. [전체 보안 경계](SECURITY.md)를 함께 따른다.

| 우선 | 점검 | 가장 빠른 확인법 |
| --- | --- | --- |
| P0 | Git 이력의 `.env` | `node ops/maple-db/preflight.mjs`; 발견 시 키 회전과 이력 처리 |
| P0 | 클라이언트 번들의 비밀값 | `npm run build` 뒤 preflight 실행. 비밀값을 터미널에 출력하지 않음 |
| P0 | 익명 사용자 데이터 | 쿠키 없이 `/api/state`가 401인지 확인 |
| P0 | 서버 인증 게이트 | HTML 화면이 아닌 보호 API를 직접 호출해 401 확인 |
| P1 | 타 사용자 작업 ID | 두 Discord 사용자로 다른 작업 ID를 호출해 404 확인 |
| 해당 없음 | 결제·웹훅 | 메이플 포털에는 결제/웹훅 수신 경로가 없음. 새로 추가되면 서명 검증 필요 |
| 해당 없음 | 파일 업로드 | 메이플 포털에 업로드 경로가 없음. 새로 추가되면 타입·크기 검사 필요 |
| 해당 없음 | AI 호출 비용 | 메이플 포털에 LLM 호출이 없음. 경매장 조회 한도는 별도 확인 |
| P1 | 세션 만료·회전 | 30일 만료된 포털 토큰 거절, 새 Discord 연결 시 이전 토큰 거절 확인 |
| P1 | 공개 포트·CORS | `node --test ops/security/config.test.mjs`, `docker compose --profile maple config --quiet`, 방화벽, 외부에서 5432·6379·5672·8081 접근 차단 확인 |
| P0 | 오류·접근 로그 | OAuth 콜백의 `code`·`state`와 연결 `ticket`이 Nginx access/error log·ngrok 캡처·앱 로그에 남지 않는지 값 출력 없이 확인 |
| P2 | 감사 이벤트 | DB `maple.audit_event`에 연결·캐릭터 변경·검색·삭제·중지가 남고 비밀값이 없는지 확인 |

현재 Node 포털은 세션·Origin·CSRF와 사용자별 작업 소유권을 검사한다. PostgreSQL 이전 후에도 [통합 테스트](../../.github/workflows/maple-postgres-test.yml)와 운영 환경 두 사용자 검증을 모두 수행한다. ngrok Traffic Inspector의 캡처 설정과 로그 보관은 ngrok 계정에서 별도로 확인한다.
