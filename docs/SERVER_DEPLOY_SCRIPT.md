# 서버 배포 스크립트 가이드

## 개요

현재 배포는 GitHub Actions `Deploy Bot` 워크플로우와 서버의 `deploy.sh`를 기준으로 동작한다.

배포 대상:

- `gateway-app`
- `audio-node-app`
- `stock-node-app`
- `docker-compose.yml`
- `ops/`
- `ops/observability/`
- `.env.cicd`

## CI 흐름

1. `main` push 또는 `workflow_dispatch`
2. `./gradlew clean test bootJarAll --no-daemon`
3. 세 앱 Docker image build
4. image를 `tar.gz`로 저장
5. `.env.cicd`, `docker-compose.yml`, `ops/`, `ops/observability/` 업로드
6. 서버에서 `deploy.sh <git-sha>` 실행

## 서버 디렉터리 구조

```text
/home/ubuntu/dis-bot
  deploy.sh
  incoming/
    discord-gateway-<sha>.tar.gz
    discord-audio-node-<sha>.tar.gz
    discord-stock-node-<sha>.tar.gz
    .env.cicd
    docker-compose.yml
    ops/
  releases/
    <sha>/
      docker-compose.yml
      .env
      discord-gateway.tar.gz
      discord-audio-node.tar.gz
      discord-stock-node.tar.gz
      ops/
  current -> /home/ubuntu/dis-bot/releases/<sha>
```

## deploy.sh 동작

- `incoming`에 필요한 파일이 다 있는지 검증
- 새 release 디렉터리 생성
- compose, env, image archive, `ops/` 복사
- `docker load`로 세 image 적재
- 이전 release가 있으면 `docker compose down --remove-orphans`
- `current` symlink 교체
- 새 release에서 `docker compose up -d --no-build --remove-orphans`
- `incoming` 정리
- 오래된 release 5개 초과분 삭제

## GitHub Actions 변수와 시크릿

### 필수 Secrets

- `SSH_PRIVATE_KEY`
- `SSH_HOST`
- `SSH_PORT`
- `SSH_USER`
- `DISCORD_TOKEN` 또는 `TOKEN`
- `RABBITMQ_USERNAME`
- `RABBITMQ_PASSWORD`
- `POSTGRES_PASSWORD`

Finnhub 사용 시:

- `FINNHUB_API_KEY`

### 주요 Variables

- `OBSERVABILITY_ENABLED`
- `STOCK_QUOTE_PROVIDER`
- `STOCK_MARKET_DATA_ENABLED`
- `STOCK_MARKET_REFRESH_DELAY_MS`
- `POSTGRES_DB`
- `POSTGRES_USER`

권장:

- `STOCK_QUOTE_PROVIDER=finnhub` 또는 `mock`

## Finnhub 배포 반영 조건

실제로 Finnhub 모드로 뜨려면 아래 두 조건이 모두 필요하다.

1. `FINNHUB_API_KEY` secret 존재
2. `STOCK_QUOTE_PROVIDER=finnhub` variable 존재

이 두 값이 `.env.cicd`에 써져서 `stock-node` 컨테이너 env로 전달된다.

## 배포 후 확인

```bash
cd /home/ubuntu/dis-bot/current
docker compose --env-file .env ps
docker compose --project-name discord-bot --env-file .env logs stock-node --tail=200
docker inspect discord-bot-stock-node-1 --format '{{range .Config.Env}}{{println .}}{{end}}' | grep -E 'STOCK_QUOTE_PROVIDER|FINNHUB_API_KEY'
```

## 알려진 리스크

- 현재 스크립트는 image archive를 release 디렉터리로 복사한 뒤 `docker load`한다.
- 디스크가 작은 서버에서는 배포 중간에 용량이 두 번 잡힐 수 있다.
- 서버에 별도 디스크를 붙여도 `DEPLOY_DIR`과 Docker `data-root`가 루트 디스크를 계속 쓰면 해결되지 않는다.
