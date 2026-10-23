# Windows 로컬 Codex에서 이어서 하기

이 폴더는 `som141/dis`의 메이플 개인용 프로토타입이다. 기존 Discord 음악·주식 봇과 별도로 실행한다. 사용자는 Windows PC를 사용하며 기본 캐릭터는 **에반이였솜**이다.

## 다음 작업

1. 이 폴더를 Codex의 **로컬 프로젝트**로 연다. Docker Desktop이 Linux 컨테이너 모드로 실행 가능한지 확인한다.
2. [DOCKER.md](DOCKER.md)를 읽고 PowerShell에서 다음 명령으로 시작한다. Node가 없다면 설치된 도구를 확인하고 비밀번호 생성 단계만 Docker 안에서 실행할 수 있게 조정한다.

   ```powershell
   node docker/setup.mjs
   docker compose up -d --build
   docker compose ps
   ```

3. Windows PC에서 실행하므로 SSH 터널 없이 `http://127.0.0.1:17900/vnc.html?autoconnect=1&resize=scale`로 접속한다. 원격 화면 비밀번호는 로컬 `.docker-secrets/vnc-password` 파일에서 사용자가 직접 확인한다.
4. 원격 Chromium의 **공식 경매장 탭에서 사용자가 직접 로그인·OTP**를 진행한다. 프로토타입의 연결 확인으로 본인 캐릭터를 조회한 뒤 에반이였솜을 선택한다.
5. 계산용 공개 캐릭터 API 키는 앱 화면에서 사용자에게 직접 입력하게 한다. 키 없이 검색만 가능하다. 실제 매물 검색과 장비 교체 계산을 대조하고, 검색 한도 안에서 30분 감시를 확인한다.

PC에서 실행하면 PC와 Docker Desktop이 켜져 있어야 감시가 유지된다. Linux 운영 서버로의 이동·배포는 별도 작업이다. 이 요청만으로 운영 봇을 재시작하거나 Git을 푸시하지 않는다.

## 이미 검증한 범위

- Codex Linux 클라우드에서 도커 이미지 빌드와 실제 컨테이너 실행, `healthy` 확인.
- `npm test` 18개 통과. 도커 브라우저 가상 응답으로 로그인 계정 조회·에반 장비 계산·검색·가상 시계의 30분 감시/중복 억제 통과.
- 컨테이너 교체 후 프로필/캐시 볼륨 유지, 브라우저 재시작, 메모리 API 키 삭제 확인.
- 전용 프로필의 배타적 flock과 오래된 Chromium Singleton 프로세스 링크 정리 구현.
- 실제 사용자 로그인·실제 매물 검색은 **미검증**. Codex 클라우드 프록시가 `signin.nexon.com/sdk/inface.js` CONNECT를 403으로 차단했다. 로컬 PC의 접속 결과는 별도로 확인한다.

## 구현 경계

- Docker 모드: `compose.yaml`, `Dockerfile`, `docker/`, `src/docker-server.mjs`, `src/browser-transport.mjs`.
- Windows npm+Chrome 확장 방식도 별도로 남아 있다. Docker 모드에는 확장을 설치하지 않는다.
- 경매장은 로그인한 공식 페이지 안의 fetch로 조회한다. 쿠키를 추출하거나 Node 서버로 옮기지 않는다. 조회·검색 경로만 허용하며 자동 구매·판매는 없다.
- 로그인 프로필과 공개 캐릭터 캐시는 볼륨에 보관한다. API 키·감시·수신함은 메모리 기반이어서 재시작하면 초기화된다.
- 계산은 독립 장비 교체 모델이며 환산주스탯의 템환산과 동일하지 않다.
- Discord 연결·DM 발송·다중 사용자 지원·DB 감시 복원은 미구현이다.
- Codex 클라우드 전용 `compose.cloud.yaml`은 개인 PC에서 사용하지 않는다. 프록시/TLS 제한을 우회하지 않는다.

[전체 실행 안내](README.md) · [도커 안내](DOCKER.md) · [검증 기록](VALIDATION.md)
