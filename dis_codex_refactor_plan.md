# dis 리팩터링 및 유휴 퇴장 기능 작업 지시서

## 문서 목적

이 문서는 `dis` 저장소를 대상으로 구조 개선과 음성 채널 유휴 퇴장 기능을 단계적으로 구현하기 위한 **Codex 작업 지시용 Markdown**이다. 설명 위주 문서가 아니라, 실제 코드 변경을 수행할 수 있도록 **작업 목표, 수정 대상, 구현 순서, 완료 조건**을 명확히 적는다.

---

## 1. 현재 구조 요약

현재 저장소는 아래 멀티모듈 구조를 사용한다.

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

루트 README 및 모듈 문서 기준 현재 역할은 다음과 같다.

- `apps/gateway-app`
  - Discord slash command 수신
  - autocomplete 처리
  - 입력 검증
  - `MusicCommand` 생성
  - RabbitMQ command producer
- `apps/audio-node-app`
  - RabbitMQ command consumer
  - 실제 재생 및 음성 연결
  - recovery 실행
- `modules/common-core`
  - 공용 command/event 모델
  - playback 코어
  - Redis/RabbitMQ 인프라
  - 공통 Spring bootstrap

참고 코드/문서:

- `README.md`
- `docs/MODULE_STRUCTURE.md`
- `docs/CODEBASE_ANALYSIS.md`
- `build.gradle`
- `settings.gradle`

---

## 2. 현재 구조의 문제점

### 2.1 common-core가 너무 비대함

현재 `common-core`는 아래를 모두 포함한다.

- application
- application.event
- audio
- bootstrap
- domain
- infrastructure

또한 Spring Boot, Web, Actuator, AMQP, JDA, LavaPlayer, Redis 등 많은 의존성을 한 번에 가진다.

즉, 이름은 공용 코어지만 실제로는 시스템 대부분을 품고 있는 중심 모듈이다.

### 2.2 패키지 경계가 모듈 경계를 잘 드러내지 못함

현재는 여러 모듈이 `discordgateway.application`, `discordgateway.discord`, `discordgateway.infrastructure` 같은 일반 패키지명을 공유한다.

이 상태에서는:

- 이 클래스가 gateway 전용인지
- audio-node 전용인지
- 공용인지

패키지 이름만 보고 이해하기 어렵다.

### 2.3 종료/정리 로직이 분산될 가능성이 있음

현재 수동 `/leave`, `stop`, recovery, future lifecycle 기능이 서로 다른 위치에 흩어질 수 있다. 앞으로 자동 퇴장 기능을 넣으면 종료 처리 기준을 한 곳으로 모을 필요가 있다.

---

## 3. 이번 작업의 목표

이번 작업은 한 번에 모든 구조를 갈아엎는 것이 아니라, **기능 추가 + 구조 정리 기반 마련**을 목표로 한다.

### 최종 목표

1. `audio-node-app`에 **음성 채널 유휴 퇴장 기능** 추가
2. 봇이 있는 음성 채널에 **실제 사용자 0명**이면 **5분 후 자동 퇴장**
3. 5분 안에 사람이 다시 들어오면 타이머 취소
4. 자동 퇴장과 수동 퇴장이 **같은 종료/정리 로직**을 사용하도록 정리
5. 이후 `common-core` 분해를 쉽게 만들도록 코드 경계 정리

---

## 4. 작업 원칙

### 4.1 이번 PR에서 하지 않을 것

- 전체 모듈을 한 번에 `common-contract`, `playback-core`, `infra-*`로 완전히 분리하는 작업
- 기존 public API 전체를 전면 수정하는 작업
- 운영 경로를 바꾸는 대규모 메시징 구조 변경

### 4.2 이번 PR에서 할 것

- 자동 퇴장 기능 도입
- 종료 절차 공통화
- audio-node 쪽 lifecycle 관련 코드 추가
- 필요한 최소 수준의 프로퍼티 확장
- 문서 업데이트

---

## 5. 권장 목표 구조

이번 작업이 끝난 뒤 지향할 구조는 아래와 같다.

```text
apps/
  gateway-app/
    src/main/java/discordgateway/gateway/
      config/
      presentation/
      application/

  audio-node-app/
    src/main/java/discordgateway/audionode/
      config/
      messaging/
      recovery/
      lifecycle/

modules/
  common-contract/
    command/
    event/
    result/

  playback-core/
    application/
    domain/
    port/

  infra-discord/
  infra-redis/
  infra-rabbit/
```

