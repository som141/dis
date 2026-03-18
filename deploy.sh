#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 1 ]]; then
  echo "usage: $0 <git-sha>"
  exit 1
fi

SHA="$1"
DEPLOY_DIR="${DEPLOY_DIR:-/home/ubuntu/dis-bot}"
COMPOSE_PROJECT_NAME="${COMPOSE_PROJECT_NAME:-discord-bot}"
INCOMING_DIR="${DEPLOY_DIR}/incoming"
RELEASES_DIR="${DEPLOY_DIR}/releases"
RELEASE_DIR="${RELEASES_DIR}/${SHA}"
CURRENT_LINK="${DEPLOY_DIR}/current"
IMAGE_ARCHIVE="${INCOMING_DIR}/discord-bot-${SHA}.tar.gz"
ENV_FILE="${INCOMING_DIR}/.env.cicd"
COMPOSE_FILE="${INCOMING_DIR}/docker-compose.yml"
OPS_DIR="${INCOMING_DIR}/ops"
PREVIOUS_RELEASE_DIR=""

require_file() {
  local path="$1"
  if [[ ! -f "${path}" ]]; then
    echo "missing required file: ${path}"
    exit 1
  fi
}

require_file "${IMAGE_ARCHIVE}"
require_file "${ENV_FILE}"
require_file "${COMPOSE_FILE}"

if [[ -L "${CURRENT_LINK}" || -e "${CURRENT_LINK}" ]]; then
  PREVIOUS_RELEASE_DIR="$(readlink -f "${CURRENT_LINK}" || true)"
fi

compose_project_from_env() {
  local env_path="$1"
  if [[ ! -f "${env_path}" ]]; then
    return 0
  fi
  grep -E '^COMPOSE_PROJECT_NAME=' "${env_path}" | tail -n 1 | cut -d '=' -f 2-
}

compose_in_dir() {
  local dir="$1"
  local project_name="$2"
  shift 2

  pushd "${dir}" >/dev/null
  if [[ -n "${project_name}" ]]; then
    docker compose --project-name "${project_name}" --env-file .env "$@"
  else
    docker compose --env-file .env "$@"
  fi
  popd >/dev/null
}

remove_legacy_fixed_name_containers() {
  local legacy_containers=()
  local name

  for name in discord-gateway discord-audio-node discord-redis discord-rabbitmq; do
    if docker container inspect "${name}" >/dev/null 2>&1; then
      legacy_containers+=("${name}")
    fi
  done

  if [[ ${#legacy_containers[@]} -gt 0 ]]; then
    echo "removing legacy fixed-name containers: ${legacy_containers[*]}"
    docker rm -f "${legacy_containers[@]}"
  fi
}

mkdir -p "${RELEASE_DIR}"

cp "${COMPOSE_FILE}" "${RELEASE_DIR}/docker-compose.yml"
cp "${ENV_FILE}" "${RELEASE_DIR}/.env"
cp "${IMAGE_ARCHIVE}" "${RELEASE_DIR}/discord-bot.tar.gz"
if [[ -d "${OPS_DIR}" ]]; then
  rm -rf "${RELEASE_DIR}/ops"
  cp -R "${OPS_DIR}" "${RELEASE_DIR}/ops"
  find "${RELEASE_DIR}/ops" -type f -name "*.sh" -exec chmod +x {} \;
fi

gzip -dc "${RELEASE_DIR}/discord-bot.tar.gz" | docker load

if [[ -n "${PREVIOUS_RELEASE_DIR}" && -d "${PREVIOUS_RELEASE_DIR}" && "${PREVIOUS_RELEASE_DIR}" != "${RELEASE_DIR}" ]]; then
  PREVIOUS_PROJECT_NAME="$(compose_project_from_env "${PREVIOUS_RELEASE_DIR}/.env")"
  if [[ -f "${PREVIOUS_RELEASE_DIR}/docker-compose.yml" && -f "${PREVIOUS_RELEASE_DIR}/.env" ]]; then
    compose_in_dir "${PREVIOUS_RELEASE_DIR}" "${PREVIOUS_PROJECT_NAME}" down --remove-orphans
  fi
fi

remove_legacy_fixed_name_containers

ln -sfn "${RELEASE_DIR}" "${CURRENT_LINK}"

compose_in_dir "${RELEASE_DIR}" "${COMPOSE_PROJECT_NAME}" up -d --no-build --remove-orphans

rm -f "${IMAGE_ARCHIVE}" "${ENV_FILE}" "${COMPOSE_FILE}"
rm -rf "${OPS_DIR}"
find "${RELEASES_DIR}" -mindepth 1 -maxdepth 1 -type d | sort | head -n -5 | xargs -r rm -rf

echo "deployed release ${SHA}"
