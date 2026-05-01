# 문서 인덱스

`docs/`는 공개용 운영 문서와 내부 작업 기록을 분리해서 관리한다.

## 공개 문서

### Reference

- [reference/CURRENT_ARCHITECTURE.md](reference/CURRENT_ARCHITECTURE.md)
  - 현재 서비스 구성과 데이터/이벤트 흐름
- [reference/MODULE_STRUCTURE.md](reference/MODULE_STRUCTURE.md)
  - 앱과 모듈의 책임 분리, 패키지 경계
- [reference/EVENT_CONTRACT.md](reference/EVENT_CONTRACT.md)
  - music/stock command-result 계약과 RabbitMQ 토폴로지 개요
- [reference/POSTGRESQL_STOCK_SCHEMA.md](reference/POSTGRESQL_STOCK_SCHEMA.md)
  - 주식 시스템 PostgreSQL 스키마와 운영 확인 SQL

### Operations

- [operations/OPERATIONS_RUNBOOK.md](operations/OPERATIONS_RUNBOOK.md)
  - 운영 점검, 로그, health check, 장애 대응 메모
- [operations/SERVER_DEPLOY_SCRIPT.md](operations/SERVER_DEPLOY_SCRIPT.md)
  - GitHub Actions와 `deploy.sh` 기반 배포 흐름
- [operations/OBSERVABILITY_PLAN.md](operations/OBSERVABILITY_PLAN.md)
  - 현재 observability stack과 확장 우선순위
- [operations/CI_TESTING.md](operations/CI_TESTING.md)
  - CI 테스트 범위와 확인 방법

## 내부 작업 기록

### Analysis

- [internal/analysis/CODEBASE_ANALYSIS.md](internal/analysis/CODEBASE_ANALYSIS.md)
  - 코드베이스 분석 메모
- [internal/analysis/CODEX_WORK_LOG.md](internal/analysis/CODEX_WORK_LOG.md)
  - 에이전트 작업 로그

### Stock History

- [internal/stock-history/README.md](internal/stock-history/README.md)
  - stock 기능 개발 계획서와 주차별 보고서 모음

## 관리 기준

- `reference/`, `operations/`는 GitHub 공개 문서 기준으로 유지한다.
- `internal/`은 내부 계획, 회고, 작업 기록을 모아 둔다.
- 과거 계획 문서는 기록 보존 목적이므로 현재 상태 설명으로 간주하지 않는다.
