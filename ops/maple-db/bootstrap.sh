#!/bin/sh
set -eu

: "${POSTGRES_USER:?}"
: "${POSTGRES_PASSWORD:?}"
: "${POSTGRES_DB:?}"
: "${MAPLE_POSTGRES_PASSWORD:?}"

export PGPASSWORD="$POSTGRES_PASSWORD"
psql -X -v ON_ERROR_STOP=1 -h "${MAPLE_POSTGRES_HOST:-postgres}" -U "$POSTGRES_USER" -d "$POSTGRES_DB" \
  -v maple_password="$MAPLE_POSTGRES_PASSWORD" <<'SQL'
SELECT format('CREATE ROLE maple_app LOGIN PASSWORD %L', :'maple_password')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'maple_app') \gexec
SELECT format('ALTER ROLE maple_app PASSWORD %L', :'maple_password') \gexec
CREATE SCHEMA IF NOT EXISTS maple AUTHORIZATION maple_app;
ALTER SCHEMA maple OWNER TO maple_app;
SELECT format('GRANT CONNECT ON DATABASE %I TO maple_app', current_database()) \gexec
SQL
