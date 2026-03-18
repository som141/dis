# 이벤트 계약 정의

## 1. 목적

이 문서는 현재 코드베이스에서 이벤트를 어떤 기준으로 정의했고, 어떤 방식으로 전달하며, 어느 수준까지 전달 보장을 제공하는지 정리한다.

## 2. 설계 원칙

- 이벤트는 "명령"이 아니라 "상태 변화"를 표현한다.
- 이벤트 모델은 transport에 종속되지 않는다.
- 모든 이벤트는 공통 메타데이터를 가진다.
- 같은 명령에서 파생된 이벤트는 `correlationId`로 묶는다.
- 계약 변경을 대비해 `schemaVersion`을 유지한다.
- 실제 transport는 Spring in-process 또는 RabbitMQ로 선택 가능하다.

## 3. 공통 메타데이터

모든 `MusicEvent`는 아래 필드를 공통으로 가진다.

- `eventId`
  - 이벤트 자체의 고유 식별자
- `schemaVersion`
  - 이벤트 계약 버전
- `occurredAtEpochMs`
  - 이벤트 발생 시각
- `producer`
  - 이벤트를 만든 노드 이름
- `guildId`
  - 이벤트가 속한 Discord 길드 ID
- `correlationId`
  - 원인이 된 명령과 연결하기 위한 추적 ID
- `eventType`
  - 사람이 읽을 수 있는 이벤트 타입 문자열

## 4. `correlationId` 정의

현재 `correlationId`는 "이 이벤트를 유발한 명령의 `commandId`"로 정의한다.

흐름은 다음과 같다.

1. Gateway가 `MusicCommandMessage`를 생성한다.
2. `MusicCommandMessage`에는 `commandId`가 들어간다.
3. Worker는 `MusicCommandTraceContext`에 현재 명령 정보를 넣는다.
4. `MusicEventFactory`는 현재 trace를 읽어서 `correlationId`를 채운다.

이 구조 덕분에 `/play` 한 번으로 발생한 여러 이벤트를 같은 추적 ID로 묶을 수 있다.

## 5. `schemaVersion` 정의

현재 프로토콜 버전은 아래 상수로 관리한다.

- `MusicProtocol.SCHEMA_VERSION = 1`

적용 대상은 다음과 같다.

- `MusicCommandMessage`
- `MusicEvent`

즉, 명령과 이벤트 모두 동일한 버전 축으로 관리한다.

## 6. 현재 이벤트 종류

현재 정의된 이벤트는 다음과 같다.

- `voice.connection.changed`
  - 음성 연결/해제 상태 변화
- `playback.autoplay.changed`
  - autoplay 설정 변화
- `track.queued`
  - 곡이 큐에 적재됨
- `track.playback.changed`
  - 재생 시작/종료/중지/일시정지/재개
- `queue.cleared`
  - 큐 비움
- `track.load.failed`
  - 로딩 실패

## 7. 이벤트 생성 위치

이벤트는 주로 아래 계층에서 생성된다.

- `MusicWorkerService`
  - 음성 연결 관련 이벤트
- `PlayerManager`
  - 로드 실패 이벤트
- `TrackScheduler`
  - 큐 적재, 재생 시작/종료/중지, recovery 관련 이벤트

이벤트 인스턴스 생성은 `MusicEventFactory`로 통일했다.

## 8. transport 정의

### `event-transport=spring`

- Spring in-process 이벤트만 발행
- 로컬 로깅과 관측 용도

### `event-transport=rabbitmq`

- Spring in-process 이벤트는 그대로 유지
- 동시에 RabbitMQ exchange로도 발행
- routing key 형식은 아래와 같다

`guild.<guildId>.<eventType>`

예시:

`guild.123456789.track.playback.changed`

## 9. 전달 보장 방식

### 기본 보장

- 이벤트 모델은 transport 독립적으로 유지
- RabbitMQ 사용 시 즉시 발행을 먼저 시도
- 즉시 발행 뒤 broker publisher confirm을 기다린다
- mandatory return이 오면 발행 실패로 간주한다

### 실패 처리

- 즉시 발행이 nack, return, timeout, 예외 중 하나로 실패하면 `MusicEventOutboxRepository`에 저장
- 저장된 이벤트는 `MusicEventOutboxRelay`가 주기적으로 읽는 것이 아니라 먼저 claim한다
- claim한 노드만 재전송을 시도한다
- 성공 시 같은 `claimToken`을 가진 노드만 outbox에서 삭제할 수 있다
- 실패 시 같은 `claimToken`을 가진 노드만 다음 시각으로 재예약할 수 있다
- lease가 만료되면 다른 노드가 다시 claim할 수 있다

현재 outbox 저장소 구현은 아래 두 가지다.

- `InMemoryMusicEventOutboxRepository`
- `RedisMusicEventOutboxRepository`

## 10. claim / lease 모델

`PendingMusicEvent`는 아래 claim 메타데이터를 가진다.

- `claimOwner`
  - 현재 이벤트를 claim한 노드 이름
- `claimToken`
  - 현재 claim의 고유 토큰
- `claimUntilEpochMs`
  - 이 시각 전까지는 다른 노드가 재claim할 수 없음

Redis 구현은 Lua 스크립트로 아래 작업을 원자적으로 수행한다.

- due 이벤트 claim
- `claimToken` 비교 후 성공 삭제
- `claimToken` 비교 후 실패 재예약

## 11. 관련 설정

### 앱 설정

- `app.eventOutboxStore`
  - outbox 저장소 선택
  - 비어 있으면 `app.stateStore`를 따른다

### 메시징 설정

- `messaging.event-transport`
- `messaging.event-exchange`
- `messaging.event-routing-key-prefix`
- `messaging.event-publish-confirm-timeout-ms`
- `messaging.event-outbox-flush-interval-ms`
- `messaging.event-outbox-claim-ttl-ms`
- `messaging.event-outbox-retry-delay-ms`
- `messaging.event-outbox-batch-size`

### Spring Rabbit 설정

- `spring.rabbitmq.publisher-confirm-type=correlated`
- `spring.rabbitmq.publisher-returns=true`
- `spring.rabbitmq.template.mandatory=true`

## 12. 예시 payload

```json
{
  "eventKind": "trackPlaybackChanged",
  "eventId": "5a6f1c1c-9d2b-4f68-9387-b7f8e0d9f8d4",
  "schemaVersion": 1,
  "occurredAtEpochMs": 1773700000000,
  "producer": "audio-node-1",
  "guildId": 123456789,
  "correlationId": "aa27e0e4-5dfd-4de2-a0db-7b8f5c40d2a9",
  "state": "STARTED",
  "identifier": "https://www.youtube.com/watch?v=abc123def45",
  "title": "sample track",
  "author": "sample artist",
  "source": "QUEUE",
  "detail": null
}
```

## 13. 현재 한계와 남은 보강 포인트

- command DLQ 재처리 운영 절차는 아직 문서화/도구화되지 않았다
- 실제 분리 배포 환경에서 gateway / audio-node 이중 JDA 세션 운영 검증이 남아 있다

## 14. 정리

현재 이벤트는 "길드 단위 재생 상태 변화"를 transport 독립적인 계약으로 정의하고, `schemaVersion`과 `correlationId`를 통해 버전 관리와 추적성을 유지한다. RabbitMQ를 사용할 때는 broker confirm / mandatory return을 먼저 확인하고, 실패 시 outbox에 저장한다. 저장된 이벤트는 claim/lease 기반으로 재전송해서 여러 `audio-node`가 같은 outbox를 공유하더라도 같은 이벤트를 동시에 처리할 가능성을 줄이도록 설계했다.
