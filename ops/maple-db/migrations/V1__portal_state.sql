CREATE TABLE maple.portal_account (
    id varchar(32) PRIMARY KEY,
    token_hash varchar(64) NOT NULL UNIQUE,
    discord_user_id varchar(22) UNIQUE,
    owner_hash varchar(64),
    character_id bigint,
    flow_version integer NOT NULL DEFAULT 0,
    legacy_flow jsonb,
    created_at timestamptz NOT NULL,
    session_expires_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT portal_account_discord_id_format CHECK (discord_user_id IS NULL OR discord_user_id ~ '^[0-9]{1,22}$'),
    CONSTRAINT portal_account_owner_format CHECK (owner_hash IS NULL OR owner_hash ~ '^[a-f0-9]{64}$')
);

CREATE UNIQUE INDEX portal_account_linked_owner_unique
    ON maple.portal_account (owner_hash)
    WHERE owner_hash IS NOT NULL AND discord_user_id IS NOT NULL;

CREATE TABLE maple.character_workspace (
    portal_id varchar(32) NOT NULL REFERENCES maple.portal_account(id) ON DELETE CASCADE,
    workspace_key varchar(40) NOT NULL,
    character_id bigint,
    is_active boolean NOT NULL,
    PRIMARY KEY (portal_id, workspace_key),
    CONSTRAINT workspace_key_format CHECK (workspace_key = 'active' OR workspace_key ~ '^character:[0-9]+$')
);

CREATE TABLE maple.search_config (
    portal_id varchar(32) NOT NULL,
    workspace_key varchar(40) NOT NULL,
    config_id varchar(70) NOT NULL,
    mode varchar(10) NOT NULL,
    position integer NOT NULL,
    payload jsonb NOT NULL,
    PRIMARY KEY (portal_id, workspace_key, config_id, mode),
    FOREIGN KEY (portal_id, workspace_key) REFERENCES maple.character_workspace(portal_id, workspace_key) ON DELETE CASCADE,
    CONSTRAINT config_payload_object CHECK (jsonb_typeof(payload) = 'object')
);

CREATE TABLE maple.search_job (
    portal_id varchar(32) NOT NULL,
    workspace_key varchar(40) NOT NULL,
    job_id varchar(100) NOT NULL,
    position integer NOT NULL,
    phase varchar(40) NOT NULL,
    stopped boolean NOT NULL,
    payload jsonb NOT NULL,
    PRIMARY KEY (portal_id, workspace_key, job_id),
    FOREIGN KEY (portal_id, workspace_key) REFERENCES maple.character_workspace(portal_id, workspace_key) ON DELETE CASCADE,
    CONSTRAINT job_payload_object CHECK (jsonb_typeof(payload) = 'object')
);

CREATE TABLE maple.watch_seen_listing (
    portal_id varchar(32) NOT NULL,
    workspace_key varchar(40) NOT NULL,
    job_id varchar(100) NOT NULL,
    item_id text NOT NULL,
    PRIMARY KEY (portal_id, workspace_key, job_id, item_id),
    FOREIGN KEY (portal_id, workspace_key, job_id) REFERENCES maple.search_job(portal_id, workspace_key, job_id) ON DELETE CASCADE
);

CREATE TABLE maple.notification_outbox (
    event_id varchar(64) PRIMARY KEY,
    portal_id varchar(32) NOT NULL REFERENCES maple.portal_account(id) ON DELETE CASCADE,
    discord_user_id varchar(22) NOT NULL,
    job_id varchar(100) NOT NULL,
    item_id text NOT NULL,
    name varchar(100) NOT NULL,
    price numeric(25, 0),
    found_at timestamptz NOT NULL,
    status varchar(16) NOT NULL,
    attempts integer NOT NULL DEFAULT 0,
    ack_at timestamptz,
    CONSTRAINT notification_status CHECK (status IN ('pending', 'delivered', 'unavailable')),
    CONSTRAINT notification_attempts CHECK (attempts >= 0)
);
CREATE INDEX notification_outbox_pending_idx ON maple.notification_outbox (found_at, event_id) WHERE status = 'pending';
CREATE INDEX search_job_phase_idx ON maple.search_job (phase) WHERE stopped = false;

CREATE TABLE maple.audit_event (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    portal_id varchar(32) REFERENCES maple.portal_account(id) ON DELETE SET NULL,
    discord_user_id varchar(22),
    event_type varchar(50) NOT NULL,
    occurred_at timestamptz NOT NULL DEFAULT now(),
    details jsonb NOT NULL DEFAULT '{}'::jsonb,
    CONSTRAINT audit_details_object CHECK (jsonb_typeof(details) = 'object')
);
CREATE INDEX audit_event_portal_time_idx ON maple.audit_event (portal_id, occurred_at DESC);
