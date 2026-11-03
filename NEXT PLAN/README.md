# 메이플 경매장 프로젝트 문서

이 디렉터리는 메이플 경매장 추천·감시 기능의 시작 배경부터 현재 프로토타입, 로컬 실증, DIS 연동 계획까지 이어서 관리하는 기준 문서다.

현재 목표는 **Windows 로컬 환경에서 `에반이였솜`의 실제 로그인·장비·매물·30분 감시를 검증하는 것**이다. Discord 통합은 실제 계정 검증이 끝난 뒤 진행한다.

## 문서 지도

| 순서 | 문서 | 역할 |
| --- | --- | --- |
| 1 | [PROJECT_RECORD.md](PROJECT_RECORD.md) | 시작한 이유, 개발 원칙, 전체 기능 흐름, 목표 구조, 장기 발전 방향 |
| 2 | [CURRENT_STATE.md](CURRENT_STATE.md) | 현재 구현된 코드, 데이터·인증 경계, 확인한 것과 남은 것 |
| 3 | [NEXT_PLAN.md](NEXT_PLAN.md) | P0 실제 연결부터 P7 다중 사용자 운영까지의 실행 순서와 완료 조건 |
| 4 | [LOCAL_WORKFLOW.md](LOCAL_WORKFLOW.md) | GitHub에서 받기, Windows Docker 실행, 로그인·검색·감시 실증 절차 |
| 5 | [REFERENCES.md](REFERENCES.md) | 넥슨 공식 문서, 참고한 GitHub 코드, 기존 조사 문서 |
| 6 | [LOCAL_PROMPT.md](LOCAL_PROMPT.md) | Windows 로컬 Codex에 그대로 전달할 작업 프롬프트 |
| 7 | [MAPLE_AUCTION_SERVICE_DESIGN.pdf](MAPLE_AUCTION_SERVICE_DESIGN.pdf) | 요구사항 수집·개념설계·화면 설계·논리 설계를 한 문서로 묶은 발표·제출용 PDF |
| 8 | [design-report/MAPLE_AUCTION_SERVICE_DESIGN.md](design-report/MAPLE_AUCTION_SERVICE_DESIGN.md) | PDF 원문과 검증 근거를 수정할 때 사용하는 설계 문서 |

실행 코드와 세부 검증 기록은 다음 위치에 있다.

- [프로토타입 코드와 실행 안내](../tools/maple-local-prototype/)
- [Docker 실행 안내](../tools/maple-local-prototype/DOCKER.md)
- [검증 기록](../tools/maple-local-prototype/VALIDATION.md)
- [기능 명세 V3](../docs/internal/analysis/MAPLE_GEAR_FEATURE_SPEC_2026-10-23.md)
- [기술 명세 V3](../docs/internal/analysis/MAPLE_MARKET_TECH_SPEC_2026-10-23.md)

## 현재 위치

2026-10-23에 개인용 프로토타입을 만들고 다음 항목을 확인했다.

- Windows Chrome 확장 방식과 Linux Docker 브라우저 방식 구현
- 캐릭터 선택, 매물 검색, 장비 교체 점수와 가성비 계산
- 조건 하나를 30분마다 다시 검색하는 감시와 중복 억제
- 자동 테스트 18개 통과
- Docker 빌드·실행·원격 화면·가상 브라우저 전체 흐름 통과

실제 넥슨 계정 로그인과 실제 매물 검색은 아직 확인하지 못했다. Codex 클라우드에서는 `signin.nexon.com` 연결이 프록시 정책으로 차단됐기 때문에 Windows 또는 개인 Linux 서버에서 검증해야 한다. 가상 데이터 성공과 실제 계정 성공을 같은 것으로 기록하지 않는다.

현재 단계는 [NEXT_PLAN.md](NEXT_PLAN.md)의 **P0 · 실제 계정으로 로컬 연결**이다.

## 핵심 결정

- 넥슨 비밀번호·OTP·로그인 쿠키는 공식 로그인 화면 밖에서 받지 않는다.
- 경매장 로그인 세션과 계산용 Nexon Open API 키는 서로 다른 권한으로 관리한다.
- API 키가 필요하더라도 Discord 채팅으로 받지 않고 별도 웹 화면에서 처리한다.
- 경매장 검색은 공개 Open API가 아니라 공식 웹 경매장의 내부 요청을 사용하므로 변경 가능성과 이용 범위를 계속 확인한다.
- 계산 결과는 환산주스탯의 템환산과 같다고 표시하지 않는다.
- AI는 매물을 직접 결정하는 기준이 아니라, 검증된 점수와 필터 결과를 설명하는 보조 기능으로 둔다.
- 자동 구매·판매는 범위에 넣지 않는다.

## 문서 갱신 규칙

- 실제 실행 결과는 날짜·환경·커밋과 함께 [`VALIDATION.md`](../tools/maple-local-prototype/VALIDATION.md)에 남긴다.
- 구현 범위가 바뀌면 `CURRENT_STATE.md`의 표와 코드 위치를 먼저 갱신한다.
- 단계가 완료되거나 순서가 바뀌면 `NEXT_PLAN.md`의 상태와 완료 조건을 갱신한다.
- 기능의 목적, 보안 원칙, 사용자 흐름, 목표 구조가 바뀌면 `PROJECT_RECORD.md`에 결정 이유를 남긴다.
- 외부 인터페이스나 참고 구현이 바뀌면 `REFERENCES.md`에 확인 날짜, 출처, 고정 커밋을 갱신한다.
- 실제 API 응답, 가상 fixture, 공개 코드에서 관찰한 내용을 구분한다. 비밀 값과 원본 인증 응답은 기록하지 않는다.
- 발표용 PDF를 다시 만들 때는 원문, 다이어그램 원본(`.dot`), 스타일을 함께 갱신하고 요구사항 ID와 사용자 트랜잭션 ID가 PDF에도 남았는지 확인한다.

## GitHub와 운영 배포

- 저장소: <https://github.com/som141/dis>
- 작업 브랜치: `next-plan/maple-local-handoff`
- 프로토타입 출발점: `32df09c84365726d5c110472738f913bdef25fae`

[`cicd-deploy.yml`](../.github/workflows/cicd-deploy.yml)은 `main` push마다 운영 봇을 배포한다. 로컬 검증과 문서 갱신은 작업 브랜치에서 진행한다. `main` 병합은 실제 검증과 운영 영향 확인 뒤 별도 작업으로 수행한다.
