# Discord Music Bot

현재 저장소는 `공용 코어 + 독립 실행 앱` 기준으로 정리되어 있다.

## 최상위 구조

```text
apps/
  gateway-app/
  audio-node-app/
modules/
  common-core/
docs/
docker-compose.yml
deploy.sh
```

## 구성 요소

- `apps/gateway-app`
  - Discord slash command 수신
  - autocomplete 처리
  - command bus로 명령 전달
- `apps/audio-node-app`
  - RabbitMQ command 소비
  - 실제 음성 연결과 재생
  - playback recovery
- `modules/common-core`
  - 도메인
  - 오디오 엔진
  - Redis/RabbitMQ 인프라
  - 공통 Spring 설정

## 먼저 읽을 문서

- [gateway-app README](apps/gateway-app/README.md)
- [audio-node-app README](apps/audio-node-app/README.md)
- [common-core README](modules/common-core/README.md)
- [문서 인덱스](docs/README.md)

## 빠른 실행

전체 산출물 생성:

```powershell
.\gradlew.bat bootJarAll
```

게이트웨이만 실행:

```powershell
java -jar apps/gateway-app/build/libs/gateway-app.jar
```

오디오 노드만 실행:

```powershell
java -jar apps/audio-node-app/build/libs/audio-node-app.jar
```

묶어서 실행:

```powershell
docker compose up -d --build
```
