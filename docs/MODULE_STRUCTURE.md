# 모듈 구조

## Gradle 구성

현재 `settings.gradle`에 등록된 모듈은 다음과 같다.

- `modules:common-core`
- `modules:stock-core`
- `apps:gateway-app`
- `apps:audio-node-app`
- `apps:stock-node-app`

## 디렉터리 구조

```text
apps/
  gateway-app/
  audio-node-app/
  stock-node-app/
modules/
  common-core/
  stock-core/
docs/
ops/
docker-compose.yml
deploy.sh
```

## 앱별 책임

### apps/gateway-app

- Discord slash command 진입점
- music/stock command envelope 생성
- pending interaction 저장
- result event 수신 후 응답 수정

주요 패키지:

- `application`
- `config`
- `interaction`
- `messaging`
- `presentation/discord`

### apps/audio-node-app

- 음악 command consumer
- 재생 실행
- recovery
- idle disconnect

주요 패키지:

- `config`
- `lifecycle`
- `recovery`

### apps/stock-node-app

- 주식 command consumer
- quote cache 조회/갱신
- 거래 실행
- 포트폴리오/랭킹/스냅샷 계산
- PostgreSQL persistence

주요 패키지:

- `application`
- `bootstrap`
- `cache`
- `config`
- `lock`
- `messaging`
- `persistence/entity`
- `persistence/repository`
- `quote/finnhub`
- `quote/model`
- `quote/provider`
- `quote/service`

## 공용 모듈 책임

### modules/common-core

- 음악 command/event 계약
- playback 코어 로직
- Redis/RabbitMQ/JDA 인프라
- 공용 bootstrap 설정

주요 패키지:

- `discordgateway.common.*`
- `discordgateway.playback.*`
- `discordgateway.infra.*`

### modules/stock-core

- 주식 command 계약
- 주식 result event 계약
- stock 메시징 속성/프로토콜

주요 패키지:

- `discordgateway.stock.command`
- `discordgateway.stock.event`
- `discordgateway.stock.messaging`

## 경계 규칙

- `gateway-app`은 Discord/JDA 진입만 담당한다.
- `audio-node-app`은 음악만 담당한다.
- `stock-node-app`은 주식만 담당한다.
- `common-core`는 음악 공용 코어다.
- `stock-core`는 주식 공용 계약 모듈이다.

## 왜 이렇게 분리했는가

- Discord 진입과 실제 처리 worker를 분리해 응답 지연과 책임을 분리한다.
- 음악과 주식을 별도 worker로 나눠 서로의 도메인 변경이 섞이지 않게 한다.
- Redis, RabbitMQ, JDA 구현은 공용 모듈에 모아 중복을 줄인다.
- 주식 계약은 stock 전용 모듈로 분리해 `common-core`가 불필요하게 비대해지지 않게 한다.
