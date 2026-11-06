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
RELEASES_TO_KEEP="${RELEASES_TO_KEEP:-2}"
GATEWAY_IMAGE_ARCHIVE="${INCOMING_DIR}/discord-gateway-${SHA}.tar.gz"
AUDIONODE_IMAGE_ARCHIVE="${INCOMING_DIR}/discord-audio-node-${SHA}.tar.gz"
STOCKNODE_IMAGE_ARCHIVE="${INCOMING_DIR}/discord-stock-node-${SHA}.tar.gz"
YOUTUBE_CIPHER_IMAGE_ARCHIVE="${INCOMING_DIR}/discord-youtube-cipher-${SHA}.tar.gz"
MAPLE_PORTAL_IMAGE_ARCHIVE="${INCOMING_DIR}/dis-maple-portal-${SHA}.tar.gz"
MAPLE_SERVICE_KEY_FILE="${INCOMING_DIR}/maple-nexon-service-key"
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
require_file "${STOCKNODE_IMAGE_ARCHIVE}"
require_file "${YOUTUBE_CIPHER_IMAGE_ARCHIVE}"
require_file "${ENV_FILE}"
require_file "${COMPOSE_FILE}"

MAPLE_ENABLED="$(grep -E '^MAPLE_DISCORD_ENABLED=' "${ENV_FILE}" | tail -n 1 | cut -d '=' -f 2- || true)"
if [[ "${MAPLE_ENABLED,,}" == "true" ]]; then
  [[ -n "$(grep -E '^MAPLE_POSTGRES_PASSWORD=' "${ENV_FILE}" | tail -n 1 | cut -d '=' -f 2- || true)" ]] || {
    echo "missing MAPLE_POSTGRES_PASSWORD in release configuration" >&2; exit 1;
  }
  require_file "${MAPLE_PORTAL_IMAGE_ARCHIVE}"
  require_file "${MAPLE_SERVICE_KEY_FILE}"
  require_file "${OPS_DIR}/maple-edge/nginx.conf"
fi

validate_observability_files() {
  local release_dir="$1"
  local required_paths=(
    "ops/observability/prometheus/prometheus.yml"
    "ops/observability/grafana/provisioning/dashboards/dashboards.yml"
    "ops/observability/grafana/provisioning/datasources/datasources.yml"
    "ops/observability/grafana/dashboards/discord-bot-app-overview.json"
    "ops/observability/grafana/dashboards/discord-bot-infra-overview.json"
    "ops/observability/grafana/dashboards/discord-bot-postgres-overview.json"
    "ops/observability/grafana/dashboards/discord-bot-stock-node-overview.json"
  )
  local path

  for path in "${required_paths[@]}"; do
    require_file "${release_dir}/${path}"
  done
}

prune_unused_dis_images() {
  echo "pruning unused DIS docker images before loading release"
  docker image prune -f || true
  docker images --format '{{.Repository}} {{.ID}}' \
    | awk '$1 == "discord-gateway" || $1 == "discord-audio-node" || $1 == "discord-stock-node" || $1 == "discord-youtube-cipher" { print $2 }' \
    | sort -u \
    | xargs -r docker image rm || true
}

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
  local maple_enabled
  maple_enabled="$(env_value_from_file "${dir}/.env" "MAPLE_DISCORD_ENABLED")"
  if [[ "${maple_enabled,,}" == "true" ]]; then
    compose_cmd+=(--profile maple)
  fi
  compose_cmd+=("$@")
  "${compose_cmd[@]}"
  popd >/dev/null
}

