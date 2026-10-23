# 메이플 로컬 작업 이어받기

2026-10-23 · Windows 로컬 Codex / Docker Desktop 기준.

다음 목표는 **본인 PC에서 프로토타입을 실행하고 `에반이였솜`의 실제 로그인·장비·매물·30분 감시를 검증하는 것**이다. Discord 통합은 그 이후 단계다.

| 순서 | 문서 | 내용 |
| --- | --- | --- |
| 1 | [CURRENT_STATE.md](CURRENT_STATE.md) | 구현된 코드, 검증 결과, 실제 계정에서 남은 항목 |
| 2 | [LOCAL_WORKFLOW.md](LOCAL_WORKFLOW.md) | GitHub에서 받기, Windows 실행, 로그인 이후 검증 순서 |
| 3 | [REFERENCES.md](REFERENCES.md) | GitHub 공개 구현·공식 문서와 참고할 코드 위치 |
| 4 | [NEXT_PLAN.md](NEXT_PLAN.md) | 로컬 실증부터 Discord 통합까지 단계별 완료 조건 |
| 5 | [LOCAL_PROMPT.md](LOCAL_PROMPT.md) | 로컬 Codex에 그대로 붙여 넣을 프롬프트 |

실행 코드는 [tools/maple-local-prototype](../tools/maple-local-prototype/)에 있다. 이 디렉터리만 받아서는 실행되지 않으므로 **저장소 전체를 같은 브랜치로 받는다**.

- 저장소: https://github.com/som141/dis
- 인수인계 브랜치: `next-plan/maple-local-handoff`
- 출발점 커밋: `32df09c84365726d5c110472738f913bdef25fae` 위에 추가한 작업.
- 실행 안내: [프로토타입 README](../tools/maple-local-prototype/README.md), [도커 안내](../tools/maple-local-prototype/DOCKER.md).
- 근거: [검증 기록](../tools/maple-local-prototype/VALIDATION.md), [기능 명세 V3](../docs/internal/analysis/MAPLE_GEAR_FEATURE_SPEC_2026-10-23.md), [기술 명세 V3](../docs/internal/analysis/MAPLE_MARKET_TECH_SPEC_2026-10-23.md).

## 작업 범위

현재 구현은 개인용 프로토타입이다. 계산은 독립 장비 교체 모델이며 환산주스탯의 템환산과 동일하지 않다. 경매장 웹 로그인과 공개 캐릭터 데이터 API 키를 별도로 사용한다. Discord 연결·DM·다중 사용자 계정 연동·감시 DB 복원은 앞으로 구현할 기능이다.

Codex 클라우드에서는 실제 도커 실행과 가상 데이터 검증을 마쳤다. 그러나 `signin.nexon.com` 접속이 프록시 CONNECT 403으로 차단되어 실제 계정 로그인은 완료하지 못했다. Windows 네트워크에서 다시 확인해야 한다.

## GitHub와 운영 배포

[cicd-deploy.yml](../.github/workflows/cicd-deploy.yml)은 **main push마다 운영 봇을 배포**한다. 로컬 검증은 인수인계 브랜치에서 진행한다. 이 디렉터리는 운영 배포 지시가 아니다. 향후 main에 병합하면 문서 변경도 현재 워크플로의 배포 조건에 포함된다는 점을 확인한다.

진행 결과는 날짜·환경·커밋과 함께 `VALIDATION.md`에 남긴다. 실제 API 응답, 가상 fixture, 공개 코드 관찰을 구분해서 기록한다.
