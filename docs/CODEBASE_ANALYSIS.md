# 코드베이스 분석

## 1. 현재 목표 구조

현재 코드베이스는 아래 목표에 맞춰 정리되어 있다.

- Discord 명령 진입점과 실제 재생 실행점을 분리
- 공유 상태는 Redis에서만 관리
- 앱 간 명령 전달은 RabbitMQ로만 처리
- 현재 쓰지 않는 fallback 경로는 제거

## 2. 모듈 구조

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

## 3. 모듈별 책임

### `apps/gateway-app`

- slash command 수신
- autocomplete
- command 생성
- RabbitMQ command producer

### `apps/audio-node-app`

- RabbitMQ command consumer
- recovery 시작점
- 실제 재생 실행 앱

### `modules/common-core`

- 공용 command / event 모델
- worker 로직
- 재생 엔진
- Redis 저장소
- RabbitMQ command 인프라
- 공통 bootstrap

## 4. 핵심 도메인 경계

### command

- `MusicCommand`
- `MusicCommandMessage`
- `MusicCommandBus`

현재 command bus 구현은 `RabbitMusicCommandBus`만 남아 있다.

### worker

- `MusicWorkerService`

명령을 실제 비즈니스 로직으로 바꾸는 중심 계층이다.

### playback

- `PlaybackGateway`
- `PlayerManager`
- `TrackScheduler`
- `GuildMusicManager`

실제 트랙 로드, 큐 전이, skip, stop, clear, recovery playback이 여기서 처리된다.

### state

- `GuildStateRepository`
- `QueueRepository`
- `PlayerStateRepository`
- `ProcessedCommandRepository`

현재 구현은 모두 Redis 기반이다.

## 5. 현재 실제 저장소 경로

`ApplicationFactory` 기준으로 아래 저장소는 모두 Redis 구현으로 고정됐다.

- `RedisGuildStateRepository`
- `RedisQueueRepository`
- `RedisPlayerStateRepository`
- `RedisProcessedCommandRepository`
- `RedisGuildPlaybackLockManager`

삭제된 경로:

- `InMemoryGuildStateRepository`
- `InMemoryQueueRepository`
- `InMemoryPlayerStateRepository`
- `InMemoryProcessedCommandRepository`
- `InMemoryGuildPlaybackLockManager`

## 6. 현재 메시징 경로

### command

- producer: `gateway-app`
- consumer: `audio-node-app`
- transport: RabbitMQ RPC
- 안전장치: dedup + DLQ

### event

- transport: Spring local event
- 목적: 로그와 내부 관측

삭제된 event transport 경로:

- Rabbit event publisher
- event outbox relay
- Rabbit event sender

## 7. 현재 설정 구조

### 앱별 설정

각 앱은 현재 최소 설정만 가진다.

- `app.node-name`

### 공통 설정

`application-common.yml`에 아래만 남아 있다.

- Discord 설정
- health / actuator 설정
- RabbitMQ command 설정
- Redis 연결 설정
- 운영용 DLQ replay 설정

삭제된 설정:

- `app.role`
- `app.state-store`
- `app.queue-store`
- `app.player-state-store`
- `app.command-dedup-store`
- `app.event-outbox-store`
- `messaging.command-transport`
- `messaging.event-transport`
- `messaging.event-*`

## 8. 현재 구조의 장점

- 실제 운영 경로만 남아서 읽기 쉬움
- 저장소와 transport 선택 분기가 사라져 bootstrap이 단순해짐
- gateway와 audio-node의 책임이 더 명확해짐
- Redis가 source of truth라는 점이 코드에서 분명해짐

## 9. 현재 구조에서 주의할 점

- gateway와 audio-node는 여전히 같은 Discord 토큰으로 각각 JDA 세션을 연다
- autocomplete를 위해 gateway도 공용 playback 코드를 일부 참조한다
- YouTube 재생 성공 여부는 서버 IP/ASN과 토큰 설정에 영향을 받는다

## 10. 결론

현재 코드베이스는 "공용 코어 + 두 실행 앱" 구조를 유지하면서도, 실제 운영에 쓰지 않는 local fallback과 선택형 분기를 제거한 상태다.
