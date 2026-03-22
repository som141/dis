# dis: Ephemeral 응답 + RabbitMQ RPC 제거 + 진짜 비동기 command/event 전환 계획

## User request (English)
**“I want music execution info to be visible only to the user who invoked it, like Discord ephemeral replies, not shared to the whole server. Also, for performance, I want to stop using RabbitMQ RPC and move to a truly asynchronous event-driven flow. Please create a step-by-step scenario and organize it so I can give it to Codex.”**

---

## 1. 배경 요약

현재 레포는 아래 구조를 사용한다.

- `apps/gateway-app`: Discord slash command 진입점
- `apps/audio-node-app`: RabbitMQ command consumer + 실제 재생 실행
- `modules/common-core`: 공용 command/event 모델, playback, Redis, RabbitMQ, Spring bootstrap

현재 command 흐름은 **RabbitMQ RPC** 기반이다.

- `MusicCommandBus`는 `CompletableFuture<CommandResult>`를 반환한다.
- `RabbitMusicCommandBus`는 `RabbitTemplate.convertSendAndReceiveAsType(...)`로 RPC 응답을 기다린다.
- `RabbitMusicCommandListener`는 `CommandResult`를 반환한다.
- gateway는 slash command에서 `deferReply(true)`를 한 뒤, RPC 결과를 받아 `editOriginal(...)`로 응답한다.

또한 실제 재생 정보는 `PlayerManager` 내부에서 `textChannel.sendMessage(...)`로 전송되므로, 현재는 서버 채널에 공개 메시지가 남는다.

---

## 2. 현재 코드에서 확인된 핵심 포인트

### 2.1 ephemeral 응답은 이미 일부 사용 중
- `CommandResult`는 `message` + `ephemeral`로 구성되어 있다.
- gateway의 `DiscordBotListener`는 `result.ephemeral()`에 따라 ephemeral 응답을 준다.
- `/join`, `/play`, `/sfx`는 `deferReply(true)`를 먼저 수행한다.

### 2.2 현재 병목은 RabbitMQ RPC
- `RabbitMusicCommandBus`는 `convertSendAndReceiveAsType(...)`를 사용한다.
- 이는 gateway가 audio-node 처리 결과를 동기적으로 기다리는 구조다.
- 실제로는 “메시지를 보냈다”가 아니라 “보내고 결과를 기다렸다”라서 진짜 비동기 이벤트 구조가 아니다.

### 2.3 공개 음악 정보 메시지의 직접 원인
- `PlayerManager.loadAndPlay(...)`에서 아래 공개 메시지를 바로 보낸다.
  - `🎵 대기열에 추가: ...`
  - `▶️ 재생: ...`
  - `플레이리스트가 비어 있습니다.`
  - `일치하는 결과가 없습니다.`
  - `재생할 수 없습니다. ...`

즉, **“음악 실행 결과가 서버 전체에 보이는 이유”는 PlayerManager가 TextChannel에 직접 메시지를 보내고 있기 때문**이다.

---

## 3. 목표

이번 변경의 목표는 정확히 두 가지다.

### 목표 A — Discord 응답 정책 변경
- slash command 실행 결과는 **기본적으로 본인만 보는 ephemeral 응답**으로 보낸다.
- `PlayerManager`와 playback 계층은 더 이상 텍스트 채널에 직접 공개 메시지를 보내지 않는다.
- 실행 성공/실패/대기열 추가/재생 시작/로드 실패 결과는 **gateway가 ephemeral로 응답**한다.

### 목표 B — RabbitMQ RPC 제거
- gateway → audio-node 명령 전달은 **비동기 command publish**로 바꾼다.
- audio-node는 명령 처리 후 **command result event**를 발행한다.
- gateway는 이 result event를 consume해서 **원래 interaction의 ephemeral 응답을 업데이트**한다.
- 즉,
  - 기존: request/reply
  - 변경 후: command publish + result event consume

---

## 4. 가장 중요한 설계 결론

