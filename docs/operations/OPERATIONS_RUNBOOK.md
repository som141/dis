# 운영 런북

컨테이너·로그·메시지 큐·시세·음악 재생 문제를 순서대로 확인한다. 설정과 포트는 [로컬 개발 안내](LOCAL_DEVELOPMENT.md), 새 버전 반영은 [배포 가이드](SERVER_DEPLOY_SCRIPT.md)를 따른다.

## 1. 작업 위치와 상태

배포 서버의 기본 경로:

```bash
cd /home/ubuntu/dis-bot/current
docker compose --env-file .env ps
```

로컬 개발은 저장소 루트에서 같은 Compose 명령을 사용한다. 서버 경로는 `DEPLOY_DIR`에 따라 다를 수 있다. Compose 파일 위치를 모르면 먼저 `docker ps --format 'table {{.Names}}\t{{.Status}}'`로 이름을 찾는다. 홈 디렉터리에서 `docker compose logs`를 실행해 `no configuration file provided`가 나오면 저장소/release 경로로 이동하거나 `-f`로 파일 위치를 지정한다.

기본 서비스는 `gateway`, `audio-node`, `stock-node`, `youtube-cipher`, `redis`, `rabbitmq`, `postgres`다. 관측성 profile에는 Prometheus·Loki·Alloy·Grafana·exporter가 있다.

## 2. 로그와 health

```bash
docker compose --env-file .env logs --since=10m --tail=200 gateway audio-node stock-node youtube-cipher
# 실시간 추적
docker compose --env-file .env logs -f gateway audio-node stock-node
curl -fsS http://127.0.0.1:8081/actuator/health
curl -fsS http://127.0.0.1:8082/actuator/health
curl -fsS http://127.0.0.1:8083/actuator/health
```

Compose 파일 없이도 `docker logs --since=10m --tail=200 <컨테이너명>`으로 조회할 수 있다. `--since` 구간에 로그가 없으면 명령 직후 다시 확인하거나 시간 범위를 늘린다. `commandId`로 Gateway 발행과 worker 수신·결과 로그를 연결한다.

Gateway·audio Docker health는 `/tmp/ready` 파일을, stock은 actuator health를, cipher는 `/metrics` HTTP 응답을 검사한다. 앱 health가 정상이어도 실제 Discord 회신·음성 재생·최신 시세까지 보장하지 않는다.

## 3. 음악이 무음이거나 즉시 종료됨

1. 봇·사용자가 같은 음성 채널에 있고 봇의 연결·말하기 권한, mute 상태가 적절한지 확인한다.
2. `/play` 직후 audio 로그에서 `track playback failed`, `LOAD_FAILED`, `AllClientsFailedException`, `403`, `playback_stuck`을 찾는다. `/queue`가 비었다면 재생 접수 뒤 실패했을 수도 있다.
3. cipher 준비 상태와 로그를 확인한다.

```bash
docker compose --env-file .env ps youtube-cipher audio-node
docker compose --env-file .env logs --since=10m --tail=100 youtube-cipher
docker compose --env-file .env exec -T youtube-cipher /usr/local/bin/busybox wget -Y off -q -O - http://127.0.0.1:8001/metrics
```

OAuth 상태는 컨테이너 이름을 확인한 뒤 아래처럼 상태 문구만 추출한다. 기본 프로젝트명은 `discord-bot`이다.

```bash
docker logs --tail=10000 discord-bot-audio-node-1 2>&1 | grep -oE 'YouTube access token refreshed successfully|YouTube OAuth enabled with refresh token\.|YouTube OAuth disabled\. No YOUTUBE_REFRESH_TOKEN found\.|Refreshing YouTube access token failed|Refreshing access token returned error [a-z_]+' | tail -n 20
```

토큰 설정 여부만으로 갱신 성공을 판단하지 않는다. cipher는 서명 해독 서비스여서 로그인 요구·bot 확인·SABR·음원 403을 모두 해결하지 않는다. 음악은 접수 후 실패하면 원래 interaction을 수정하며 유효 기간 15분을 넘긴 곡은 로그로 확인한다. 자세한 인증·클라이언트 대응은 [배포 가이드](SERVER_DEPLOY_SCRIPT.md)에 있다. 로그를 공유할 때 토큰·Authorization·서명된 음원 URL을 제외한다.

## 4. 주식 시세·거래 실패

먼저 공급자와 활성화 여부를 확인한다. 아래는 자격 증명 값을 출력하지 않는다.

