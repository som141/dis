# Stock Week 1-2 Task Breakdown

## 1. 목적

이 문서는 현재 `week-0` 스켈레톤 위에서 `week-1`, `week-2`를 실제 구현하기 위해 작업을 작은 단위로 나눈 실행 문서다.

범위는 다음까지만 포함한다.

- `week-1`: PostgreSQL schema, JPA persistence, account create/query
- `week-2`: Redis quote cache, lock, rate-limit, mock quote provider, quote service

다음 범위는 아직 포함하지 않는다.

- Discord `/stock` 명령 연결
- 실제 RabbitMQ stock command consume/publish 완성
- buy/sell 체결 로직
- ranking 계산
- 외부 시세 provider 실연동

## 2. 진행 가능 여부 판단

현재 저장소는 `week-1`을 바로 시작할 수 있다.

이미 준비된 것:

- `modules/stock-core` 존재
- `apps/stock-node-app` 존재
- `docker-compose.yml`에 `postgres`, `stock-node` 존재
- `application.yml`에 stock/postgres/redis 기본 설정 자리 존재
- `stock-core`에 command/result DTO 초안 존재

아직 비어 있는 것:

- `spring.datasource`
- `spring.jpa`
- `spring.flyway`
- JPA entity / repository
- Redis quote cache / lock
- `QuoteService`
- stock 테스트 체계

결론:

- `week-1`은 바로 진행 가능
- `week-2`도 진행 가능하지만, `week-1`의 schema/entity/repository 기준이 먼저 고정되어야 함

## 3. 구현 원칙

- 기존 `gateway-app`, `audio-node-app` 책임은 건드리지 않는다.
- stock 구현은 우선 `apps/stock-node-app` 안에 집중한다.
- `modules/stock-core`는 shared contract 전용으로 유지한다.
- 기능 추가와 테스트 추가를 같은 작업 단위로 묶는다.
- `week-1`이 green 되기 전에는 `week-2`로 넘어가지 않는다.

## 4. 선결 결정

구현 전에 아래 기준을 먼저 고정한다.

1. migration 도구는 `Flyway`로 사용한다.
2. persistence 패키지는 `apps/stock-node-app` 내부에 둔다.
3. account 생성 기준은 `guildId + userId` 조합으로 한다.
4. `week-1` 테스트는 가능하면 PostgreSQL 기준으로 검증한다.
5. `week-2` 테스트는 가능하면 Redis 기준으로 검증한다.

## 5. 권장 패키지 구조

```text
apps/stock-node-app/src/main/java/discordgateway/stocknode
  application/
  config/
  messaging/
  persistence/
    entity/
    repository/
  quote/
    provider/
    service/
  cache/
  lock/
```

테스트는 아래처럼 맞춘다.

```text
apps/stock-node-app/src/test/java/discordgateway/stocknode
  persistence/
  quote/
  cache/
  lock/
```

## 6. Week 1 작업 단위

### W1-1. build and datasource wiring

작업:

- `apps/stock-node-app/build.gradle`에 JPA, Flyway, test 의존성 추가
- `application.yml`에 datasource, jpa, flyway 설정 추가
- 테스트 프로필 또는 테스트용 설정 추가

산출물:

- stock-node가 PostgreSQL 연결 정보를 인식하는 상태
- 테스트 실행 가능한 build 상태

테스트:

- context load 테스트
- datasource / flyway 기본 기동 테스트

완료 기준:

- `:apps:stock-node-app:test`에서 wiring 관련 테스트 통과

### W1-2. schema migration v1

작업:

- `db/migration/V1__init_stock_schema.sql` 생성
- 아래 테이블 생성
  - `stock_account`
  - `stock_position`
  - `trade_ledger`
  - `allowance_ledger`
  - `account_snapshot`
- 유니크 제약 추가
  - `(guild_id, user_id)`
  - `(account_id, symbol)`

산출물:

- stock 기본 스키마

테스트:

- migration 적용 테스트
- 테이블 생성 확인 테스트
- unique constraint 동작 테스트

완료 기준:

- 테스트용 PostgreSQL에서 migration이 정상 적용됨

### W1-3. entity mapping

작업:

- 각 테이블에 대응하는 JPA entity 작성
- enum 또는 value object가 필요하면 최소 범위만 도입
- ledger/snapshot는 아직 최소 필드만 반영

산출물:

- entity 클래스 일체

테스트:

- entity save/load 테스트
- 기본 연관관계 또는 키 매핑 테스트

완료 기준:

- schema와 entity가 충돌 없이 동작

### W1-4. repository layer

작업:

- `StockAccountRepository`
- `StockPositionRepository`
- 필요 최소 수준의 ledger/snapshot repository
- finder 메서드 추가

산출물:

- 계좌/포지션 저장 및 조회 repository

테스트:

- account save/query 테스트
- `guildId + userId` 기준 조회 테스트
- `accountId + symbol` 기준 포지션 조회 테스트

완료 기준:

- 기본 CRUD와 finder 검증 통과

### W1-5. account application service

작업:

- account create/query application service 작성
- 없으면 생성, 있으면 조회하는 흐름 구현
- 아직 잔고/거래 로직은 넣지 않음

산출물:

- stock account 생성/조회 서비스

테스트:

- 최초 호출 시 계좌 생성 테스트
- 재호출 시 중복 생성되지 않는 테스트
- 동시성까지는 아직 선택 사항

