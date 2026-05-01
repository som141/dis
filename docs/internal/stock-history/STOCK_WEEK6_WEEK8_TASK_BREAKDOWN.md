# 주식 시스템 6주차-8주차 작업 분해

## 1. 목표

남은 주차의 목표는 주식 시스템을 “gateway 연동 완료 상태”에서 “랭킹, snapshot, provider 선택, fallback, 문서 정리까지 끝난 상태”로 끌어올리는 것이다.

이번 범위의 완료 기준은 아래와 같다.

- `rank day/week/all` 명령이 worker에서 실제 처리될 것
- snapshot 저장/조회 구조가 추가될 것
- Redis 기반 rank cache가 적용될 것
- mock / real provider 전환 구조가 존재할 것
- provider 실패 시 fallback 정책이 테스트될 것
- 환경변수, compose, 실행 문서가 갱신될 것
- 전체 테스트와 패키징이 다시 통과할 것

## 2. 작업 단위

### W6-1. ranking period와 기준 수익률 규칙 확정

- `RankingPeriod` 추가
- `DAY`, `WEEK`, `ALL` 계산 기준 고정
- `ALL`은 누적 지급금 대비 현재 총자산 수익률로 계산
- `DAY`, `WEEK`는 snapshot baseline 기반으로 계산

테스트:

- `RankingServiceTest`

완료 기준:

- 기간별 baseline 규칙이 코드와 테스트로 고정됨

### W6-2. snapshot 서비스 추가

- `SnapshotService` 추가
- 전체 계좌 snapshot 저장 로직 추가
- 길드 단위 snapshot 저장 로직 추가
- daily / weekly scheduler 추가

테스트:

- `StockRankingIntegrationTest`

완료 기준:

- rank 계산 이전 baseline snapshot을 실제로 만들 수 있음

### W6-3. ranking cache 추가

- `RankingCacheRepository` 추가
- `RedisRankingCacheRepository` 추가
- `RankingService`에서 cache read/write 적용
- allowance, buy, sell 이후 cache eviction 추가

테스트:

- `RankingServiceTest`
- 거래/지급 후 기존 테스트 회귀 없음

완료 기준:

- rank 결과가 Redis TTL 기반으로 캐시되고, 핵심 상태 변경 이후 무효화됨

### W6-4. rank command 실제 연결

- `StockCommandApplicationService`의 `Rank` 분기 구현
- `StockResponseFormatter.formatRanking` 추가
- Rabbit messaging integration에 rank 경로 추가

테스트:

- `StockCommandApplicationServiceTest`
- `StockResponseFormatterTest`
- `StockMessagingIntegrationTest`

완료 기준:

- `/stock rank`가 worker에서 `NOT_IMPLEMENTED`가 아니라 실제 응답을 생성함

### W7-1. provider 선택 구조 추가

- `StockProviderProperties` 추가
- `AlphaVantageQuoteProvider` 추가
- `FallbackQuoteProvider` 추가
- config에서 `mock` / `alphavantage` 선택 가능하게 정리

테스트:

- `AlphaVantageQuoteProviderTest`
- `FallbackQuoteProviderTest`

완료 기준:

- 설정값만으로 provider를 바꿀 수 있고, primary provider 실패 시 fallback 정책이 동작함

### W7-2. 실행 설정 반영

- `application.yml`에 provider 설정 추가
- `.env.example`에 provider env 추가
- `docker-compose.yml`에 stock-node provider env 추가

테스트:

- `clean test`
- `bootJarAll`

완료 기준:

- local/compose 기준으로 provider 설정이 주입 가능함

### W8-1. 문서 정리

- 6~8주차 작업 분해 문서 작성
- 6~8주차 작업 보고서 작성
- 문서 인덱스 갱신
- 실행/설정 문서에 stock provider 정보 반영

완료 기준:

- 남은 구현 범위와 결과가 문서로 추적 가능함

### W8-2. 최종 검증

실행 명령:

```powershell
.\\gradlew.bat :apps:stock-node-app:test
.\\gradlew.bat clean test
.\\gradlew.bat bootJarAll
```

완료 기준:

- stock-node 세부 테스트 통과
- 전체 테스트 통과
- 전체 부트 jar 패키징 통과
