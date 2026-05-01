# 주식 시스템 0주차 작업 보고서

## 1. 목적

이번 0주차 작업의 목표는 기존 Discord 음악 봇 저장소를 유지한 채, 향후 주식 기능을 올릴 수 있는 최소 스켈레톤을 추가하는 것이었다.

핵심 원칙은 아래와 같았다.

- 기존 `gateway-app`, `audio-node-app`, `common-core` 구조를 깨지 않는다.
- 현재 음악 봇 동작을 유지한다.
- 주식 기능은 별도 worker와 shared contract만 먼저 추가한다.
- 비즈니스 로직, DB 영속화, 실제 거래 처리 같은 1주차 이후 범위는 구현하지 않는다.

## 2. 이번 주차에서 완료한 항목

### 2.1 모듈 추가

- `modules/stock-core` 추가
- `apps/stock-node-app` 추가

`stock-core`는 gateway와 stock-node가 공용으로 사용할 stock command / event 계약의 시작점으로 만들었다.  
`stock-node-app`은 향후 RabbitMQ command consume, Redis/PostgreSQL 연동을 수용할 별도 Spring worker 스켈레톤으로 만들었다.

### 2.2 Gradle 배선

- `settings.gradle`에 `modules:stock-core`, `apps:stock-node-app` 등록
- 루트 `build.gradle`의 `bootJarAll`에 `:apps:stock-node-app:bootJar` 추가
- `modules/stock-core/build.gradle` 추가
- `apps/stock-node-app/build.gradle` 추가

이로써 기존 멀티모듈 빌드 구조 위에 stock 관련 모듈이 같은 방식으로 편입되었다.

### 2.3 공유 DTO 초안 작성

`modules/stock-core`에 아래 DTO를 추가했다.

- `StockCommand`
- `StockCommandEnvelope`
- `StockCommandResultEvent`

`StockCommand`는 sealed interface + record 형태로 두고, 향후 지원 예정인 명령 타입을 초안 수준으로 포함했다.

- `quote`
- `buy`
- `sell`
- `balance`
- `portfolio`
- `history`
- `rank`

현재 단계에서는 실제 로직이 아니라 비동기 command/result 패턴을 위한 계약만 정의했다.

### 2.4 stock-node 앱 스켈레톤 구성

`apps/stock-node-app`에는 아래 최소 구조를 만들었다.

- `StockNodeApplication`
- `config/`
- `application/`
- `messaging/`
- `bootstrap/`
- `application.yml`
- `Dockerfile`

이 앱은 독립적으로 부팅 가능하지만, 현재는 주식 비즈니스 로직을 수행하지 않는다.  
또한 의도적으로 JDA를 연결하지 않았고, Discord 진입 책임도 포함하지 않았다.

### 2.5 Docker Compose 확장

기존 스택은 유지한 채 아래 서비스만 추가했다.

- `postgres`
- `stock-node`

기존 `redis`, `rabbitmq`, `gateway`, `audio-node`, observability 스택은 그대로 유지했다.  
`stock-node`는 기존 compose 스타일을 따라 `depends_on`, 환경 변수, health 대상 포트를 최소 수준으로 맞췄다.

### 2.6 배포/운영 배선 확장

새 서비스가 추가된 만큼 아래도 같이 정리했다.

- `.env.example`에 stock/postgres 관련 기본값 추가
- GitHub Actions 배포 워크플로에 stock-node 이미지 빌드/업로드 추가
- `deploy.sh`에 stock-node 이미지 적재 및 정리 로직 추가
- `ops/smoke-check.sh`에 stock-node health check 추가

이 배선은 단순 코드 추가가 아니라, 새 앱이 기존 배포 경로 안에서 깨지지 않게 맞추기 위한 최소 작업이다.

### 2.7 책임 경계 문서화

주식 기능이 기존 음악 봇 책임과 섞이지 않도록 아래 문서를 추가했다.

- `docs/internal/stock-history/STOCK_WEEK0_BOUNDARIES.md`

정리한 경계는 다음과 같다.