이 요구사항 두 개는 같이 움직여야 한다.

왜냐하면 **Discord에서 “본인만 보는 응답(ephemeral)”은 interaction 기반 응답이기 때문**이다.

현재 구조는 RPC 응답이 오면 gateway가 `editOriginal(...)` 하는 방식이라서 ephemeral 응답이 가능하다.  
하지만 RPC를 제거하고 완전 비동기로 가면, audio-node는 Discord interaction을 직접 모르기 때문에 결과를 바로 ephemeral로 보낼 수 없다.

따라서 **비동기 전환 후에도 gateway가 최종 응답자 역할을 유지해야 한다.**

즉 최종 구조는 아래여야 한다.

1. gateway가 slash command를 받고 `deferReply(true)` 한다.
2. gateway가 command message를 RabbitMQ로 publish 한다.
3. audio-node가 command를 처리한다.
4. audio-node가 `command-result event`를 발행한다.
5. gateway가 그 이벤트를 받아서, **처음 defer했던 interaction의 original reply를 edit** 한다.
6. 결과적으로 사용자에게는 계속 ephemeral 응답처럼 보인다.

---

## 5. 목표 시나리오

## 시나리오 1 — `/play` 성공
1. 사용자가 `/play` 실행
2. gateway가 `deferReply(true)` 수행
3. gateway가 `MusicCommandEnvelope`를 RabbitMQ로 publish
4. audio-node가 command consume
5. audio-node가 실제 재생 로직 수행
6. audio-node가 `CommandResultEvent(success=true, message="▶️ 재생: ...")` 발행
7. gateway가 result event를 consume
8. gateway가 해당 interaction original reply를 edit
9. 사용자는 본인만 보이는 메시지로 결과를 확인
10. 서버 채널에는 공개 메시지를 남기지 않음

## 시나리오 2 — `/play` 검색 결과 없음
1. 사용자가 `/play query:...`
2. gateway가 ephemeral defer
3. audio-node가 처리
4. no match 발생
5. audio-node가 `CommandResultEvent(success=false, message="일치하는 결과가 없습니다.")` 발행
6. gateway가 original reply를 ephemeral 실패 메시지로 수정
7. 서버 채널에는 아무 공개 메시지도 남기지 않음

## 시나리오 3 — `/queue`
1. 사용자가 `/queue`
2. gateway가 ephemeral 응답을 준비
3. command 처리 후 현재 큐 내용을 result event로 전달
4. gateway가 본인에게만 대기열을 보여줌

## 시나리오 4 — duplicate command
1. 같은 `commandId` 또는 중복 command가 다시 들어옴
2. audio-node가 dedup repository 확인
3. 이미 완료된 명령이면 기존 결과를 result event로 재전송하거나 duplicate status event 발행
4. gateway는 사용자에게 “이미 처리되었음” 또는 기존 결과를 ephemeral로 보여줌

## 시나리오 5 — result event 지연 또는 누락
1. gateway가 deferReply(true) 후 pending response registry에 저장
2. 일정 시간(예: 10초/15초) 안에 result event가 안 오면
3. gateway는 “요청은 접수되었지만 결과 수신이 지연되고 있습니다.” 같은 ephemeral fallback을 남김
4. 이후 늦게 result event가 오면 follow-up edit 또는 추가 follow-up 처리
5. interaction token 만료 시 fallback 정책 필요

---

## 6. 핵심 설계 변경 사항

## 6.1 command result를 “return”하지 말고 “event”로 발행
현재:
- `MusicCommandBus.dispatch(...) -> CompletableFuture<CommandResult>`
- `RabbitMusicCommandListener.handle(...) -> CommandResult`

목표:
- `MusicCommandBus.dispatch(...) -> CompletableFuture<Void>` 또는 `CompletionStage<DispatchAck>`
- `RabbitMusicAsyncCommandBus.publish(...)`
- `RabbitMusicCommandListener.handle(...) -> void`
- 처리 결과는 `CommandResultEvent`로 별도 exchange에 발행

