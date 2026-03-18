# 이벤트 계약

## 1. 현재 이벤트의 역할

현재 `MusicEvent`는 서비스 간 transport가 아니라, 앱 내부 상태 변화를 기록하고 관찰하기 위한 로컬 이벤트다.

즉 현재 구조에서는:

- 발행 위치: `audio-node-app` 내부 공용 코어
- transport: Spring local event
- 주 사용처: 로그, 상태 추적

이벤트는 RabbitMQ로 외부 발행하지 않는다.

## 2. 이벤트 모델

핵심 타입:

- `MusicEvent`
- `MusicEventFactory`
- `MusicEventPublisher`
- `SpringMusicEventPublisher`
- `MusicEventLogListener`

## 3. 발행 지점

이벤트는 주로 아래 지점에서 발행된다.

- `MusicWorkerService`
  - 음성 채널 연결/해제
- `PlayerManager`
  - load 실패
- `TrackScheduler`
  - queue 적재
  - 재생 시작
  - 재생 종료
  - stop / clear
  - recovery 재생

## 4. 현재 이벤트 타입

### `VoiceConnectionChanged`

- 음성 채널 연결/해제

### `AutoPlaySettingChanged`

- 자동재생 on/off 전환

### `TrackQueued`

- 곡이 큐에 적재됨

### `TrackPlaybackChanged`

- 재생 상태 전이
- 예: started, finished, stopped, skipped, load failed

### `QueueCleared`

- 큐 비움 처리

### `TrackLoadFailed`

- 트랙 로드 실패

## 5. 공통 필드

이벤트는 아래 공통 필드를 가진다.

- `eventId`
- `schemaVersion`
- `createdAtEpochMs`
- `producer`
- `guildId`
- `correlationId`

## 6. producer 결정 방식

`MusicEventFactory`는 `app.node-name`을 읽어서 producer 값을 만든다.

즉 현재 producer는 보통 아래처럼 찍힌다.

- `gateway-1`
- `audio-node-1`

실제 재생 관련 이벤트는 `audio-node` 이름으로 남는 것이 정상이다.

## 7. correlationId

`correlationId`는 현재 command trace에서 가져온다.

즉 `/play` 한 번으로 발생한 여러 상태 변화 이벤트를 같은 상관관계로 묶을 수 있다.

## 8. 현재 이벤트 전송 정책

현재는 아래 정책으로 고정되어 있다.

- publish 실패 시 별도 outbox 저장 없음
- RabbitMQ event transport 없음
- Spring local event로만 발행

삭제된 경로:

- `RabbitMusicEventPublisher`
- `RabbitMusicEventSender`
- `MusicEventOutboxRelay`
- `MusicEventOutboxRepository`

## 9. 로그 관측

`MusicEventLogListener`가 `@EventListener`로 이벤트를 받아 구조화 로그를 남긴다.

대표 로그 형식:

- `music-event type=...`
- `schema=...`
- `correlationId=...`
- `guild=...`
- `producer=...`

## 10. 현재 해석 기준

현재 이벤트는 외부 시스템과의 계약이라기보다, audio-node 내부 동작을 설명하는 내부 관측 계약으로 보는 것이 맞다.
