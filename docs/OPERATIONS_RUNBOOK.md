# 운영 런북

## 1. 목적

이 문서는 현재 배포 구조에서 운영자가 바로 사용할 수 있는 점검 절차를 정리한다.

대상:

- 앱 기동 / 정지
- 장애 확인
- DLQ 재처리
- 관측성 스택 확인
- 배포 후 스모크 체크

## 2. 현재 운영 구조

- 앱:
  - `gateway-app`
  - `audio-node-app`
- 공통 인프라:
  - Redis
  - RabbitMQ
- 관측성:
  - Prometheus
  - Loki
  - Alloy
  - redis-exporter
  - Grafana

현재 운영 경로:

- 상태 저장소: Redis
- 명령 전달: RabbitMQ
- 이벤트 전달: Spring local event

## 3. 원격 서버 기본 명령

작업 디렉터리:

```bash
cd /home/ubuntu/dis-bot/current
```

전체 상태 확인:

```bash
docker compose --env-file .env ps
```

관측성 포함 상태 확인:

```bash
docker compose --profile observability --env-file .env ps
```

## 4. 봇 정지 / 시작

앱만 잠깐 정지:

```bash
docker compose --project-name discord-bot --env-file .env stop gateway audio-node
```

앱만 다시 시작:

```bash
docker compose --project-name discord-bot --env-file .env start gateway audio-node
```

전체 스택 정지:

```bash
docker compose --project-name discord-bot --env-file .env stop
```

전체 스택 시작:

```bash
docker compose --project-name discord-bot --env-file .env start
```

## 5. 로그 확인

앱 로그:

```bash
docker compose --project-name discord-bot --env-file .env logs gateway --tail=200
docker compose --project-name discord-bot --env-file .env logs audio-node --tail=200
```

실시간 추적:

```bash
docker compose --project-name discord-bot --env-file .env logs -f gateway audio-node
```

관측성 로그:

```bash
docker compose --project-name discord-bot --profile observability --env-file .env logs grafana --tail=200
docker compose --project-name discord-bot --profile observability --env-file .env logs prometheus --tail=200
```

## 6. 헬스 체크

Gateway:

```bash
curl http://127.0.0.1:8081/actuator/health
```

Audio Node:

```bash
curl http://127.0.0.1:8082/actuator/health
```

Prometheus:

```bash
curl http://127.0.0.1:9090/-/ready
```

Grafana:

```bash
curl http://127.0.0.1:3000/api/health
```

## 7. 스모크 체크

배포 직후:

```bash
bash /home/ubuntu/dis-bot/current/ops/smoke-check.sh
```

추가 수동 확인:

1. slash command가 Discord에서 보이는지 확인
2. `/join` 응답 확인
3. `/play`로 실제 재생 확인
4. `/skip`, `/stop` 응답 확인
5. 컨테이너 재시작 후 recovery 확인

## 8. Command DLQ 재처리

기본 실행:

```bash
bash /home/ubuntu/dis-bot/current/ops/replay-command-dlq.sh
```

최대 메시지 수 제한:

```bash
bash /home/ubuntu/dis-bot/current/ops/replay-command-dlq.sh 100
```

동작 방식:

1. DLQ에서 메시지를 읽는다.
2. `MusicCommandMessage`로 역직렬화한다.
3. 메인 command exchange로 다시 발행한다.
4. 성공 시 ACK
5. 실패 시 중단

DLQ 재처리 모드에서는 JDA와 일반 command consumer가 같이 뜨지 않도록 구성되어 있다.

## 9. 레거시 컨테이너 정리

예전 배포 잔재가 남아 있으면:

```bash
bash /home/ubuntu/dis-bot/current/ops/cleanup-legacy-deploy.sh
```

이미지까지 정리:

```bash
PURGE_BOT_IMAGES=true bash /home/ubuntu/dis-bot/current/ops/cleanup-legacy-deploy.sh
```

## 10. 관측성 스택

기동:

```bash
docker compose --profile observability --env-file .env up -d prometheus loki alloy redis-exporter grafana
```

정지:

```bash
docker compose --profile observability --env-file .env stop prometheus loki alloy redis-exporter grafana
```

접속:

- Grafana: `http://127.0.0.1:3000`
- Prometheus: `http://127.0.0.1:9090`
- Loki: `http://127.0.0.1:3100`
- Alloy: `http://127.0.0.1:12345`

## 11. Grafana 운영 주의점

- `GRAFANA_ADMIN_USER`, `GRAFANA_ADMIN_PASSWORD`는 최초 기동 시점에만 적용된다.
- 기존 `grafana-data` 볼륨이 있으면 env 변경만으로 계정이 바뀌지 않는다.

비밀번호 재설정:

```bash
docker compose --profile observability --env-file .env exec grafana grafana cli --homepath /usr/share/grafana admin reset-admin-password '<새비밀번호>'
```

## 12. 알림

기본 상태:

- `GRAFANA_ALERT_DEFAULT_RECEIVER=observability-noop`

Discord 알림 활성화:

- `GRAFANA_ALERT_DEFAULT_RECEIVER=observability-discord`
- `GRAFANA_ALERT_DISCORD_WEBHOOK_URL=<실제 webhook>`

## 13. 현재 알려진 운영 리스크

- 원격 서버에서 YouTube 재생이 로컬보다 더 자주 실패할 수 있다.
- 이 문제는 현재까지의 관측상 코드 자체보다 서버 IP/ASN과 YouTube 응답 차이의 영향을 크게 받는다.
- `gateway-app`과 `audio-node-app`은 같은 Discord 토큰으로 각각 JDA 세션을 연다.
