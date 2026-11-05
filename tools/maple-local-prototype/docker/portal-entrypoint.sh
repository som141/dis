#!/bin/sh
set -eu
umask 077
mkdir -p /home/node/portal-data /home/node/maple-data
chown node:node /home/node/portal-data /home/node/maple-data
exec flock --nonblock --no-fork /home/node/portal-data/.portal.lock /bin/sh /app/docker/portal-session.sh
