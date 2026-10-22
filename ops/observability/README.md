# 메트릭·로그·대시보드

DIS의 앱·Redis·PostgreSQL·RabbitMQ 상태를 Prometheus와 Grafana로 보고, Docker stdout 로그를 Alloy·Loki로 수집하는 설정이다. 전체 앱 실행은 [루트 README](../../README.md), 지표 해석은 [관측성 구성 문서](../../docs/operations/OBSERVABILITY_PLAN.md)를 본다.

## 시작과 확인

저장소 루트에서 JAR 빌드·`.env` 준비 후 실행한다.

```bash
docker compose --env-file .env --profile observability up -d --build
curl -fsS http://localhost:9090/-/ready
curl -fsS http://localhost:3000/api/health
```

`OBSERVABILITY_ENABLED=true`만으로 로컬 Compose profile이 켜지지는 않는다. 해당 변수는 배포 스크립트가 사용한다. 로컬은 `--profile observability`를 지정한다.

| 구성 | 호스트 주소 | 역할 / 설정 |
| --- | --- | --- |
| Grafana | `http://localhost:3000` | 대시보드·알림, [provisioning/](grafana/provisioning) |
| Prometheus | `http://localhost:9090` | 메트릭 수집, [prometheus.yml](prometheus/prometheus.yml) |
| Loki | `http://localhost:3100` | 로그 저장, [loki-config.yml](loki/loki-config.yml) |
| Alloy | `http://localhost:12345` | Docker 로그 수집, [config.alloy](alloy/config.alloy) |
| Redis exporter | `http://localhost:9121/metrics` | Redis 상태 |
| PostgreSQL exporter | `http://localhost:9187/metrics` | 연결·DB·트랜잭션 상태 |

Prometheus는 `gateway`, `audio-node`, `stock-node`, `redis-exporter`, `postgres-exporter`, `rabbitmq`, `prometheus`, `loki`, `alloy`를 15초 간격으로 scrape한다. RabbitMQ 메트릭은 내부 `rabbitmq:15692`에서 가져온다.

## Grafana와 알림

계정은 `.env`의 `GRAFANA_ADMIN_USER`, `GRAFANA_ADMIN_PASSWORD`를 사용한다. 기본 예제 값은 `admin`/`admin`이다. 최초 `grafana-data` volume 초기화 뒤에는 환경 변수 변경만으로 기존 계정 비밀번호가 바뀌지 않을 수 있다.

Datasource는 Prometheus·Loki, 대시보드는 [이 목록](grafana/dashboards/README.md)의 네 개가 file provisioning으로 등록된다. 앱·인프라·stock·PostgreSQL 대시보드에서 지표와 로그를 함께 확인한다.

알림 기본 receiver는 `observability-noop`이며 실제 Discord webhook은 설정돼 있지 않다. Discord 알림을 쓰려면 `.env`에 receiver와 webhook을 지정하고 Grafana를 재생성한다.

```env
GRAFANA_ALERT_DEFAULT_RECEIVER=observability-discord
GRAFANA_ALERT_DISCORD_WEBHOOK_URL=your-discord-webhook-url
```

```bash
docker compose --env-file .env --profile observability up -d grafana
```

알림 규칙은 [Grafana rules](grafana/provisioning/alerting/alert-rules.yml)와 [Prometheus alerts](prometheus/alerts.yml), 전달 정책은 [notification-policies.yml](grafana/provisioning/alerting/notification-policies.yml)을 확인한다. 설정 파일을 읽거나 이 문서를 여는 것만으로 알림이 전송되는 것은 아니다.

## 중지

앱은 유지하고 관측성 서비스만 멈춘다.

```bash
docker compose --env-file .env --profile observability stop prometheus loki alloy redis-exporter postgres-exporter grafana
```

[운영 파일 목록](../README.md) · [장애 대응](../../docs/operations/OPERATIONS_RUNBOOK.md)