```bash
docker compose --env-file .env exec -T stock-node sh -c 'printf "STOCK_QUOTE_PROVIDER=%s\n" "$STOCK_QUOTE_PROVIDER"; printf "STOCK_MARKET_DATA_ENABLED=%s\n" "$STOCK_MARKET_DATA_ENABLED"; if [ -n "${FINNHUB_API_KEY:-}" ]; then echo "FINNHUB_API_KEY=set"; else echo "FINNHUB_API_KEY=unset"; fi'
docker compose --env-file .env logs --since=10m --tail=200 stock-node
docker compose --env-file .env exec -T redis redis-cli --scan --pattern 'stock:quote:US:*'
```

- `mock`은 Finnhub 주기 갱신을 실행하지 않는다. 빈 캐시에서 가격 조회·거래가 준비 안 됨으로 응답할 수 있다.
- Finnhub 사용은 `STOCK_QUOTE_PROVIDER=finnhub`, `FINNHUB_API_KEY`, `STOCK_MARKET_DATA_ENABLED=true`가 함께 필요하다.
- `completed stock watchlist refresh run`의 성공·실패 수와 `Provider rate limit exceeded`를 확인한다.
- quote 기본 TTL은 60초, 거래 freshness는 45초다. 오래된 시세의 거래 거절은 캐시 갱신부터 점검한다.
- 10종목·20초 간격의 약 30콜/분·43,200콜/일은 호출 시간을 무시한 예상치다. 앱 한도와 공급자 계약 한도를 함께 확인한다.

DB 테이블 조회는 컨테이너에 적용된 계정을 사용한다.

```bash
docker compose --env-file .env exec -T postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "\dt"'
curl -fsS http://127.0.0.1:8083/actuator/prometheus | grep 'stock_'
```

관련 테이블·확인 SQL은 [PostgreSQL 스키마](../reference/POSTGRESQL_STOCK_SCHEMA.md)를 본다.

## 5. 큐와 관측성

RabbitMQ 관리 UI 기본 주소는 `http://localhost:15672`다. `music.command.queue`, `stock.command.queue`의 ready·unacked·consumer 수와 Gateway 결과 큐를 확인한다. unacked는 완료가 아니라 consumer가 받아 처리·응답 중일 수 있는 메시지다.

관측성 profile을 실행한 경우:

```bash
curl -fsS http://127.0.0.1:9090/-/ready
curl -fsS http://127.0.0.1:3000/api/health
curl -fsS http://127.0.0.1:9187/metrics | grep 'pg_up'
```

대표 query는 `up{job="stock-node"}`, `stock_quote_cache_ready`, `stock_quote_cache_expected`, `stock_trade_rejections_total`, `pg_up{job="postgres-exporter"}`다. [관측성 안내](../../ops/observability/README.md)에서 대시보드·알림·scrape 설정으로 연결한다.

## 6. 재시작·재생성·배포 점검

```bash
# 현재 설정으로 앱만 재시작. 실행 중 명령·음성 연결에 영향
docker compose --env-file .env restart gateway audio-node stock-node

# .env 변경 후 컨테이너 재생성
docker compose --env-file .env up -d gateway audio-node stock-node
```

코드·이미지 변경은 재빌드 또는 배포 절차가 필요하다. 배포 직후 서버에서 `bash /home/ubuntu/dis-bot/current/ops/smoke-check.sh`로 health·metrics를 확인하고, [테스트 가이드](CI_TESTING.md)의 실제 Discord 확인을 수행한다. 자동 smoke script는 실제 곡 재생·거래를 실행하지 않는다.

## 7. 음악 DLQ 재처리

실패 원인과 같은 commandId의 처리 상태를 확인한 후 서버에서 실행한다.

```bash
# 기본 서버 release에서 최대 50개 음악 명령을 재처리
bash /home/ubuntu/dis-bot/current/ops/replay-command-dlq.sh 50
```

이 명령은 읽기 전용이 아니며 음악 명령을 다시 실행할 수 있다. 주식 큐에 동일한 DLQ·중복 방지 정책이 있다고 가정하지 않는다. [계약과 중복 처리](../reference/EVENT_CONTRACT.md)를 함께 확인한다.

## 8. 디스크 확인

```bash
df -h
du -sh /home/ubuntu/dis-bot/incoming /home/ubuntu/dis-bot/releases
docker system df
```

배포는 archive 복사와 image 적재에 공간을 사용한다. release 보존 수·Docker data-root·실행/rollback에 필요한 이미지를 확인하고 정리한다. 데이터 volume 삭제나 서버 전체 image prune을 일반 점검 명령으로 실행하지 않는다.

[개발·운영 문서](README.md) · [운영 파일 목록](../../ops/README.md)
