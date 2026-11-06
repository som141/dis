#!/bin/sh
set -eu
umask 077
mkdir -p /home/node/portal-data /home/node/maple-data
chown node:node /home/node/portal-data /home/node/maple-data
if [ -n "${MAPLE_SERVICE_KEY_FILE:-}" ]; then
  install -d -m 700 -o node -g node /run/maple
  install -m 600 -o node -g node "$MAPLE_SERVICE_KEY_FILE" /run/maple/nexon-service-key
  export MAPLE_SERVICE_KEY_FILE=/run/maple/nexon-service-key
fi
exec flock --nonblock --no-fork /home/node/portal-data/.portal.lock /bin/sh /app/docker/portal-session.sh
