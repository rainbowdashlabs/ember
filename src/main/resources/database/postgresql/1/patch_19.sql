UPDATE ember_schema.station_storage_usage
SET category = 'PAGE_FILES'
WHERE category = 'PAGE_IMAGES';

DELETE FROM ember_schema.station_storage_usage
WHERE category IN ('AVATARS', 'IMAGES');

CREATE TABLE ember_schema.station_storage_config (
    station_id   INTEGER NOT NULL PRIMARY KEY REFERENCES ember_schema.station(id) ON DELETE CASCADE,
    backend_type TEXT    NOT NULL,
    config       JSONB   NOT NULL DEFAULT '{}',
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE ember_schema.station_storage_config IS
    'Per-station remote storage backend override. One row per station that has opted out of the instance-default storage; the override applies across every station-scoped movable category at that station.';
COMMENT ON COLUMN ember_schema.station_storage_config.backend_type IS
    'Discriminator for the typed StationStorageBackendConfig variant carried in config.';
COMMENT ON COLUMN ember_schema.station_storage_config.config IS
    'Typed StationStorageBackendConfig variant. Credentials inside it are encrypted with storage.credentialEncryptionKey before being written.';

CREATE TABLE ember_schema.storage_backend_audit (
    id                BIGSERIAL PRIMARY KEY,
    ts                TIMESTAMPTZ NOT NULL DEFAULT now(),
    actor_account_id  INTEGER     NULL REFERENCES ember_schema.account(id) ON DELETE SET NULL,
    actor_member_id   INTEGER     NULL REFERENCES ember_schema.station_member(id) ON DELETE SET NULL,
    system_actor      TEXT        NULL,
    station_id        INTEGER     NULL REFERENCES ember_schema.station(id) ON DELETE SET NULL,
    action            TEXT        NOT NULL,
    old_config        JSONB       NULL,
    new_config        JSONB       NULL,
    outcome           TEXT        NOT NULL,
    error             TEXT        NULL,
    CONSTRAINT storage_backend_audit_actor_present
        CHECK (actor_account_id IS NOT NULL OR system_actor IS NOT NULL)
);

COMMENT ON TABLE ember_schema.storage_backend_audit IS
    'Append-only audit trail for every backend-config mutation (CREATE / UPDATE / DELETE / REJECT) plus user-triggered probes and migration lifecycle events. Indefinite retention: actor and station FKs use ON DELETE SET NULL so the row survives account / station deletion as proof-of-action; only the attribution thins out.';
COMMENT ON COLUMN ember_schema.storage_backend_audit.system_actor IS
    'Identifier of the system actor (scheduler, migration, boot) when no human actor is present.';
COMMENT ON COLUMN ember_schema.storage_backend_audit.old_config IS
    'Full redacted snapshot of the prior config. Credentials are replaced with {"credentials":"redacted","sha256OfCiphertext":"…"} before the row is written; raw cipher material never enters this table.';
COMMENT ON COLUMN ember_schema.storage_backend_audit.new_config IS
    'Full redacted snapshot of the new config. Credentials are replaced with {"credentials":"redacted","sha256OfCiphertext":"…"} before the row is written; raw cipher material never enters this table.';

CREATE INDEX idx_storage_backend_audit_station_ts
    ON ember_schema.storage_backend_audit (station_id, ts DESC);
CREATE INDEX idx_storage_backend_audit_ts
    ON ember_schema.storage_backend_audit (ts DESC);

ALTER TABLE ember_schema.station
    ADD COLUMN read_only_for_transfer BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN ember_schema.station.read_only_for_transfer IS
    'Set by the source operator when a station export-transfer token is created. While the flag is on, StorageService.store refuses uploads with 503 so the destination instance does not race with new writes on the source mid-transfer. The flag clears when the source operator deletes the station (existing cascade) or calls POST /station/transfer/abort.';

ALTER TABLE ember_schema.transfer_token
    ADD COLUMN target_instance_url TEXT NULL;

COMMENT ON COLUMN ember_schema.transfer_token.target_instance_url IS
    'Identifies the destination instance that is currently importing the station. The destination sends X-Ember-Importing-From on its first /public/transfer/{token}/tables call; the source records the value here so the station settings banner can advertise where the station is going.';
