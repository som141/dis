# DIS 메이플 Discord 연결 운영 가이드

## 필요한 설정

1. 저장소의 `.env.example`을 참고해 `.env`에 기존 DIS 봇 설정을 유지한다.
2. Discord 개발자 포털에서 **같은 봇 애플리케이션**의 OAuth2 Client ID와 Client Secret을 확인한다. `identify` 권한을 쓰고 Redirect URI를 `https://<도메인>/maple/oauth/callback`으로 등록한다.
3. `.env`에 다음 값을 설정한다.

| 값 | 의미 |
| --- | --- |
| `MAPLE_DISCORD_ENABLED=true` | `/maple` 연결 활성화 |
| `MAPLE_PUBLIC_BASE_URL=https://<도메인>` | 사용자에게 보여줄 포털 주소 |
| `MAPLE_DISCORD_REDIRECT_URI=https://<도메인>/maple/oauth/callback` | Discord에 등록한 URI와 정확히 일치 |
| `MAPLE_DISCORD_CLIENT_ID`, `MAPLE_DISCORD_CLIENT_SECRET` | 해당 Discord 애플리케이션의 OAuth2 값 |
| `MAPLE_LINK_SIGNING_KEY`, `MAPLE_DISCORD_INTERNAL_KEY` | 각각 독립적인 임의 문자열, 32자 이상 |
| `MAPLE_PORTAL_USERS` | 저장 가능한 사용자 수. 기본 2 |

로컬 시험은 `http://127.0.0.1:18877`과 같은 주소를 사용할 수 있지만 다른 사람의 PC에서는 열 수 없다. 공개 서버는 외부 HTTPS 프록시를 `maple-edge:80`으로 연결하고 `MAPLE_EDGE_BIND=127.0.0.1`을 유지해 내부 HTTP 포트를 직접 노출하지 않는다. 외부 프록시가 원래 Host를 전달해야 한다.

넥슨 서비스 API 키는 기존 포털 비밀 파일 `tools/maple-local-prototype/.docker-secrets/nexon-service-key`에 둔다. 파일 내용은 Git에 올리지 않는다. OAuth2 Client Secret과 두 서명/내부 키도 `.env`에만 둔다.

## 실행

Docker Desktop이 실행 중인 환경에서 저장소 루트에서:

```powershell
.\gradlew.bat bootJarAll
docker compose --profile maple config --quiet
docker compose --profile maple up -d --build gateway maple-portal maple-edge
docker compose --profile maple ps
```

기존 전체 DIS 서비스가 이미 구동 중이라면 Gateway만 교체되는지 확인한다. `maple-edge`를 통해 포털을 열고 Discord 개발 길드에서 `/maple connect`를 실행한다. 같은 사람의 다른 기기 연결은 이전 포털 쿠키를 무효화한다. `/maple status`의 작업 ID로 `/maple stop`을 실행한다. 웹의 감시 작업은 해당 사용자에게만 DM된다.

### 인바운드가 없는 Ubuntu 서버 배포

`main` 푸시가 검증된 이미지 릴리스를 만든다. [아웃바운드 전용 배포 안내](OUTBOUND_ONLY_DEPLOY.md)에 따라 Ubuntu 서버가 릴리스를 가져와 적용한다. GitHub 변수 `MAPLE_DISCORD_ENABLED`가 `true`일 때만 메이플 포털 이미지가 릴리스에 포함된다. 설정 전에는 Gateway 코드만 배포되고 `/maple` 연결은 활성화되지 않는다.

활성화 시 GitHub Variables에 `MAPLE_DISCORD_ENABLED=true`를 등록하고, 서버의 `/home/ubuntu/dis-bot/.env`에 공개 주소·Discord OAuth2 값·두 비밀 키를 설정한다. 넥슨 서비스 키는 서버의 `/home/ubuntu/dis-bot/secrets/maple-nexon-service-key` 파일에 둔다. 실제 HTTPS 진입점이 `maple-edge`에 연결된 후 워크플로를 다시 실행한다. 비밀값은 GitHub 릴리스에 포함하지 않는다.

### ngrok HTTPS 진입점

인바운드 포트를 열 수 없는 Ubuntu 서버는 ngrok의 고정 도메인을 사용할 수 있다. 공식 ngrok 에이전트를 서버에 설치하고 `ngrok config add-authtoken`으로 해당 계정의 토큰을 등록한다. `/home/ubuntu/dis-bot/ngrok.env`에는 `NGROK_URL=https://<할당된 ngrok 도메인>`만 넣는다. 토큰은 Git이나 Discord에 올리지 않는다.

`ops/dis-maple-ngrok.service`를 `/etc/systemd/system/`에 설치해 `systemctl daemon-reload`와 `systemctl enable --now dis-maple-ngrok.service`를 실행한다. 서비스는 호스트의 `127.0.0.1:18877`만 ngrok HTTPS 도메인으로 전달한다. Compose의 `MAPLE_EDGE_BIND=127.0.0.1`을 유지한다. Discord OAuth2 Redirect URI, `MAPLE_DISCORD_REDIRECT_URI`는 모두 `https://<할당된 ngrok 도메인>/maple/oauth/callback`으로 일치시킨다. `MAPLE_PUBLIC_BASE_URL`에는 같은 도메인의 HTTPS 원점만 넣는다.

배포 후 `systemctl is-active dis-maple-ngrok.service`, `docker compose --profile maple ps`, 공개 도메인의 `/` 응답을 확인한다. ngrok 에이전트가 재시작되어도 고정 도메인을 재사용하므로 systemd가 연결을 복구한다.

## 운영 경계

- 기본 2명 상한은 총 사용자 기록 수다. 상한 증가는 포털 메모리/CPU 조정과 함께 한다. 현재 구성은 자동 사용자 삭제를 하지 않는다.
- 포털/게이트웨이를 각각 여러 복제본으로 늘리지 않는다. 계정 예약과 알림 발송 상태는 단일 인스턴스 전제다.
- Discord DM을 막아 둔 사용자는 알림을 받지 못하며, 웹의 작업 기록을 확인할 수 있다.
- 포털 볼륨 `maple-portal-data`는 사용자 쿠키 해시, 작업 상태, 브라우저 프로필을 담는다. 운영 백업과 접근 제한이 필요하다.
- 현재 독립 로컬 포털(`tools/maple-local-prototype/compose.portal.yaml`)의 데이터는 새 Compose 볼륨으로 자동 이전되지 않는다.
