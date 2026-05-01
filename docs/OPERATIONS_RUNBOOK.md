# 운영 런북

## 목적

이 문서는 현재 DIS 운영 환경에서 자주 확인하는 명령과 점검 포인트를 정리한다.

## 기본 구성

기본 compose 서비스:

- `gateway`
- `audio-node`
- `stock-node`
- `redis`
- `rabbitmq`
- `postgres`

관측성 profile 추가 시:

- `prometheus`
- `loki`
- `alloy`
- `redis-exporter`
- `grafana`

## 기본 작업 디렉터리

성공적으로 배포된 서버 기준 작업 디렉터리:

```bash
cd /home/ubuntu/dis-bot/current
```

`current`가 없으면 배포가 중간에 실패했거나 release symlink가 아직 잡히지 않은 상태다.

## 컨테이너 상태 확인

전체 상태:

```bash
docker compose --env-file .env ps
```

관측성 포함 상태:

```bash
docker compose --profile observability --env-file .env ps
```

compose 파일이 없는 위치라면 직접 컨테이너를 본다.

```bash
docker ps --format 'table {{.Names}}\t{{.Status}}'
```

## 로그 확인

핵심 앱 로그:

```bash
docker compose --project-name discord-bot --env-file .env logs gateway --tail=200
docker compose --project-name discord-bot --env-file .env logs audio-node --tail=200
docker compose --project-name discord-bot --env-file .env logs stock-node --tail=200
```

infra 로그:

```bash
docker compose --project-name discord-bot --env-file .env logs redis --tail=100
docker compose --project-name discord-bot --env-file .env logs rabbitmq --tail=100
docker compose --project-name discord-bot --env-file .env logs postgres --tail=100
```

실시간 추적:

```bash
docker compose --project-name discord-bot --env-file .env logs -f gateway audio-node stock-node
```

## health check

```bash
curl http://127.0.0.1:8081/actuator/health
curl http://127.0.0.1:8082/actuator/health
curl http://127.0.0.1:8083/actuator/health
curl http://127.0.0.1:9090/-/ready
curl http://127.0.0.1:3000/api/health
```

## stock-node 운영 체크

### provider 상태 확인

```bash
docker inspect discord-bot-stock-node-1 --format '{{range .Config.Env}}{{println .}}{{end}}' | grep -E 'STOCK_QUOTE_PROVIDER|FINNHUB_API_KEY|STOCK_MARKET|STOCK_PROVIDER_PER_MINUTE_LIMIT|STOCK_PROVIDER_PER_DAY_LIMIT'
```

정상 예시:

- `STOCK_QUOTE_PROVIDER=finnhub`
- `FINNHUB_API_KEY=...`
- `STOCK_MARKET=US`
- `STOCK_PROVIDER_PER_MINUTE_LIMIT=60`
- `STOCK_PROVIDER_PER_DAY_LIMIT=100000`

### Finnhub 갱신 로그 확인

```bash
docker logs discord-bot-stock-node-1 --tail=200
```

확인할 메시지:

- watchlist refresh 시작
- 종목별 refresh 성공/실패
- 전체 success count

### Redis quote cache 확인

```bash
docker exec -it discord-bot-redis-1 redis-cli KEYS 'stock:quote:US:*'
```

정상이라면 `NVDA`, `AAPL` 등 10개 키가 보여야 한다.

### PostgreSQL 테이블 확인

```bash
docker exec -it discord-bot-postgres-1 psql -U stock -d stock -c '\dt'
```

주식 관련 핵심 테이블:

- `stock_account`
- `stock_position`
- `trade_ledger`
- `allowance_ledger`
- `account_snapshot`
- `stock_watchlist`

## 재시작

앱만 재시작:

```bash
docker compose --project-name discord-bot --env-file .env restart gateway audio-node stock-node
```

관측성 포함 전체 재시작:

```bash
docker compose --project-name discord-bot --profile observability --env-file .env restart
```

## smoke check

배포 직후:

```bash
bash /home/ubuntu/dis-bot/current/ops/smoke-check.sh
```

주식 쪽 추가 수동 확인:

1. `/stock list`가 watchlist를 보여주는지
2. `finnhub` 모드면 `quote pending`이 아닌 실제 가격이 보이는지
3. `/stock buy`가 공개 메시지로 보이는지
4. `/stock balance`, `/stock portfolio`, `/stock history`는 비공개인지

### Finnhub rate limit 점검

현재 기본 스케줄 호출량:

- 10종목
- 20초 주기
- 분당 30콜
- 일간 43,200콜

따라서 `STOCK_PROVIDER_PER_DAY_LIMIT`가 이보다 낮으면 몇 시간 뒤 모든 refresh가 `Provider rate limit exceeded`로 막힌다.

## DLQ 재처리

음악 command DLQ 재처리:

```bash
bash /home/ubuntu/dis-bot/current/ops/replay-command-dlq.sh
```

## 용량 문제 점검

전체 디스크:

```bash
df -h
```

배포 디렉터리 크기:

```bash
du -sh /home/ubuntu/dis-bot
du -sh /home/ubuntu/dis-bot/incoming
du -sh /home/ubuntu/dis-bot/releases
```

Docker 사용량:

```bash
docker system df
```

이미지 정리:

```bash
docker image prune -a -f
```

주의:

- 현재 배포 스크립트는 image tar.gz를 release 디렉터리로 한 번 더 복사한다.
- 디스크 여유가 적으면 배포 중 `No space left on device`가 날 수 있다.

## 현재 운영 주의사항

- `stock-node`는 현재 Prometheus scrape 대상이 아니다.
- Finnhub API 키는 `FINNHUB_API_KEY` secret로만 넣는다.
- `STOCK_QUOTE_PROVIDER`는 GitHub Actions Variable로 관리하는 쪽이 가장 단순하다.
- 주식 거래는 실제 주문이 아니라 모의투자 게임이다.
