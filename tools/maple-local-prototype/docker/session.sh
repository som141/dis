#!/bin/sh
set -eu
# A replaced container has a new PID namespace. Chromium's previous process
# lock can survive an abrupt stop and incorrectly look like another computer.
# This profile volume belongs exclusively to this desktop (flock held here).
rm -f /home/node/browser-profile/SingletonLock /home/node/browser-profile/SingletonSocket /home/node/browser-profile/SingletonCookie
exec /usr/bin/supervisord -n -c /app/docker/supervisord.conf
