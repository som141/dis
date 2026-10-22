# YouTube cipher 이미지

`youtube-cipher`는 YouTube player script의 서명·URL 해독을 처리하는 내부 서비스다. 음악 검색·인증·음원 다운로드는 봇이 수행한다. cipher의 health가 정상이더라도 로그인 요구나 음원 HTTP 403은 별도로 발생할 수 있다.

## 구성과 실행

[Dockerfile](Dockerfile)은 upstream yt-cipher revision `1e1fd8e2f34ca90cf23545be72e46307bd3d3d2a`의 이미지를 digest로 고정한다. 정적 BusyBox를 추가해 `/metrics` 응답으로 HTTP 준비 상태를 검사한다.

저장소 루트에서:

```bash
docker compose --env-file .env up -d --build --wait youtube-cipher
docker compose --env-file .env logs --tail=50 youtube-cipher
docker compose --env-file .env exec -T youtube-cipher /usr/local/bin/busybox wget -Y off -q -O - http://127.0.0.1:8001/metrics
```

| 설정 | 적용 |
| --- | --- |
| 내부 주소 | `http://youtube-cipher:8001`. 호스트 포트는 기본 비공개 |
| `YOUTUBE_REMOTE_CIPHER_URL` | Gateway·audio가 호출할 주소. 외부 cipher로 변경 가능 |
| `YOUTUBE_REMOTE_CIPHER_PASSWORD` | cipher `API_TOKEN`과 봇 요청 인증에 같은 값 적용 |
| `YOUTUBE_CIPHER_MAX_THREADS` | worker 수, 기본 1 |
| `youtube-cipher-cache` | player script cache를 보존하는 volume |

Compose는 `OVERRIDE_PLAYER_VARIANT=IAS`를 사용한다. 이미지 갱신 시 upstream revision·API 호환성·준비 상태 검사·실제 재생을 함께 확인한다. 외부 cipher URL을 지정해도 현재 Compose의 내부 서비스는 계속 시작된다.

[배포·OAuth 설명](../../docs/operations/SERVER_DEPLOY_SCRIPT.md) · [음악 장애 점검](../../docs/operations/OPERATIONS_RUNBOOK.md) · [운영 파일 목록](../README.md)
