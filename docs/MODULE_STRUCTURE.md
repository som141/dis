# 모듈 구조

## 개요

현재 저장소는 `단일 애플리케이션`이 아니라 아래 3개 모듈 축으로 나뉜다.

- `modules/common-core`
- `apps/gateway-app`
- `apps/audio-node-app`

목표는 다음과 같다.

- 공용 엔진과 공용 인프라는 한 곳에 둔다.
- 실행 단위는 gateway와 audio-node로 나눈다.
- 각 앱은 독립 JAR과 독립 Docker 이미지로 실행 가능해야 한다.
- 묶어서 띄울 때만 compose와 배포 파이프라인이 두 앱을 함께 다룬다.

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
    src/test/java/discordgateway/
docs/
  README.md
docker-compose.yml
deploy.sh
.github/workflows/cicd-deploy.yml
```

## 모듈 책임

### `modules/common-core`

- 도메인 모델
- command / event 모델
- worker 로직
- 플레이어 엔진
- Redis 저장소
- RabbitMQ 인프라
- 공용 Spring 설정

즉 두 앱이 함께 써야 하는 모든 공용 코드를 가진다.

### `apps/gateway-app`

- Discord slash command 수신
- autocomplete
- 사용자 입력 검증
- command bus로 명령 전달
- command 등록

즉 사용자 요청의 진입점이다.

### `apps/audio-node-app`

- RabbitMQ command 소비
- 실제 재생과 음성 연결
- playback recovery
- runtime 복구 시작점

즉 명령을 실제로 수행하는 실행 노드다.

## 실행 구조

### gateway 단독 실행

```powershell
.\gradlew.bat :apps:gateway-app:bootJar
java -jar apps/gateway-app/build/libs/gateway-app.jar
```

### audio-node 단독 실행

```powershell
.\gradlew.bat :apps:audio-node-app:bootJar
java -jar apps/audio-node-app/build/libs/audio-node-app.jar
```

### 묶음 실행

```powershell
.\gradlew.bat bootJarAll
docker compose up -d --build
```

## 산출물

- `apps/gateway-app/build/libs/gateway-app.jar`
- `apps/audio-node-app/build/libs/audio-node-app.jar`

단일 `TBot1-all.jar` 기준 구조는 더 이상 사용하지 않는다.

## 배포 구조 변화

이전:

- 단일 JAR
- 단일 Dockerfile
- 단일 이미지

현재:

- gateway-app JAR
- audio-node-app JAR
- 앱별 Dockerfile
- 앱별 Docker 이미지

따라서 GitHub Actions와 `deploy.sh`도 두 이미지를 각각 적재하고 compose에서 함께 올리는 방식으로 변경되었다.
