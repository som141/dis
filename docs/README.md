# 문서 인덱스

이 디렉터리의 문서는 모두 현재 코드 기준으로 정리되어 있다. 예전 설계 초안이나 제거된 fallback 경로 기준 문서는 남기지 않았다.

## 구조 문서

- [CURRENT_ARCHITECTURE.md](CURRENT_ARCHITECTURE.md)
  - 현재 런타임 아키텍처, 흐름, 컴포넌트 역할
- [CODEBASE_ANALYSIS.md](CODEBASE_ANALYSIS.md)
  - 코드베이스 관점의 구조 분석
- [MODULE_STRUCTURE.md](MODULE_STRUCTURE.md)
  - 멀티모듈 디렉터리 구조와 책임 분리

## 계약 문서

- [EVENT_CONTRACT.md](EVENT_CONTRACT.md)
  - 현재 `MusicEvent` 계약과 발행 방식

## 운영 / 배포 문서

- [OPERATIONS_RUNBOOK.md](OPERATIONS_RUNBOOK.md)
  - 장애 대응, 점검, DLQ 재처리, 관측성 운영
- [SERVER_DEPLOY_SCRIPT.md](SERVER_DEPLOY_SCRIPT.md)
  - GitHub Actions와 `deploy.sh` 기준 원격 배포 구조

## 관측성 문서

- [OBSERVABILITY_PLAN.md](OBSERVABILITY_PLAN.md)
  - 현재 구현 상태, 남은 관측성 작업, 도입 이유
- [../ops/observability/README.md](../ops/observability/README.md)
  - 실제 compose 기반 관측성 스택 실행법

## 기록 문서

- [CODEX_WORK_LOG.md](CODEX_WORK_LOG.md)
  - 단계별 작업 이력과 현재 기준 요약
