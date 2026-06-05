#!/usr/bin/env bash
set -euo pipefail

DEPLOY_DIR="${DEPLOY_DIR:-/home/ubuntu/dis-bot}"
CURRENT_DIR="${DEPLOY_DIR}/current"
ENV_FILE="${CURRENT_DIR}/.env"
COMPOSE_PROJECT_NAME="${COMPOSE_PROJECT_NAME:-discord-bot}"
GATEWAY_HEALTH_URL="${GATEWAY_HEALTH_URL:-http://127.0.0.1:8081/actuator/health}"
AUDIO_NODE_HEALTH_URL="${AUDIO_NODE_HEALTH_URL:-http://127.0.0.1:8082/actuator/health}"
STOCK_NODE_HEALTH_URL="${STOCK_NODE_HEALTH_URL:-http://127.0.0.1:8083/actuator/health}"
STOCK_NODE_METRICS_URL="${STOCK_NODE_METRICS_URL:-http://127.0.0.1:8083/actuator/prometheus}"

if [[ ! -d "${CURRENT_DIR}" ]]; then
  echo "missing current release directory: ${CURRENT_DIR}"
  exit 1
fi

if [[ ! -f "${ENV_FILE}" ]]; then
  echo "missing env file: ${ENV_FILE}"
  exit 1
fi

pushd "${CURRENT_DIR}" >/dev/null
echo "[1/4] docker compose ps"
docker compose --project-name "${COMPOSE_PROJECT_NAME}" --env-file .env ps

echo "[2/4] gateway health"
curl -fsS "${GATEWAY_HEALTH_URL}"
echo

echo "[3/4] audio-node health"
curl -fsS "${AUDIO_NODE_HEALTH_URL}"
echo

echo "[4/5] stock-node health"
curl -fsS "${STOCK_NODE_HEALTH_URL}"
echo

echo "[5/5] stock-node metrics"
curl -fsS "${STOCK_NODE_METRICS_URL}" | grep -q "jvm_memory_used_bytes"
echo "stock-node metrics endpoint is available"
popd >/dev/null

cat <<'EOF'
Recommended manual checks after the automated probe:
- Discord slash command response
- voice channel join / play / skip / stop
- recovery after container restart
- DLQ growth and outbox replay logs
EOF