단, 이번 작업에서는 완전 분리 대신 **현재 구조 안에서 lifecycle 경계를 먼저 만들고**, 이후 분리를 쉽게 만드는 방향으로 구현한다.

---

## 6. 단계별 작업 계획

# Phase 1. 종료/정리 로직 공통화

## 목표

수동 `/leave`와 자동 퇴장이 같은 종료 규칙을 따르도록, 음성 세션 종료 절차를 한 서비스로 모은다.

## 해야 할 일

1. `modules/common-core` 안에 종료 전용 서비스 추가
2. 서비스는 아래 동작을 한 번에 수행해야 함
   - playback 정지
   - queue 정리
   - 음성 연결 해제
   - guild/player 상태 제거
3. 기존 `MusicWorkerService.leave(...)`가 이 서비스를 사용하도록 수정

## 권장 클래스명

- `VoiceSessionLifecycleService`

## 권장 위치

```text
modules/common-core/src/main/java/discordgateway/application/VoiceSessionLifecycleService.java
```

## 포함해야 할 책임

- `PlaybackGateway.stop(guild)` 호출
- `QueueRepository.clear(guildId)` 호출
- `VoiceGateway.disconnect(guild)` 호출
- `GuildStateRepository.remove(guildId)` 호출
- `PlayerStateRepository.remove(guildId)` 호출

## 수정 대상 후보

- `modules/common-core/src/main/java/discordgateway/application/MusicWorkerService.java`
- `modules/common-core/src/main/java/discordgateway/infrastructure/audio/PlaybackGateway.java`
- `modules/common-core/src/main/java/discordgateway/infrastructure/audio/VoiceGateway.java`
- `modules/common-core/src/main/java/discordgateway/domain/GuildStateRepository.java`
- `modules/common-core/src/main/java/discordgateway/domain/PlayerStateRepository.java`
- `modules/common-core/src/main/java/discordgateway/domain/QueueRepository.java`

## 완료 조건

- `/leave` 실행 시 기존과 동일하게 퇴장 가능
- 종료 관련 핵심 정리 코드가 `MusicWorkerService` 안에 중복되지 않음
- 향후 자동 퇴장이 같은 서비스를 재사용 가능함

---

# Phase 2. 운영 설정 프로퍼티 확장

## 목표

자동 퇴장 기능을 설정으로 켜고 끌 수 있게 하고, 유휴 시간도 설정 가능하도록 만든다.

## 해야 할 일

`OperationsProperties`에 아래 프로퍼티 추가:

- `voiceIdleDisconnectEnabled` : 기본값 `true`
- `voiceIdleTimeout` : 기본값 `5분`

## 수정 대상

- `modules/common-core/src/main/java/discordgateway/bootstrap/OperationsProperties.java`
- `modules/common-core/src/main/resources/application-common.yml` 또는 적절한 설정 파일

## 권장 설정 예시

```yaml
ops:
  voice-idle-disconnect-enabled: true
  voice-idle-timeout: 5m
```

## 완료 조건

- 설정으로 기능 활성/비활성 가능
- 타임아웃을 코드 상수로 박아두지 않고 프로퍼티로 관리함

---

# Phase 3. audio-node 전용 유휴 퇴장 서비스 추가

## 목표

음성 채널에 사람이 0명이 되었을 때 5분 타이머를 걸고, 사람이 돌아오면 취소하며, 시간이 지나도 비어 있으면 자동 퇴장시킨다.

## 핵심 설계

이 기능은 **반드시 audio-node 쪽에만 둔다.**

이유:

- 실제 재생과 음성 연결의 실행 경계가 audio-node임
- gateway는 명령 진입점에 집중해야 함
- 같은 토큰으로 두 앱이 JDA 세션을 열기 때문에, lifecycle 리스너는 실행 노드 쪽에만 두는 편이 안전함

## 권장 클래스

- `VoiceChannelIdleDisconnectService`
- `VoiceChannelIdleListener`

## 권장 위치

```text
apps/audio-node-app/src/main/java/discordgateway/audionode/lifecycle/
  VoiceChannelIdleDisconnectService.java
  VoiceChannelIdleListener.java
```

