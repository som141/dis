# 로컬 개발과 환경 설정

[프로젝트 README](../../README.md)의 빠른 시작을 보완한다. 아래 명령의 작업 위치는 **저장소 루트**다. 서버 release 경로에서의 점검·배포는 [운영 런북](OPERATIONS_RUNBOOK.md), [배포 가이드](SERVER_DEPLOY_SCRIPT.md)를 따른다.

## 실행 전 준비

- JDK 21과 Docker Compose v2가 필요하다. 전체 앱의 기본 실행 경로는 Docker의 Alpine JRE 이미지다.
- [Gradle wrapper](../../gradle/wrapper/gradle-wrapper.properties)는 8.8을 사용한다. 별도 Gradle 설치는 필요 없다.
- [.env.example](../../.env.example)을 `.env`로 복사하고 `DISCORD_TOKEN`을 설정한다.
- Discord 서버에 `bot`, `applications.commands` scope로 봇을 초대하고, 텍스트 채널 보기·회신 및 음성 연결·말하기 권한을 부여한다. 개발 서버는 `DISCORD_DEV_GUILD_ID`로 지정한다.

```bash
cp .env.example .env
bash ./gradlew bootJarAll
docker compose --env-file .env config --quiet
docker compose --env-file .env up -d --build --wait --wait-timeout 180
```

PowerShell은 파일 복사에 `Copy-Item .env.example .env`, 빌드에 `.\gradlew.bat bootJarAll`을 사용한다. Docker 명령은 동일하다. `--wait`와 `--wait-timeout`을 지원하는 Compose v2를 사용한다.

Dockerfile은 각 앱의 `build/libs/*.jar`를 복사한다. `COPY ...jar: not found`라면 먼저 `bootJarAll`을 실행한다. 설정 검증에는 값을 전부 출력하는 `config` 대신 `config --quiet`를 사용한다.

## 설정의 기준과 적용 위치

| 설정 | 기본 / 적용 |
| --- | --- |
| `DISCORD_TOKEN` | Gateway·audio에 필요한 봇 토큰. stock은 Discord에 접속하지 않음 |
| `DISCORD_DEV_GUILD_ID` | 유효한 개발 서버가 있으면 해당 서버에 명령 등록. 비우면 전역 등록 |
| `RABBITMQ_USERNAME`, `RABBITMQ_PASSWORD` | Compose가 broker와 세 앱에 같은 값 주입 |
| `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` | 기본 `stock`. PostgreSQL·stock 앱·exporter에서 사용 |
| `STOCK_QUOTE_PROVIDER` | 기본 `mock`. `finnhub` 선택 시 `FINNHUB_API_KEY` 필요 |
| `STOCK_MARKET_DATA_ENABLED` | 기본 `true`. Finnhub 정기 갱신 활성화 조건 중 하나 |
| `STOCK_MARKET_REFRESH_DELAY_MS` | 기본 `20000`. 한 갱신 실행이 끝난 후 다음 실행까지의 지연 |
| `STOCK_QUOTE_CACHE_TTL` | 기본 `60s` |
| `STOCK_QUOTE_TRADE_FRESHNESS` | 기본 `45s`. 오래된 가격으로 거래하지 않음 |
| `YOUTUBE_REFRESH_TOKEN` | YouTube OAuth를 사용할 때 설정. 컨테이너에 값이 있어도 갱신 성공은 별도 확인 |
| `YOUTUBE_OAUTH_INIT` | 기본 `false`. 인증 초기화 용도로만 사용 |
| `YOUTUBE_REMOTE_CIPHER_URL` | 기본 `http://youtube-cipher:8001`. Compose 네트워크 내부 주소 |
| `YOUTUBE_REMOTE_CIPHER_PASSWORD` | 내부 cipher API와 앱이 공유하는 선택 인증 값 |
| `YOUTUBE_PO_TOKEN`, `YOUTUBE_VISITOR_DATA` | 선택 설정. 모든 곡의 재생을 보장하는 값은 아님 |
| `COMPOSE_PROJECT_NAME` | 예제 `.env`는 `discord-bot`. 컨테이너·volume 이름에 반영 |

전체 값은 [.env.example](../../.env.example), [Compose](../../docker-compose.yml), [공통 설정](../../modules/common-core/src/main/resources/application-common.yml), [주식 앱 설정](../../apps/stock-node-app/src/main/resources/application.yml)을 기준으로 한다. RabbitMQ·PostgreSQL의 초기 계정 설정은 기존 데이터 volume의 계정을 자동 변경하지 않는다.

`OBSERVABILITY_ENABLED`는 `deploy.sh`가 profile 선택에 사용하는 값이다. 로컬 `docker compose up`에서는 이 값만으로 관측성 서비스가 켜지지 않으므로 `--profile observability`를 명시한다.

## Finnhub 시세 사용

`.env`에 다음을 설정하고 stock 컨테이너를 다시 만든다.

```env
STOCK_QUOTE_PROVIDER=finnhub
FINNHUB_API_KEY=your-finnhub-api-key
STOCK_MARKET_DATA_ENABLED=true
```

```bash
docker compose --env-file .env up -d stock-node
docker compose --env-file .env logs --since=5m --tail=100 stock-node
```

`completed stock watchlist refresh run`의 성공·실패 수를 확인한다. 기본 watchlist는 마이그레이션에 넣은 2026-05-01 기준 10종목이며 시가총액 순위를 자동 수집하는 기능은 없다. 가격 갱신과 종목 선정은 별개다.

