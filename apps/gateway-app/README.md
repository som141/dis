# gateway-app

## 역할

`gateway-app`은 Discord 진입점이다. 실제 음악 재생이나 주식 계산은 하지 않고, slash command를 command envelope로 바꿔 RabbitMQ로 publish한다.

주요 책임:

- Discord slash command 수신
- `deferReply` 시작
- pending interaction 저장
- music/stock command publish
- result event 수신 후 Discord 응답 수정

## 현재 응답 정책

- 음악 명령: 비공개
- 주식 `buy`, `sell`, `rank`: 공개
- 나머지 주식 명령: 비공개

## 주요 패키지

- `application`
  - command 준비와 publish facade
- `config`
  - bean wiring, result queue/binding
- `interaction`
  - pending interaction 저장소
- `messaging`
  - result listener
- `presentation/discord`
  - JDA listener, command catalog

## 동작 흐름

1. Discord interaction 수신
2. `deferReply(...)`
3. pending interaction 저장
4. command envelope 생성
5. RabbitMQ publish
6. result event 수신
7. 원래 interaction 응답 수정

## 환경 변수

- `DISCORD_TOKEN`
- `DISCORD_DEV_GUILD_ID`
- `RABBITMQ_HOST`
- `RABBITMQ_PORT`
- `RABBITMQ_USERNAME`
- `RABBITMQ_PASSWORD`
- `REDIS_HOST`
- `REDIS_PORT`
- `APP_NODE_NAME`
- `HEALTH_PORT`

## health endpoint

- `/actuator/health`
- `/actuator/info`
- `/actuator/prometheus`
