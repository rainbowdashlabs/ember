CREATE TABLE ember_schema.checklist
(
    id                SERIAL PRIMARY KEY,
    station_id        INTEGER     NOT NULL REFERENCES ember_schema.station (id) ON DELETE CASCADE,
    name              TEXT        NOT NULL,
    description       TEXT        NOT NULL DEFAULT '',
    restriction_mode  TEXT        NOT NULL DEFAULT 'AND',
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by        INTEGER     NULL REFERENCES ember_schema.station_member (id) ON DELETE SET NULL,
    last_refreshed_at TIMESTAMPTZ NULL
);

CREATE INDEX idx_checklist_station ON ember_schema.checklist (station_id);

COMMENT ON TABLE ember_schema.checklist IS
    'A station-scoped boolean follow-up matrix. Defines an ordered set of yes/no columns and a frozen member set materialised from a restriction filter at creation time. Manager-facing only in v1.';
COMMENT ON COLUMN ember_schema.checklist.restriction_mode IS
    'AND or OR — combination semantics for the restriction filter, same wire shape as event_restriction / form_restriction / news_restriction.';
COMMENT ON COLUMN ember_schema.checklist.last_refreshed_at IS
    'Timestamp of the most recent additive refresh that pulled newly-matching members onto the list. NULL means never refreshed since creation.';

CREATE TABLE ember_schema.checklist_column
(
    id           SERIAL PRIMARY KEY,
    checklist_id INTEGER NOT NULL REFERENCES ember_schema.checklist (id) ON DELETE CASCADE,
    position     INTEGER NOT NULL,
    label        TEXT    NOT NULL,
    description  TEXT    NOT NULL DEFAULT '',
    UNIQUE (checklist_id, position)
);

CREATE INDEX idx_checklist_column_checklist ON ember_schema.checklist_column (checklist_id);

COMMENT ON TABLE ember_schema.checklist_column IS
    'One ordered yes/no question on a checklist. Position is unique within a checklist and drives the display order.';

CREATE TABLE ember_schema.checklist_member_filter
(
    id           SERIAL PRIMARY KEY,
    checklist_id INTEGER NOT NULL REFERENCES ember_schema.checklist (id) ON DELETE CASCADE,
    user_type    TEXT    NULL,
    group_id     INTEGER NULL REFERENCES ember_schema.member_group (id) ON DELETE CASCADE,
    tag_id       INTEGER NULL REFERENCES ember_schema.user_tag (id) ON DELETE CASCADE,
    member_id    INTEGER NULL REFERENCES ember_schema.station_member (id) ON DELETE CASCADE,
    CHECK (num_nonnulls(user_type, group_id, tag_id, member_id) = 1)
);

CREATE INDEX idx_checklist_member_filter_checklist ON ember_schema.checklist_member_filter (checklist_id);

COMMENT ON TABLE ember_schema.checklist_member_filter IS
    'Materialisation filter that resolved into the frozen member set at checklist creation. Exactly one of user_type, group_id, tag_id, member_id is set per row. Editing the filter does not refresh; refresh is explicit.';

CREATE TABLE ember_schema.checklist_entry
(
    id           SERIAL PRIMARY KEY,
    checklist_id INTEGER     NOT NULL REFERENCES ember_schema.checklist (id) ON DELETE CASCADE,
    member_id    INTEGER     NOT NULL REFERENCES ember_schema.station_member (id) ON DELETE CASCADE,
    added_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at   TIMESTAMPTZ NULL,
    UNIQUE (checklist_id, member_id)
);

CREATE INDEX idx_checklist_entry_checklist ON ember_schema.checklist_entry (checklist_id);

COMMENT ON TABLE ember_schema.checklist_entry IS
    'One row per member on a checklist. Soft-deletion via deleted_at is sticky: refresh never resurrects a deleted row; the manager must restore it explicitly through the add-members flow.';

CREATE TABLE ember_schema.checklist_cell
(
    id         SERIAL PRIMARY KEY,
    entry_id   INTEGER     NOT NULL REFERENCES ember_schema.checklist_entry (id) ON DELETE CASCADE,
    column_id  INTEGER     NOT NULL REFERENCES ember_schema.checklist_column (id) ON DELETE CASCADE,
    checked    BOOLEAN     NOT NULL DEFAULT FALSE,
    note       TEXT        NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by INTEGER     NULL REFERENCES ember_schema.station_member (id) ON DELETE SET NULL,
    UNIQUE (entry_id, column_id)
);

CREATE INDEX idx_checklist_cell_entry ON ember_schema.checklist_cell (entry_id);

COMMENT ON TABLE ember_schema.checklist_cell IS
    'One boolean state per (entry, column). Rows are created lazily on first PUT; absence of a row means unchecked with no note.';

CREATE TABLE ember_schema.checklist_cell_note_history
(
    id         SERIAL PRIMARY KEY,
    cell_id    INTEGER     NOT NULL REFERENCES ember_schema.checklist_cell (id) ON DELETE CASCADE,
    old_note   TEXT        NULL,
    new_note   TEXT        NULL,
    changed_by INTEGER     NULL REFERENCES ember_schema.station_member (id) ON DELETE SET NULL,
    changed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_checklist_cell_note_history_cell ON ember_schema.checklist_cell_note_history (cell_id);

COMMENT ON TABLE ember_schema.checklist_cell_note_history IS
    'Append-only log of every change to checklist_cell.note. Boolean flips are not tracked — checklist_cell.updated_by / updated_at carry last-write metadata for the boolean.';
