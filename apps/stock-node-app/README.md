# stock-node-app · 모의투자 worker

주식 명령을 RabbitMQ에서 받아 시세 조회, 모의 매매, 계좌·포트폴리오·거래내역·랭킹을 처리한다. Discord/JDA에 직접 연결하지 않고 결과 메시지를 Gateway로 돌려준다.

## 코드 읽기 순서

1. [StockNodeApplication](src/main/java/discordgateway/stocknode/StockNodeApplication.java) → [config/](src/main/java/discordgateway/stocknode/config): 앱과 메시징·저장소 조립.
2. [StockCommandListener](src/main/java/discordgateway/stocknode/messaging/StockCommandListener.java) → [StockCommandApplicationService](src/main/java/discordgateway/stocknode/application/StockCommandApplicationService.java): 명령 분기·결과 생성.
3. [TradeExecutionService](src/main/java/discordgateway/stocknode/application/TradeExecutionService.java), [RankingService](src/main/java/discordgateway/stocknode/application/RankingService.java): 거래·조회 업무 처리.
4. [persistence/](src/main/java/discordgateway/stocknode/persistence), [cache/](src/main/java/discordgateway/stocknode/cache): PostgreSQL 계좌·원장과 Redis 캐시.
5. [FinnhubTop10RefreshScheduler](src/main/java/discordgateway/stocknode/application/FinnhubTop10RefreshScheduler.java): 시세 갱신·청산 검사.

## 거래·시즌 정책

| 항목 | 현재 동작 |
| --- | --- |
| 계좌 | 길드·사용자·월 시즌별 관리. 첫 진입 시 10,000 지급 |
| 시즌 | `Asia/Seoul` 기준 매월 1일 00:00 전환 |
| 매매 | 정수 주식 수량, 매수 leverage 1~50. 실제 주문은 발생하지 않음 |
| 종목 | DB의 활성 watchlist. 초기 10종목은 2026-05-01 기준 seed이며 순위를 자동 수집하지 않음 |
| 시세 공급 | 기본 `mock`, 실제 가격은 `finnhub` + API 키 |
| 정기 갱신 | provider가 `finnhub`이고 market data가 켜졌을 때 실행. 실행 종료 후 기본 20초 대기 |
| 캐시 | 기본 TTL 60초, 거래는 45초 이내 quote 요구 |
| 자동 청산 | Finnhub 정기 갱신 성공 시 고립 증거금 평가. 자산이 0 이하인 포지션 청산 |

명령 처리 경로는 Redis에 저장된 시세를 읽는다. 기본 `mock` 모드는 캐시를 자동으로 채우지 않으므로 빈 캐시의 가격 조회·거래는 준비 안 됨으로 응답할 수 있다. Finnhub HTTP 호출은 정기 갱신 경로에 둔다. 10종목을 20초 간격으로 갱신할 때 분당 약 30콜·일간 약 43,200콜은 호출 소요 시간을 무시한 추정치다. 실제 요청 수는 `fixedDelay`, 실패, 제한 정책에 따라 달라진다. 기본 앱 보호 한도는 분당 60·일간 100,000이며 공급자의 계약 한도와 별개다.

DB 테이블과 마이그레이션은 [PostgreSQL 스키마](../../docs/reference/POSTGRESQL_STOCK_SCHEMA.md)를 본다.

## 실행·테스트·점검

- Compose 서비스: `stock-node`, 호스트 HTTP `8083` → 컨테이너 `8080`.
- 의존 서비스: RabbitMQ, Redis, PostgreSQL. Finnhub 모드에서 외부 API 사용.
- 계약 모듈: [stock-core](../../modules/stock-core/README.md).
- 환경 변수와 기본값: [application.yml](src/main/resources/application.yml), [로컬 개발 안내](../../docs/operations/LOCAL_DEVELOPMENT.md).

저장소 루트에서:

```bash
# Docker/Testcontainers 필요
bash ./gradlew :apps:stock-node-app:test
docker compose --env-file .env logs --since=5m --tail=100 stock-node
curl -fsS http://localhost:8083/actuator/health
```

`/actuator/info`, `/actuator/prometheus`도 노출한다. 테스트의 종류와 실제 Discord 매매 확인은 [테스트 가이드](../../docs/operations/CI_TESTING.md)를 따른다.

[앱 목록](../README.md) · [메시지 계약](../../docs/reference/EVENT_CONTRACT.md) · [시세 장애 점검](../../docs/operations/OPERATIONS_RUNBOOK.md)
