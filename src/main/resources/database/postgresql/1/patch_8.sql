-- Procedure module: templates, procedures, items, dependencies, assignees

-- Procedure template (reusable checklist blueprint)
CREATE TABLE ember_schema.procedure_template (
    id          SERIAL PRIMARY KEY,
    station_id  INTEGER NOT NULL REFERENCES ember_schema.station(id) ON DELETE CASCADE,
    name        TEXT NOT NULL,
    description TEXT,
    archived    BOOLEAN NOT NULL DEFAULT FALSE,
    created_by  INTEGER NOT NULL REFERENCES ember_schema.station_member(id) ON DELETE CASCADE,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Template items (checklist steps in a template)
CREATE TABLE ember_schema.procedure_template_item (
    id            SERIAL PRIMARY KEY,
    template_id   INTEGER NOT NULL REFERENCES ember_schema.procedure_template(id) ON DELETE CASCADE,
    title         TEXT NOT NULL,
    description   TEXT,
    public        BOOLEAN NOT NULL DEFAULT TRUE,
    user_assigned BOOLEAN NOT NULL DEFAULT TRUE,
    position      INTEGER NOT NULL DEFAULT 0
);

-- Template item dependencies (DAG)
CREATE TABLE ember_schema.procedure_template_item_dependency (
    item_id            INTEGER NOT NULL REFERENCES ember_schema.procedure_template_item(id) ON DELETE CASCADE,
    depends_on_item_id INTEGER NOT NULL REFERENCES ember_schema.procedure_template_item(id) ON DELETE CASCADE,
    PRIMARY KEY (item_id, depends_on_item_id),
    CHECK (item_id != depends_on_item_id)
);

-- Procedure instance (created from template or ad-hoc)
CREATE TABLE ember_schema.procedure (
    id          SERIAL PRIMARY KEY,
    station_id  INTEGER NOT NULL REFERENCES ember_schema.station(id) ON DELETE CASCADE,
    template_id INTEGER REFERENCES ember_schema.procedure_template(id) ON DELETE SET NULL,
    name        TEXT NOT NULL,
    description TEXT,
    public      BOOLEAN NOT NULL DEFAULT TRUE,
    status      TEXT NOT NULL DEFAULT 'OPEN',
    assigned_by INTEGER NOT NULL REFERENCES ember_schema.station_member(id) ON DELETE CASCADE,
    due_at      TIMESTAMP WITH TIME ZONE,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    resolved_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_procedure_station_status ON ember_schema.procedure(station_id, status);

-- Procedure assignees (members assigned to a procedure)
CREATE TABLE ember_schema.procedure_assignee (
    procedure_id INTEGER NOT NULL REFERENCES ember_schema.procedure(id) ON DELETE CASCADE,
    member_id    INTEGER NOT NULL REFERENCES ember_schema.station_member(id) ON DELETE CASCADE,
    PRIMARY KEY (procedure_id, member_id)
);

-- Procedure items (checklist steps in an instance)
CREATE TABLE ember_schema.procedure_item (
    id            SERIAL PRIMARY KEY,
    procedure_id  INTEGER NOT NULL REFERENCES ember_schema.procedure(id) ON DELETE CASCADE,
    title         TEXT NOT NULL,
    description   TEXT,
    note          TEXT,
    public        BOOLEAN NOT NULL DEFAULT TRUE,
    user_assigned BOOLEAN NOT NULL DEFAULT FALSE,
    position      INTEGER NOT NULL DEFAULT 0,
    checked       BOOLEAN NOT NULL DEFAULT FALSE,
    checked_at    TIMESTAMP WITH TIME ZONE,
    checked_by    INTEGER REFERENCES ember_schema.station_member(id) ON DELETE SET NULL
);

-- Procedure item dependencies (DAG)
CREATE TABLE ember_schema.procedure_item_dependency (
    item_id            INTEGER NOT NULL REFERENCES ember_schema.procedure_item(id) ON DELETE CASCADE,
    depends_on_item_id INTEGER NOT NULL REFERENCES ember_schema.procedure_item(id) ON DELETE CASCADE,
    PRIMARY KEY (item_id, depends_on_item_id),
    CHECK (item_id != depends_on_item_id)
);

-- Add procedure permissions
INSERT INTO ember_schema.station_permission (name) VALUES
    ('PROCEDURE_READ'),
    ('PROCEDURE_EDIT'),
    ('PROCEDURE_MANAGER');