## 6.2 interaction 응답 문맥을 command에 싣기
현재 `MusicCommandMessage`는 아래 정보만 가진다.
- `commandId`
- `schemaVersion`
- `sentAtEpochMs`
- `producer`
- `command`

비동기 + ephemeral 응답을 위해 아래 문맥이 추가되어야 한다.

### 새로 필요한 응답 문맥 예시
- `interactionId`
- `interactionToken`
- `applicationId` 또는 webhook 응답에 필요한 값
- `guildId`
- `userId`
- `issuedAt`
- `expiresAt`
- `responseMode=EPHEMERAL`

권장 형태:
- `MusicCommandEnvelope`
  - `commandMessage`
  - `responseContext`

또는
- `MusicCommandMessage`에 `responseContext` 필드 추가

## 6.3 gateway에 pending interaction registry 추가
gateway는 defer한 interaction을 나중에 결과 event로 찾을 수 있어야 한다.

새 구성 예시:
- `PendingInteractionRepository`
  - key: `commandId`
  - value: `InteractionResponseContext`
- TTL: Discord interaction token 유효 시간 기준으로 만료 관리

## 6.4 audio-node는 Discord 채널에 직접 말하지 않음
audio-node/playback 쪽은 더 이상 `TextChannel.sendMessage(...)`를 호출하지 않는다.

대신:
- domain event 발행
- command result event 발행
- 로그 기록

즉 **실행 계층은 “무슨 일이 일어났는지”만 말하고, Discord 사용자 응답은 gateway가 맡는다.**

---

## 7. 단계별 작업 지시서 (Codex용)

## Phase 0 — 기준선 확보
### 목표
현재 동작을 망가뜨리지 않도록 변경 전 기준을 확보한다.

### 작업
1. 현재 flow를 문서화한다.
2. `/join`, `/play`, `/queue`, `/stop`, `/skip`, `/pause`, `/resume`, `/leave`, `/sfx`의 응답 정책을 표로 정리한다.
3. 다음 파일의 현재 책임을 정리한다.
   - `apps/gateway-app/.../DiscordBotListener`
   - `modules/common-core/.../CommandResult`
   - `modules/common-core/.../MusicCommandBus`
   - `modules/common-core/.../RabbitMusicCommandBus`
   - `modules/common-core/.../RabbitMusicCommandListener`
   - `modules/common-core/.../PlayerManager`

### 완료 조건
- 현재 공개 메시지가 어떤 코드에서 발생하는지 문서화 완료
- RPC 의존 경로 문서화 완료

---

## Phase 1 — 공개 채널 메시지 제거
### 목표
재생/대기열/실패 메시지가 서버 채널에 공개되지 않도록 만든다.

### 작업
1. `PlayerManager.loadAndPlay(...)` 안의 `textChannel.sendMessage(...)` 호출을 제거한다.
2. 공개 메시지 대신 아래 중 하나로 치환한다.
   - `MusicEvent` 발행
   - `CommandExecutionUpdate` 생성
3. `TextChannel` 의존을 최소화한다.
4. 가능하면 `PlaybackGateway.loadAndPlay(TextChannel, ...)` 시그니처도 재검토해서,
   장기적으로 `guildId`, `requestContext`, `traceContext`만 전달하도록 바꾼다.

### 완료 조건
- `PlayerManager`에서 직접 공개 채널 메시지를 보내지 않음
- 실행 결과는 응답 이벤트/도메인 이벤트로만 노출됨

---

## Phase 2 — 비동기 command publish로 전환
### 목표
RabbitMQ RPC를 제거하고 publish-only command bus로 바꾼다.

### 작업
1. `MusicCommandBus` 인터페이스를 변경한다.
   - 기존: `CompletableFuture<CommandResult> dispatch(MusicCommand command)`
   - 목표: `CompletableFuture<Void> dispatch(MusicCommandEnvelope envelope)` 또는 유사 형태
