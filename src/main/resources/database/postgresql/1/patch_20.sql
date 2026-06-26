CREATE TABLE ember_schema.inventory_container_kind
(
    id         SERIAL PRIMARY KEY,
    station_id INTEGER NOT NULL REFERENCES ember_schema.station (id) ON DELETE CASCADE,
    key        TEXT    NOT NULL,
    label      TEXT    NOT NULL,
    icon       TEXT    NOT NULL DEFAULT 'box',
    sort_order INTEGER NOT NULL DEFAULT 0,
    enabled    BOOLEAN NOT NULL DEFAULT TRUE,
    UNIQUE (station_id, key)
);

CREATE INDEX idx_inventory_container_kind_station ON ember_schema.inventory_container_kind (station_id);

COMMENT ON TABLE ember_schema.inventory_container_kind IS
    'Station-defined container kinds (room, drawer, box, etc.). Seven defaults are seeded on first use; stations can rename, disable, or add custom kinds. Kinds are a display hint only — picking any kind on any container is allowed.';

CREATE TABLE ember_schema.inventory_container
(
    id          SERIAL PRIMARY KEY,
    station_id  INTEGER     NOT NULL REFERENCES ember_schema.station (id) ON DELETE CASCADE,
    parent_id   INTEGER     NULL REFERENCES ember_schema.inventory_container (id) ON DELETE SET NULL,
    internal_id TEXT        NULL,
    name        TEXT        NOT NULL,
    kind_id     INTEGER     NULL REFERENCES ember_schema.inventory_container_kind (id) ON DELETE SET NULL,
    description TEXT        NOT NULL DEFAULT '',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by  INTEGER     NULL REFERENCES ember_schema.station_member (id) ON DELETE SET NULL,
    CONSTRAINT inventory_container_no_self_parent CHECK (parent_id IS DISTINCT FROM id),
    UNIQUE (station_id, internal_id)
);

CREATE INDEX idx_inventory_container_station ON ember_schema.inventory_container (station_id);
CREATE INDEX idx_inventory_container_parent ON ember_schema.inventory_container (parent_id);

COMMENT ON TABLE ember_schema.inventory_container IS
    'Station-scoped storage location. Every room, drawer, shelf, and box is one row; parent_id forms a forest. Cycles are prevented at the service layer.';
COMMENT ON COLUMN ember_schema.inventory_container.internal_id IS
    'Scannable internal id, drawn from the same per-station namespace as inventory_item.internal_id. NULL is allowed for containers the operator has not labelled yet.';
COMMENT ON COLUMN ember_schema.inventory_container.parent_id IS
    'Optional parent container. ON DELETE SET NULL on purpose: deleting a parent promotes its children to roots rather than cascading the delete.';

CREATE TABLE ember_schema.inventory_container_history
(
    id           SERIAL PRIMARY KEY,
    container_id INTEGER     NULL REFERENCES ember_schema.inventory_container (id) ON DELETE SET NULL,
    station_id   INTEGER     NOT NULL REFERENCES ember_schema.station (id) ON DELETE CASCADE,
    event_kind   TEXT        NOT NULL,
    event_ts     TIMESTAMPTZ NOT NULL DEFAULT now(),
    actor_id     INTEGER     NULL REFERENCES ember_schema.station_member (id) ON DELETE SET NULL,
    details      JSONB       NOT NULL DEFAULT '{}'
);

CREATE INDEX idx_inventory_container_history_container ON ember_schema.inventory_container_history (container_id);
CREATE INDEX idx_inventory_container_history_station ON ember_schema.inventory_container_history (station_id, event_ts DESC);

COMMENT ON TABLE ember_schema.inventory_container_history IS
    'Append-only ledger of container lifecycle events (CREATED, RENAMED, MOVED, DELETED). container_id uses ON DELETE SET NULL so a deletion event survives the deletion of its subject.';

