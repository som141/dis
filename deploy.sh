#!/usr/bin/env bash
set -euo pipefail

on_error() {
  local exit_code="$1"
  local line_no="$2"
  echo "deploy failed at line ${line_no} with exit code ${exit_code}"
}

trap 'on_error "$?" "$LINENO"' ERR

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
GATEWAY_IMAGE_ARCHIVE="${INCOMING_DIR}/discord-gateway-${SHA}.tar.gz"
AUDIONODE_IMAGE_ARCHIVE="${INCOMING_DIR}/discord-audio-node-${SHA}.tar.gz"
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

require_file "${GATEWAY_IMAGE_ARCHIVE}"
require_file "${AUDIONODE_IMAGE_ARCHIVE}"
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
  grep -E '^COMPOSE_PROJECT_NAME=' "${env_path}" | tail -n 1 | cut -d '=' -f 2- || true
}

env_value_from_file() {
  local env_path="$1"
  local key="$2"
  if [[ ! -f "${env_path}" ]]; then
    return 0
  fi
  grep -E "^${key}=" "${env_path}" | tail -n 1 | cut -d '=' -f 2- || true
}

compose_in_dir() {
  local dir="$1"
  local project_name="$2"
  local observability_enabled="$3"
  shift 3

  pushd "${dir}" >/dev/null
  local compose_cmd=(docker compose)
  if [[ -n "${project_name}" ]]; then
    compose_cmd+=(--project-name "${project_name}")
  fi
  compose_cmd+=(--env-file .env)
  if [[ "${observability_enabled,,}" == "true" ]]; then
    compose_cmd+=(--profile observability)
  fi
  compose_cmd+=("$@")
  "${compose_cmd[@]}"
  popd >/dev/null
}

remove_legacy_fixed_name_containers() {
  local legacy_containers=()
  local name

  for name in dis-bot discord-gateway discord-audio-node discord-redis discord-rabbitmq; do
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

echo "preparing release directory: ${RELEASE_DIR}"
cp "${COMPOSE_FILE}" "${RELEASE_DIR}/docker-compose.yml"
cp "${ENV_FILE}" "${RELEASE_DIR}/.env"
cp "${GATEWAY_IMAGE_ARCHIVE}" "${RELEASE_DIR}/discord-gateway.tar.gz"
cp "${AUDIONODE_IMAGE_ARCHIVE}" "${RELEASE_DIR}/discord-audio-node.tar.gz"
if [[ -d "${OPS_DIR}" ]]; then
  rm -rf "${RELEASE_DIR}/ops"
  cp -R "${OPS_DIR}" "${RELEASE_DIR}/ops"
  find "${RELEASE_DIR}/ops" -type f -name "*.sh" -exec chmod +x {} \;
fi

echo "loading gateway image archive"
gzip -dc "${RELEASE_DIR}/discord-gateway.tar.gz" | docker load

echo "loading audio-node image archive"
gzip -dc "${RELEASE_DIR}/discord-audio-node.tar.gz" | docker load

echo "removing legacy fixed-name containers if present"
remove_legacy_fixed_name_containers

if [[ -n "${PREVIOUS_RELEASE_DIR}" && -d "${PREVIOUS_RELEASE_DIR}" && "${PREVIOUS_RELEASE_DIR}" != "${RELEASE_DIR}" ]]; then
  PREVIOUS_PROJECT_NAME="$(compose_project_from_env "${PREVIOUS_RELEASE_DIR}/.env")"
  PREVIOUS_OBSERVABILITY_ENABLED="$(env_value_from_file "${PREVIOUS_RELEASE_DIR}/.env" "OBSERVABILITY_ENABLED")"
  if [[ -f "${PREVIOUS_RELEASE_DIR}/docker-compose.yml" && -f "${PREVIOUS_RELEASE_DIR}/.env" ]]; then
    echo "stopping previous release: ${PREVIOUS_RELEASE_DIR}"
    compose_in_dir "${PREVIOUS_RELEASE_DIR}" "${PREVIOUS_PROJECT_NAME}" "${PREVIOUS_OBSERVABILITY_ENABLED}" down --remove-orphans || true
  fi
fi

echo "switching current release symlink"
ln -sfn "${RELEASE_DIR}" "${CURRENT_LINK}"

CURRENT_OBSERVABILITY_ENABLED="$(env_value_from_file "${RELEASE_DIR}/.env" "OBSERVABILITY_ENABLED")"
if [[ "${CURRENT_OBSERVABILITY_ENABLED,,}" == "true" ]]; then
  echo "starting compose project with observability profile: ${COMPOSE_PROJECT_NAME}"
else
  echo "starting compose project: ${COMPOSE_PROJECT_NAME}"
fi
compose_in_dir "${RELEASE_DIR}" "${COMPOSE_PROJECT_NAME}" "${CURRENT_OBSERVABILITY_ENABLED}" up -d --no-build --remove-orphans

echo "cleaning incoming artifacts"
rm -f "${GATEWAY_IMAGE_ARCHIVE}" "${AUDIONODE_IMAGE_ARCHIVE}" "${ENV_FILE}" "${COMPOSE_FILE}"
rm -rf "${OPS_DIR}"
find "${RELEASES_DIR}" -mindepth 1 -maxdepth 1 -type d | sort | head -n -5 | xargs -r rm -rf

echo "deployed release ${SHA}"
