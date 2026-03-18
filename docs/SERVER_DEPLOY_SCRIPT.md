# 서버 배포 스크립트 가이드

## 1. 목적

이 문서는 GitHub Actions와 서버의 [deploy.sh](/C:/Users/s0302/OneDrive/바탕%20화면/portpolio/dis/deploy.sh)가 현재 프로젝트를 어떻게 배포하는지 정리한 문서다.

현재 배포 구조는 다음 기준으로 동작한다.

- `gateway`
- `audio-node`
- `redis`
- `rabbitmq`
- `prometheus`
- `loki`
- `alloy`
- `redis-exporter`
- `grafana`

단, 관측성 스택은 `OBSERVABILITY_ENABLED=true`일 때만 같이 올라간다.

## 2. 배포 흐름

현재 배포는 아래 순서로 진행된다.

1. GitHub Actions가 `bootJarAll`로 애플리케이션을 빌드한다.
2. `gateway`, `audio-node` Docker 이미지를 `<git-sha>` 태그로 만든다.
3. 이미지를 `tar.gz`로 묶는다.
4. `docker-compose.yml`, `.env.cicd`, `ops/` 디렉터리를 서버 `incoming`으로 업로드한다.
5. 서버에서 `deploy.sh <git-sha>`를 실행한다.
6. `deploy.sh`가 새 release 디렉터리를 만들고 파일을 복사한다.
7. `docker load`로 이미지를 적재한다.
8. 이전 release가 있으면 먼저 `docker compose down --remove-orphans`로 내린다.
9. `OBSERVABILITY_ENABLED=true`면 `--profile observability`로 같이 올린다.
10. `current` 심볼릭 링크를 새 release로 바꾼다.

## 3. 서버 디렉터리 구조

```text
/home/ubuntu/dis-bot
├─ incoming
│  └─ ops
│     └─ observability
├─ releases
│  └─ <git-sha>
│     └─ ops
│        └─ observability
└─ current -> /home/ubuntu/dis-bot/releases/<git-sha>
```

## 4. deploy.sh 동작

[deploy.sh](/C:/Users/s0302/OneDrive/바탕%20화면/portpolio/dis/deploy.sh)는 아래 작업을 수행한다.

- `incoming`의 `docker-compose.yml`, `.env.cicd`, 이미지 아카이브를 release 디렉터리로 복사
- `incoming/ops` 전체를 release 디렉터리로 복사
- `docker load`로 `gateway`, `audio-node` 이미지 적재
- 이전 release의 `.env`에서 `COMPOSE_PROJECT_NAME`, `OBSERVABILITY_ENABLED`를 읽어 이전 스택 정리
- `current` 링크 교체
- 새 release의 `.env`에서 `OBSERVABILITY_ENABLED`를 읽어:
  - `false`면 일반 compose 기동
  - `true`면 `--profile observability` 포함 기동

## 5. GitHub Secrets / Variables

### 필수 Secrets

- `SSH_PRIVATE_KEY`
- `SSH_HOST`
- `SSH_PORT`
- `SSH_USER`
- `DISCORD_TOKEN` 또는 `TOKEN`
- `RABBITMQ_USERNAME`
- `RABBITMQ_PASSWORD`

### 권장 Secrets

- `DISCORD_DEV_GUILD_ID`
- `YOUTUBE_REFRESH_TOKEN`
- `YOUTUBE_PO_TOKEN`
- `YOUTUBE_VISITOR_DATA`
- `YOUTUBE_REMOTE_CIPHER_URL`
- `YOUTUBE_REMOTE_CIPHER_PASSWORD`
- `YOUTUBE_REMOTE_CIPHER_USER_AGENT`
- `GRAFANA_ADMIN_USER`
- `GRAFANA_ADMIN_PASSWORD`
- `GRAFANA_ALERT_DISCORD_WEBHOOK_URL`

### 권장 Variables

- `APP_ENV`
- `OBSERVABILITY_ENABLED`
- `GRAFANA_ANONYMOUS_ENABLED`
- `GRAFANA_ALERT_DEFAULT_RECEIVER`
- `GRAFANA_ALERT_NOOP_WEBHOOK_URL`
- `GRAFANA_ALERT_DISCORD_AVATAR_URL`
- `GRAFANA_ALERT_DISCORD_USE_USERNAME`
- `GRAFANA_IMAGE`
- `PROMETHEUS_IMAGE`
- `LOKI_IMAGE`
- `ALLOY_IMAGE`
- `REDIS_EXPORTER_IMAGE`

기본 권장값:

- `APP_ENV=prod`
- `OBSERVABILITY_ENABLED=true`
- `GRAFANA_ANONYMOUS_ENABLED=false`
- `GRAFANA_ALERT_DEFAULT_RECEIVER=observability-noop`

실제 Discord 알림까지 켜려면:

- `GRAFANA_ALERT_DEFAULT_RECEIVER=observability-discord`
- `GRAFANA_ALERT_DISCORD_WEBHOOK_URL=<실제 Discord webhook URL>`

## 6. YouTube 재생 관련 환경변수

다음 값은 필요 시 순서대로 붙인다.

1. `YOUTUBE_REFRESH_TOKEN`
2. `YOUTUBE_PO_TOKEN`
3. `YOUTUBE_VISITOR_DATA`
4. `YOUTUBE_REMOTE_CIPHER_URL`
5. `YOUTUBE_REMOTE_CIPHER_PASSWORD`
6. `YOUTUBE_REMOTE_CIPHER_USER_AGENT`

## 7. 관측성 자동 배포 조건

`OBSERVABILITY_ENABLED=true`일 때:

- `prometheus`
- `loki`
- `alloy`
- `redis-exporter`
- `grafana`

가 release와 함께 자동 기동된다.

또한 `ops/observability/**` 파일도 서버 release 디렉터리로 같이 업로드된다.

## 8. 수동 배포

GitHub Actions 없이도 아래처럼 가능하다.

```bash
bash /home/ubuntu/dis-bot/deploy.sh <git-sha>
```

단, 먼저 `/home/ubuntu/dis-bot/incoming` 아래에 아래 파일이 있어야 한다.

- `discord-gateway-<git-sha>.tar.gz`
- `discord-audio-node-<git-sha>.tar.gz`
- `.env.cicd`
- `docker-compose.yml`
- `ops/`

## 9. 수동 확인

관측성 포함 전체 스택 확인:

```bash
cd /home/ubuntu/dis-bot/current
docker compose --profile observability --env-file .env ps
```

Grafana health 확인:

```bash
curl http://127.0.0.1:3000/api/health
```

Prometheus rules 확인:

```bash
curl http://127.0.0.1:9090/api/v1/rules
```