CREATE TABLE ember_schema.inventory_field_definition
(
    id           SERIAL PRIMARY KEY,
    inventory_id INTEGER NOT NULL REFERENCES ember_schema.inventory (id) ON DELETE CASCADE,
    key          TEXT    NOT NULL,
    label        TEXT    NOT NULL,
    field_type   TEXT    NOT NULL,
    required     BOOLEAN NOT NULL DEFAULT FALSE,
    sort_order   INTEGER NOT NULL DEFAULT 0,
    config       JSONB   NOT NULL DEFAULT '{}',
    UNIQUE (inventory_id, key)
);

CREATE INDEX idx_inventory_field_definition_inventory ON ember_schema.inventory_field_definition (inventory_id);

COMMENT ON TABLE ember_schema.inventory_field_definition IS
    'Per-inventory custom field schema. Items in the inventory carry one JSONB sub-object keyed by field key inside inventory_item.metadata.fields.';
COMMENT ON COLUMN ember_schema.inventory_field_definition.key IS
    'Stable machine identifier used in API responses and CSV exports. Renaming the key is not a UI action — the operator deletes and re-adds.';
COMMENT ON COLUMN ember_schema.inventory_field_definition.field_type IS
    'One of DATE, ENUM, TEXT, NUMBER, BOOLEAN.';
COMMENT ON COLUMN ember_schema.inventory_field_definition.config IS
    'Typed config record per field_type (FieldConfig.Date, FieldConfig.Enum, FieldConfig.Text, FieldConfig.Number, FieldConfig.Boolean), parsed on read.';

ALTER TABLE ember_schema.inventory_item
    ADD COLUMN container_id INTEGER NULL REFERENCES ember_schema.inventory_container (id) ON DELETE SET NULL;

CREATE INDEX idx_inventory_item_container
    ON ember_schema.inventory_item (container_id)
    WHERE container_id IS NOT NULL;

ALTER TABLE ember_schema.inventory_item
    ADD CONSTRAINT inventory_item_assigned_xor_in_container CHECK (
        NOT (assigned_to IS NOT NULL AND container_id IS NOT NULL)
    );

COMMENT ON COLUMN ember_schema.inventory_item.container_id IS
    'Optional reference to the container that physically holds this item. NULL means unlocated. An item with a container is not assigned to a member; the two states are mutually exclusive via the inventory_item_assigned_xor_in_container CHECK. ON DELETE SET NULL keeps the item even if the container is deleted.';

ALTER TABLE ember_schema.inventory_check
    ALTER COLUMN member_id DROP NOT NULL;

ALTER TABLE ember_schema.inventory_check
    ADD COLUMN scope        TEXT    NOT NULL DEFAULT 'MEMBER',
    ADD COLUMN container_id INTEGER NULL REFERENCES ember_schema.inventory_container (id) ON DELETE CASCADE,
    ADD COLUMN deep         BOOLEAN NOT NULL DEFAULT FALSE,
    ADD CONSTRAINT inventory_check_scope_target CHECK (
        (scope = 'MEMBER' AND member_id IS NOT NULL AND container_id IS NULL)
        OR (scope = 'CONTAINER' AND container_id IS NOT NULL AND member_id IS NULL)
    );

CREATE INDEX idx_inventory_check_container ON ember_schema.inventory_check (container_id) WHERE container_id IS NOT NULL;

COMMENT ON COLUMN ember_schema.inventory_check.scope IS
    'Discriminator: MEMBER (default, legacy rows) means the check covers a single member''s assigned items; CONTAINER means the check covers the items physically held inside a container.';
COMMENT ON COLUMN ember_schema.inventory_check.container_id IS
    'Target container for a CONTAINER-scope check. NULL on MEMBER-scope checks.';
COMMENT ON COLUMN ember_schema.inventory_check.deep IS
    'On a CONTAINER-scope check, whether the walk includes all descendant containers (TRUE) or only the direct container (FALSE).';
