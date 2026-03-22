# 코드베이스 분석

## 1. 현재 목표 구조

현재 코드베이스는 아래 방향으로 정리돼 있다.

- Discord 명령 진입과 실제 재생 실행 노드를 분리
- Redis를 단일 상태 저장소로 사용
- RabbitMQ를 command transport와 command result transport로 사용
- 공개 채널 메시지 대신 Discord ephemeral 응답 중심으로 UX 통일
- fallback 경로를 제거하고 실제 운영 경로만 남김

## 2. 모듈 구조

```text
apps/
  gateway-app/
  audio-node-app/
modules/
  common-core/
docs/
ops/
docker-compose.yml
deploy.sh
```

## 3. 모듈별 책임

### `apps/gateway-app`

- slash command 수신
- autocomplete
- 입력 검증
- command envelope 생성
- RabbitMQ producer
- pending interaction 관리
- command result event 소비

### `apps/audio-node-app`

- RabbitMQ consumer
- recovery 시작
- 실제 재생 실행
- command result event 발행
- 유휴 음성 채널 퇴장

### `modules/common-core`

- 공용 command / event 모델
- worker 로직
- playback 엔진
- Redis repository
- RabbitMQ command infrastructure
- 공통 bootstrap

## 4. 코드 경계

### command 경계

- 모델: `MusicCommand`, `MusicCommandMessage`, `MusicCommandEnvelope`
- producer: `RabbitMusicCommandBus`
- consumer: `RabbitMusicCommandListener`
- result: `MusicCommandResultEvent`, `RabbitMusicCommandResultPublisher`

현재 command 경로는 RabbitMQ async publish + result event로 고정되어 있다.

### worker 경계

- 중심 클래스: `MusicWorkerService`

`MusicWorkerService`는 command를 실제 비즈니스 로직으로 바꾸는 중간 계층이다.

### playback 경계

- `PlaybackGateway`
- `PlayerManager`
- `TrackScheduler`
- `GuildMusicManager`

실제 곡 로드, 재생, 큐 전이, stop, skip, clear, recovery playback은 이쪽에서 처리된다.

### state 경계

- `GuildStateRepository`
- `QueueRepository`
- `PlayerStateRepository`
- `ProcessedCommandRepository`
- `GuildPlaybackLockManager`

현재 구현은 모두 Redis 기반이다.

## 5. 현재 설정 구조

### 앱 전용 설정

각 앱은 `spring.application.name`과 `app.node-name`을 직접 가진다.

- `apps/gateway-app/src/main/resources/application.yml`
- `apps/audio-node-app/src/main/resources/application.yml`

### 공통 설정

`modules/common-core/src/main/resources/application-common.yml`에는 아래가 들어 있다.

- Discord token
- actuator
- structured logging
- RabbitMQ command / result 설정
- DLQ replay 설정
- voice idle disconnect 설정

## 6. 현재 transport

### command

- producer: gateway-app
- consumer: audio-node-app
- transport: RabbitMQ async publish
- 보강: dedup + DLQ

### command result

- producer: audio-node-app
- consumer: gateway-app
- transport: RabbitMQ direct exchange
- 목적: original ephemeral reply 수정

### event

- transport: Spring local event
- 목적: 로그와 관측

현재는 Rabbit event transport를 쓰지 않는다.

## 7. 현재 제거된 경로

다음 구현은 현재 코드베이스에서 제거됐다.

- `InMemory*Repository`
- `InMemoryGuildPlaybackLockManager`
- `InProcessMusicCommandBus`
- Rabbit event outbox 계열
- role 기반 런타임 분기
- store selector
- event transport selector
- RabbitMQ RPC
- playback 계층의 공개 채널 재생 결과 메시지

즉 현재 코드는 실제로 운영하는 경로만 남긴 상태다.

## 8. 장점

- bootstrap이 단순하다.
- Redis가 source of truth라는 점이 분명하다.
- gateway와 audio-node 책임이 코드와 디렉터리에서 명확하다.
- command publish와 result consume 흐름이 분리돼 있다.
- 사용자 응답 정책이 ephemeral 기준으로 정리돼 있다.

## 9. 주의점

- pending interaction 저장소는 Redis TTL 기반이라 gateway 재기동 후에도 interaction token 만료 전까지 result event를 이어받을 수 있다.
- 다만 Discord interaction token 자체는 15분 제한이 있으므로 장기 지연 결과는 복구되지 않는다.
- autocomplete 때문에 gateway가 일부 playback 검색 코드를 참조한다.
- YouTube 재생 성공 여부는 서버 네트워크 환경 영향이 크다.
- Grafana 관리자 계정은 env를 바꿔도 기존 볼륨에는 자동 반영되지 않는다.

## 10. 결론

현재 코드베이스는 `공용 코어 + 두 실행 앱` 구조를 유지하면서도, 실제 운영 경로만 남긴 단순화된 아키텍처로 정리돼 있다. 특히 최근 변경으로 command 흐름이 RPC에서 비동기 publish/result event 구조로 바뀌었고, Discord 응답도 ephemeral 중심으로 일관되게 맞춰졌다.
