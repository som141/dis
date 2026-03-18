#!/usr/bin/env bash
set -euo pipefail

DEPLOY_DIR="${DEPLOY_DIR:-/home/ubuntu/dis-bot}"
CURRENT_DIR="${DEPLOY_DIR}/current"
ENV_FILE="${CURRENT_DIR}/.env"
MAX_MESSAGES="${1:-50}"

if [[ ! -d "${CURRENT_DIR}" ]]; then
  echo "missing current release directory: ${CURRENT_DIR}"
  exit 1
fi

if [[ ! -f "${ENV_FILE}" ]]; then
  echo "missing env file: ${ENV_FILE}"
  exit 1
fi

pushd "${CURRENT_DIR}" >/dev/null
docker compose --env-file .env run --rm --no-deps audio-node \
  --spring.profiles.active=audio-node \
  --app.node-name=ops-command-dlq-replay \
  --ops.command-dlq-replay-enabled=true \
  --ops.command-dlq-replay-exit-after-run=true \
  --ops.command-dlq-replay-max-messages="${MAX_MESSAGES}"
popd >/dev/null
