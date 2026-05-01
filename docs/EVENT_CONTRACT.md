# 이벤트 계약

현재 저장소에는 음악과 주식 두 개의 비동기 command/result 계약이 있다.

## 음악 계약

음악 쪽 공용 계약은 `modules/common-core`에 있다.

핵심 타입:

- `MusicCommand`
- `MusicCommandEnvelope`
- `MusicCommandResultEvent`
- `MusicEvent`

역할:

- `MusicCommand`
  - worker가 처리할 실제 명령 본문
- `MusicCommandEnvelope`
  - command id, producer, target node 같은 메타데이터 포함
- `MusicCommandResultEvent`
  - command 처리 결과를 gateway로 돌려주는 메시지
- `MusicEvent`
  - worker 내부 상태 변화 관측용 로컬 이벤트

transport:

- command/result: RabbitMQ
- `MusicEvent`: Spring local event

## 주식 계약

주식 쪽 공용 계약은 `modules/stock-core`에 있다.

핵심 타입:

- `StockCommand`
- `StockCommandEnvelope`
- `StockCommandResultEvent`

현재 `StockCommand` 하위 명령:

- `Quote`
- `ListQuotes`
- `Buy`
- `Sell`
- `Balance`
- `Portfolio`
- `History`
- `Rank`

특징:

- `Quote`는 단일 종목 또는 여러 종목을 받을 수 있다.
- `Buy`는 `amount`와 optional `leverage`를 가진다.
- result event는 성공/실패와 사용자에게 보여줄 최종 메시지를 함께 가진다.

## RabbitMQ 토폴로지

### 음악

- command exchange: `music.command.exchange`
- command queue: `music.command.queue`
- result exchange: `music.command.result.exchange`

### 주식

- command exchange: `stock.command.exchange`
- command queue: `stock.command.queue`
- result exchange: `stock.command.result.exchange`

## gateway 응답 완료 방식

1. `gateway-app`이 slash command를 받는다.
2. `deferReply(...)`를 먼저 수행한다.
3. command id와 interaction token을 Redis pending interaction 저장소에 넣는다.
4. command envelope를 RabbitMQ로 publish한다.
5. worker가 처리 후 result event를 publish한다.
6. `gateway-app`이 result event를 받아 원래 interaction 응답을 수정한다.

핵심 포인트:

- Discord 응답은 RPC로 기다리지 않는다.
- command/result를 분리한 비동기 구조다.
- pending interaction은 Redis TTL 기반으로 관리한다.

## 공개/비공개 응답 정책

gateway는 `deferReply` 단계에서 공개 여부를 결정한다.

- 음악 명령: 비공개
- 주식 `buy`, `sell`, `rank`: 공개
- 나머지 주식 명령: 비공개