## 구현 요구사항

### 3.1 타이머 관리

- `guildId` 기준으로 예약 작업을 저장
- 이미 예약이 있으면 중복 예약 금지
- 사람이 다시 들어오면 예약 취소

### 3.2 퇴장 조건

- 봇이 연결된 채널 기준으로 확인
- `member.getUser().isBot()`이 아닌 사용자만 카운트
- 인간 사용자 수가 `0`이면 빈 채널로 간주

### 3.3 최종 실행 시 재검증

타이머가 실행될 때도 다시 한 번 아래를 확인해야 함.

- 아직 봇이 그 길드에 연결되어 있는지
- 연결 채널이 존재하는지
- 여전히 인간 사용자 수가 0인지

조건이 모두 맞을 때만 퇴장 실행

### 3.4 실제 종료 처리

자동 퇴장은 `VoiceSessionLifecycleService`를 호출해서 수행

직접 중복 구현하지 말 것.

## 완료 조건

- 채널이 비면 예약됨
- 5분 안에 사람이 들어오면 취소됨
- 5분 후에도 비어 있으면 자동 퇴장함
- 자동 퇴장이 수동 `/leave`와 같은 정리 절차를 사용함

---

# Phase 4. JDA 이벤트 리스너 등록

## 목표

`GuildVoiceUpdateEvent`를 수신해서 유휴 퇴장 서비스를 호출한다.

## 해야 할 일

1. `VoiceChannelIdleListener`를 `ListenerAdapter`로 구현
2. `onGuildVoiceUpdate(...)`에서 현재 봇의 연결 상태 확인
3. 연결된 채널의 인간 사용자 수에 따라
   - 0명 → 타이머 예약
   - 1명 이상 → 타이머 취소
4. 리스너는 반드시 `audio-node-app`에서만 빈으로 등록

## 수정 대상

- `apps/audio-node-app/src/main/java/discordgateway/audionode/AudioNodeComponentConfiguration.java`
- 새로 만드는 lifecycle 패키지

## 주의사항

- gateway-app에는 이 리스너를 등록하지 말 것
- 두 앱 모두 반응하는 구조를 만들지 말 것

## 완료 조건

- audio-node 실행 시에만 유휴 퇴장 로직이 동작
- gateway 실행만으로는 유휴 퇴장 기능이 활성화되지 않음

---

# Phase 5. 사용자 알림 및 로그 보강

## 목표

자동 퇴장이 발생했을 때 이유를 남기고, 운영자가 추적 가능하도록 로그를 보강한다.

## 해야 할 일

1. 아래 로그를 추가
   - 빈 채널 감지
   - 예약 생성
   - 예약 취소
   - 자동 퇴장 실행
2. 가능하면 마지막 텍스트 채널에 안내 메시지 전송할 수 있는 구조 검토
3. 다만 이번 단계에서는 **로그 우선**, 텍스트 채널 안내는 선택사항으로 둠

## 권장 로그 키

- `guildId`
- `voiceChannelId`
- `idleTimeoutMs`
- `humanUserCount`
- `action=schedule|cancel|disconnect`

## 완료 조건

- 자동 퇴장 관련 흐름을 로그만 보고 추적 가능

---

# Phase 6. 테스트 및 검증

## 목표

실제 동작과 회귀 여부를 검증한다.

## 최소 검증 시나리오

### 시나리오 A. 수동 퇴장

1. `/join`
2. `/leave`
3. 정상적으로 연결 해제되고 상태 정리되는지 확인

### 시나리오 B. 유휴 퇴장 예약

1. 사용자가 있는 상태에서 봇이 채널에 들어감
2. 모든 인간 사용자가 나감
3. 예약 로그 생성 확인

### 시나리오 C. 유휴 퇴장 취소

1. 예약 생성 후 5분 안에 사용자가 다시 들어옴
2. 예약 취소 로그 확인
3. 자동 퇴장되지 않는지 확인

### 시나리오 D. 유휴 퇴장 실행

1. 인간 사용자 0명 상태 유지
2. 5분 후 자동 퇴장 확인
3. playback/queue/state 정리가 수행되는지 확인

### 시나리오 E. 비활성화 설정

1. `ops.voice-idle-disconnect-enabled=false`
2. 빈 채널이 되어도 자동 퇴장되지 않아야 함

