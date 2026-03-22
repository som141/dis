# dis 리팩터 플랜

## 목적

이 문서는 `dis` 저장소의 구조 정리와 음성 채널 유휴 퇴장 기능 추가 작업을 Codex 기준으로 정리한 실행 계획 문서다. 현재는 주요 구현이 반영된 상태이며, 이 문서는 “원래 목표”와 “현재 완료 상태”를 함께 기록한다.

## 기준 구조

현재 저장소는 아래 구조를 사용한다.

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

현재 역할 분리는 다음과 같다.

- `apps/gateway-app`
  - Discord slash command 진입
  - autocomplete 처리
  - command 생성
  - RabbitMQ RPC producer
- `apps/audio-node-app`
  - RabbitMQ command consumer
  - 실제 재생 / 음성 연결 / 복구 실행
  - 유휴 음성 채널 자동 퇴장
- `modules/common-core`
  - 공용 command / event 계약
  - playback 코어
  - Redis / RabbitMQ / JDA 인프라
  - 공통 Spring bootstrap

## 이번 리팩터 목표

1. 수동 `/leave`와 자동 퇴장이 같은 종료 경로를 사용하게 만들기
2. audio-node에 유휴 음성 채널 자동 퇴장 기능 추가
3. 유휴 퇴장 기능을 설정으로 on/off 및 timeout 제어 가능하게 만들기
4. 앱 경계와 패키지 경계가 일치하도록 패키지 루트 재정리

## 구현 단계와 상태

### Phase 1. 종료 로직 공통화

목표:

- 공통 종료 서비스로 stop, queue clear, disconnect, state cleanup을 한 번에 처리

상태:

- 완료
- `VoiceSessionLifecycleService` 추가

### Phase 2. 운영 설정 확장

목표:

- `voiceIdleDisconnectEnabled`
- `voiceIdleTimeout`

상태:

- 완료
- `OperationsProperties`와 `application-common.yml` 반영

### Phase 3. audio-node 유휴 퇴장 서비스 추가

목표:

- 비어 있는 음성 채널 감지 시 timeout 예약
- 사람이 돌아오면 취소
- 만료 시 자동 퇴장

상태:

- 완료
- `VoiceChannelIdleDisconnectService`
- `VoiceChannelIdleListener`

### Phase 4. JDA 이벤트 연결

목표:

- `GuildVoiceUpdateEvent` 기반 감시
- audio-node에서만 listener 등록

상태:

- 완료

### Phase 5. 로그 및 사용자 안내

목표:

- 유휴 퇴장 로그 보강
- 필요 시 텍스트 채널 안내 메시지 전송

상태:

- 완료
- 로그 추가
- 마지막 텍스트 채널에 자동 퇴장 안내 전송 구현

### Phase 6. 시나리오 검증

목표:

- `/leave`
- 예약
- 취소
- 자동 퇴장
- 설정 on/off

상태:

- 코드 반영 완료
- 실제 환경 시나리오 검증은 별도 수행 필요

## 패키지 재정리 목표

원래 목표였던 패키지 루트는 아래와 같았다.

- `discordgateway.gateway.*`
- `discordgateway.audionode.*`
- `discordgateway.common.*`
- `discordgateway.playback.*`
- `discordgateway.infra.*`

현재 상태:

- 완료
- gateway / audionode / common / playback / infra 기준으로 재정리 반영

## 현재 남은 백로그

### Backlog A. common-core 세분화

후속으로 아래 수준까지 더 분리할 수 있다.

- `common-contract`
- `playback-core`
- `infra-discord`
- `infra-redis`
- `infra-rabbit`

### Backlog B. bootstrap 조립 분리

현재 `ApplicationFactory`가 여전히 공통 조립 책임을 많이 가진다. 후속으로 아래 단위로 더 쪼갤 수 있다.

- Discord / JDA configuration
- Messaging configuration
- Persistence configuration
- Playback configuration

### Backlog C. 실환경 검증

- 유휴 퇴장 실제 시나리오 검증
- 원격 서버 YouTube 재생 안정화
- 운영 알림 튜닝

## 완료 기준

현재 기준으로 아래는 충족된 상태다.

- `/leave`가 공통 종료 서비스 사용
- 유휴 퇴장 기능이 audio-node-app에만 존재
- 사람 0명일 때 timeout 예약
- 사람이 돌아오면 timeout 취소
- timeout 후 자동 퇴장
- 기능 on/off 및 timeout 설정 가능
- 자동 퇴장 안내 메시지 전송
- 패키지 루트 재정리 완료

남은 것은 주로 실동작 검증과 추가 분해 작업이다.
