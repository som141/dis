# 인바운드가 없는 Ubuntu 인스턴스에 DIS 배포

GitHub 제공 runner가 `main`을 테스트하고 Docker 이미지를 GitHub Release에 올린다. Ubuntu 인스턴스의 systemd timer가 10분마다 GitHub로 **나가는 HTTPS 연결**을 열어 최신 릴리스를 확인하고 이미지를 내려받는다. 인스턴스에 SSH 인바운드나 GitHub self-hosted runner를 열 필요가 없다. [GitHub runner 통신 요건](https://docs.github.com/en/actions/reference/runners/self-hosted-runners)은 아웃바운드 HTTPS 443을 설명하며, [공개 저장소의 self-hosted runner 위험](https://docs.github.com/en/actions/reference/security/secure-use) 때문에 이 저장소에는 서버 runner를 붙이지 않는다.

## 인스턴스에서 한 번만 준비

Ubuntu 콘솔에서 GitHub 저장소를 가져오고 작업 폴더로 이동할 수 있다.

```bash
sudo apt-get update
sudo apt-get install -y git curl jq ca-certificates util-linux
sudo mkdir -p /home/ubuntu/dis-bot
sudo git clone https://github.com/som141/dis.git /home/ubuntu/dis-bot/source
cd /home/ubuntu/dis-bot/source
```

[Docker의 Ubuntu 설치 안내](https://docs.docker.com/engine/install/ubuntu/)에 따라 Docker Engine과 Compose plugin을 설치한 뒤 `sudo docker version`과 `sudo docker compose version`이 성공하는지 확인한다.

서버의 런타임 설정은 GitHub에 올리지 않는다. 다음 파일을 만든 뒤 `DISCORD_TOKEN`, RabbitMQ/PostgreSQL 암호와 사용하는 시세 공급자 값을 편집한다. `.env.example`의 봇 토큰 예시 값은 실제 토큰이 아니다.

```bash
sudo cp /home/ubuntu/dis-bot/source/.env.example /home/ubuntu/dis-bot/.env
sudo chmod 600 /home/ubuntu/dis-bot/.env
sudo nano /home/ubuntu/dis-bot/.env
```

`MAPLE_DISCORD_ENABLED=false`를 유지한다. 메이플 웹 연결에는 외부 HTTPS 주소와 Discord OAuth2 redirect URI가 추가로 필요하다. 인바운드 전체 차단 상태에서는 Discord 봇의 명령·DM은 동작할 수 있지만, 다른 사용자가 메이플 웹 포털에 접속할 수 없다. 공개할 때는 443 허용 또는 별도 아웃바운드 터널/프록시를 마련한다.

timer를 등록하고 첫 배포를 실행한다.

```bash
sudo install -m 644 /home/ubuntu/dis-bot/source/ops/dis-pull-deploy.service /etc/systemd/system/
sudo install -m 644 /home/ubuntu/dis-bot/source/ops/dis-pull-deploy.timer /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now docker.service dis-pull-deploy.timer
sudo systemctl start dis-pull-deploy.service
sudo systemctl status dis-pull-deploy.service --no-pager
sudo journalctl -u dis-pull-deploy.service -n 80 --no-pager
```

GitHub Actions가 새 성공 릴리스를 만들면 timer가 SHA-256을 확인하고 `deploy.sh`를 실행한다. 릴리스가 바뀌지 않으면 기존 컨테이너를 건드리지 않는다. 작업 상태는 `sudo docker compose -f /home/ubuntu/dis-bot/current/docker-compose.yml --env-file /home/ubuntu/dis-bot/current/.env ps`로 확인한다.

## 메이플 포털을 나중에 켤 때

GitHub Variables의 `MAPLE_DISCORD_ENABLED=true`로 포털 이미지를 릴리스에 포함시키고, [메이플 Discord 운영 가이드](MAPLE_DISCORD_SETUP.md)의 OAuth2 주소·키를 서버 `.env`에 설정한다. 넥슨 서비스 키는 서버의 `/home/ubuntu/dis-bot/secrets/maple-nexon-service-key`에 600 권한으로 둔다. 그런 다음 GitHub Actions `Build Bot Release`를 다시 실행한다. 서버 timer가 다음 릴리스에서 포털을 올린다.
