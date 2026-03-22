# 문서 인덱스

이 디렉터리의 문서는 현재 코드 구조와 운영 상태를 기준으로 정리되어 있다. 기준 구조는 `gateway-app + audio-node-app + common-core`이며, 패키지 루트는 `gateway / audionode / common / playback / infra` 계열로 재정리된 상태다.

## 구조 문서

- [CURRENT_ARCHITECTURE.md](CURRENT_ARCHITECTURE.md)
  - 현재 시스템 구성, 흐름, 컴포넌트 역할
- [CODEBASE_ANALYSIS.md](CODEBASE_ANALYSIS.md)
  - 저장소 전체 구조 분석
- [MODULE_STRUCTURE.md](MODULE_STRUCTURE.md)
  - 모듈 경계와 패키지 기준

## 계약 문서

- [EVENT_CONTRACT.md](EVENT_CONTRACT.md)
  - `MusicEvent` 구조와 발행 방식

## 운영 / 배포 문서

- [OPERATIONS_RUNBOOK.md](OPERATIONS_RUNBOOK.md)
  - 운영 모드, 점검, DLQ, 장애 대응
- [SERVER_DEPLOY_SCRIPT.md](SERVER_DEPLOY_SCRIPT.md)
  - GitHub Actions + `deploy.sh` 기준 원격 배포 구조

## 관측성 문서

- [OBSERVABILITY_PLAN.md](OBSERVABILITY_PLAN.md)
  - 현재 관측성 구성과 향후 확장 방향
- [../ops/observability/README.md](../ops/observability/README.md)
  - 로컬/원격 관측성 스택 실행법

## 기록 문서

- [CODEX_WORK_LOG.md](CODEX_WORK_LOG.md)
  - 단계별 작업 기록과 현재 잔여 이슈
