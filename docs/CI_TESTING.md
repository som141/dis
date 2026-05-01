# CI 테스트 가이드

## GitHub Actions에서 실행하는 명령

현재 배포 워크플로우는 아래 명령을 사용한다.

```bash
./gradlew clean test bootJarAll --no-daemon
```

즉, 테스트가 모두 통과해야 image build와 원격 배포가 진행된다.

## 테스트 위치

주요 테스트 디렉터리:

- `apps/gateway-app/src/test/java`
- `apps/stock-node-app/src/test/java`

## 현재 포함된 테스트 범위

### gateway-app

- stock command preparation
- Discord command catalog
- Discord listener 공개/비공개 응답 분기
- result listener

### stock-node-app 단위/애플리케이션 테스트

- schema/entity/repository
- monthly seed settlement
- quote cache/lock/rate limit
- Finnhub client/mapper/scheduler
- trade execution
- portfolio/history/ranking formatter

### stock-node-app 통합 테스트

Testcontainers 기반:

- PostgreSQL integration
- Redis integration
- stock messaging integration
- stock trading integration
- stock ranking integration

## 결과 확인 위치

GitHub UI:

1. `Actions`
2. `Deploy Bot`
3. `build-and-deploy`
4. `Test and build with Gradle`

artifact:

- `gradle-test-reports-<git-sha>`

## 로컬 재현 명령

빠른 확인:

```powershell
.\gradlew.bat compileJava compileTestJava
.\gradlew.bat :apps:gateway-app:test
.\gradlew.bat bootJarAll
```

CI와 같은 흐름:

```powershell
.\gradlew.bat clean test bootJarAll
```

## Windows 로컬 주의사항

- 현재 작업 경로가 비ASCII 경로면 Gradle test worker가 흔들릴 수 있다.
- 이 경우 ASCII junction 경로에서 테스트를 돌리는 편이 안전하다.
- 예: `C:\Users\s0302\.codex\memories\dis-ascii`

## Docker 의존 테스트

`stock-node-app` 통합 테스트는 Docker/Testcontainers가 필요하다.

Docker가 안 보이면 다음 테스트들이 실패한다.

- `StockPersistenceIntegrationTest`
- `StockRedisIntegrationTest`
- `StockMessagingIntegrationTest`
- `StockTradingIntegrationTest`
- `StockRankingIntegrationTest`

이 경우 코드 로직 실패가 아니라 테스트 환경 실패로 해석해야 한다.
