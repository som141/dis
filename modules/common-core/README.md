# common-core

## 역할

`common-core`는 음악 시스템 공용 코어 모듈이다. 실행 앱은 아니며, `gateway-app`과 `audio-node-app`이 함께 사용하는 계약과 인프라를 담는다.

## 포함 내용

- 음악 command/event 계약
- playback 코어 로직
- Redis 구현
- RabbitMQ 구현
- JDA/Discord 연동 구현
- 공용 bootstrap 설정

## 주요 패키지

- `discordgateway.common.*`
- `discordgateway.playback.*`
- `discordgateway.infra.*`

## 포함하지 않는 것

- stock 전용 계약
- stock persistence
- stock quote provider

이 영역은 `modules/stock-core`와 `apps/stock-node-app`이 담당한다.