remove_legacy_fixed_name_containers() {
  local legacy_containers=()
  local name

  for name in dis-bot discord-gateway discord-audio-node discord-stock-node discord-redis discord-rabbitmq; do
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
chmod 600 "${RELEASE_DIR}/.env"
cp "${GATEWAY_IMAGE_ARCHIVE}" "${RELEASE_DIR}/discord-gateway.tar.gz"
cp "${AUDIONODE_IMAGE_ARCHIVE}" "${RELEASE_DIR}/discord-audio-node.tar.gz"
cp "${STOCKNODE_IMAGE_ARCHIVE}" "${RELEASE_DIR}/discord-stock-node.tar.gz"
cp "${YOUTUBE_CIPHER_IMAGE_ARCHIVE}" "${RELEASE_DIR}/discord-youtube-cipher.tar.gz"
if [[ "${MAPLE_ENABLED,,}" == "true" ]]; then
  cp "${MAPLE_PORTAL_IMAGE_ARCHIVE}" "${RELEASE_DIR}/dis-maple-portal.tar.gz"
  mkdir -p "${RELEASE_DIR}/apps/maple-portal/.docker-secrets"
  cp "${MAPLE_SERVICE_KEY_FILE}" "${RELEASE_DIR}/apps/maple-portal/.docker-secrets/nexon-service-key"
  chmod 600 "${RELEASE_DIR}/apps/maple-portal/.docker-secrets/nexon-service-key"
fi
if [[ -d "${OPS_DIR}" ]]; then
  rm -rf "${RELEASE_DIR}/ops"
  cp -R "${OPS_DIR}" "${RELEASE_DIR}/ops"
  find "${RELEASE_DIR}/ops" -type f -name "*.sh" -exec chmod +x {} \;
fi

RELEASE_OBSERVABILITY_ENABLED="$(env_value_from_file "${RELEASE_DIR}/.env" "OBSERVABILITY_ENABLED")"
if [[ "${RELEASE_OBSERVABILITY_ENABLED,,}" == "true" ]]; then
  echo "validating observability provisioning files"
  validate_observability_files "${RELEASE_DIR}"
fi

echo "validating compose configuration"
compose_in_dir "${RELEASE_DIR}" "${COMPOSE_PROJECT_NAME}" "${RELEASE_OBSERVABILITY_ENABLED}" config --quiet

prune_unused_dis_images

echo "loading gateway image archive"
gzip -dc "${RELEASE_DIR}/discord-gateway.tar.gz" | docker load

echo "loading audio-node image archive"
gzip -dc "${RELEASE_DIR}/discord-audio-node.tar.gz" | docker load

echo "loading stock-node image archive"
gzip -dc "${RELEASE_DIR}/discord-stock-node.tar.gz" | docker load

echo "loading youtube-cipher image archive"
gzip -dc "${RELEASE_DIR}/discord-youtube-cipher.tar.gz" | docker load

if [[ "${MAPLE_ENABLED,,}" == "true" ]]; then
  echo "loading Maple portal image archive"
  gzip -dc "${RELEASE_DIR}/dis-maple-portal.tar.gz" | docker load
fi

echo "removing legacy fixed-name containers if present"
remove_legacy_fixed_name_containers

if [[ -n "${PREVIOUS_RELEASE_DIR}" && -d "${PREVIOUS_RELEASE_DIR}" && "${PREVIOUS_RELEASE_DIR}" != "${RELEASE_DIR}" ]]; then
  PREVIOUS_PROJECT_NAME="$(compose_project_from_env "${PREVIOUS_RELEASE_DIR}/.env")"
  PREVIOUS_OBSERVABILITY_ENABLED="$(env_value_from_file "${PREVIOUS_RELEASE_DIR}/.env" "OBSERVABILITY_ENABLED")"
  if [[ -f "${PREVIOUS_RELEASE_DIR}/docker-compose.yml" && -f "${PREVIOUS_RELEASE_DIR}/.env" ]]; then
    if [[ "${MAPLE_ENABLED,,}" == "true" ]]; then
      mkdir -p "${RELEASE_DIR}/backup"
      chmod 700 "${RELEASE_DIR}/backup"
      compose_in_dir "${PREVIOUS_RELEASE_DIR}" "${PREVIOUS_PROJECT_NAME}" "${PREVIOUS_OBSERVABILITY_ENABLED}" \
        exec -T postgres sh -c 'PGPASSWORD="$POSTGRES_PASSWORD" pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' \
        > "${RELEASE_DIR}/backup/postgres-before-maple.dump"
      chmod 600 "${RELEASE_DIR}/backup/postgres-before-maple.dump"
      maple_volume="${PREVIOUS_PROJECT_NAME}_maple-portal-data"
      if docker volume inspect "${maple_volume}" >/dev/null 2>&1; then
        docker run --rm -v "${maple_volume}:/data:ro" -v "${RELEASE_DIR}/backup:/backup" postgres:16-alpine \
          sh -c 'cd /data; set -- *.json; if [ -e "$1" ]; then tar -czf /backup/maple-json-before-migration.tar.gz "$@"; fi'
      fi
    fi
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
compose_in_dir "${RELEASE_DIR}" "${COMPOSE_PROJECT_NAME}" "${CURRENT_OBSERVABILITY_ENABLED}" up -d --no-build --remove-orphans --wait --wait-timeout 180

echo "cleaning incoming artifacts"
rm -f "${GATEWAY_IMAGE_ARCHIVE}" "${AUDIONODE_IMAGE_ARCHIVE}" "${STOCKNODE_IMAGE_ARCHIVE}" "${YOUTUBE_CIPHER_IMAGE_ARCHIVE}" "${ENV_FILE}" "${COMPOSE_FILE}"
rm -f "${MAPLE_PORTAL_IMAGE_ARCHIVE}" "${MAPLE_SERVICE_KEY_FILE}"
rm -rf "${OPS_DIR}"
mapfile -t release_dirs < <(find "${RELEASES_DIR}" -mindepth 1 -maxdepth 1 -type d -printf '%T@ %p\n' | sort -rn | cut -d ' ' -f 2-)
for ((index=RELEASES_TO_KEEP; index<${#release_dirs[@]}; index++)); do
  stale_dir="${release_dirs[index]}"
  if [[ "${stale_dir}" == "${RELEASE_DIR}" || "${stale_dir}" == "${PREVIOUS_RELEASE_DIR}" ]]; then
    continue
  fi
  rm -rf -- "${stale_dir}"
done

echo "deployed release ${SHA}"
