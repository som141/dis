# DIS 문서 안내

처음 실행하려면 [프로젝트 README](../README.md)의 빠른 시작부터 읽는다. 이 디렉터리는 현재 코드의 구조·설정·운영 절차와 과거 작업 기록을 연결한다.

## 목적별 시작점

| 하고 싶은 일 | 먼저 읽을 문서 |
| --- | --- |
| 로컬 실행·IntelliJ 설정·환경 변수 확인 | [로컬 개발 안내](operations/LOCAL_DEVELOPMENT.md) |
| 앱과 저장소의 연결 이해 | [현재 아키텍처](reference/CURRENT_ARCHITECTURE.md) |
| 수정할 모듈과 코드 진입점 찾기 | [모듈 구조](reference/MODULE_STRUCTURE.md), [앱 목록](../apps/README.md) |
| 명령·결과 메시지와 큐 확인 | [이벤트 계약](reference/EVENT_CONTRACT.md) |
| 모의투자 DB 구조 확인 | [PostgreSQL 스키마](reference/POSTGRESQL_STOCK_SCHEMA.md) |
| 테스트 실행·검증 범위 확인 | [테스트 가이드](operations/CI_TESTING.md) |
| 상태·로그·무음·시세 장애 점검 | [운영 런북](operations/OPERATIONS_RUNBOOK.md) |
| GitHub Actions·서버 배포 설정 | [배포 가이드](operations/SERVER_DEPLOY_SCRIPT.md) |
| Grafana·Prometheus 설정 | [관측성 안내](../ops/observability/README.md) |
| README나 상세 문서 수정 | [문서 작성 기준](DOCUMENTATION_GUIDE.md) |

## 디렉터리별 기준

- [reference/](reference/README.md): 현재 코드의 구조, 계약, 데이터 모델.
- [operations/](operations/README.md): 로컬 개발, 테스트, 배포, 운영 절차.
- [internal/](internal/README.md): 과거 분석, 계획, 작업 보고서. 작성 당시 상태를 보존한다.
- [ops/](../ops/README.md): 실제 실행 스크립트와 설정 파일. 절차 문서에서 해당 파일로 연결한다.

현재 동작은 같은 커밋의 소스·설정과 `reference/`, `operations/`를 함께 본다. 과거 보고서의 완료 표시는 현재 커밋의 테스트 실행 결과를 대신하지 않는다.
