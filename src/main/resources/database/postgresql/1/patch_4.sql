-- ============================================================
-- Allow accounts without email (non-login members)
-- ============================================================
ALTER TABLE ember_schema.account DROP CONSTRAINT IF EXISTS account_email_key;
ALTER TABLE ember_schema.account ALTER COLUMN email DROP NOT NULL;
CREATE UNIQUE INDEX account_email_key ON ember_schema.account (email) WHERE email IS NOT NULL;

-- ============================================================
-- Federation
-- ============================================================

-- Federation partnerships between stations
CREATE TABLE ember_schema.federation_partner (
    id                SERIAL PRIMARY KEY,
    station_id        INT       NOT NULL REFERENCES ember_schema.station (id) ON DELETE CASCADE,
    partner_station_id INT      NOT NULL REFERENCES ember_schema.station (id) ON DELETE CASCADE,
    invite_code       TEXT      UNIQUE,
    public_key        TEXT,
    partner_public_key TEXT,
    status            TEXT      NOT NULL DEFAULT 'PENDING',
    federation_version INT     NOT NULL DEFAULT 1,
    created_at        TIMESTAMP NOT NULL DEFAULT now(),
    updated_at        TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE (station_id, partner_station_id),
    CHECK (station_id != partner_station_id)
);

-- Capabilities per federation partner (what is shared and in which direction)
CREATE TABLE ember_schema.federation_capability (
    id          SERIAL PRIMARY KEY,
    partner_id  INT  NOT NULL REFERENCES ember_schema.federation_partner (id) ON DELETE CASCADE,
    capability  TEXT NOT NULL,
    direction   TEXT NOT NULL,
    enabled     BOOLEAN NOT NULL DEFAULT TRUE,
    UNIQUE (partner_id, capability, direction)
);

-- KB sharing configuration
CREATE TABLE ember_schema.federation_kb_share (
    id          SERIAL PRIMARY KEY,
    station_id  INT  NOT NULL REFERENCES ember_schema.station (id) ON DELETE CASCADE,
    file_id     INT  REFERENCES ember_schema.kb_file (id) ON DELETE CASCADE,
    folder_id   INT  REFERENCES ember_schema.kb_folder (id) ON DELETE CASCADE,
    share_scope TEXT NOT NULL DEFAULT 'ALL_PARTNERS',
    CHECK ((file_id IS NOT NULL AND folder_id IS NULL) OR (file_id IS NULL AND folder_id IS NOT NULL))
);

-- KB share targets (only used when share_scope = 'SPECIFIC')
CREATE TABLE ember_schema.federation_kb_share_target (
    share_id    INT NOT NULL REFERENCES ember_schema.federation_kb_share (id) ON DELETE CASCADE,
    partner_id  INT NOT NULL REFERENCES ember_schema.federation_partner (id) ON DELETE CASCADE,
    PRIMARY KEY (share_id, partner_id)
);

-- Quiz catalog sharing configuration
CREATE TABLE ember_schema.federation_quiz_share (
    id          SERIAL PRIMARY KEY,
    station_id  INT  NOT NULL REFERENCES ember_schema.station (id) ON DELETE CASCADE,
    catalog_id  INT  NOT NULL REFERENCES ember_schema.quiz_catalog (id) ON DELETE CASCADE,
    share_scope TEXT NOT NULL DEFAULT 'ALL_PARTNERS'
);

CREATE TABLE ember_schema.federation_quiz_share_target (
    share_id    INT NOT NULL REFERENCES ember_schema.federation_quiz_share (id) ON DELETE CASCADE,
    partner_id  INT NOT NULL REFERENCES ember_schema.federation_partner (id) ON DELETE CASCADE,
    PRIMARY KEY (share_id, partner_id)
);

-- Test protocol sharing configuration
CREATE TABLE ember_schema.federation_protocol_share (
    id          SERIAL PRIMARY KEY,
    station_id  INT  NOT NULL REFERENCES ember_schema.station (id) ON DELETE CASCADE,
    protocol_id INT  NOT NULL REFERENCES ember_schema.test_protocol (id) ON DELETE CASCADE,
    share_scope TEXT NOT NULL DEFAULT 'ALL_PARTNERS'
);

CREATE TABLE ember_schema.federation_protocol_share_target (
    share_id    INT NOT NULL REFERENCES ember_schema.federation_protocol_share (id) ON DELETE CASCADE,
    partner_id  INT NOT NULL REFERENCES ember_schema.federation_partner (id) ON DELETE CASCADE,
    PRIMARY KEY (share_id, partner_id)
);

