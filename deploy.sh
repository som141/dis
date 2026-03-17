#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 1 ]]; then
  echo "usage: $0 <git-sha>"
  exit 1
fi

SHA="$1"
DEPLOY_DIR="${DEPLOY_DIR:-/home/ubuntu/dis-bot}"
INCOMING_DIR="${DEPLOY_DIR}/incoming"
RELEASES_DIR="${DEPLOY_DIR}/releases"
RELEASE_DIR="${RELEASES_DIR}/${SHA}"
CURRENT_LINK="${DEPLOY_DIR}/current"
IMAGE_ARCHIVE="${INCOMING_DIR}/discord-bot-${SHA}.tar.gz"
ENV_FILE="${INCOMING_DIR}/.env.cicd"
COMPOSE_FILE="${INCOMING_DIR}/docker-compose.yml"

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

mkdir -p "${RELEASE_DIR}"

cp "${COMPOSE_FILE}" "${RELEASE_DIR}/docker-compose.yml"
cp "${ENV_FILE}" "${RELEASE_DIR}/.env"
cp "${IMAGE_ARCHIVE}" "${RELEASE_DIR}/discord-bot.tar.gz"

gzip -dc "${RELEASE_DIR}/discord-bot.tar.gz" | docker load

ln -sfn "${RELEASE_DIR}" "${CURRENT_LINK}"

pushd "${RELEASE_DIR}" >/dev/null
docker compose --env-file .env up -d --no-build --remove-orphans
popd >/dev/null

rm -f "${IMAGE_ARCHIVE}" "${ENV_FILE}" "${COMPOSE_FILE}"
find "${RELEASES_DIR}" -mindepth 1 -maxdepth 1 -type d | sort | head -n -5 | xargs -r rm -rf

echo "deployed release ${SHA}"
