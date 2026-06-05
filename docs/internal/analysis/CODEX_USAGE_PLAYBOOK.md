# Codex 활용 방식 정리

## 1. 목적

이 문서는 DIS 프로젝트에서 Codex를 어떤 방식으로 사용했는지 정리한 내부 작업 가이드다.

정리 대상은 다음과 같다.

- 사용자가 Codex에게 준 작업 지시 방식
- 작업을 시작하기 전에 요구한 기준
- 구현, 테스트, 문서화, 커밋, 푸시 흐름
- 운영 설정과 secret 처리 방식
- 문제가 생겼을 때 Codex에게 재현과 원인 분석을 시킨 방식

이 문서는 사용자가 프로젝트 진행 중 Codex에 제공한 운영 방식과 프롬프트 패턴을 정리한 것이다. 시스템 또는 개발자 프롬프트 원문은 포함하지 않는다.

## 2. 기본 사용 전략

Codex는 단순 코드 생성기가 아니라, 저장소를 직접 읽고 수정하는 작업 에이전트로 사용했다.

기본 전략은 아래와 같았다.

- 먼저 현재 레포 구조를 읽게 한다.
- 기존 아키텍처를 보존하라고 명시한다.
- 작업 범위와 하지 말아야 할 일을 강하게 제한한다.
- 큰 작업은 주차별, 단계별 문서로 쪼갠다.
- 각 단계마다 테스트를 만들고 통과해야 완료로 본다.
- 구현 후 작업 보고서를 남기게 한다.
- 검증이 끝난 뒤 커밋과 푸시까지 맡긴다.

즉, Codex에게 매번 "코드만 작성"이 아니라 "분석 -> 계획 -> 구현 -> 테스트 -> 문서화 -> 커밋/푸시"까지 이어지는 흐름을 요구했다.

## 3. 기본 프롬프트 구조

가장 자주 사용한 프롬프트 구조는 아래 형태였다.

```text
현재 레포는 어떤 프로젝트인지 먼저 확인해라.
기존 구조를 보존해라.
이번 목표는 이것이다.
이번 범위에서 할 일은 이것이다.
이번 범위에서 하지 말아야 할 일은 이것이다.
테스트를 반드시 추가하고 통과시켜라.
작업 결과를 문서로 남겨라.
완료되면 커밋하고 푸시해라.
```

이 방식은 특히 stock 기능을 추가할 때 반복적으로 사용했다.

예시 기준:

- `gateway-app`, `audio-node-app`, `stock-node-app`의 책임을 분리한다.
- 기존 음악 봇 기능을 깨지 않는다.
- RabbitMQ, Redis, Docker Compose 구조를 유지한다.
- stock 기능은 독립 worker로 확장한다.
- JDA는 `gateway-app`에서만 사용한다.
- `stock-node-app`은 Discord를 직접 알지 못하게 한다.

## 4. 강하게 제한한 작업 규칙

사용자는 Codex에게 다음 제한을 반복해서 걸었다.

### 4.1 기존 구조 보존

새 기능을 추가하더라도 기존 음악 봇 구조를 갈아엎지 않도록 했다.

명시한 기준:

- `gateway-app`은 Discord/JDA 진입점으로 유지
- `audio-node-app`은 음악 worker로 유지
- `stock-node-app`은 주식 worker로 분리
- `modules/common-core`와 `modules/stock-core`의 책임을 섞지 않기
- RabbitMQ 기반 command/result 흐름 유지
- Redis 제거 금지
- Kafka 같은 다른 메시징 시스템으로 교체 금지

### 4.2 범위 통제

큰 기능을 요청할 때마다 "이번 주차 범위"와 "비범위"를 분리했다.

예시:

- week-0에서는 skeleton만 만든다.
- week-1/2에서는 persistence와 quote cache만 만든다.
- week-3/4에서는 거래 로직과 worker messaging을 만든다.
- week-5에서는 Discord slash command 연동만 한다.
- 이후에는 Finnhub, 월 시즌, 레버리지, 자동 청산을 별도 작업으로 나눈다.

이 방식으로 Codex가 한 번에 전체 시스템을 재설계하지 않도록 제어했다.

### 4.3 테스트 강제

구현만 하고 끝내지 않도록 다음 규칙을 반복했다.

- 작업 단위마다 테스트를 작성한다.
- 테스트가 통과해야 완료로 본다.
- CI에서 `clean test bootJarAll`을 통과해야 배포한다.
- 통합 테스트는 Testcontainers 기반으로 검증한다.
- 로컬 Windows 한글 경로 문제가 있으면 ASCII junction 경로에서 다시 검증한다.

자주 사용한 검증 명령:

```powershell
.\gradlew.bat :apps:gateway-app:test
.\gradlew.bat :apps:stock-node-app:test
.\gradlew.bat clean test
.\gradlew.bat bootJarAll
```

