# 모듈 구조

## 개요

현재 저장소는 공용 코어와 두 실행 앱으로 분리되어 있다.

- `modules/common-core`
- `apps/gateway-app`
- `apps/audio-node-app`

핵심 원칙은 두 가지다.

1. 실행 단위는 앱 모듈에 둔다.
2. 공용 계약과 재생 코어는 `common-core`에 둔다.

## 디렉터리

```text
apps/
  gateway-app/
    src/main/java/discordgateway/gateway/
      application/
      config/
      presentation/discord/
  audio-node-app/
    src/main/java/discordgateway/audionode/
      config/
      lifecycle/
      recovery/
modules/
  common-core/
    src/main/java/discordgateway/common/
      bootstrap/
      command/
      event/
    src/main/java/discordgateway/playback/
      application/
      audio/
      domain/
    src/main/java/discordgateway/infra/
      audio/
      discord/
      messaging/rabbit/
      redis/
```

## 패키지 루트 규칙

### `discordgateway.gateway.*`

- gateway 전용 진입점
- Discord interaction 처리
- command 생성

### `discordgateway.audionode.*`

- audio-node 전용 진입점
- recovery
- voice idle lifecycle

### `discordgateway.common.*`

- 공용 bootstrap
- 공용 command 계약
- 공용 event 계약

### `discordgateway.playback.*`

- 실제 재생 비즈니스 로직
- worker 서비스
- scheduler
- 도메인 모델 / repository port

### `discordgateway.infra.*`

- Redis 구현
- RabbitMQ 구현
- Discord/JDA 구현
- playback gateway 구현

## 모듈별 책임

### `modules/common-core`

- 공용 계약과 코어 로직 제공
- 실행 진입점은 없음
- 최종 산출물은 앱 모듈에서 만든다

### `apps/gateway-app`

- Discord slash command 수신
- autocomplete 처리
- 입력 검증
- `MusicCommand` 생성 후 RabbitMQ RPC 전송

### `apps/audio-node-app`

- RabbitMQ command 소비
- 실제 재생과 음성 연결 실행
- recovery 수행
- 유휴 음성 채널 자동 퇴장 수행

## 빌드

Gateway만:

```powershell
.\gradlew.bat :apps:gateway-app:bootJar
```

Audio Node만:

```powershell
.\gradlew.bat :apps:audio-node-app:bootJar
```

전체:

```powershell
.\gradlew.bat bootJarAll
```

산출물:

- `apps/gateway-app/build/libs/gateway-app.jar`
- `apps/audio-node-app/build/libs/audio-node-app.jar`

## 정리 기준

이번 패키지 재정리의 목적은 “기능이 어디에 속하는지 패키지 이름만 보고도 구분되게 만드는 것”이다. 즉 예전처럼 `application`, `discord`, `infrastructure` 같은 넓은 이름을 앱 경계 바깥으로 공유하지 않고, 앱 경계와 공용 경계를 패키지 루트에서 바로 드러내도록 정리했다.