2. `RabbitMusicCommandBus`를 `RabbitMusicAsyncCommandBus`로 바꾸거나 역할을 변경한다.
3. `convertSendAndReceiveAsType(...)` 제거
4. `convertAndSend(...)` 기반 publish-only 로직으로 변경
5. `rpcTimeoutMs` 사용 제거
6. `MessagingProperties`에서 RPC 전용 필드 정리 계획 수립

### 완료 조건
- gateway는 더 이상 Rabbit 응답을 기다리지 않음
- command dispatch는 publish-only

---

## Phase 3 — command envelope + response context 도입
### 목표
비동기 처리 후에도 gateway가 ephemeral 응답을 마무리할 수 있게 한다.

### 작업
1. 새 타입 추가
   - `InteractionResponseContext`
   - `MusicCommandEnvelope`
2. gateway에서 slash command 수신 시 아래 정보를 담아 envelope 생성
   - `commandId`
   - `guildId`
   - `userId`
   - `interaction token/context`
   - `ephemeral=true`
3. `MusicCommandMessageFactory`는 envelope 생성까지 책임을 넓히거나 새 factory를 만든다.
4. `PendingInteractionRepository` 추가
   - in-memory 시작
   - 필요하면 Redis 확장 가능
5. gateway는 command publish 직후 `commandId -> responseContext` 저장

### 완료 조건
- result event만 받아도 gateway가 어떤 interaction을 수정해야 하는지 알 수 있음

---

## Phase 4 — command result event 도입
### 목표
audio-node의 처리 결과를 RabbitMQ event로 gateway에 돌려준다.

### 작업
1. 새 모델 추가
   - `MusicCommandResultEvent`
2. 권장 필드
   - `commandId`
   - `guildId`
   - `userId`
   - `success`
   - `message`
   - `ephemeral`
   - `resultType`
   - `occurredAt`
   - `producer`
3. audio-node에서 command 처리 완료 시 return 대신 event publish
4. 실패/no match/duplicate/in progress 등도 동일하게 result event로 통일
5. result exchange / queue / routing key 추가
   - 예: `music.command.result.exchange`
   - 예: `music.command.result.gateway.queue`
   - 예: `music.command.result`

### 완료 조건
- `RabbitMusicCommandListener.handle(...)`는 더 이상 `CommandResult`를 반환하지 않음
- 결과는 전부 result event로 발행됨

---

## Phase 5 — gateway result consumer 추가
### 목표
gateway가 result event를 받아 ephemeral original reply를 업데이트한다.

### 작업
1. gateway에 `RabbitMusicCommandResultListener` 추가
2. `commandId`로 `PendingInteractionRepository` 조회
3. 찾으면 original interaction reply를 edit
4. 처리 성공 후 pending entry 제거
5. entry가 없거나 token 만료면 fallback 정책 적용
   - 로그만 남김
   - 가능하면 follow-up 실패 처리
   - 필요하면 사용자 DM은 선택사항

### 완료 조건
- `/play` 결과가 서버 채널이 아니라 본인만 보는 응답으로 돌아감
- result event 기반으로 동작

---

## Phase 6 — Discord 응답 정책 정리
### 목표
모든 명령의 UX를 일관되게 맞춘다.

### 정책 권장안
- `/play`, `/join`, `/leave`, `/queue`, `/pause`, `/resume`, `/stop`, `/skip`, `/clear`, `/sfx`
  - 기본: `deferReply(true)` 또는 `reply(...).setEphemeral(true)`
- 성공/실패/중복/빈 대기열/검색 실패:
  - 전부 ephemeral
- 공개 채널 안내:
  - 기본 비활성화
  - 필요하면 config로 opt-in

### 완료 조건
- 두 번째 이미지처럼 사용자 본인만 보는 응답이 기본 동작이 됨
- 공개 텍스트 채널 오염 제거

---

## Phase 7 — 설정/문서/마이그레이션 정리
### 목표
운영 환경과 문서 기준을 새 구조에 맞춘다.

