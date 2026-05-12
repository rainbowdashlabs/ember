-- ============================================================
-- Accounts & Authentication
-- ============================================================

CREATE TABLE ember_schema.account
(
    id    SERIAL PRIMARY KEY,
    email TEXT NOT NULL UNIQUE,
    name  TEXT NOT NULL
);

CREATE TABLE ember_schema.account_credential
(
    account_id    INTEGER PRIMARY KEY REFERENCES ember_schema.account (id) ON DELETE CASCADE,
    password_hash TEXT NOT NULL
);

CREATE TABLE ember_schema.account_external_auth
(
    id         SERIAL PRIMARY KEY,
    account_id INTEGER NOT NULL REFERENCES ember_schema.account (id) ON DELETE CASCADE,
    provider   TEXT    NOT NULL,
    external_id TEXT   NOT NULL,
    UNIQUE (provider, external_id)
);

CREATE INDEX idx_account_external_auth_account ON ember_schema.account_external_auth (account_id);

-- ============================================================
-- Stations
-- ============================================================

CREATE TABLE ember_schema.station
(
    id   SERIAL PRIMARY KEY,
    name TEXT NOT NULL
);

-- ============================================================
-- Station Membership & Roles
-- ============================================================

CREATE TABLE ember_schema.station_member
(
    id         SERIAL PRIMARY KEY,
    station_id INTEGER NOT NULL REFERENCES ember_schema.station (id) ON DELETE CASCADE,
    account_id INTEGER NOT NULL REFERENCES ember_schema.account (id) ON DELETE CASCADE,
    UNIQUE (station_id, account_id)
);

CREATE INDEX idx_station_member_account ON ember_schema.station_member (account_id);

CREATE TABLE ember_schema.role
(
    id   SERIAL PRIMARY KEY,
    name TEXT NOT NULL UNIQUE
);

INSERT INTO ember_schema.role (name)
VALUES ('login'),
       ('member'),
       ('member_manager'),
       ('attendance_management'),
       ('inventory_management'),
       ('member_management'),
       ('manager'),
       ('admin');

CREATE TABLE ember_schema.station_member_role
(
    member_id INTEGER NOT NULL REFERENCES ember_schema.station_member (id) ON DELETE CASCADE,
    role_id   INTEGER NOT NULL REFERENCES ember_schema.role (id) ON DELETE CASCADE,
    PRIMARY KEY (member_id, role_id)
);

-- Member-manages-member relationship (e.g. legal guardian)
CREATE TABLE ember_schema.member_manager
(
    manager_id INTEGER NOT NULL REFERENCES ember_schema.station_member (id) ON DELETE CASCADE,
    managed_id INTEGER NOT NULL REFERENCES ember_schema.station_member (id) ON DELETE CASCADE,
    PRIMARY KEY (manager_id, managed_id)
);

CREATE INDEX idx_member_manager_managed ON ember_schema.member_manager (managed_id);

-- ============================================================
-- Groups
-- ============================================================

CREATE TABLE ember_schema.member_group
(
    id         SERIAL PRIMARY KEY,
    station_id INTEGER NOT NULL REFERENCES ember_schema.station (id) ON DELETE CASCADE,
    name       TEXT    NOT NULL,
    UNIQUE (station_id, name)
);

CREATE TABLE ember_schema.member_group_entry
(
    group_id  INTEGER NOT NULL REFERENCES ember_schema.member_group (id) ON DELETE CASCADE,
    member_id INTEGER NOT NULL REFERENCES ember_schema.station_member (id) ON DELETE CASCADE,
    PRIMARY KEY (group_id, member_id)
);

CREATE INDEX idx_member_group_entry_member ON ember_schema.member_group_entry (member_id);

-- ============================================================
-- Member Profile Fields (per-station, configurable)
-- ============================================================

CREATE TABLE ember_schema.profile_field
(
    id         SERIAL PRIMARY KEY,
    station_id INTEGER NOT NULL REFERENCES ember_schema.station (id) ON DELETE CASCADE,
    name       TEXT    NOT NULL,
    field_type TEXT    NOT NULL, -- text, number, date, enum, boolean, composite
    config     JSONB   NOT NULL DEFAULT '{}',
    position   INTEGER NOT NULL DEFAULT 0,
    UNIQUE (station_id, name)
);

CREATE TABLE ember_schema.profile_field_value
(
    member_id INTEGER NOT NULL REFERENCES ember_schema.station_member (id) ON DELETE CASCADE,
    field_id  INTEGER NOT NULL REFERENCES ember_schema.profile_field (id) ON DELETE CASCADE,
    value     JSONB   NOT NULL DEFAULT '{}',
    PRIMARY KEY (member_id, field_id)
);

