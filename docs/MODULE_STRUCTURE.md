# 모듈 구조

## 개요

현재 저장소는 하나의 스프링 앱에 역할을 억지로 욱여넣는 구조가 아니라, 공용 코어와 두 실행 앱으로 나뉜 멀티모듈 구조다.

- `modules/common-core`
- `apps/gateway-app`
- `apps/audio-node-app`

목표는 아래와 같다.

- 공용 코드는 한 곳에 둔다.
- 실행 경계는 gateway와 audio-node로 나눈다.
- 각 앱은 독립적인 JAR과 Docker 이미지로 실행한다.
- 묶어서 올릴 때만 compose와 배포 파이프라인이 두 앱을 함께 다룬다.

## 디렉터리

```text
apps/
  gateway-app/
    build.gradle
    Dockerfile
    README.md
    src/main/java/discordgateway/gateway/
    src/main/java/discordgateway/application/
    src/main/java/discordgateway/discord/
    src/main/resources/application.yml
  audio-node-app/
    build.gradle
    Dockerfile
    README.md
    src/main/java/discordgateway/audionode/
    src/main/java/discordgateway/application/
    src/main/java/discordgateway/discord/
    src/main/resources/application.yml
modules/
  common-core/
    build.gradle
    README.md
    src/main/java/discordgateway/
    src/main/resources/application-common.yml
docs/
ops/
docker-compose.yml
deploy.sh
```

## 모듈별 책임

### `modules/common-core`

- 공용 도메인
- command / event 모델
- worker 로직
- 재생 엔진
- Redis repository
- RabbitMQ command infrastructure
- 공용 Spring bootstrap
- 공용 observability 설정

### `apps/gateway-app`

- Discord slash command 수신
- autocomplete
- 입력 검증
- command bus로 명령 전달
- slash command 등록

즉 사용자 요청의 진입점이다.

### `apps/audio-node-app`

- RabbitMQ command 소비
- 실제 재생과 음성 연결
- playback recovery
- runtime 복구 시작

즉 명령을 실제로 수행하는 실행 노드다.

## 빌드 구조

Gateway만:

```powershell
.\gradlew.bat :apps:gateway-app:bootJar
```

Audio Node만:

```powershell
.\gradlew.bat :apps:audio-node-app:bootJar
```

둘 다:

```powershell
.\gradlew.bat bootJarAll
```

산출물:

- `apps/gateway-app/build/libs/gateway-app.jar`
- `apps/audio-node-app/build/libs/audio-node-app.jar`

예전 단일 산출물 `TBot1-all.jar` 구조는 더 이상 쓰지 않는다.

## Docker 구조

- gateway 이미지: `discord-gateway`
- audio-node 이미지: `discord-audio-node`
- compose는 두 이미지를 각각 올린다.

관측성은 기본 스택과 분리하기 위해 `observability` profile로 붙어 있다.

## 왜 이렇게 나눴는가

- Discord interaction 처리와 실제 재생 처리는 워크로드 성격이 다르다.
- 재생 노드는 Redis, RabbitMQ, JDA voice 복구까지 포함하므로 운영 경계가 다르다.
- 공용 코드를 별도 모듈로 두지 않으면 두 앱 중복이 커진다.

현재 구조는 “공용 코어 + 두 실행 앱”을 가장 단순하게 유지하는 쪽으로 정리한 결과다.
