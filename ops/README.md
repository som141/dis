# 운영 스크립트와 설정

이 디렉터리는 실제 실행 파일과 인프라 설정을 보관한다. 사용 순서는 [운영 런북](../docs/operations/OPERATIONS_RUNBOOK.md), [배포 가이드](../docs/operations/SERVER_DEPLOY_SCRIPT.md)에서 확인한다.

| 파일 / 디렉터리 | 목적 | 실행 효과 |
| --- | --- | --- |
| [smoke-check.sh](smoke-check.sh) | 배포 release의 앱 health·stock metrics 확인 | 상태 조회. Discord 재생·거래를 직접 실행하지 않음 |
| [replay-command-dlq.sh](replay-command-dlq.sh) | 음악 command DLQ 재처리 | 최대 건수만큼 명령을 다시 발행·처리 |
| [cleanup-legacy-deploy.sh](cleanup-legacy-deploy.sh) | 레거시 컨테이너 정리 | 현재 Compose 정지·기존 고정 이름 컨테이너 제거·선택 image 삭제 |
| [youtube-cipher/](youtube-cipher/README.md) | YouTube cipher 이미지·준비 상태 검사 | Compose의 내부 cipher 서비스 구성 |
| [observability/](observability/README.md) | Prometheus·Loki·Alloy·Grafana | 메트릭·로그·대시보드·알림 설정 |

배포용 셸 스크립트는 기본 `/home/ubuntu/dis-bot/current`와 그 안의 `.env`를 사용한다. 다른 설치 경로는 `DEPLOY_DIR`, 프로젝트명은 `COMPOSE_PROJECT_NAME`으로 지정한다. 로컬 저장소 루트에서 전체 스택을 실행하는 명령과 서버 release용 스크립트를 구분한다.

[프로젝트 README](../README.md) · [개발·운영 문서](../docs/operations/README.md)
