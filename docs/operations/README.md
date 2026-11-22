# 개발·테스트·운영 안내

작업 위치와 실행 효과를 확인한 뒤 목적에 맞는 문서를 선택한다. 로컬 개발은 저장소 루트, 서버 운영은 기본 `/home/ubuntu/dis-bot/current`를 기준으로 한다.

| 목적 | 문서 | 주요 내용 |
| --- | --- | --- |
| 첫 실행·IDE·환경 변수 | [LOCAL_DEVELOPMENT.md](LOCAL_DEVELOPMENT.md) | 빌드 순서, 설정, 포트, IntelliJ 개별 실행 |
| 테스트·검증 범위 | [CI_TESTING.md](CI_TESTING.md) | 단위/통합 테스트, 결과 위치, 실제 Discord 확인 |
| 현재 배포 | [OUTBOUND_ONLY_DEPLOY.md](OUTBOUND_ONLY_DEPLOY.md) | CI 릴리스 게시, 서버 timer pull·검증·release 전환 |
| 메이플 운영 | [MAPLE_DISCORD_SETUP.md](MAPLE_DISCORD_SETUP.md) | HTTPS·OAuth·사용자 한도·DB·서비스 키 |
| 메이플 보안 점검 | [MAPLE_SECURITY_PREFLIGHT.md](MAPLE_SECURITY_PREFLIGHT.md) | 세션·소유권·시크릿·로그 점검과 검증 경계 |
| 과거 SSH 배포 기록 | [SERVER_DEPLOY_SCRIPT.md](SERVER_DEPLOY_SCRIPT.md) | 현재 배포 지침이 아닌 과거 업로드 절차 |
| 장애 대응 | [OPERATIONS_RUNBOOK.md](OPERATIONS_RUNBOOK.md) | 상태·로그·무음·시세·DLQ 점검 |
| 관측성 구성·향후 확장 | [OBSERVABILITY_PLAN.md](OBSERVABILITY_PLAN.md) | 메트릭·로그·알림과 확장 후보 |

실제 스크립트는 [ops/](../../ops/README.md), 대시보드 시작은 [관측성 안내](../../ops/observability/README.md)를 본다. 배포와 DLQ 재처리는 서비스를 바꾸는 작업이고, health·로그 조회는 상태 확인이다.

[전체 문서](../README.md) · [문서 작성 기준](../DOCUMENTATION_GUIDE.md)
