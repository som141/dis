# stock-core

## 역할

`stock-core`는 주식 시스템 공용 계약 모듈이다. 실행 앱은 아니며, `gateway-app`과 `stock-node-app`이 함께 사용하는 DTO와 메시징 계약을 담는다.

## 포함 내용

- `StockCommand`
- `StockCommandEnvelope`
- `StockCommandResultEvent`
- stock messaging properties
- stock command message factory

## 경계

이 모듈은 계약만 가진다.

포함하지 않는 것:

- 비즈니스 로직
- JPA entity/repository
- Redis/PostgreSQL 접근
- Discord/JDA 코드
- Finnhub 연동
