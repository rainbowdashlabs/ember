-- ============================================================
-- Test Protocol (Prüfungsbogen)
-- ============================================================

-- Protocol template (blueprint)
CREATE TABLE ember_schema.test_protocol (
    id             SERIAL PRIMARY KEY,
    station_id     INT       NOT NULL REFERENCES ember_schema.station (id) ON DELETE CASCADE,
    name           TEXT      NOT NULL,
    description    TEXT      NOT NULL DEFAULT '',
    pass_threshold INT,
    created_at     TIMESTAMP NOT NULL DEFAULT now(),
    updated_at     TIMESTAMP NOT NULL DEFAULT now()
);

-- Sections (hierarchical: parent_id for subsections)
CREATE TABLE ember_schema.test_protocol_section (
    id             SERIAL PRIMARY KEY,
    protocol_id    INT  NOT NULL REFERENCES ember_schema.test_protocol (id) ON DELETE CASCADE,
    parent_id      INT REFERENCES ember_schema.test_protocol_section (id) ON DELETE CASCADE,
    name           TEXT NOT NULL,
    description    TEXT NOT NULL DEFAULT '',
    max_points     INT,
    pass_threshold INT,
    position       INT  NOT NULL DEFAULT 0
);

-- Individual checkboxes within a section
CREATE TABLE ember_schema.test_protocol_item (
    id          SERIAL PRIMARY KEY,
    section_id  INT     NOT NULL REFERENCES ember_schema.test_protocol_section (id) ON DELETE CASCADE,
    label       TEXT    NOT NULL,
    description TEXT    NOT NULL DEFAULT '',
    points      NUMERIC NOT NULL DEFAULT 1,
    position    INT     NOT NULL DEFAULT 0
);

-- A test run instance
CREATE TABLE ember_schema.test_protocol_run (
    id          SERIAL PRIMARY KEY,
    protocol_id INT       NOT NULL REFERENCES ember_schema.test_protocol (id) ON DELETE CASCADE,
    station_id  INT       NOT NULL REFERENCES ember_schema.station (id) ON DELETE CASCADE,
    name        TEXT      NOT NULL,
    test_date   DATE      NOT NULL DEFAULT current_date,
    status      TEXT      NOT NULL DEFAULT 'OPEN',
    created_by  INT       NOT NULL REFERENCES ember_schema.station_member (id),
    created_at  TIMESTAMP NOT NULL DEFAULT now()
);

-- Members being tested in a run
CREATE TABLE ember_schema.test_protocol_run_member (
    id          SERIAL PRIMARY KEY,
    run_id      INT     NOT NULL REFERENCES ember_schema.test_protocol_run (id) ON DELETE CASCADE,
    member_id   INT     NOT NULL REFERENCES ember_schema.station_member (id) ON DELETE CASCADE,
    locked_by   INT REFERENCES ember_schema.station_member (id),
    locked_at   TIMESTAMP,
    completed   BOOLEAN NOT NULL DEFAULT FALSE,
    total_score NUMERIC NOT NULL DEFAULT 0,
    UNIQUE (run_id, member_id)
);

-- Tracks which top-level sections have been marked as tested
CREATE TABLE ember_schema.test_protocol_run_section_done (
    run_member_id INT       NOT NULL REFERENCES ember_schema.test_protocol_run_member (id) ON DELETE CASCADE,
    section_id    INT       NOT NULL REFERENCES ember_schema.test_protocol_section (id) ON DELETE CASCADE,
    done_by       INT REFERENCES ember_schema.station_member (id),
    done_at       TIMESTAMP NOT NULL DEFAULT now(),
    PRIMARY KEY (run_member_id, section_id)
);

-- Individual checkbox results
CREATE TABLE ember_schema.test_protocol_run_check (
    run_member_id INT     NOT NULL REFERENCES ember_schema.test_protocol_run_member (id) ON DELETE CASCADE,
    item_id       INT     NOT NULL REFERENCES ember_schema.test_protocol_item (id) ON DELETE CASCADE,
    checked       BOOLEAN NOT NULL DEFAULT FALSE,
    checked_by    INT REFERENCES ember_schema.station_member (id),
    checked_at    TIMESTAMP,
    PRIMARY KEY (run_member_id, item_id)
);
