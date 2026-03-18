# 서버 배포 쉘 스크립트 가이드

## 1. 목적

이 문서는 GitHub Actions가 서버에 전달한 산출물을 어떤 쉘 스크립트로 배포하는지 설명한다.

실제 스크립트 파일은 [deploy.sh](C:/Users/s0302/OneDrive/바탕%20화면/portpolio/dis/deploy.sh)다.

## 2. 배포 방식

현재 배포는 아래 순서로 진행된다.

1. GitHub Actions가 `bootJar`로 JAR를 만든다.
2. Docker 이미지를 `discord-bot:<git-sha>` 태그로 빌드한다.
3. 이미지를 `tar.gz`로 저장한다.
4. 서버로 아래 파일을 업로드한다.
   - Docker 이미지 아카이브
   - `.env.cicd`
   - `docker-compose.yml`
   - `deploy.sh`
   - `ops/replay-command-dlq.sh`
   - `ops/smoke-check.sh`
5. 서버에서 `deploy.sh <git-sha>`를 실행한다.
6. 스크립트가 이미지를 `docker load`로 적재한다.
7. 이전 릴리스가 있으면 먼저 `docker compose down --remove-orphans`로 정리한다.
8. 고정된 Compose 프로젝트 이름으로 `docker compose up -d --no-build --remove-orphans`를 실행해 `gateway`, `audio-node`, `redis`, `rabbitmq`를 갱신한다.

## 3. 서버 디렉터리 구조

기본 배포 경로는 아래를 기준으로 한다.

```text
/home/ubuntu/dis-bot
├─ incoming
│  └─ ops
├─ releases
│  ├─ <git-sha>
│  │  └─ ops
│  └─ ...
└─ current -> /home/ubuntu/dis-bot/releases/<git-sha>
```

각 경로의 의미는 아래와 같다.

- `incoming`
  - GitHub Actions가 업로드한 최신 산출물 임시 보관
- `incoming/ops`
  - 운영용 보조 스크립트 임시 보관
- `releases/<git-sha>`
  - 실제 배포 릴리스 디렉터리
- `releases/<git-sha>/ops`
  - DLQ 재처리, 스모크 체크용 보조 스크립트
- `current`
  - 현재 활성 릴리스를 가리키는 심볼릭 링크

## 4. 스크립트 동작

`deploy.sh`는 아래 작업을 수행한다.

- 인자로 받은 `git-sha`를 기준으로 릴리스 디렉터리를 만든다.
- `incoming` 아래의 `docker-compose.yml`, `.env.cicd`, 이미지 압축 파일을 릴리스 디렉터리로 복사한다.
- `incoming/ops`가 있으면 릴리스 안으로 같이 복사한다.
- `docker load`로 이미지 태그를 로컬 Docker에 적재한다.
- 이전 릴리스가 있으면 그 릴리스의 compose 설정으로 먼저 중지한다.
- 과거 `container_name` 고정 방식으로 띄운 레거시 컨테이너가 남아 있으면 함께 제거한다.
- `current` 링크를 새 릴리스로 바꾼다.
- 새 릴리스 디렉터리에서 고정 Compose 프로젝트 이름으로 `docker compose --env-file .env up -d --no-build --remove-orphans`를 실행한다.
- 처리 후 `incoming` 파일과 `incoming/ops`를 정리한다.
- 오래된 릴리스는 최근 5개만 남기고 정리한다.

현재 기본 Compose 프로젝트 이름은 `discord-bot`이다.
이 값을 고정한 이유는 릴리스 디렉터리 이름이 매번 바뀌더라도 네트워크와 볼륨, 서비스 수명주기를 같은 프로젝트로 관리하기 위해서다.
또한 과거 `dis-bot`, `discord-redis`, `discord-rabbitmq`, `discord-gateway`, `discord-audio-node` 같은 레거시 컨테이너와 충돌하지 않도록 deploy 스크립트에서 자동 정리도 수행한다.

## 5. 필요한 서버 사전 조건

- Docker 설치
- Docker Compose v2 사용 가능
- 배포 유저가 Docker 명령을 실행할 수 있어야 함
- `/home/ubuntu/dis-bot` 디렉터리에 쓰기 가능해야 함

최초 1회는 아래처럼 준비하면 된다.

```bash
mkdir -p /home/ubuntu/dis-bot/incoming/ops
mkdir -p /home/ubuntu/dis-bot/releases
chmod +x /home/ubuntu/dis-bot/deploy.sh
```

## 6. GitHub Actions 시크릿

현재 워크플로가 기대하는 주요 시크릿은 아래와 같다.

- `SSH_PRIVATE_KEY`
- `SSH_HOST`
- `SSH_PORT`
- `SSH_USER`
- `DISCORD_TOKEN`
- `DISCORD_DEV_GUILD_ID`
- `YOUTUBE_REFRESH_TOKEN`
- `RABBITMQ_USERNAME`
- `RABBITMQ_PASSWORD`

추가 환경값:

- `COMPOSE_PROJECT_NAME`
  - 기본값은 `discord-bot`

## 7. 서버에서 수동 배포하는 방법

GitHub Actions 없이 수동으로도 같은 방식으로 배포할 수 있다.

```bash
bash /home/ubuntu/dis-bot/deploy.sh <git-sha>
```

전제 조건:

- `/home/ubuntu/dis-bot/incoming` 아래에 아래 파일이 있어야 한다.
  - `discord-bot-<git-sha>.tar.gz`
  - `.env.cicd`
  - `docker-compose.yml`
- 운영 스크립트도 같이 둘 거라면 `/home/ubuntu/dis-bot/incoming/ops` 아래에 넣으면 된다.

## 8. 배포 후 운영 스크립트

배포가 끝나면 아래 스크립트를 `current/ops` 아래에서 사용할 수 있다.

- [ops/replay-command-dlq.sh](C:/Users/s0302/OneDrive/바탕%20화면/portpolio/dis/ops/replay-command-dlq.sh)
  - command DLQ 재처리
- [ops/smoke-check.sh](C:/Users/s0302/OneDrive/바탕%20화면/portpolio/dis/ops/smoke-check.sh)
  - health endpoint와 compose 상태 점검

운영 절차 전체는 [OPERATIONS_RUNBOOK.md](C:/Users/s0302/OneDrive/바탕%20화면/portpolio/dis/OPERATIONS_RUNBOOK.md)에 정리했다.