### 4.4 문서화 강제

사용자는 기능을 구현한 뒤 반드시 문서를 남기게 했다.

문서 유형:

- 작업 계획서
- 작업 보고서
- 운영 가이드
- PostgreSQL schema 문서
- CI 테스트 문서
- 현재 아키텍처 문서
- 내부 Codex 작업 로그

문서 위치 정책:

- 공개 기준 문서: `docs/reference/`
- 운영 문서: `docs/operations/`
- 내부 계획, 보고서, Codex 작업 기록: `docs/internal/`

## 5. 작업 단위 운영 방식

큰 작업은 바로 구현하지 않고 먼저 작업 단위로 나누었다.

표준 흐름:

1. 현재 상태 확인
2. 바로 진행 가능한지 판단
3. 작업 단위 문서 작성
4. 각 작업 단위에 테스트 기준 추가
5. 구현
6. 테스트 실행
7. 보고서 작성
8. 커밋
9. 푸시
10. CI 또는 운영 로그 확인

예시 문서:

- `STOCK_WEEK1_WEEK2_TASK_BREAKDOWN.md`
- `STOCK_WEEK3_WEEK4_TASK_BREAKDOWN.md`
- `STOCK_FINNHUB_TOP10_TASK_BREAKDOWN.md`
- `STOCK_LIQUIDATION_AND_RESPONSE_PLAN.md`

## 6. Codex에게 맡긴 주요 역할

### 6.1 코드베이스 분석

Codex에게 먼저 저장소를 읽게 하고, 다음을 확인하게 했다.

- 모듈 구조
- Gradle wiring
- Docker Compose 구성
- RabbitMQ topology
- Redis 사용처
- PostgreSQL migration
- Grafana/Prometheus/Loki/Alloy 구성
- GitHub Actions 배포 흐름

### 6.2 기능 구현

주요 구현 범위:

- `stock-core` 추가
- `stock-node-app` 추가
- PostgreSQL persistence
- Redis quote cache, lock, rate limit
- Finnhub REST API refresh
- `/stock` slash command
- 월 시즌 계좌
- 레버리지 거래
- 수량 기준 매수/매도
- 자동 청산
- 한국어 Discord 응답 정리

### 6.3 장애 분석

운영 중 문제가 생기면 로그와 스크린샷을 주고 Codex에게 원인을 좁히게 했다.

사례:

- Grafana `DatasourceNoData` alert 원인 분석
- Discord 응답이 `생각 중이에요...`에서 멈추는 문제
- GitHub Actions 실패 테스트 수정
- 서버 디스크 부족과 Docker image 정리
- Finnhub quote pending 원인 분석
- provider rate limit 초과 원인 분석

### 6.4 배포와 Git 관리

구현 완료 후에는 Codex에게 다음까지 맡겼다.

- `git status` 확인
- staging 정리
- 커밋 메시지 작성
- `main` 푸시
- CI 실패 시 원인 분석과 재수정

단, `.env`와 API key는 커밋하지 않는 방식으로 관리했다.

## 7. 환경값과 Secret 운영

민감값과 일반 설정값을 구분해서 관리했다.

민감값:

- `FINNHUB_API_KEY`
- Discord token
- RabbitMQ password
- Grafana webhook URL

관리 방식:

- GitHub Actions Secret 사용
- 코드나 문서에 실제 키를 쓰지 않음
- `.env.example`에는 빈 값 또는 예시만 작성

일반 설정값:

- `STOCK_QUOTE_PROVIDER`
- `STOCK_MARKET_REFRESH_DELAY_MS`
- `STOCK_PROVIDER_PER_MINUTE_LIMIT`
- `STOCK_PROVIDER_PER_DAY_LIMIT`

관리 방식:

- GitHub Actions Variable 또는 서버 `.env`
- `STOCK_QUOTE_PROVIDER=finnhub`를 넣어야 Finnhub mode로 동작
- 기본값은 `mock`으로 유지

## 8. Codex에게 준 대표 지시 패턴

### 8.1 skeleton 추가

```text
기존 music bot 구조를 깨지 말고 week-0 범위만 추가해줘.
stock-core와 stock-node-app만 추가하고, 실제 비즈니스 로직은 넣지 마.
Gradle, Docker Compose, DTO skeleton, 책임 경계 문서까지만 해줘.
```

### 8.2 테스트 포함 구현

```text
테스트도 짜서 매번 검증하자.
각 작업 단위마다 테스트가 있고, 이걸 통과해야 성공하는 거야.
```

### 8.3 보고서 작성

```text
이번 주차에서 한 내용을 0주차처럼 작업 보고서로 적어줘.
각 세부 작업 단위마다 한 것과 테스트 결과를 자세히 정리해줘.
```

### 8.4 운영 문제 분석

