# 분석·협업 기록

작성 당시의 코드 분석과 작업 과정을 보존한다. 이곳의 구조·실행 명령·완료 기록은 현재 커밋의 코드 및 [기준 문서](../../reference/README.md)와 구분해서 읽는다.

| 문서 | 목적 |
| --- | --- |
| [CODEBASE_ANALYSIS.md](CODEBASE_ANALYSIS.md) | 당시 구조·의존성·정리 방향을 이해하기 |
| [CODEX_WORK_LOG.md](CODEX_WORK_LOG.md) | 작업 순서와 과거 판단의 근거 찾기 |
| [CODEX_USAGE_PLAYBOOK.md](CODEX_USAGE_PLAYBOOK.md) | Codex 협업·검증·작업 지시 방식 참고 |
| [메이플 API 기능 조사·제안 (2026-10-22)](MAPLE_API_FEATURE_RESEARCH_2026-10-22.md) | 공개 사례·API 구조에 근거한 기능 후보 5개와 구현 메커니즘 검토. 미구현 제안 |
| [인벤 조사 범위·경매장 AI 사례 (2026-10-23)](MAPLE_AUCTION_AI_CASES_2026-10-23.md) | 경매장 검색·AI 화면 분석·장비 추천 사례와 코드 확인. 인벤 원문 조사는 접속 제한으로 미완료 |
| [환산주스탯 외부 API 확인 (2026-10-23)](MAPLESCOUTER_API_CHECK_2026-10-23.md) | 공식 약관 제15조 확인. 공개 개발자 API는 미확인, 직접 계산 연동은 운영자 협의 필요 |
| [템환산 계산식 공개 범위 조사 (2026-10-23)](MAPLESCOUTER_GEAR_FORMULA_CHECK_2026-10-23.md) | 일반 환산·헥사환산과 아이템스코어 구분. 템환산 전체 산식·계수표는 미확인 |
| [메이플 매물 검색·기간 알림 기능 명세 V3 (2026-10-23)](MAPLE_GEAR_FEATURE_SPEC_2026-10-23.md) | `/아이템 캐릭터` 공개 조회, 선택적 넥슨 로그인·UID/토큰 연결, 아이템 다중 선택·±30% 검색·기간 DM 알림. 봇 미구현 |
| [매물 검색·알림 기술 명세·아키텍처 V3 (2026-10-23)](MAPLE_MARKET_TECH_SPEC_2026-10-23.md) | 서비스 키와 Open ID grant 분리, UID·KMS·토큰 회전·DB·Provider·감시·Outbox·DM 계약 |
| [경매장 API·개인 키 위탁·체크박스 확인 (2026-10-23)](MAPLE_MARKET_API_CHECK_2026-10-23.md) | 공식 56개 경로에 경매장 미확인, 웹 로그인 검색과 키 인증 구분, 위탁 예외 확인 필요, JDA 6.7.0 체크박스 |
| [넥슨 로그인·닉네임 공개 조회 확인 (2026-10-23)](MAPLE_OPENID_LOGIN_CHECK_2026-10-23.md) | 공식 가이드 데이터의 Open ID·UID·scope·프렌즈/검수·토큰 갱신과 `/아이템`의 서비스 키 조회 구분 |
| [웹 경매장 검색 API 상세 (2026-10-23)](MAPLE_AUCTION_SEARCH_API_REFERENCE_2026-10-23.md) | 검색 조건·응답·인증, 공식 화면의 하루 100회/자정 초기화, 공개 구현의 검색 키 재사용과 감시 검증 과제 |
| [Windows 종료 후 Linux 경매장 감시 가능성 (2026-10-23)](MAPLE_LINUX_AUCTION_FEASIBILITY_2026-10-23.md) | 공식 API·Open ID 범위, 개인용 도커 브라우저 실행·모의 검증과 실제 인증 검증 과제 |

새 구현 상태는 이 과거 본문을 덮어쓰기보다 현재 문서에 반영한다. 실행 절차는 [개발·운영 안내](../../operations/README.md)를 사용한다.

[내부 기록 목록](../README.md) · [전체 문서](../../README.md)
