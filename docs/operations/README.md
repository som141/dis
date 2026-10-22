# 개발·테스트·운영 안내

작업 위치와 실행 효과를 확인한 뒤 목적에 맞는 문서를 선택한다. 로컬 개발은 저장소 루트, 서버 운영은 기본 `/home/ubuntu/dis-bot/current`를 기준으로 한다.

| 목적 | 문서 | 주요 내용 |
| --- | --- | --- |
| 첫 실행·IDE·환경 변수 | [LOCAL_DEVELOPMENT.md](LOCAL_DEVELOPMENT.md) | 빌드 순서, 설정, 포트, IntelliJ 개별 실행 |
| 테스트·검증 범위 | [CI_TESTING.md](CI_TESTING.md) | 단위/통합 테스트, 결과 위치, 실제 Discord 확인 |
| 배포 | [SERVER_DEPLOY_SCRIPT.md](SERVER_DEPLOY_SCRIPT.md) | main push, 이미지 전달, Secrets, release 전환 |
| 장애 대응 | [OPERATIONS_RUNBOOK.md](OPERATIONS_RUNBOOK.md) | 상태·로그·무음·시세·DLQ 점검 |
| 관측성 구성·향후 확장 | [OBSERVABILITY_PLAN.md](OBSERVABILITY_PLAN.md) | 메트릭·로그·알림과 확장 후보 |

실제 스크립트는 [ops/](../../ops/README.md), 대시보드 시작은 [관측성 안내](../../ops/observability/README.md)를 본다. 배포와 DLQ 재처리는 서비스를 바꾸는 작업이고, health·로그 조회는 상태 확인이다.

[전체 문서](../README.md) · [문서 작성 기준](../DOCUMENTATION_GUIDE.md)