완료 기준:

- `guildId + userId` 기준 idempotent create/query 가능

### W1-6. week 1 마감 검증

실행:

```powershell
.\gradlew.bat :apps:stock-node-app:test
.\gradlew.bat bootJarAll
```

점검:

- stock-node test green
- 전체 bootJar 생성 green
- 기존 gateway/audio-node build 영향 없음

## 7. Week 2 작업 단위

### W2-1. Redis key policy and boundary

작업:

- Redis key naming 규칙 고정
- quote / lock / rate-limit / rank key 분리
- stale/freshness 정책 초안 고정

예시:

- `stock:quote:{market}:{symbol}`
- `stock:quote:lock:{market}:{symbol}`
- `stock:provider:{provider}:minute:{timestamp}`
- `stock:provider:{provider}:day:{date}`
- `stock:rank:{guildId}:{period}`

산출물:

- key 정책 문서화 또는 코드 상수화

테스트:

- key 생성 규칙 단위 테스트
- TTL 정책 단위 테스트

완료 기준:

- quote/lock/rate-limit key가 충돌 없이 분리됨

### W2-2. RedisQuoteRepository

작업:

- quote read/write repository 구현
- TTL 반영
- quote serialization 방식 결정

산출물:

- Redis quote cache adapter

테스트:

- cache hit/miss 테스트
- TTL 만료 관련 테스트
- serialize/deserialize 테스트

완료 기준:

- quote 캐시 저장/조회 가능

### W2-3. RedisLockService

작업:

- 동일 symbol 조회 dedupe용 lock 구현
- 락 획득/실패/해제 흐름 정리

산출물:

- Redis 기반 lock service

테스트:

- 동일 key 동시 요청 시 1개만 획득되는 테스트
- lock timeout 또는 release 동작 테스트

완료 기준:

- 중복 quote 조회 제어 가능

### W2-4. ProviderRateLimitService

작업:

- provider 호출 횟수 카운팅 구조 구현
- 분당/일당 제한 카운터 분리

산출물:

- provider rate-limit service

테스트:

- 분당 카운트 증가 테스트
- 일당 카운트 증가 테스트
- 제한 초과 판단 테스트

완료 기준:

- provider 호출 제한 상태를 조회 가능

### W2-5. QuoteProvider abstraction and mock provider

작업:

- `QuoteProvider` interface 작성
- `MockQuoteProvider` 작성
- market/symbol 기반 quote 응답 초안 작성

산출물:

- 외부 provider 없이도 quote flow를 검증할 수 있는 mock 계층

테스트:

- symbol별 quote 반환 테스트
- provider 호출 횟수 검증 테스트

완료 기준:

- 실제 외부 API 없이 quote service 테스트 가능

### W2-6. QuoteService

작업:

- cache 우선 조회
- stale 시 provider fallback
- lock 사용
- provider rate-limit 확인
- freshness 정책 반영

산출물:

- 초기 `QuoteService`

테스트:

- fresh cache hit 테스트
- stale cache fallback 테스트
- cache miss 시 provider 호출 테스트
- 동시 요청 시 provider 1회 근사 보장 테스트

완료 기준:

- quote 조회 기본 흐름 작동

### W2-7. week 2 마감 검증

실행:

```powershell
.\gradlew.bat :apps:stock-node-app:test
.\gradlew.bat bootJarAll
```

추가 점검:

- Redis 관련 테스트 green
- mock quote flow green
- 기존 music bot build 영향 없음

## 8. 테스트 전략

### 필수 원칙

- 구현 파일 추가 시 대응 테스트 파일도 같이 추가
- 기능만 먼저 만들고 테스트를 나중에 몰아서 작성하지 않음
- `week-1`과 `week-2` 경계마다 전체 테스트를 다시 실행

### 테스트 종류

- 단위 테스트
  - key policy
  - quote serialization
  - stale 정책
- slice/integration 테스트
  - JPA repository
  - Flyway migration
  - Redis cache/lock
- 서비스 테스트
  - account create/query
  - quote service flow

### 권장 검증 명령

빠른 확인:

```powershell
.\gradlew.bat :apps:stock-node-app:test
```

주차 마감 확인:

```powershell
.\gradlew.bat test bootJarAll
```

## 9. 실제 실행 순서

다음 순서로 진행한다.

1. `W1-1 build and datasource wiring`
2. `W1-2 schema migration v1`
3. `W1-3 entity mapping`
4. `W1-4 repository layer`
5. `W1-5 account application service`
6. `W1-6 week 1 마감 검증`
7. `W2-1 Redis key policy and boundary`
8. `W2-2 RedisQuoteRepository`
9. `W2-3 RedisLockService`
10. `W2-4 ProviderRateLimitService`
11. `W2-5 QuoteProvider abstraction and mock provider`
12. `W2-6 QuoteService`
13. `W2-7 week 2 마감 검증`

## 10. 지금 바로 시작할 첫 작업

바로 착수할 첫 묶음은 아래다.

1. `apps/stock-node-app/build.gradle`에 JPA/Flyway/test 의존성 추가
2. `application.yml`에 datasource/jpa/flyway 설정 추가
3. 테스트 기본 뼈대 추가
4. `V1__init_stock_schema.sql` 작성 시작

이 첫 묶음이 끝나면 `week-1`의 나머지 작업은 persistence 중심으로 안정적으로 이어갈 수 있다.