### 작업
1. `README.md` 수정
2. `docs/CURRENT_ARCHITECTURE.md` 수정
3. `docs/MODULE_STRUCTURE.md` 수정
4. `docs/EVENT_CONTRACT.md`에 새 command result event 추가
5. `MessagingProperties` 정리
   - RPC timeout 제거
   - command result exchange/queue 추가
6. observability 태그 추가
   - `commandId`
   - `resultType`
   - `interactionResponseMode=ephemeral`

### 완료 조건
- 문서와 코드가 일치
- 운영자가 새 흐름을 이해할 수 있음

---

## 8. 구체적으로 수정할 파일 목록

## 기존 파일 수정
- `apps/gateway-app/src/main/java/discordgateway/discord/DiscordBotListener.java`
- `apps/gateway-app/src/main/java/discordgateway/gateway/GatewayComponentConfiguration.java`
- `modules/common-core/src/main/java/discordgateway/application/MusicCommandBus.java`
- `modules/common-core/src/main/java/discordgateway/application/MusicCommandMessage.java`
- `modules/common-core/src/main/java/discordgateway/application/MusicCommandMessageFactory.java`
- `modules/common-core/src/main/java/discordgateway/application/CommandResult.java`
- `modules/common-core/src/main/java/discordgateway/infrastructure/messaging/rabbit/RabbitMusicCommandBus.java`
- `modules/common-core/src/main/java/discordgateway/infrastructure/messaging/rabbit/RabbitMusicCommandListener.java`
- `modules/common-core/src/main/java/discordgateway/bootstrap/MessagingProperties.java`
- `modules/common-core/src/main/java/discordgateway/audio/PlayerManager.java`

## 새 파일 후보
- `apps/gateway-app/src/main/java/discordgateway/gateway/interaction/InteractionResponseContext.java`
- `apps/gateway-app/src/main/java/discordgateway/gateway/interaction/PendingInteractionRepository.java`
- `apps/gateway-app/src/main/java/discordgateway/gateway/interaction/InMemoryPendingInteractionRepository.java`
- `apps/gateway-app/src/main/java/discordgateway/gateway/messaging/RabbitMusicCommandResultListener.java`
- `modules/common-core/src/main/java/discordgateway/application/MusicCommandEnvelope.java`
- `modules/common-core/src/main/java/discordgateway/application/event/MusicCommandResultEvent.java`
- `modules/common-core/src/main/java/discordgateway/infrastructure/messaging/rabbit/RabbitMusicCommandResultPublisher.java`

---

## 9. acceptance criteria

### 기능 기준
1. `/play` 실행 시 서버 텍스트 채널에 “재생”, “대기열 추가” 메시지가 공개로 남지 않는다.
2. `/play` 결과는 호출한 사용자에게만 보인다.
3. `/queue` 결과도 본인만 보인다.
4. `no match`, `load failed`, `duplicate` 모두 본인만 보인다.
5. gateway는 RabbitMQ RPC 응답을 기다리지 않는다.
6. audio-node는 result event를 발행한다.
7. gateway는 result event를 consume해서 original interaction response를 수정한다.

### 기술 기준
1. `RabbitTemplate.convertSendAndReceiveAsType(...)` 사용 제거
2. `RabbitMusicCommandListener.handle(...)` 반환형에서 `CommandResult` 제거
3. `PlayerManager`에서 직접 `textChannel.sendMessage(...)` 제거
4. result event contract 문서화 완료
5. 최소한 happy-path 통합 테스트 또는 수동 검증 시나리오 문서화

---

## 10. 테스트 시나리오

### 테스트 1 — play success
- `/play` 실행
- 즉시 ephemeral “요청 접수” 또는 deferred 상태
- 잠시 후 ephemeral “▶️ 재생: ...”
- 서버 채널엔 공개 메시지 없음

### 테스트 2 — no match
- 이상한 검색어 입력
- 사용자 본인에게만 “일치하는 결과가 없습니다.”

