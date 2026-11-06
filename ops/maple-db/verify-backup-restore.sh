#!/bin/sh
set -eu

psql -d maple_test -v ON_ERROR_STOP=1 <<'SQL'
INSERT INTO maple.portal_account(id, token_hash, created_at, session_expires_at)
VALUES (repeat('9', 32), repeat('8', 64), now(), now() + interval '1 day');
INSERT INTO maple.character_workspace(portal_id, workspace_key, is_active)
VALUES (repeat('9', 32), 'active', true);
INSERT INTO maple.search_job(portal_id, workspace_key, job_id, position, phase, stopped, payload)
VALUES (repeat('9', 32), 'active', 'backup-fixture', 0, 'complete', false, '{}');
INSERT INTO maple.watch_seen_listing(portal_id, workspace_key, job_id, item_id)
VALUES (repeat('9', 32), 'active', 'backup-fixture', 'listing-1');
SQL

pg_dump -d maple_test -n maple -Fc -f /backup/maple-test.dump
psql -d maple_test -v ON_ERROR_STOP=1 -c 'CREATE DATABASE maple_restore'
pg_restore -d maple_restore --no-owner --no-privileges /backup/maple-test.dump

for table in portal_account character_workspace search_job watch_seen_listing notification_outbox audit_event flyway_schema_history; do
  original=$(psql -d maple_test -At -c "SELECT count(*) FROM maple.$table")
  restored=$(psql -d maple_restore -At -c "SELECT count(*) FROM maple.$table")
  if [ "$original" != "$restored" ]; then
    echo "Restore count mismatch: $table" >&2
    exit 1
  fi
done

echo 'Maple PostgreSQL backup and restore counts match'
