# 서버 배포 스크립트 가이드

## 1. 개요

현재 원격 배포는 GitHub Actions와 `deploy.sh`를 기준으로 동작한다.

배포 대상:

- `gateway-app`
- `audio-node-app`
- `redis`
- `rabbitmq`
- 관측성 스택
  - `prometheus`
  - `loki`
  - `alloy`
  - `redis-exporter`
  - `grafana`

단, 관측성 스택은 `OBSERVABILITY_ENABLED=true`일 때만 같이 올라간다.

## 2. 배포 흐름

1. `main` push 또는 workflow_dispatch
2. GitHub Actions가 `bootJarAll` 수행
3. `gateway-app`, `audio-node-app` 이미지를 `<git-sha>` 태그로 빌드
4. 이미지를 `tar.gz`로 저장
5. `.env.cicd`, `docker-compose.yml`, `ops/`, `ops/observability/`를 서버 `incoming`으로 업로드
6. 서버에서 `deploy.sh <git-sha>` 실행
7. `deploy.sh`가 release 디렉터리를 만들고 파일을 복사
8. `docker load`로 이미지를 적재
9. 이전 release가 있으면 `docker compose down --remove-orphans`
10. 새 release를 `current` 심볼릭 링크로 전환
11. `OBSERVABILITY_ENABLED` 값에 따라 일반 compose 또는 `--profile observability`로 기동

## 3. 서버 디렉터리 구조

```text
/home/ubuntu/dis-bot
├─ incoming/
│  └─ ops/
│     └─ observability/
├─ releases/
│  └─ <git-sha>/
│     ├─ docker-compose.yml
│     ├─ .env
│     ├─ discord-gateway.tar.gz
│     ├─ discord-audio-node.tar.gz
│     └─ ops/
│        └─ observability/
└─ current -> /home/ubuntu/dis-bot/releases/<git-sha>
```

## 4. `deploy.sh`가 하는 일

- release 디렉터리 생성
- `incoming` 파일 복사
- `ops/` 복사 및 실행 권한 부여
- gateway/audio-node 이미지 `docker load`
- 레거시 고정 이름 컨테이너 자동 정리
- 이전 release compose down
- `current` 심볼릭 링크 갱신
- `OBSERVABILITY_ENABLED`에 따라 compose up
- `incoming` 정리
- 오래된 release 5개만 유지

## 5. Compose 프로젝트 이름

원격 배포는 `COMPOSE_PROJECT_NAME=discord-bot` 기준으로 고정되어 있다.

이렇게 하는 이유:

- release 디렉터리 이름이 바뀌어도 compose 프로젝트명이 매번 달라지지 않게 하기 위해서
- 예전 `container_name` 충돌 문제를 피하기 위해서

## 6. GitHub Secrets

### 필수

- `SSH_PRIVATE_KEY`
- `SSH_HOST`
- `SSH_PORT`
- `SSH_USER`
- `DISCORD_TOKEN` 또는 `TOKEN`
- `RABBITMQ_USERNAME`
- `RABBITMQ_PASSWORD`

### 권장

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

## 7. GitHub Variables

권장 값:

- `APP_ENV=prod`
- `OBSERVABILITY_ENABLED=true`
- `GRAFANA_ANONYMOUS_ENABLED=false`
- `GRAFANA_ALERT_DEFAULT_RECEIVER=observability-noop`
- `GRAFANA_ALERT_NOOP_WEBHOOK_URL=http://127.0.0.1:9/disabled`
- `GRAFANA_ALERT_DISCORD_AVATAR_URL=`
- `GRAFANA_ALERT_DISCORD_USE_USERNAME=true`

이미지 버전 오버라이드가 필요하면:

- `GRAFANA_IMAGE`
- `PROMETHEUS_IMAGE`
- `LOKI_IMAGE`
- `ALLOY_IMAGE`
- `REDIS_EXPORTER_IMAGE`

## 8. 관측성 자동 배포 조건

`OBSERVABILITY_ENABLED=true`면 아래가 같이 올라간다.

- `prometheus`
- `loki`
- `alloy`
- `redis-exporter`
- `grafana`

`false`면 앱과 Redis/RabbitMQ만 올라간다.

## 9. 수동 배포

GitHub Actions 없이 수동으로 하려면 먼저 `incoming/`에 아래를 넣어야 한다.

- `discord-gateway-<git-sha>.tar.gz`
- `discord-audio-node-<git-sha>.tar.gz`
- `.env.cicd`
- `docker-compose.yml`
- `ops/`

그 다음:

```bash
bash /home/ubuntu/dis-bot/deploy.sh <git-sha>
```

## 10. 배포 후 확인

전체 상태:

```bash
cd /home/ubuntu/dis-bot/current
docker compose --env-file .env ps
```

관측성 포함 상태:

```bash
cd /home/ubuntu/dis-bot/current
docker compose --profile observability --env-file .env ps
```

Grafana health:

```bash
curl http://127.0.0.1:3000/api/health
```

Prometheus rules:

```bash
curl http://127.0.0.1:9090/api/v1/rules
```