- `gateway-app`: Discord/JDA 진입점만 담당, 향후 stock command publish 가능
- `audio-node-app`: 음악/음성 처리만 담당
- `stock-node-app`: 향후 stock command 처리 worker
- `modules/stock-core`: stock shared contract 전용

## 3. 이번 주차에 변경한 파일

### 수정한 파일

- `settings.gradle`
- `build.gradle`
- `docker-compose.yml`
- `.env.example`
- `.github/workflows/cicd-deploy.yml`
- `deploy.sh`
- `ops/smoke-check.sh`

### 새로 추가한 파일

- `modules/stock-core/build.gradle`
- `modules/stock-core/src/main/java/discordgateway/stock/command/StockCommand.java`
- `modules/stock-core/src/main/java/discordgateway/stock/command/StockCommandEnvelope.java`
- `modules/stock-core/src/main/java/discordgateway/stock/event/StockCommandResultEvent.java`
- `apps/stock-node-app/build.gradle`
- `apps/stock-node-app/Dockerfile`
- `apps/stock-node-app/src/main/java/discordgateway/stocknode/StockNodeApplication.java`
- `apps/stock-node-app/src/main/java/discordgateway/stocknode/application/StockCommandApplicationService.java`
- `apps/stock-node-app/src/main/java/discordgateway/stocknode/bootstrap/StockNodeMessagingProperties.java`
- `apps/stock-node-app/src/main/java/discordgateway/stocknode/bootstrap/StockNodeStorageProperties.java`
- `apps/stock-node-app/src/main/java/discordgateway/stocknode/config/StockNodeComponentConfiguration.java`
- `apps/stock-node-app/src/main/java/discordgateway/stocknode/messaging/StockCommandListener.java`
- `apps/stock-node-app/src/main/java/discordgateway/stocknode/messaging/StockCommandResultPublisher.java`
- `apps/stock-node-app/src/main/resources/application.yml`
- `docs/internal/stock-history/STOCK_WEEK0_BOUNDARIES.md`

## 4. 의도적으로 하지 않은 것

이번 0주차에서는 아래 항목을 구현하지 않았다.

- 주식 시세 조회 비즈니스 로직
- 매수/매도 실행 로직
- 잔고/포트폴리오/거래내역/랭킹 서비스
- JPA entity / repository
- PostgreSQL 실제 저장 구현
- Flyway / Liquibase migration
- Redis quote cache / distributed lock
- 외부 시세 제공자 연동
- Discord `/stock` slash command 플로우
- stock-node의 실제 RabbitMQ consumer/publisher 동작
- stock-node의 JDA 연결

이 항목들은 모두 1주차 이후에 붙여야 하는 범위로 남겨 두었다.

## 5. 검증 결과

이번 작업 후 아래 명령으로 컴파일과 bootJar 생성까지 확인했다.

```powershell
.\gradlew.bat compileJava compileTestJava
.\gradlew.bat bootJarAll
```

확인 결과:

- 기존 `gateway-app` 빌드 유지
- 기존 `audio-node-app` 빌드 유지
- 신규 `stock-node-app` bootJar 생성 성공
- 기존 음악 봇 멀티모듈 구조를 유지한 상태로 stock week-0 스켈레톤 편입 완료

## 6. 남아 있는 후속 작업

다음 주차에서 다뤄야 할 항목은 아래와 같다.

- gateway에서 stock command publish 진입 추가
- stock-node의 RabbitMQ command consume 실제 구현
- stock result event publish 구현
- PostgreSQL schema / persistence 도입
- Redis 사용 전략 확정
- 실제 quote provider 연동
- stock slash command 응답 흐름 연결

## 7. 결론

이번 0주차 작업으로 저장소는 다음 상태로 바뀌었다.

- 이전: 음악 봇 전용 저장소
- 현재: 음악 봇을 유지한 채, 주식 기능을 위한 week-0 스켈레톤이 포함된 저장소

중요한 점은 음악 봇 구조를 갈아엎지 않았다는 것이다.  
기존 gateway/audio-node 분리, RabbitMQ/Redis 기반 런타임, docker-compose 중심 운영 방식을 그대로 유지하면서, 주식 기능을 올릴 별도 경계만 추가했다.
