# 테스트와 검증 가이드

이 문서는 테스트의 실행 방법과 각 결과가 검증하는 범위를 설명한다. 테스트 파일의 존재와 실제 실행 성공은 구분하며, 해당 커밋의 CI artifact 또는 로컬 보고서로 결과를 확인한다.

## 실행 명령

모든 명령은 저장소 루트에서 실행한다. JDK 21이 필요하고, 전체 주식 통합 테스트는 Docker/Testcontainers와 테스트 이미지 다운로드가 가능한 환경을 요구한다.

```bash
# Gateway·음악 코어를 먼저 확인
bash ./gradlew :apps:gateway-app:test :modules:common-core:test

# 컴파일만 확인
bash ./gradlew compileJava compileTestJava

# 전체 테스트와 세 앱 JAR 생성: CI와 동일
bash ./gradlew clean test bootJarAll --no-daemon
```

PowerShell에서는 `bash ./gradlew` 대신 `.\gradlew.bat`를 사용한다. 일부 모듈 테스트가 실패하면 Gradle 출력의 원인과 보고서를 확인한다. Docker 접속·이미지 pull 실패는 환경 문제이며, 준비된 컨테이너에서 assertion이 실패하는 경우와 구분한다.

`verifyYoutubeSources`는 기존 YouTube 1.18.2 공식 릴리스 JAR의 SHA-256을 컴파일·테스트·패키징 전에 검사한다. Maven 저장소의 HTML 응답 장애를 피하면서 버전과 런타임 의존성을 보존하는 설정이며, 출처·해시·갱신 절차는 [Gradle 공급 안내](../../gradle/README.md)에 있다.

## 코드별 검사 범위

| 위치 | 주요 검사 | 검증하지 않는 경계 |
| --- | --- | --- |
| [gateway 테스트](../../apps/gateway-app/src/test/java) | 명령 카탈로그, `/man` 비공개 응답, 음악·주식 listener, result/pending 처리 | 실제 Discord 수신·REST 전달·권한·rate limit |
| [common-core 테스트](../../modules/common-core/src/test/java) | YouTube OAuth HTTP context, TV User-Agent, remote cipher 설정, 재생 실패 통지 | 실제 YouTube 전체 곡 가용성·Discord 음질 |
| [stock 단위·애플리케이션 테스트](../../apps/stock-node-app/src/test/java/discordgateway/stocknode) | 시세 캐시·잠금·provider 제한, 시즌 지급, 거래·조회·랭킹, 메시지·메트릭 | 운영 데이터 크기·전체 트래픽 한계 |
| [stock 통합 테스트](../../apps/stock-node-app/src/test/java/discordgateway/stocknode/integration) | Testcontainers PostgreSQL·Redis·RabbitMQ를 통한 저장·메시징·거래·랭킹 | 실제 Discord 표시와 장시간 운영 |

음악 앱의 주요 재생 로직은 common-core에 있어 해당 모듈 테스트를 함께 실행해야 한다. [StockCommandContractTest](../../apps/stock-node-app/src/test/java/discordgateway/stocknode/application/StockCommandContractTest.java)는 주식 계약을 확인한다. 계약 변경 시 Gateway 발행 코드와 worker 소비 코드도 같이 검토한다.

## 결과 확인

로컬 HTML 보고서는 테스트가 있는 모듈의 `build/reports/tests/test/index.html`, XML 결과는 `build/test-results/test/`에 생성된다.

[Deploy Bot 워크플로우](../../.github/workflows/cicd-deploy.yml)는 `main` push 또는 수동 실행에서 전체 테스트·빌드를 먼저 수행한다. 성공 후 이미지 빌드·서버 배포가 이어진다. 테스트 보고서는 `if: always()`로 `gradle-test-reports-<git-sha>` artifact에 업로드한다. GitHub의 Actions → Deploy Bot → 해당 실행에서 내려받는다. 워크플로우 수동 실행도 배포를 포함하므로 단순 테스트 실행 버튼으로 취급하지 않는다.

## 실제 Discord에서 확인할 항목

자동 테스트 뒤에는 개발 봇·개발 서버에서 아래 기능을 확인한다.

1. `/man`의 안내가 본인에게만 표시되는지 확인한다.
2. 음성 채널에서 `/play query:<곡>` 후 실제 소리를 듣는다. 접수 메시지만으로 성공을 판단하지 않는다.
3. `/pause` → `/resume` → `/queue` → `/skip` → `/stop`을 확인한다. 공개 범위·대기열·음성 상태를 함께 본다.
4. 시세가 준비된 상태에서 `/stock quote`, `/stock buy`, `/stock sell`, 계좌·원장을 확인한다. buy/sell/rank는 공개, 조회는 비공개다.
5. 앱 재시작 후 복구·응답, 빈 채널 퇴장, 실패 안내를 확인한다.

새 Redis와 기본 `mock` 모드는 가격 캐시를 자동 준비하지 않는다. 실제 거래 smoke check는 [Finnhub 설정](LOCAL_DEVELOPMENT.md)을 적용해 quote 갱신을 확인하거나, 테스트에서 가격을 준비한 조건으로 한다.

## health와 성능 측정의 한계

[ops/smoke-check.sh](../../ops/smoke-check.sh)는 서버 release의 앱 health와 stock metrics를 조회한다. Discord 명령·음성·거래를 직접 실행하지 않는다. 컨테이너 health와 이 HTTP 검사, 명령 결과, 실제 청취는 별도 검증 단계다.

현재 커밋에는 별도 부하 발생기와 성능 결과 파일이 포함돼 있지 않다. 기능 테스트만으로 최대 동시 채널 수·처리량·p95를 기재하지 않는다. 부하 시험을 추가할 때는 다음을 함께 저장한다.

- 대상 커밋과 설정, 자원 제한, 사용자·길드·데이터 규모.
- 목표·실제 주입률, 미발행·실패·timeout 수, 단계 길이와 반복 횟수.
- 응답 지연의 시작·끝, 큐 ready/unacked, worker 처리 시간·CPU·DB 대기.
- 음악 source 프레임 공급과 소비기 스케줄 지연을 구분한 지표.
- 원본 데이터·그래프·재현 명령과 실제 Discord를 포함했는지 여부.

내부 주입 시험은 worker·큐·DB의 병목을 찾는 데 사용할 수 있지만 Discord 네트워크·ACK/REST 제한·음성 수신 구간은 별도의 작은 실험으로 확인한다. 세부 기록 기준은 [문서 작성 기준](../DOCUMENTATION_GUIDE.md)에 있다.

[개발·운영 문서](README.md) · [운영 런북](OPERATIONS_RUNBOOK.md)
