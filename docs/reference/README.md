# 구조·계약·데이터 모델

현재 커밋의 구현을 설명하는 기준 문서다. 실행 명령은 [개발·운영 안내](../operations/README.md), 과거 작업 내용은 [내부 기록](../internal/README.md)에서 찾는다.

| 문서 | 답하는 내용 | 함께 볼 코드 |
| --- | --- | --- |
| [현재 아키텍처](CURRENT_ARCHITECTURE.md) | Discord 명령·음악 재생·시세 갱신은 어떻게 연결되는가 | [앱 목록](../../apps/README.md), [Compose](../../docker-compose.yml) |
| [모듈 구조](MODULE_STRUCTURE.md) | 기능을 어느 앱·모듈에서 수정하는가 | [settings.gradle](../../settings.gradle), [모듈 목록](../../modules/README.md) |
| [이벤트 계약](EVENT_CONTRACT.md) | command/result·pending·큐·실패 처리는 어떻게 연결되는가 | common-core·stock-core의 메시지 타입 |
| [PostgreSQL 스키마](POSTGRESQL_STOCK_SCHEMA.md) | 계좌·포지션·시즌·원장은 어떻게 저장되는가 | stock 앱의 Flyway migration |

[전체 문서](../README.md) · [문서 작성 기준](../DOCUMENTATION_GUIDE.md)
