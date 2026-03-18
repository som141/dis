# 이벤트 계약

## 1. 현재 이벤트의 역할

현재 `MusicEvent`는 서비스 간 메시지 transport가 아니라, audio-node 내부 상태 변화를 기록하고 관측하기 위한 로컬 이벤트 계약이다.

즉 현재 구조에서 이벤트는 아래처럼 사용된다.

- 발행 위치: 공용 재생 코어
- transport: Spring local event
- 목적: 구조 로그, 상태 추적, 관측성 보강

RabbitMQ event publish는 현재 사용하지 않는다.

## 2. 중심 타입

- `MusicEvent`
- `MusicEventFactory`
- `MusicEventPublisher`
- `SpringMusicEventPublisher`
- `MusicEventLogListener`

## 3. 공통 필드

모든 이벤트는 아래 공통 필드를 가진다.

- `eventId`
- `schemaVersion`
- `occurredAtEpochMs`
- `producer`
- `guildId`
- `correlationId`
- `eventType`

`schemaVersion`과 `correlationId`는 command trace와 연결된다.

## 4. 이벤트 타입

### `voice.connection.changed`

음성 채널 연결 또는 해제를 나타낸다.

추가 필드:

- `action`
- `voiceChannelId`
- `userId`
- `reason`

### `playback.autoplay.changed`

자동 재생 on/off 전환을 나타낸다.

추가 필드:

- `enabled`

### `track.queued`

큐 적재를 나타낸다.

추가 필드:

- `identifier`
- `title`
- `author`
- `source`

### `track.playback.changed`

재생 상태 전이를 나타낸다.

추가 필드:

- `state`
- `identifier`
- `title`
- `author`
- `source`
- `detail`

가능한 `state`:

- `STARTED`
- `FINISHED`
- `STOPPED`
- `PAUSED`
- `RESUMED`

### `queue.cleared`

큐 비우기를 나타낸다.

추가 필드:

- `hadEntries`
- `currentTrackPreserved`

### `track.load.failed`

트랙 로드 실패를 나타낸다.

추가 필드:

- `identifier`
- `source`
- `failureType`
- `message`

## 5. 발행 지점

주요 발행 지점은 아래와 같다.

- `MusicWorkerService`
  - 음성 채널 연결/해제
  - autoplay 상태 변경
- `PlayerManager`
  - load 실패
- `TrackScheduler`
  - queue 적재
  - 재생 시작
  - 재생 종료
  - stop / clear
  - recovery playback

## 6. producer 값

`MusicEventFactory`는 `app.node-name`을 읽어서 `producer` 값을 만든다.

보통 값:

- `gateway-1`
- `audio-node-1`

현재 재생 관련 이벤트는 대부분 `audio-node-1`로 기록되는 것이 정상이다.

## 7. correlationId

`correlationId`는 현재 command trace의 `commandId`를 따라간다. 따라서 `/play` 하나로 발생한 여러 상태 전이 이벤트를 같은 흐름으로 묶을 수 있다.

## 8. 현재 발행 방식

현재 이벤트는 아래 방식으로 고정되어 있다.

- publish 실패 대비 outbox 없음
- RabbitMQ event transport 없음
- Spring local event로만 발행

즉 현재 이벤트 계약은 “외부 서비스 간 전송 계약”보다 “재생 코어의 관측 계약”에 가깝다.

## 9. 로그 관측

`MusicEventLogListener`가 모든 이벤트를 받아 구조 로그로 남긴다.

대표 키:

- `eventType`
- `schemaVersion`
- `correlationId`
- `guildId`
- `producer`
- `summary`