## 추가 권장 테스트

- 타임아웃을 10초 정도로 줄여 로컬 검증
- 사람/봇 카운트에서 봇 계정이 제외되는지 검증
- 이미 disconnect된 상태에서 예약 작업이 실행되어도 에러 없이 종료되는지 검증

---

## 7. 구조 개선 후속 작업 백로그

이번 PR에서 바로 하지 않더라도, 다음 단계에서 진행할 구조 개선 백로그를 남긴다.

### Backlog A. common-core 분해

`common-core`를 아래 방향으로 분리

- `common-contract`
- `playback-core`
- `infra-discord`
- `infra-redis`
- `infra-rabbit`

### Backlog B. 패키지 재정리

다음 package root를 권장

- `discordgateway.gateway.*`
- `discordgateway.audionode.*`
- `discordgateway.common.*`
- `discordgateway.playback.*`
- `discordgateway.infra.*`

### Backlog C. 설정 조립 경계 분리

현재 `ApplicationFactory`가 다양한 bean을 광범위하게 조립하고 있으므로, 향후 아래처럼 분리 검토

- Discord/JDA configuration
- Messaging configuration
- Persistence configuration
- Playback configuration

---

## 8. Codex에게 줄 실행 지시 문구

아래 문장을 Codex 작업 프롬프트에 그대로 사용 가능하다.

```text
목표:
현재 dis 저장소에 음성 채널 유휴 퇴장 기능을 추가하고, 종료/정리 로직을 공통 서비스로 정리해 주세요.

필수 요구사항:
1. audio-node-app에만 유휴 퇴장 로직을 추가할 것
2. 봇이 연결된 음성 채널에서 실제 사용자(봇 제외)가 0명이 되면 5분 후 자동 퇴장할 것
3. 5분 안에 사용자가 다시 들어오면 예약을 취소할 것
4. 자동 퇴장과 수동 /leave가 같은 종료/정리 로직을 재사용하도록 만들 것
5. 설정으로 기능 on/off 및 timeout 조절이 가능해야 함
6. 변경 후 관련 문서도 업데이트할 것

구현 방향:
- VoiceSessionLifecycleService를 추가해서 stop + queue clear + disconnect + state cleanup을 한곳에 모아 주세요.
- OperationsProperties에 voice idle timeout 관련 설정을 추가해 주세요.
- apps/audio-node-app 쪽에 VoiceChannelIdleDisconnectService, VoiceChannelIdleListener를 추가해 주세요.
- GuildVoiceUpdateEvent 기반으로 사람 수를 체크해 예약/취소/자동 퇴장을 구현해 주세요.
- gateway-app에는 해당 리스너를 등록하지 마세요.

완료 후 제출물:
- 변경된 코드
- 수정된 설정 파일
- 간단한 검증 방법
- 변경 요약 문서
```

---

## 9. 최종 완료 기준

아래를 모두 만족하면 이번 작업을 완료로 본다.

- [ ] `/leave`가 공통 종료 서비스 사용
- [ ] 유휴 퇴장 기능이 audio-node-app에만 존재
- [ ] 인간 사용자 0명 시 5분 예약
- [ ] 사용자가 돌아오면 예약 취소
- [ ] 5분 후에도 비어 있으면 자동 퇴장
- [ ] 기능 on/off, timeout 설정 가능
- [ ] 로그로 흐름 추적 가능
- [ ] 문서 업데이트 완료

---

## 10. 구현 시 주의사항

1. 자동 퇴장 로직을 `gateway-app`에 넣지 말 것
2. 이벤트 리스너를 두 앱에 동시에 등록하지 말 것
3. 타이머가 실행될 때 상태를 반드시 다시 검증할 것
4. 종료/정리를 여기저기 복붙하지 말고 공통 서비스로 모을 것
5. 향후 common-core 분해를 고려해 lifecycle 책임은 audio-node 쪽에서 선명하게 유지할 것

---

## 11. 한 줄 요약

이번 작업은 **구조 전체를 한 번에 뜯어고치는 것보다**, 먼저 **audio-node에 5분 유휴 퇴장 기능을 안전하게 추가하고**, 동시에 **종료/정리 로직을 공통화해서 이후 모듈 분해가 쉬워지게 만드는 작업**으로 진행한다.
