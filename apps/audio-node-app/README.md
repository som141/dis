# audio-node-app

## 역할

`audio-node-app`은 음악 worker다. RabbitMQ에서 음악 command를 consume하고 실제 재생, recovery, idle disconnect를 수행한다.

## 주요 책임

- music command consumer
- 음성 채널 연결과 재생 실행
- queue/track 상태 갱신
- Redis 상태 저장
- result event publish
- recovery
- idle disconnect

## 주요 패키지

- `config`
  - audio-node 전용 bean 조립
- `lifecycle`
  - idle disconnect 관련 listener/service
- `recovery`
  - Redis 상태 기준 playback recovery

실제 playback 코어는 `modules/common-core`에 있다.

## 의존 저장소와 메시징

- RabbitMQ
  - music command consume
  - music result publish
- Redis
  - guild/player/queue 상태 저장

## health endpoint

- `/actuator/health`
- `/actuator/info`
- `/actuator/prometheus`
