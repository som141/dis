# 이벤트 계약

## 1. 현재 이벤트의 역할

현재 저장소에는 두 종류의 이벤트가 있다.

1. 내부 상태 관측용 `MusicEvent`
2. gateway 응답 완료용 `MusicCommandResultEvent`

둘은 목적이 다르다.

- `MusicEvent`
  - audio-node 내부 상태 변화를 기록하고 관측하기 위한 이벤트
  - transport: Spring local event
- `MusicCommandResultEvent`
  - audio-node 처리 결과를 gateway로 되돌리기 위한 이벤트
  - transport: RabbitMQ

## 2. 내부 상태 이벤트

### 대상

- `MusicEvent`
- `MusicEventFactory`
- `MusicEventPublisher`
- `SpringMusicEventPublisher`
- `MusicEventLogListener`

### 공통 필드

- `eventId`
- `schemaVersion`
- `occurredAtEpochMs`
- `producer`
- `guildId`
- `correlationId`
- `eventType`

### 이벤트 종류

- `voice.connection.changed`
- `playback.autoplay.changed`
- `track.queued`
- `track.playback.changed`
- `queue.cleared`
- `track.load.failed`

### 발행 위치

- `MusicWorkerService`
- `PlayerManager`
- `TrackScheduler`

### 소비 방식

현재는 `MusicEventLogListener`가 받아 구조 로그로 남긴다.

## 3. command result 이벤트

### 대상

- `MusicCommandResultEvent`
- `RabbitMusicCommandResultPublisher`
- `RabbitMusicCommandResultListener`

### 목적

gateway가 `deferReply(true)`로 시작한 Discord interaction을 나중에 마무리할 수 있게, audio-node 처리 결과를 비동기로 전달한다.

### 필드

- `commandId`
- `schemaVersion`
- `occurredAtEpochMs`
- `producer`
- `targetNode`
- `guildId`
- `success`
- `message`
- `ephemeral`
- `resultType`

### resultType 예시

- `SUCCESS`
- `FAILED`
- `IN_PROGRESS`
- `DUPLICATE_REPLAY`

### 발행 위치

- `RabbitMusicCommandListener`

### 소비 위치

- `gateway-app`의 `RabbitMusicCommandResultListener`

### 사용 방식

1. gateway가 `MusicCommandEnvelope`를 publish
2. audio-node가 command 처리
3. audio-node가 `MusicCommandResultEvent` 발행
4. gateway가 `commandId`로 pending interaction을 찾아 original ephemeral reply를 수정

현재 pending interaction은 Redis에 TTL과 함께 저장되며, 저장되는 값은 `InteractionHook` 객체가 아니라 `interaction token`과 메타데이터다.

## 4. producer 값

`MusicEventFactory`와 command/result 발행 경로는 `app.node-name`을 읽어 `producer` 값을 만든다.

보통 값:

- `gateway-1`
- `audio-node-1`

## 5. correlationId

`MusicEvent`의 `correlationId`는 현재 command trace의 `commandId`를 따라간다. 따라서 `/play` 하나로 발생한 여러 상태 전이 이벤트를 같은 흐름으로 묶을 수 있다.

## 6. 현재 발행 방식

- `MusicEvent`: Spring local event
- `MusicCommandResultEvent`: RabbitMQ direct exchange

즉 현재 이벤트 계약은 내부 관측과 사용자 응답 완료 목적을 분리해서 사용한다.