### 테스트 3 — queue empty
- `/queue`
- 사용자 본인에게만 “현재 대기열이 비어 있습니다.”

### 테스트 4 — duplicate
- 같은 명령을 짧은 시간 안에 연속 입력
- duplicate/in-progress가 ephemeral로만 보임

### 테스트 5 — gateway restart edge case
- defer 후 result event 도착 전에 gateway 재기동
- pending interaction 유실 정책 확인
- 현재 단계에서는 in-memory면 유실 허용, 차후 Redis 고려

---

## 11. 리스크와 주의사항

### 리스크 1 — interaction token 만료
비동기 result가 너무 늦게 오면 original reply 수정이 실패할 수 있다.  
따라서 result event는 되도록 빠르게 돌아와야 하며, pending response TTL도 신중히 잡아야 한다.

### 리스크 2 — gateway 재기동 시 pending interaction 유실
처음 단계에서 in-memory registry를 쓰면 gateway 재기동 시 유실된다.  
초기 구현은 허용 가능하지만, 안정성이 중요하면 Redis 저장으로 확장해야 한다.

### 리스크 3 — PlayerManager API가 아직 TextChannel 중심
현재 playback 시작 API가 `TextChannel`에 묶여 있어, 완전한 구조 개선을 위해서는 request context 분리가 필요하다.

### 리스크 4 — “모든 상태 변화”를 ephemeral로 보내면 과도함
재생 시작 결과, 명령 결과는 ephemeral이 맞다.  
하지만 곡 종료, autoplay 전환, recovery 같은 시스템성 이벤트까지 전부 사용자 응답으로 보내는 건 오히려 UX가 나쁠 수 있다.  
**이번 작업 범위는 slash command 실행 결과를 ephemeral로 만드는 데 집중**한다.

---

## 12. Codex에게 바로 줄 작업 순서 (요약판)

1. `PlayerManager`의 공개 `sendMessage(...)` 제거
2. Rabbit RPC 제거:
   - `convertSendAndReceiveAsType(...)` 삭제
   - publish-only command bus로 전환
3. `InteractionResponseContext`, `PendingInteractionRepository`, `MusicCommandEnvelope` 추가
4. `MusicCommandResultEvent` 추가
5. audio-node에서 command 처리 결과를 result event로 publish
6. gateway에서 result event consume 후 original interaction reply를 ephemeral로 edit
7. 문서/설정/테스트 업데이트
8. 기존 공개 메시지 없는지 최종 점검

---

## 13. 구현 시 권장 원칙

- gateway는 **사용자 응답 책임**
- audio-node는 **명령 실행 책임**
- playback 계층은 **Discord 채널 출력 금지**
- RabbitMQ는 **command transport + result event transport**
- RPC 금지
- slash command 결과는 기본 ephemeral
- 실패/중복/빈 결과도 동일 정책 유지

---

## 14. 이번 작업의 최종 정의

이번 변경은 단순 UI 수정이 아니다.  
아래 아키텍처 전환이다.

### Before
Discord Slash Command  
→ gateway  
→ RabbitMQ RPC  
→ audio-node  
→ result return  
→ gateway editOriginal  
+ PlayerManager가 공개 채널에 직접 메시지 전송

### After
Discord Slash Command  
→ gateway deferReply(ephemeral)  
→ RabbitMQ command publish  
→ audio-node command consume  
→ audio-node result event publish  
→ gateway result consume  
→ gateway가 original reply를 ephemeral로 edit  
+ playback 계층은 공개 메시지 전송 금지

---

## 15. Codex에게 요청할 산출물

Codex에게는 아래 결과물을 요구한다.

1. 코드 변경
2. 새 event contract
3. 설정 변경
4. README / docs 수정
5. 테스트 시나리오
6. 공개 메시지 제거 여부 점검 결과

필수 산출물:
- 변경된 파일 목록
- 핵심 설계 설명
- 명령 흐름 다이어그램(텍스트 가능)
- 수동 검증 체크리스트