```text
운영 로그가 이렇게 뜨는데 왜 그런지 기존 코드 기반으로 확인해줘.
코드 문제인지 환경 문제인지 나눠서 설명해줘.
```

### 8.5 배포 전 확인

```text
푸시해도 CI/CD 타고 오류 안 일으킬지 확인해줘.
위험하면 workflow나 테스트 게이트를 먼저 고쳐줘.
```

### 8.6 문서 정리

```text
현재 상태와 다른 문서가 있으면 전부 고쳐줘.
운영 관련 문서와 내부 계획 문서를 디렉터리별로 정리해줘.
```

## 9. 효과가 좋았던 운영 방식

### 9.1 "하지 말 것"을 명확히 적기

Codex에게 단순히 목표만 주면 범위가 커질 수 있다.

효과가 좋았던 방식:

- 구현할 것
- 구현하지 않을 것
- 유지할 기존 구조
- 테스트 기준
- 문서 기준

이 다섯 가지를 같이 적는 방식이다.

### 9.2 큰 기능은 주차 단위로 쪼개기

주식 시스템은 한 번에 만들지 않고 다음처럼 나눴다.

- week-0: skeleton
- week-1/2: persistence, Redis quote cache
- week-3/4: trade, query, worker messaging
- week-5: gateway slash command
- week-6/8: ranking, provider, stabilization
- 이후: Finnhub, monthly season, leverage, liquidation

이 방식은 테스트와 문서가 각 단계에 붙기 쉬웠다.

### 9.3 운영 로그를 그대로 제공하기

스크린샷과 로그를 그대로 제공하면 Codex가 코드 경로와 연결해 원인을 빠르게 좁혔다.

예시:

- `music-command result dropped because pending interaction was not found`
- `Provider rate limit exceeded for finnhub`
- GitHub Actions test failure stack trace
- Docker `No space left on device`

### 9.4 실제 실행 환경을 계속 확인하기

로컬 테스트만으로 끝내지 않고 다음을 같이 확인했다.

- GitHub Actions 결과
- 서버 컨테이너 env
- Docker logs
- Redis key
- PostgreSQL table state
- Discord command 실제 응답

## 10. 주의할 점

### 10.1 문서와 코드의 현재 상태가 달라질 수 있음

주차별 계획서는 기록 문서다. 현재 기준은 아래 문서를 우선한다.

- `docs/reference/CURRENT_ARCHITECTURE.md`
- `docs/reference/MODULE_STRUCTURE.md`
- `docs/reference/EVENT_CONTRACT.md`
- `docs/reference/POSTGRESQL_STOCK_SCHEMA.md`
- `docs/operations/OPERATIONS_RUNBOOK.md`

### 10.2 테스트 환경 이슈와 코드 이슈를 분리해야 함

Windows OneDrive 한글 경로에서 Gradle test worker나 class loading 문제가 발생한 적이 있다.

그 경우 코드 실패로 단정하지 않고 ASCII 경로에서 다시 검증했다.

대표 경로:

```text
C:\Users\s0302\.codex\memories\dis-ascii
```

### 10.3 Secret은 Codex에게 직접 노출하지 않는 편이 좋음

API key는 Codex에게 채팅으로 전달하지 않고 다음 경로로 넣었다.

- 로컬 `.env`
- GitHub Actions Secret
- 서버 env

Codex는 값 자체가 아니라 변수 이름과 전달 흐름만 관리했다.

## 11. 추천 재사용 프롬프트

다음 작업에도 이 형식을 재사용할 수 있다.

```text
현재 레포 구조를 먼저 확인해줘.
기존 아키텍처와 책임 경계를 보존해줘.

목표:
- ...

해야 할 것:
- ...

하지 말아야 할 것:
- ...

작업 방식:
- 먼저 작업 단위 계획을 docs/internal/... 에 MD로 작성
- 각 작업 단위마다 테스트 기준 포함
- 구현 후 테스트 실행
- 현재 상태 문서와 README가 달라지면 갱신
- 검증 통과 후 커밋/푸시

완료 보고:
- 변경 파일
- 통과한 테스트
- 구현한 범위
- 의도적으로 하지 않은 범위
- 운영에서 확인할 항목
```

## 12. 결론

이 프로젝트에서 Codex는 단발성 코드 생성 도구가 아니라, 저장소를 읽고 기존 설계를 보존하면서 단계별로 기능을 확장하는 작업 파트너로 사용했다.

가장 중요한 운영 방식은 다음 세 가지였다.

- 범위를 명확히 제한한다.
- 테스트와 문서화를 작업의 일부로 강제한다.
- 운영 로그와 CI 결과를 다시 코드 수정으로 연결한다.

이 방식 덕분에 음악 봇 구조를 유지한 채 stock worker, PostgreSQL, Redis, Finnhub, RabbitMQ, Discord command, 자동 청산까지 점진적으로 확장할 수 있었다.