-- Cached metadata for federated content (for browsing when remote is unavailable)
CREATE TABLE ember_schema.federation_metadata_cache (
    id              SERIAL PRIMARY KEY,
    partner_id      INT       NOT NULL REFERENCES ember_schema.federation_partner (id) ON DELETE CASCADE,
    content_type    TEXT      NOT NULL,
    remote_id       INT       NOT NULL,
    title           TEXT      NOT NULL,
    description     TEXT      NOT NULL DEFAULT '',
    extra_data      JSONB,
    cached_at       TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE (partner_id, content_type, remote_id)
);

-- Inventory sharing configuration (for Phase 3)
CREATE TABLE ember_schema.federation_inventory_share (
    id                  SERIAL PRIMARY KEY,
    station_id          INT  NOT NULL REFERENCES ember_schema.station (id) ON DELETE CASCADE,
    inventory_id        INT  REFERENCES ember_schema.inventory (id) ON DELETE CASCADE,
    item_id             INT  REFERENCES ember_schema.inventory_item (id) ON DELETE CASCADE,
    share_scope         TEXT NOT NULL DEFAULT 'ALL_PARTNERS',
    CHECK ((inventory_id IS NOT NULL AND item_id IS NULL) OR (inventory_id IS NULL AND item_id IS NOT NULL))
);

CREATE TABLE ember_schema.federation_inventory_share_target (
    share_id    INT NOT NULL REFERENCES ember_schema.federation_inventory_share (id) ON DELETE CASCADE,
    partner_id  INT NOT NULL REFERENCES ember_schema.federation_partner (id) ON DELETE CASCADE,
    PRIMARY KEY (share_id, partner_id)
);

-- Inventory lending requests
CREATE TABLE ember_schema.federation_lending_request (
    id                    SERIAL PRIMARY KEY,
    requesting_station_id INT       NOT NULL REFERENCES ember_schema.station (id) ON DELETE CASCADE,
    owning_station_id     INT       NOT NULL REFERENCES ember_schema.station (id) ON DELETE CASCADE,
    status                TEXT      NOT NULL DEFAULT 'REQUESTED',
    requested_date_from   DATE      NOT NULL,
    requested_date_to     DATE,
    created_by            INT       NOT NULL REFERENCES ember_schema.station_member (id),
    created_at            TIMESTAMP NOT NULL DEFAULT now(),
    updated_at            TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE ember_schema.federation_lending_request_item (
    id                  SERIAL PRIMARY KEY,
    request_id          INT NOT NULL REFERENCES ember_schema.federation_lending_request (id) ON DELETE CASCADE,
    inventory_id        INT REFERENCES ember_schema.inventory (id) ON DELETE CASCADE,
    item_id             INT REFERENCES ember_schema.inventory_item (id) ON DELETE CASCADE,
    quantity            INT NOT NULL DEFAULT 1,
    assigned_item_id    INT REFERENCES ember_schema.inventory_item (id)
);

-- Lending chat messages (text + system status messages)
CREATE TABLE ember_schema.federation_lending_message (
    id                SERIAL PRIMARY KEY,
    request_id        INT       NOT NULL REFERENCES ember_schema.federation_lending_request (id) ON DELETE CASCADE,
    sender_station_id INT       NOT NULL REFERENCES ember_schema.station (id) ON DELETE CASCADE,
    sender_member_id  INT       REFERENCES ember_schema.station_member (id),
    message           TEXT      NOT NULL,
    is_system         BOOLEAN   NOT NULL DEFAULT FALSE,
    created_at        TIMESTAMP NOT NULL DEFAULT now()
);

-- Inventory date blocking (station blocks lending during specific periods)
CREATE TABLE ember_schema.federation_inventory_block (
    id          SERIAL PRIMARY KEY,
    station_id  INT  NOT NULL REFERENCES ember_schema.station (id) ON DELETE CASCADE,
    inventory_id INT REFERENCES ember_schema.inventory (id) ON DELETE CASCADE,
    item_id     INT  REFERENCES ember_schema.inventory_item (id) ON DELETE CASCADE,
    block_from  DATE NOT NULL,
    block_to    DATE NOT NULL,
    reason      TEXT NOT NULL DEFAULT ''
);
