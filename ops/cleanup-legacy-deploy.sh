#!/usr/bin/env bash
set -euo pipefail

DEPLOY_DIR="${DEPLOY_DIR:-/home/ubuntu/dis-bot}"
COMPOSE_PROJECT_NAME="${COMPOSE_PROJECT_NAME:-discord-bot}"
CURRENT_DIR="${DEPLOY_DIR}/current"
PURGE_BOT_IMAGES="${PURGE_BOT_IMAGES:-false}"

cleanup_current_project() {
  if [[ ! -d "${CURRENT_DIR}" ]]; then
    return 0
  fi
  if [[ ! -f "${CURRENT_DIR}/docker-compose.yml" || ! -f "${CURRENT_DIR}/.env" ]]; then
    return 0
  fi

  pushd "${CURRENT_DIR}" >/dev/null
  docker compose --project-name "${COMPOSE_PROJECT_NAME}" --env-file .env down --remove-orphans || true
  popd >/dev/null
}

remove_legacy_containers() {
  local legacy_containers=()
  local name

  for name in dis-bot discord-gateway discord-audio-node discord-redis discord-rabbitmq; do
    if docker container inspect "${name}" >/dev/null 2>&1; then
      legacy_containers+=("${name}")
    fi
  done

  if [[ ${#legacy_containers[@]} -gt 0 ]]; then
    docker rm -f "${legacy_containers[@]}"
  fi
}

purge_bot_images() {
  local bot_images
  bot_images="$(docker images --format '{{.Repository}}:{{.Tag}}' | grep '^discord-bot:' || true)"
  if [[ -z "${bot_images}" ]]; then
    return 0
  fi
  echo "${bot_images}" | xargs -r docker rmi -f
}

cleanup_current_project
remove_legacy_containers

if [[ "${PURGE_BOT_IMAGES}" == "true" ]]; then
  purge_bot_images
fi

echo "legacy deployment cleanup completed"
