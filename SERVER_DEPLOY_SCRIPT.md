# 서버 배포 스크립트 가이드

## 1. 목적

이 문서는 GitHub Actions와 서버의 [deploy.sh](/C:/Users/s0302/OneDrive/바탕%20화면/portpolio/dis/deploy.sh)가 현재 프로젝트를 어떻게 배포하는지 정리한 문서다.

현재 배포 구조는 다음 기준으로 동작한다.

- `gateway`
- `audio-node`
- `redis`
- `rabbitmq`

## 2. 배포 흐름

현재 배포는 아래 순서로 진행된다.

1. GitHub Actions가 `bootJar`로 애플리케이션을 빌드한다.
2. Docker 이미지를 `discord-bot:<git-sha>` 태그로 만든다.
3. 이미지를 `tar.gz`로 묶는다.
4. 서버의 `/home/ubuntu/dis-bot/incoming`으로 산출물을 업로드한다.
5. 서버에서 `deploy.sh <git-sha>`를 실행한다.
6. `deploy.sh`가 이미지를 `docker load`로 적재한다.
7. 이전 릴리스가 있으면 먼저 `docker compose down --remove-orphans`로 내린다.
8. `docker compose up -d --no-build --remove-orphans`로 새 릴리스를 올린다.

## 3. 서버 디렉터리 구조

기본 배포 경로는 아래와 같다.

```text
/home/ubuntu/dis-bot
├─ incoming
│  └─ ops
├─ releases
│  └─ <git-sha>
│     └─ ops
└─ current -> /home/ubuntu/dis-bot/releases/<git-sha>
```

각 경로의 의미는 다음과 같다.

- `incoming`
  - GitHub Actions가 업로드한 최신 산출물 임시 보관 위치
- `incoming/ops`
  - 운영 스크립트 임시 보관 위치
- `releases/<git-sha>`
  - 실제 배포 릴리스 디렉터리
- `current`
  - 현재 활성 릴리스를 가리키는 심볼릭 링크

## 4. deploy.sh 동작

[deploy.sh](/C:/Users/s0302/OneDrive/바탕%20화면/portpolio/dis/deploy.sh)는 아래 작업을 수행한다.

- 전달받은 `git-sha` 기준으로 릴리스 디렉터리를 만든다.
- `incoming`의 `docker-compose.yml`, `.env.cicd`, 이미지 아카이브를 릴리스 디렉터리로 복사한다.
- `incoming/ops`가 있으면 운영 스크립트도 같이 복사한다.
- `docker load`로 이미지를 적재한다.
- 이전 릴리스가 있으면 compose 프로젝트 기준으로 먼저 내린다.
- 예전 단일 배포 컨테이너나 고정 이름 컨테이너가 남아 있으면 정리한다.
- `current`를 새 릴리스로 교체한다.
- 새 릴리스 디렉터리에서 compose를 다시 올린다.
- 오래된 릴리스는 최근 5개만 남기고 정리한다.

기본 Compose 프로젝트 이름은 `discord-bot`이다.

## 5. 서버 사전 준비

서버에는 최소한 아래가 준비되어 있어야 한다.

- Docker 설치
- Docker Compose v2 사용 가능
- 배포 계정이 Docker 명령을 실행할 수 있음
- `/home/ubuntu/dis-bot`에 쓰기 가능

최초 1회는 아래처럼 준비하면 된다.

```bash
mkdir -p /home/ubuntu/dis-bot/incoming/ops
mkdir -p /home/ubuntu/dis-bot/releases
```

## 6. GitHub Secrets

현재 워크플로가 기대하는 핵심 시크릿은 아래와 같다.

- `SSH_PRIVATE_KEY`
- `SSH_HOST`
- `SSH_PORT`
- `SSH_USER`
- `DISCORD_TOKEN` 또는 `TOKEN`
- `DISCORD_DEV_GUILD_ID`
- `YOUTUBE_REFRESH_TOKEN`
- `RABBITMQ_USERNAME`
- `RABBITMQ_PASSWORD`

## 7. YouTube 재생 관련 환경변수

기본적으로 YouTube 재생에는 아래 값들을 사용할 수 있다.

- `YOUTUBE_REFRESH_TOKEN`
  - OAuth refresh token
- `YOUTUBE_OAUTH_INIT`
  - OAuth bootstrap 모드 여부
- `YOUTUBE_PO_TOKEN`
  - YouTube `poToken`
- `YOUTUBE_VISITOR_DATA`
  - `poToken`과 같이 써야 하는 visitor data
- `YOUTUBE_REMOTE_CIPHER_URL`
  - remote cipher server URL
- `YOUTUBE_REMOTE_CIPHER_PASSWORD`
  - remote cipher 인증 비밀번호가 있을 때 사용
- `YOUTUBE_REMOTE_CIPHER_USER_AGENT`
  - remote cipher 호출용 user-agent가 필요할 때 사용

권장 순서는 아래와 같다.

1. `YOUTUBE_REFRESH_TOKEN` 먼저 적용
2. 여전히 `sig function`, `403`, `All clients failed to load the item`가 나오면 `YOUTUBE_PO_TOKEN`, `YOUTUBE_VISITOR_DATA` 적용
3. 그래도 재생이 안 되면 `YOUTUBE_REMOTE_CIPHER_URL` 기반 remote cipher 적용

## 8. 수동 배포

GitHub Actions 없이 수동으로도 같은 방식으로 배포할 수 있다.

```bash
bash /home/ubuntu/dis-bot/deploy.sh <git-sha>
```

단, 아래 파일이 먼저 `/home/ubuntu/dis-bot/incoming`에 올라가 있어야 한다.

- `discord-bot-<git-sha>.tar.gz`
- `.env.cicd`
- `docker-compose.yml`

운영 스크립트까지 같이 반영하려면 `/home/ubuntu/dis-bot/incoming/ops`도 같이 채워져 있어야 한다.

## 9. 운영 스크립트

배포 후에는 `current/ops`에서 아래 스크립트를 사용할 수 있다.

- [ops/replay-command-dlq.sh](/C:/Users/s0302/OneDrive/바탕%20화면/portpolio/dis/ops/replay-command-dlq.sh)
  - command DLQ 재처리
- [ops/cleanup-legacy-deploy.sh](/C:/Users/s0302/OneDrive/바탕%20화면/portpolio/dis/ops/cleanup-legacy-deploy.sh)
  - 예전 배포 컨테이너 정리
- [ops/smoke-check.sh](/C:/Users/s0302/OneDrive/바탕%20화면/portpolio/dis/ops/smoke-check.sh)
  - health / compose 상태 점검

운영 절차 전체는 [OPERATIONS_RUNBOOK.md](/C:/Users/s0302/OneDrive/바탕%20화면/portpolio/dis/OPERATIONS_RUNBOOK.md)에 정리되어 있다.