-- ============================================================
-- Inventory
-- ============================================================

CREATE TABLE ember_schema.inventory
(
    id             SERIAL PRIMARY KEY,
    station_id     INTEGER NOT NULL REFERENCES ember_schema.station (id) ON DELETE CASCADE,
    name           TEXT    NOT NULL,
    inventory_type TEXT    NOT NULL, -- external, internal, mixed
    has_sizes      BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE (station_id, name)
);

CREATE TABLE ember_schema.inventory_size
(
    id           SERIAL PRIMARY KEY,
    inventory_id INTEGER NOT NULL REFERENCES ember_schema.inventory (id) ON DELETE CASCADE,
    label        TEXT    NOT NULL,
    position     INTEGER NOT NULL DEFAULT 0,
    UNIQUE (inventory_id, label)
);

CREATE TABLE ember_schema.inventory_item
(
    id           SERIAL PRIMARY KEY,
    inventory_id INTEGER NOT NULL REFERENCES ember_schema.inventory (id) ON DELETE CASCADE,
    internal_id  TEXT,
    name         TEXT    NOT NULL,
    size_id      INTEGER REFERENCES ember_schema.inventory_size (id) ON DELETE SET NULL,
    metadata     JSONB   NOT NULL DEFAULT '{}',
    assigned_to  INTEGER REFERENCES ember_schema.station_member (id) ON DELETE SET NULL
);

CREATE INDEX idx_inventory_item_inventory ON ember_schema.inventory_item (inventory_id);
CREATE INDEX idx_inventory_item_size ON ember_schema.inventory_item (size_id) WHERE size_id IS NOT NULL;
CREATE INDEX idx_inventory_item_assigned ON ember_schema.inventory_item (assigned_to) WHERE assigned_to IS NOT NULL;

-- ============================================================
-- Attendance
-- ============================================================

-- Reusable attendance list template
CREATE TABLE ember_schema.attendance_template
(
    id         SERIAL PRIMARY KEY,
    station_id INTEGER NOT NULL REFERENCES ember_schema.station (id) ON DELETE CASCADE,
    name       TEXT    NOT NULL,
    UNIQUE (station_id, name)
);

-- Field definitions on a template
CREATE TABLE ember_schema.attendance_template_field
(
    id          SERIAL PRIMARY KEY,
    template_id INTEGER NOT NULL REFERENCES ember_schema.attendance_template (id) ON DELETE CASCADE,
    name        TEXT    NOT NULL,
    field_type  TEXT    NOT NULL, -- member, member_list, string, time, date, member_of_group, member_list_of_group, attendance, attendance_of_group
    config      JSONB   NOT NULL DEFAULT '{}',
    position    INTEGER NOT NULL DEFAULT 0,
    UNIQUE (template_id, name)
);

-- A concrete session created from a template
CREATE TABLE ember_schema.attendance_session
(
    id          SERIAL PRIMARY KEY,
    template_id INTEGER   NOT NULL REFERENCES ember_schema.attendance_template (id) ON DELETE CASCADE,
    start_time  TIMESTAMP NOT NULL,
    end_time    TIMESTAMP NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_attendance_session_template ON ember_schema.attendance_session (template_id);
CREATE INDEX idx_attendance_session_created ON ember_schema.attendance_session (created_at);

-- Field values for a session
CREATE TABLE ember_schema.attendance_session_field
(
    session_id INTEGER NOT NULL REFERENCES ember_schema.attendance_session (id) ON DELETE CASCADE,
    field_id   INTEGER NOT NULL REFERENCES ember_schema.attendance_template_field (id) ON DELETE CASCADE,
    value      JSONB   NOT NULL DEFAULT '{}',
    PRIMARY KEY (session_id, field_id)
);

-- Attendance records for a session
CREATE TABLE ember_schema.attendance_entry
(
    id         SERIAL PRIMARY KEY,
    session_id INTEGER NOT NULL REFERENCES ember_schema.attendance_session (id) ON DELETE CASCADE,
    member_id  INTEGER NOT NULL REFERENCES ember_schema.station_member (id) ON DELETE CASCADE,
    check_in   TIMESTAMP,
    check_out  TIMESTAMP,
    UNIQUE (session_id, member_id)
);

CREATE INDEX idx_attendance_entry_member ON ember_schema.attendance_entry (member_id);
