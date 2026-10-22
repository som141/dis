# Grafana 대시보드

아래 JSON은 [dashboards.yml](../provisioning/dashboards/dashboards.yml)에 의해 Grafana의 `Discord Bot` 폴더에 자동 등록된다. [관측성 시작 방법](../../README.md)을 적용한 뒤 `http://localhost:3000`에서 확인한다.

| 파일 | 먼저 볼 내용 |
| --- | --- |
| [앱 개요](discord-bot-app-overview.json) | Gateway·audio·stock의 JVM·process 상태와 로그 |
| [인프라 개요](discord-bot-infra-overview.json) | Redis·PostgreSQL exporter·RabbitMQ·수집기 상태 |
| [주식 앱](discord-bot-stock-node-overview.json) | 시세 갱신·캐시 준비·명령·거래·청산 |
| [PostgreSQL](discord-bot-postgres-overview.json) | 연결 수·상태, DB 크기, 트랜잭션 |

대시보드 수정은 저장소 JSON에 반영한다. UI 편집만으로는 변경을 다른 환경에 재현할 수 없고, file provisioning 갱신에 의해 덮어써질 수 있다. query의 job·label은 [Prometheus 설정](../../prometheus/prometheus.yml)과 앱 metric 이름을 대조한다.

시계열이 비어 있으면 먼저 Prometheus target의 `UP`, profile 활성화, datasource 연결과 조회 시간 범위를 확인한다. 실제 음성 청취와 서비스 최대 처리량은 이 대시보드만으로 검증하지 않는다.