`mock` 모드는 실제 시세를 뜻하지 않고 Finnhub 정기 갱신 작업도 실행하지 않는다. 빈 Redis로 시작하면 `/stock list`는 가격 없는 종목 목록을 표시할 수 있고, 시세 조회·거래는 `Current quote is not ready yet`로 응답할 수 있다. 정상 매매 확인에는 Finnhub 갱신을 켜거나 테스트에서 시세 캐시를 준비해야 한다. 현재 mock provider 선택만으로 캐시가 채워지는 실행 경로는 없다.

## 포트와 상태 확인

| 서비스 | 호스트 주소 | 확인 용도 |
| --- | --- | --- |
| Gateway | `http://localhost:8081/actuator/health` | Discord 진입 앱 상태 |
| audio-node | `http://localhost:8082/actuator/health` | 음악 앱 상태. 실제 음질 확인은 별도 |
| stock-node | `http://localhost:8083/actuator/health` | 주식 앱·DB 등 상태 |
| RabbitMQ | AMQP `5672`, UI `http://localhost:15672` | 큐·consumer 확인 |
| Redis | `localhost:6379` | 캐시·상태 저장 |
| PostgreSQL | `localhost:5432` | 계좌·원장 조회 |
| YouTube cipher | 내부 `youtube-cipher:8001` | 호스트 포트 기본 비공개 |
| Grafana / Prometheus | `localhost:3000` / `localhost:9090` | observability profile |
| Loki / Alloy | `localhost:3100` / `localhost:12345` | observability profile |
| Redis / PostgreSQL exporter | `localhost:9121` / `localhost:9187` | observability profile |

호스트 포트는 로컬 개발용으로 공개된다. 원격 서버 공개 범위는 배포 네트워크 정책에 맞춰 설정한다. 포트 충돌은 [docker-compose.yml](../../docker-compose.yml)의 호스트 쪽 매핑을 조정한다.

```bash
docker compose --env-file .env ps
docker compose --env-file .env logs --tail=100 gateway audio-node stock-node youtube-cipher
```

## IntelliJ에서 앱을 개별 실행하기

1. 저장소 루트를 Gradle 프로젝트로 열고 Project SDK와 Gradle JVM을 JDK 21로 지정한다.
2. 아래처럼 인프라부터 시작한다. 같은 worker를 IDE와 Compose에서 동시에 실행하면 같은 큐를 나눠 소비하므로 해당 앱의 컨테이너는 정지한다.

```bash
docker compose --env-file .env up -d redis rabbitmq postgres
docker compose --env-file .env stop stock-node
```

3. [StockNodeApplication](../../apps/stock-node-app/src/main/java/discordgateway/stocknode/StockNodeApplication.java)의 Run Configuration에 아래 값을 넣고 `main()`을 실행한다.

| IDE 환경 변수 | 값 |
| --- | --- |
| `REDIS_HOST` | `localhost` |
| `RABBITMQ_HOST` | `localhost` |
| `RABBITMQ_USERNAME`, `RABBITMQ_PASSWORD` | `.env`의 broker 계정과 동일 |
| `POSTGRES_HOST` | `localhost` |
| `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` | `.env`와 동일 |
| `STOCK_QUOTE_PROVIDER` | `mock` 또는 Finnhub 설정 |
| `APP_NODE_NAME` | `stock-node-ide` |
| `HEALTH_PORT` | `8083` |

일반 Spring Boot 실행은 저장소의 `.env`를 자동으로 읽지 않는다. IDE Run Configuration으로 필요한 값을 전달한다. `redis`, `rabbitmq`, `postgres` 같은 Compose 서비스명은 호스트 Java 프로세스에서 해석되지 않으므로 `localhost`로 바꾼다.

[GatewayApplication](../../apps/gateway-app/src/main/java/discordgateway/gateway/GatewayApplication.java), [AudioNodeApplication](../../apps/audio-node-app/src/main/java/discordgateway/audionode/AudioNodeApplication.java)도 개별 실행할 수 있으나, 각각 Discord 토큰과 서로 다른 `HEALTH_PORT`·`APP_NODE_NAME`이 필요하다. 음악 연결은 DAVE native 의존성도 사용한다. 현재 [common-core 의존성](../../modules/common-core/build.gradle)에 포함된 native는 Windows x86-64와 Linux musl x86-64다. 다른 호스트 플랫폼의 음성 실행은 의존성 확인이 필요하며, 전체 Docker 실행이 기준 경로다.

IDE에서 cipher까지 호출하려면 호스트에서 접근 가능한 `YOUTUBE_REMOTE_CIPHER_URL`이 필요하다. 기본 내부 주소는 호스트에서 접근할 수 없고 cipher 포트도 공개되지 않으므로, 호스트용 포트 매핑을 별도로 추가하거나 접근 가능한 cipher 주소를 지정한다.

## 종료·검증·장애 대응

```bash
# 컨테이너 종료. 데이터 volume 보존
docker compose --env-file .env down
```

`down -v`는 DB·Redis·cipher 캐시 등의 volume을 삭제하므로 초기화할 로컬 데이터를 확인한 경우에만 사용한다. 앱 코드 변경은 JAR 재빌드 후 `up -d --build`로 반영한다. 환경 변수 변경은 `restart`만으로 반영되지 않아 `up -d`로 컨테이너를 다시 만든다.

[테스트 가이드](CI_TESTING.md) · [무음·시세 장애 런북](OPERATIONS_RUNBOOK.md) · [관측성 시작](../../ops/observability/README.md) · [문서 안내](../README.md)
