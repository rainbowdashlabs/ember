-- The legacy enum value PAGE_IMAGES was a historical mislabel: the rows it tracked
-- correspond to data/page-files/ on disk, which the new model names PAGE_FILES.
-- Rename the rows in place so the new code reads the same totals from day one.

UPDATE ember_schema.station_storage_usage
SET category = 'PAGE_FILES'
WHERE category = 'PAGE_IMAGES';

-- The AVATARS and IMAGES rollup categories are no longer written by reconciliation;
-- bytes are now tracked per image category (IMAGE_AVATAR, IMAGE_LOST_AND_FOUND,
-- IMAGE_QUIZ_QUESTION, IMAGE_KB_ICON, IMAGE_KB_IMAGE). Drop the stale aggregate rows so
-- existing values do not skew the admin storage view.

DELETE FROM ember_schema.station_storage_usage
WHERE category IN ('AVATARS', 'IMAGES');

-- Per-station remote storage backend override. One row per station that has opted out of the
-- instance-default storage; the override applies across every station-scoped movable category
-- at that station. The config column carries the typed StationStorageBackendConfig variant;
-- credentials inside it are encrypted with storage.credentialEncryptionKey before being written.

CREATE TABLE ember_schema.station_storage_config (
    station_id   INTEGER NOT NULL PRIMARY KEY REFERENCES ember_schema.station(id) ON DELETE CASCADE,
    backend_type TEXT    NOT NULL,
    config       JSONB   NOT NULL DEFAULT '{}',
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Append-only audit trail for every backend-config mutation (CREATE / UPDATE / DELETE / REJECT)
-- plus user-triggered probes and migration lifecycle events. Indefinite retention: actor and
-- station FKs use ON DELETE SET NULL so the row survives account / station deletion as
-- proof-of-action; only the attribution thins out.
-- old_config and new_config carry FULL redacted snapshots — credentials are replaced with
-- {"credentials":"redacted","sha256OfCiphertext":"…"} before the row is written; raw cipher
-- material never enters this table.

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

CREATE INDEX idx_storage_backend_audit_station_ts
    ON ember_schema.storage_backend_audit (station_id, ts DESC);
CREATE INDEX idx_storage_backend_audit_ts
    ON ember_schema.storage_backend_audit (ts DESC);

-- Flag set by the source operator when a station export-transfer token is created. While the
-- flag is on, StorageService.store refuses uploads with 503 so the destination instance does
-- not race with new writes on the source mid-transfer. The flag clears when the source
-- operator deletes the station (existing cascade) or calls POST /station/transfer/abort.

ALTER TABLE ember_schema.station
    ADD COLUMN read_only_for_transfer BOOLEAN NOT NULL DEFAULT FALSE;

-- Identifies the destination instance that is currently importing the station. The destination
-- sends X-Ember-Importing-From on its first /public/transfer/{token}/tables call; the source
-- records the value here so the station settings banner can advertise where the station is
-- going.

ALTER TABLE ember_schema.transfer_token
    ADD COLUMN target_instance_url TEXT NULL;
