#!/bin/sh
set -eu
Xvfb :99 -screen 0 1280x900x24 -nolisten tcp -ac >/dev/null 2>&1 &
display_pid=$!
until xdpyinfo -display :99 >/dev/null 2>&1; do sleep 0.1; done
gosu node node /app/src/portal-main.mjs &
portal_pid=$!
cleanup() {
  kill -TERM "$portal_pid" 2>/dev/null || true
  wait "$portal_pid" 2>/dev/null || true
  kill -TERM "$display_pid" 2>/dev/null || true
}
trap cleanup INT TERM EXIT
wait "$portal_pid"
