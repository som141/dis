#!/bin/sh
set -eu
umask 077
mkdir -p /run/maple /home/node/browser-profile /home/node/maple-data /home/node/.pki/nssdb
chown node:node /run/maple /home/node/browser-profile /home/node/maple-data /home/node/.pki /home/node/.pki/nssdb

# Session-specific trust is installed only when starting a container, never in
# image layers. Browser TLS verification remains enabled.
if [ -f /run/secrets/proxy_ca ]; then
  cp /run/secrets/proxy_ca /usr/local/share/ca-certificates/managed-session-proxy.crt
  chmod 644 /usr/local/share/ca-certificates/managed-session-proxy.crt
  export NODE_EXTRA_CA_CERTS=/usr/local/share/ca-certificates/managed-session-proxy.crt
  update-ca-certificates >/dev/null 2>&1
  if [ ! -f /home/node/.pki/nssdb/cert9.db ]; then
    gosu node certutil -N -d sql:/home/node/.pki/nssdb --empty-password
  fi
  gosu node certutil -A -d sql:/home/node/.pki/nssdb -n managed-session-proxy -t 'C,,' -i /usr/local/share/ca-certificates/managed-session-proxy.crt
fi
if [ ! -f /run/secrets/vnc_password ]; then
  echo 'Missing desktop password. Run node docker/setup.mjs before Compose.' >&2
  exit 1
fi
# Keep the password out of argv/logs. x11vnc reads its private password file.
cp /run/secrets/vnc_password /run/maple/vnc-password
chown node:node /run/maple/vnc-password
chmod 600 /run/maple/vnc-password
# An exclusive volume lock prevents two desktops from using the same profile.
# The next stage clears only stale Chromium process/socket links, not cookies.
exec flock --nonblock --no-fork /home/node/browser-profile/.prototype-container.lock /bin/sh /app/docker/session.sh
