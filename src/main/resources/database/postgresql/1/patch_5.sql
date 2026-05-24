-- Add source reference for files copied from federation partners
ALTER TABLE ember_schema.kb_file
    ADD COLUMN source_file_id    INT DEFAULT NULL,
    ADD COLUMN source_station_id INT DEFAULT NULL;

-- Add UUID identifier to station for external use (avoids enumeration, enables cross-instance federation)
ALTER TABLE ember_schema.station
    ADD COLUMN uid UUID NOT NULL DEFAULT gen_random_uuid();
CREATE UNIQUE INDEX idx_station_uid ON ember_schema.station(uid);

-- Public knowledgebase mode per station (OFF, ALLOW_ALL, DENY_ALL)
ALTER TABLE ember_schema.station
    ADD COLUMN public_kb_mode TEXT NOT NULL DEFAULT 'OFF';

-- Per-folder/file public visibility override
CREATE TABLE ember_schema.kb_public_visibility (
    id        SERIAL PRIMARY KEY,
    folder_id INT UNIQUE REFERENCES ember_schema.kb_folder(id) ON DELETE CASCADE,
    file_id   INT UNIQUE REFERENCES ember_schema.kb_file(id) ON DELETE CASCADE,
    visible   BOOLEAN NOT NULL,
    CONSTRAINT chk_kb_public_vis_target CHECK (
        (folder_id IS NOT NULL AND file_id IS NULL)
        OR (folder_id IS NULL AND file_id IS NOT NULL)
    )
);

-- Rename management roles to manager (fix typo in ATTENDENCE too)
UPDATE ember_schema.role SET name = 'ATTENDANCE_MANAGER' WHERE name = 'ATTENDENCE_MANAGEMENT';
UPDATE ember_schema.role SET name = 'ATTENDANCE_EXPORT_MANAGER' WHERE name = 'ATTENDENCE_EXPORT_MANAGER';
UPDATE ember_schema.role SET name = 'INVENTORY_MANAGER' WHERE name = 'INVENTORY_MANAGEMENT';
UPDATE ember_schema.role SET name = 'EVENT_MANAGER' WHERE name = 'EVENT_MANAGEMENT';
UPDATE ember_schema.role SET name = 'MEMBER_MANAGER' WHERE name = 'MEMBER_MANAGEMENT';
UPDATE ember_schema.role SET name = 'NEWS_MANAGER' WHERE name = 'NEWS_MANAGEMENT';
UPDATE ember_schema.role SET name = 'POLL_MANAGER' WHERE name = 'POLL_MANAGEMENT';
UPDATE ember_schema.role SET name = 'LOST_AND_FOUND_MANAGER' WHERE name = 'LOST_AND_FOUND_MANAGEMENT';
UPDATE ember_schema.role SET name = 'WAITLIST_MANAGER' WHERE name = 'WAITLIST_MANAGEMENT';
UPDATE ember_schema.role SET name = 'QUIZ_MANAGER' WHERE name = 'QUIZ_MANAGEMENT';
UPDATE ember_schema.role SET name = 'KNOWLEDGE_MANAGER' WHERE name = 'KNOWLEDGE_MANAGEMENT';
UPDATE ember_schema.role SET name = 'FEDERATION_MANAGER' WHERE name = 'FEDERATION_MANAGEMENT';
UPDATE ember_schema.role SET name = 'PROTOCOL_MANAGER' WHERE name = 'PROTOCOL_MANAGEMENT';

-- API request log for monitoring
CREATE TABLE ember_schema.api_request_log (
    id          BIGSERIAL PRIMARY KEY,
    method      TEXT      NOT NULL,
    path        TEXT      NOT NULL,
    status_code INT       NOT NULL,
    duration_ms INT       NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_api_request_log_created ON ember_schema.api_request_log(created_at);
CREATE INDEX idx_api_request_log_path ON ember_schema.api_request_log(path, created_at);

-- Add private key storage for federation request signing (per station, reused for all partners)
ALTER TABLE ember_schema.station ADD COLUMN federation_private_key TEXT;

-- Federation partner: add remote_host for cross-instance communication (NULL = local/same instance)
ALTER TABLE ember_schema.federation_partner ADD COLUMN remote_host TEXT;

-- Federation partner: add webhook and sync columns
ALTER TABLE ember_schema.federation_partner ADD COLUMN webhook_url TEXT;
ALTER TABLE ember_schema.federation_partner ADD COLUMN last_sync_at TIMESTAMP;

-- Federation change log for sync polling
CREATE TABLE ember_schema.federation_change_log (
    id          SERIAL PRIMARY KEY,
    station_id  INT       NOT NULL REFERENCES ember_schema.station(id) ON DELETE CASCADE,
    content_type TEXT     NOT NULL,
    content_id  INT       NOT NULL,
    change_type TEXT      NOT NULL,
    changed_at  TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_federation_change_log_station ON ember_schema.federation_change_log(station_id, changed_at);

-- Restriction mode (AND/OR) for entities with role/group/tag restrictions
ALTER TABLE ember_schema.station_event ADD COLUMN restriction_mode TEXT NOT NULL DEFAULT 'AND';
ALTER TABLE ember_schema.quiz_test ADD COLUMN restriction_mode TEXT NOT NULL DEFAULT 'AND';
ALTER TABLE ember_schema.form ADD COLUMN restriction_mode TEXT NOT NULL DEFAULT 'AND';
