#!/usr/bin/env bash
set -euo pipefail

# Run on the Ubuntu server via systemd. GitHub Actions publishes verified images;
# this host initiates every network connection, so no inbound SSH is required.
DEPLOY_DIR="${DEPLOY_DIR:-/home/ubuntu/dis-bot}"
REPOSITORY="som141/dis"
SOURCE_DIR="${DEPLOY_DIR}/source"
INCOMING_DIR="${DEPLOY_DIR}/incoming"
ENV_FILE="${DEPLOY_DIR}/.env"

if [[ "${DEPLOY_DIR}" != /* || "${DEPLOY_DIR}" == / ]]; then
  echo "DEPLOY_DIR must be an absolute non-root path" >&2
  exit 1
fi
for command in curl jq git docker flock sha256sum; do
  command -v "${command}" >/dev/null || { echo "missing command: ${command}" >&2; exit 1; }
done
[[ -f "${ENV_FILE}" ]] || { echo "missing server configuration: ${ENV_FILE}" >&2; exit 1; }

mkdir -p "${DEPLOY_DIR}"
exec 9>"${DEPLOY_DIR}/.deploy.lock"
flock -n 9 || exit 0

release_json="$(curl -fsSL --retry 3 \
  -H 'Accept: application/vnd.github+json' \
  "https://api.github.com/repos/${REPOSITORY}/releases/latest")"
tag="$(jq -r '.tag_name // empty | select(startswith("deploy-"))' <<<"${release_json}")"
[[ -n "${tag}" ]] || { echo "no verified DIS release has been published"; exit 0; }
sha="${tag#deploy-}"
[[ "${sha}" =~ ^[0-9a-f]{40}$ ]] || { echo "invalid deployment tag" >&2; exit 1; }
if [[ -f "${DEPLOY_DIR}/last-successful-sha" && "$(cat "${DEPLOY_DIR}/last-successful-sha")" == "${sha}" ]]; then
  maple_active="$(grep -E '^MAPLE_DISCORD_ENABLED=' "${ENV_FILE}" | tail -n 1 | cut -d '=' -f 2- || true)"
  if [[ -f "${DEPLOY_DIR}/current/docker-compose.yml" && -f "${DEPLOY_DIR}/current/.env" ]] &&
    { [[ "${maple_active,,}" != true ]] || [[ -f "${DEPLOY_DIR}/current/apps/maple-portal/.docker-secrets/nexon-service-key" ]]; }; then
    echo "DIS ${sha} is already deployed"
    exit 0
  fi
  echo "DIS ${sha} is missing release files; redeploying"
fi

if [[ ! -d "${SOURCE_DIR}/.git" ]]; then
  git clone --quiet "https://github.com/${REPOSITORY}.git" "${SOURCE_DIR}"
fi
git -C "${SOURCE_DIR}" fetch --quiet --depth=1 origin "${sha}"
git -C "${SOURCE_DIR}" checkout --quiet --detach FETCH_HEAD

mkdir -p "${INCOMING_DIR}/ops"
chmod 700 "${INCOMING_DIR}"
release_url="https://github.com/${REPOSITORY}/releases/download/${tag}"
curl -fsSL --retry 3 -o "${INCOMING_DIR}/SHA256SUMS" "${release_url}/SHA256SUMS"
while read -r digest asset; do
  [[ "${digest}" =~ ^[0-9a-f]{64}$ ]] || { echo "invalid release checksum" >&2; exit 1; }
  [[ "${asset}" =~ ^(discord-gateway|discord-audio-node|discord-stock-node|discord-youtube-cipher|dis-maple-portal)-${sha}\.tar\.gz$ ]] || {
    echo "unexpected release asset" >&2; exit 1;
  }
  curl -fsSL --retry 3 -o "${INCOMING_DIR}/${asset}" "${release_url}/${asset}"
done < "${INCOMING_DIR}/SHA256SUMS"
(cd "${INCOMING_DIR}" && sha256sum -c SHA256SUMS)

for image in discord-gateway discord-audio-node discord-stock-node discord-youtube-cipher; do
  [[ -f "${INCOMING_DIR}/${image}-${sha}.tar.gz" ]] || { echo "missing image: ${image}" >&2; exit 1; }
done

# Runtime secrets stay on this machine. Replace only image tags in a release copy.
awk '!/^(GATEWAY|AUDIO_NODE|STOCK_NODE|YOUTUBE_CIPHER|MAPLE_PORTAL)_IMAGE_(NAME|TAG)=/' \
  "${ENV_FILE}" > "${INCOMING_DIR}/.env.cicd"
for name in GATEWAY AUDIO_NODE STOCK_NODE YOUTUBE_CIPHER MAPLE_PORTAL; do
  case "${name}" in
    GATEWAY) image=discord-gateway ;;
    AUDIO_NODE) image=discord-audio-node ;;
    STOCK_NODE) image=discord-stock-node ;;
    YOUTUBE_CIPHER) image=discord-youtube-cipher ;;
    MAPLE_PORTAL) image=dis-maple-portal ;;
  esac
  printf '%s_IMAGE_NAME=%s\n' "${name}" "${image}" >> "${INCOMING_DIR}/.env.cicd"
  printf '%s_IMAGE_TAG=%s\n' "${name}" "${sha}" >> "${INCOMING_DIR}/.env.cicd"
done
chmod 600 "${INCOMING_DIR}/.env.cicd"

maple_enabled="$(grep -E '^MAPLE_DISCORD_ENABLED=' "${ENV_FILE}" | tail -n 1 | cut -d '=' -f 2- || true)"
if [[ "${maple_enabled,,}" == true ]]; then
  maple_db_password="$(grep -E '^MAPLE_POSTGRES_PASSWORD=' "${ENV_FILE}" | tail -n 1 | cut -d '=' -f 2- || true)"
  [[ -n "${maple_db_password}" ]] || { echo "missing MAPLE_POSTGRES_PASSWORD in server .env" >&2; exit 1; }
  [[ -f "${INCOMING_DIR}/dis-maple-portal-${sha}.tar.gz" ]] || { echo "release has no Maple image" >&2; exit 1; }
  key_file="${DEPLOY_DIR}/secrets/maple-nexon-service-key"
  [[ -f "${key_file}" ]] || { echo "missing Maple service key file" >&2; exit 1; }
  cp "${key_file}" "${INCOMING_DIR}/maple-nexon-service-key"
  chmod 600 "${INCOMING_DIR}/maple-nexon-service-key"
fi

cp "${SOURCE_DIR}/docker-compose.yml" "${INCOMING_DIR}/docker-compose.yml"
cp -R "${SOURCE_DIR}/ops/." "${INCOMING_DIR}/ops/"
DEPLOY_DIR="${DEPLOY_DIR}" bash "${SOURCE_DIR}/deploy.sh" "${sha}"
printf '%s\n' "${sha}" > "${DEPLOY_DIR}/last-successful-sha"
echo "DIS ${sha} deployed"
