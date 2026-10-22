# 공용 모듈

이 디렉터리의 두 모듈은 실행 앱에 의존성을 제공하는 Java 라이브러리다. 독립 `main()`이나 Compose 서비스는 없다.

| 모듈 | 사용 앱 | 제공 내용 |
| --- | --- | --- |
| [common-core](common-core/README.md) | Gateway, audio | 음악 계약·재생 코어, Redis·RabbitMQ·JDA 구현, 공통 설정 |
| [stock-core](stock-core/README.md) | Gateway, stock | 주식 command/result DTO와 메시징 계약 |

음악 재생 동작은 common-core, 주식 계산·영속화는 [stock-node-app](../apps/stock-node-app/README.md)에서 찾는다. stock-core에는 DB나 Discord 구현을 넣지 않는다.

[전체 구조](../docs/reference/MODULE_STRUCTURE.md) · [실행 앱](../apps/README.md) · [프로젝트 README](../README.md)
