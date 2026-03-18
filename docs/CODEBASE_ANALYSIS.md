# 코드베이스 분석

## 1. 현재 목표 구조

현재 코드베이스는 아래 목표에 맞춰 정리되어 있다.

- Discord 명령 진입점과 실제 재생 실행 노드를 분리한다.
- Redis를 단일 상태 저장소로 사용한다.
- RabbitMQ를 command transport로 사용한다.
- 선택형 fallback 경로를 제거하고 운영 경로만 남긴다.

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
- command 생성
- RabbitMQ producer

### `apps/audio-node-app`

- RabbitMQ consumer
- recovery 시작
- 실제 재생 실행

### `modules/common-core`

- 공용 command/event 모델
- worker 로직
- 재생 엔진
- Redis repository
- RabbitMQ command infrastructure
- 공통 bootstrap

## 4. 코드 경계

### command 경계

- 모델: `MusicCommand`, `MusicCommandMessage`
- producer: `RabbitMusicCommandBus`
- consumer: `RabbitMusicCommandListener`

현재 command 경로는 RabbitMQ RPC로 고정되어 있다.

### worker 경계

- 중심 클래스: `MusicWorkerService`

`MusicWorkerService`는 command를 실제 비즈니스 로직으로 바꾸는 중간 계층이다.

### playback 경계

- `PlaybackGateway`
- `PlayerManager`
- `TrackScheduler`
- `GuildMusicManager`

실제 트랙 로드, 재생, 큐 전이, stop, skip, clear, recovery playback은 이쪽에서 처리한다.

### state 경계

- `GuildStateRepository`
- `QueueRepository`
- `PlayerStateRepository`
- `ProcessedCommandRepository`
- `GuildPlaybackLockManager`

현재 구현은 모두 Redis 기반이다.

## 5. 현재 설정 구조

### 앱 전용 설정

각 앱은 `spring.application.name`과 `app.node-name`만 직접 가진다.

- `apps/gateway-app/src/main/resources/application.yml`
- `apps/audio-node-app/src/main/resources/application.yml`

### 공통 설정

`modules/common-core/src/main/resources/application-common.yml`에는 아래가 들어 있다.

- Discord token
- actuator
- structured logging
- RabbitMQ command 설정
- DLQ replay 설정

## 6. 현재 남아 있는 transport

### command

- producer: gateway-app
- consumer: audio-node-app
- transport: RabbitMQ RPC
- 보강: dedup + DLQ

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

즉 현재 코드는 “지금 실제로 도는 경로만 남긴 구조”다.

## 8. 장점

- bootstrap이 단순하다.
- 현재 운영 경로가 코드에서 명확하다.
- Redis가 source of truth라는 점이 분명하다.
- gateway와 audio-node 책임이 코드와 디렉터리 모두에서 명확하다.
- 문서화와 운영 런북이 실제 코드와 맞추기 쉬워졌다.

## 9. 주의점

- `gateway-app`과 `audio-node-app`은 같은 Discord 토큰으로 각각 JDA 세션을 연다.
- autocomplete 때문에 gateway가 공용 playback 검색 코드를 일부 참조한다.
- YouTube 재생 성공 여부는 서버 네트워크 환경의 영향을 크게 받는다.
- Grafana 관리자 계정은 env를 바꾼다고 기존 볼륨에 자동 반영되지 않는다.

## 10. 결론

현재 코드베이스는 “공용 코어 + 두 실행 앱” 구조를 유지하면서도, 운영에 쓰지 않는 fallback과 선택형 분기를 제거한 상태다. 구조, 배포, 운영, 관측성 문서도 이 기준으로 정리해야 일관성이 유지된다.
