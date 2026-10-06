# 서버 배포 스크립트 가이드

## 개요

현재 배포는 GitHub Actions `Deploy Bot` 워크플로우와 서버의 `deploy.sh`를 기준으로 동작한다.

배포 대상:

- `gateway-app`
- `audio-node-app`
- `stock-node-app`
- `youtube-cipher` (YouTube 서명 해독 서비스)
- `docker-compose.yml`
- `ops/`
- `ops/observability/`
- `.env.cicd`

## CI 흐름

1. `main` push 또는 `workflow_dispatch`
2. `./gradlew clean test bootJarAll --no-daemon`
3. 세 앱과 `youtube-cipher` Docker image build
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
    discord-youtube-cipher-<sha>.tar.gz
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
      discord-youtube-cipher.tar.gz
      ops/
  current -> /home/ubuntu/dis-bot/releases/<sha>
```

## deploy.sh 동작

- `incoming`에 필요한 파일이 다 있는지 검증
- 새 release 디렉터리 생성
- compose, env, image archive, `ops/` 복사
- `docker compose config --quiet`로 새 설정 검증
- `docker load`로 네 image 적재
- 이전 release가 있으면 `docker compose down --remove-orphans`
- `current` symlink 교체
- 새 release에서 `docker compose up -d --no-build --remove-orphans --wait --wait-timeout 180`
- `youtube-cipher`를 포함한 서비스 준비 상태가 실패하면 배포 실패 처리
- `incoming` 정리
- `RELEASES_TO_KEEP` 개수(기본 2개)를 초과하는 오래된 release 삭제

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
- `YOUTUBE_REMOTE_CIPHER_URL` (기본값 `http://youtube-cipher:8001`)
- `YOUTUBE_REMOTE_CIPHER_USER_AGENT` (기본값 `dis-bot`)

YouTube 관련 선택 Secrets:

- `YOUTUBE_REMOTE_CIPHER_PASSWORD`: cipher의 `API_TOKEN`과 봇의 요청 인증 헤더에 같은 값 사용
- `YOUTUBE_REFRESH_TOKEN`: YouTube OAuth를 사용하는 경우
- `YOUTUBE_PO_TOKEN`, `YOUTUBE_VISITOR_DATA`: WEB 계열 proof of origin을 사용하는 경우

`YOUTUBE_REMOTE_CIPHER_URL`은 기존 Secret, Variable, 내부 기본값 순으로 선택한다. 기존 외부 cipher URL Secret이 있으면 자동으로 내부 서비스로 바뀌지 않으므로, 자체 서비스로 전환할 때 해당 Secret을 제거하거나 내부 주소로 변경한다. 기존 Discord 및 YouTube 인증 정보는 유지한다.

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
docker compose --project-name discord-bot --env-file .env exec -T stock-node sh -c 'printf "STOCK_QUOTE_PROVIDER=%s\n" "$STOCK_QUOTE_PROVIDER"; if [ -n "${FINNHUB_API_KEY:-}" ]; then echo "FINNHUB_API_KEY=set"; else echo "FINNHUB_API_KEY=unset"; fi'
```

## YouTube cipher 서비스

`gateway`와 `audio-node`는 내부 주소 `http://youtube-cipher:8001`을 사용하고, cipher의 HTTP healthcheck가 통과한 뒤 시작한다. 이 서비스는 호스트에 포트를 공개하지 않는다. URL을 직접 지정하면 외부 cipher를 사용할 수 있으며 내부 서비스는 계속 시작한다.

이미지는 [공식 yt-cipher](https://github.com/kikkia/yt-cipher) revision `1e1fd8e2f34ca90cf23545be72e46307bd3d3d2a`의 GHCR digest를 고정한다. distroless 기반 이미지에 digest를 고정한 정적 BusyBox만 추가하여 `/metrics` HTTP 준비 상태를 확인한다. 이미지 정의는 `ops/youtube-cipher/Dockerfile`에 있다. 서버는 CI에서 전달한 image archive를 적재하므로 서버에서 GHCR image를 내려받을 필요가 없다.

`OVERRIDE_PLAYER_VARIANT=IAS`는 upstream이 권장하는 player script 형식을 사용한다. worker 수는 기본 1개이고 `YOUTUBE_CIPHER_MAX_THREADS`로 조절한다. player script cache는 `youtube-cipher-cache` volume에 보존한다. 이미지 digest 갱신 시에는 새 upstream revision과 API 호환성을 확인하고 cipher를 다시 빌드한다.

실제 재생에는 cipher의 `www.youtube.com` HTTPS player script 접근과 audio-node의 YouTube API 및 `*.googlevideo.com` HTTPS 음원 접근이 필요하다. outbound 제한이나 프록시가 있으면 이 목적지를 허용하고 TLS 검증을 유지한다. cipher는 서명 해독만 담당하므로 YouTube의 로그인 요구, bot 확인, WEB의 SABR 응답은 별도로 남을 수 있다. OAuth를 사용하는 경우 youtube-source 1.18.2의 OAuth 적용 수정도 반영된다.

```bash
cd /home/ubuntu/dis-bot/current
docker compose --project-name discord-bot --env-file .env ps youtube-cipher gateway audio-node
docker compose --project-name discord-bot --env-file .env exec -T youtube-cipher /usr/local/bin/busybox wget -Y off -q -O - http://127.0.0.1:8001/metrics
docker compose --project-name discord-bot --env-file .env logs youtube-cipher --tail=50
```

HTTP healthcheck는 서비스 준비 상태를 확인한다. 배포 후 실제 곡 재생에서 `RemoteCipherManager`가 사용되고 오디오가 재생되는지 별도로 확인한다. 토큰이나 signed stream URL이 포함될 수 있는 YouTube DEBUG 로그를 공유할 때는 값을 가린다.

## 알려진 리스크

- 현재 스크립트는 image archive를 release 디렉터리로 복사한 뒤 `docker load`한다.
- 디스크가 작은 서버에서는 배포 중간에 용량이 두 번 잡힐 수 있다.
- 서버에 별도 디스크를 붙여도 `DEPLOY_DIR`과 Docker `data-root`가 루트 디스크를 계속 쓰면 해결되지 않는다.
