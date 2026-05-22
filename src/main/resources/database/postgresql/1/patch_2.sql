-- Application settings
CREATE TABLE ember_schema.application_setting
(
    key   TEXT NOT NULL PRIMARY KEY,
    value TEXT NOT NULL
);

INSERT INTO ember_schema.application_setting (key, value) VALUES ('station_registration_enabled', 'true');

-- Waitlist management role
INSERT INTO ember_schema.role (name) VALUES ('WAITLIST_MANAGEMENT');

-- Waiting list
CREATE TABLE ember_schema.waiting_list
(
    id                    SERIAL PRIMARY KEY,
    station_id            INTEGER   NOT NULL REFERENCES ember_schema.station (id) ON DELETE CASCADE,
    name                  TEXT      NOT NULL,
    description           TEXT      NOT NULL DEFAULT '',
    scoring_formula       TEXT,
    confirm_interval_days INTEGER   NOT NULL DEFAULT 180,
    visible_fields        JSONB     NOT NULL DEFAULT '[]'::jsonb,
    testing_group_id      INT REFERENCES ember_schema.member_group(id) ON DELETE SET NULL,
    join_group_id         INT REFERENCES ember_schema.member_group(id) ON DELETE SET NULL,
    join_role_id          INT REFERENCES ember_schema.role(id) ON DELETE SET NULL,
    attendance_threshold  INT       NOT NULL DEFAULT 5,
    created_at            TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE (station_id, name)
);

CREATE TABLE ember_schema.waiting_list_field
(
    id         SERIAL PRIMARY KEY,
    list_id    INTEGER NOT NULL REFERENCES ember_schema.waiting_list (id) ON DELETE CASCADE,
    name       TEXT    NOT NULL,
    field_type TEXT    NOT NULL,
    config     JSONB   NOT NULL DEFAULT '{}',
    position   INTEGER NOT NULL DEFAULT 0,
    required   BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE (list_id, name)
);

CREATE TABLE ember_schema.waiting_list_invite
(
    id         SERIAL PRIMARY KEY,
    list_id    INTEGER   NOT NULL REFERENCES ember_schema.waiting_list (id) ON DELETE CASCADE,
    code       TEXT      NOT NULL UNIQUE,
    max_uses   INTEGER   NOT NULL DEFAULT 1,
    uses       INTEGER   NOT NULL DEFAULT 0,
    expires_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE ember_schema.waiting_list_entry
(
    id               SERIAL PRIMARY KEY,
    list_id          INTEGER   NOT NULL REFERENCES ember_schema.waiting_list (id) ON DELETE CASCADE,
    firstname        TEXT      NOT NULL,
    lastname         TEXT      NOT NULL DEFAULT '',
    parent_name      TEXT      NOT NULL DEFAULT '',
    email            TEXT      NOT NULL,
    access_token     TEXT      NOT NULL UNIQUE,
    status           TEXT      NOT NULL DEFAULT 'WAITING',
    confirmed_at     TIMESTAMP NOT NULL DEFAULT now(),
    reminder_sent_at TIMESTAMP,
    created_at       TIMESTAMP NOT NULL DEFAULT now(),
    notes            TEXT      NOT NULL DEFAULT '',
    member_id        INT REFERENCES ember_schema.station_member(id) ON DELETE SET NULL,
    invited_at       TIMESTAMP,
    testing_at       TIMESTAMP,
    joined_at        TIMESTAMP,
    withdrawn_at     TIMESTAMP,
    attendance_count INT       NOT NULL DEFAULT 0
);

CREATE INDEX idx_wl_entry_list ON ember_schema.waiting_list_entry (list_id);
CREATE INDEX idx_wl_entry_token ON ember_schema.waiting_list_entry (access_token);
CREATE INDEX idx_wl_entry_status ON ember_schema.waiting_list_entry (list_id, status);

CREATE TABLE ember_schema.waiting_list_entry_value
(
    entry_id INTEGER NOT NULL REFERENCES ember_schema.waiting_list_entry (id) ON DELETE CASCADE,
    field_id INTEGER NOT NULL REFERENCES ember_schema.waiting_list_field (id) ON DELETE CASCADE,
    value    JSONB   NOT NULL DEFAULT '{}',
    PRIMARY KEY (entry_id, field_id)
);

-- Event tag restrictions
CREATE TABLE ember_schema.event_tag_restriction
(
    event_id INTEGER NOT NULL REFERENCES ember_schema.station_event (id) ON DELETE CASCADE,
    tag_id   INTEGER NOT NULL REFERENCES ember_schema.user_tag (id) ON DELETE CASCADE,
    PRIMARY KEY (event_id, tag_id)
);

-- Theme settings
ALTER TABLE ember_schema.user_settings ADD COLUMN theme TEXT NOT NULL DEFAULT 'ember';
ALTER TABLE ember_schema.user_settings ADD COLUMN dark_mode TEXT NOT NULL DEFAULT 'system';

ALTER TABLE ember_schema.station ADD COLUMN default_theme TEXT NOT NULL DEFAULT 'ember';
ALTER TABLE ember_schema.station ADD COLUMN allow_user_theme BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE ember_schema.station ADD COLUMN custom_theme_colors JSONB;

-- Quiz module tables

CREATE TABLE ember_schema.quiz_catalog
(
    id              SERIAL PRIMARY KEY,
    station_id      INT          NOT NULL REFERENCES ember_schema.station (id) ON DELETE CASCADE,
    name            TEXT         NOT NULL,
    description     TEXT         NOT NULL DEFAULT '',
    training_enabled BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Station-scoped categories (not catalog-scoped)
CREATE TABLE ember_schema.quiz_category
(
    id          SERIAL PRIMARY KEY,
    station_id  INT  NOT NULL REFERENCES ember_schema.station (id) ON DELETE CASCADE,
    name        TEXT NOT NULL,
    description TEXT NOT NULL DEFAULT '',
    position    INT  NOT NULL DEFAULT 0
);

CREATE TABLE ember_schema.quiz_question
(
    id            SERIAL PRIMARY KEY,
    catalog_id    INT          NOT NULL REFERENCES ember_schema.quiz_catalog (id) ON DELETE CASCADE,
    category_id   INT          REFERENCES ember_schema.quiz_category (id) ON DELETE SET NULL,
    question_type TEXT         NOT NULL,
    title         TEXT         NOT NULL,
    description   TEXT         NOT NULL DEFAULT '',
    image_url     TEXT,
    points        INT          NOT NULL DEFAULT 1,
    auto_points   BOOLEAN      NOT NULL DEFAULT TRUE,
    config        JSONB        NOT NULL DEFAULT '{}'::jsonb,
    position      INT          NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE ember_schema.quiz_test
(
    id           SERIAL PRIMARY KEY,
    station_id   INT          NOT NULL REFERENCES ember_schema.station (id) ON DELETE CASCADE,
    title        TEXT         NOT NULL,
    description  TEXT         NOT NULL DEFAULT '',
    status       TEXT         NOT NULL DEFAULT 'DRAFT',
    time_limit   INT,
    shuffle      BOOLEAN      NOT NULL DEFAULT FALSE,
    start_at     TIMESTAMPTZ,
    end_at       TIMESTAMPTZ,
    created_by   INT          NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE ember_schema.quiz_test_section
(
    id          SERIAL PRIMARY KEY,
    test_id     INT  NOT NULL REFERENCES ember_schema.quiz_test (id) ON DELETE CASCADE,
    title       TEXT NOT NULL DEFAULT '',
    description TEXT NOT NULL DEFAULT '',
    position    INT  NOT NULL DEFAULT 0
);

CREATE TABLE ember_schema.quiz_test_section_source
(
    id             SERIAL PRIMARY KEY,
    section_id     INT NOT NULL REFERENCES ember_schema.quiz_test_section (id) ON DELETE CASCADE,
    catalog_id     INT NOT NULL REFERENCES ember_schema.quiz_catalog (id) ON DELETE CASCADE,
    category_id    INT REFERENCES ember_schema.quiz_category (id) ON DELETE SET NULL,
    question_count INT NOT NULL DEFAULT 0
);

CREATE TABLE ember_schema.quiz_test_attempt
(
    id          SERIAL PRIMARY KEY,
    test_id     INT          NOT NULL REFERENCES ember_schema.quiz_test (id) ON DELETE CASCADE,
    member_id   INT          NOT NULL,
    status      TEXT         NOT NULL DEFAULT 'IN_PROGRESS',
    started_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    submitted_at TIMESTAMPTZ,
    graded_at   TIMESTAMPTZ,
    graded_by   INT,
    total_points INT         NOT NULL DEFAULT 0,
    max_points   INT         NOT NULL DEFAULT 0,
    UNIQUE (test_id, member_id)
);

CREATE TABLE ember_schema.quiz_test_answer
(
    id           SERIAL PRIMARY KEY,
    attempt_id   INT          NOT NULL REFERENCES ember_schema.quiz_test_attempt (id) ON DELETE CASCADE,
    question_id  INT          NOT NULL REFERENCES ember_schema.quiz_question (id) ON DELETE CASCADE,
    section_id   INT          REFERENCES ember_schema.quiz_test_section (id) ON DELETE SET NULL,
    answer       JSONB        NOT NULL DEFAULT '{}'::jsonb,
    points       INT,
    graded       BOOLEAN      NOT NULL DEFAULT FALSE,
    position     INT          NOT NULL DEFAULT 0
);

CREATE TABLE ember_schema.quiz_test_attempt_question
(
    id          SERIAL PRIMARY KEY,
    attempt_id  INT NOT NULL REFERENCES ember_schema.quiz_test_attempt (id) ON DELETE CASCADE,
    question_id INT NOT NULL REFERENCES ember_schema.quiz_question (id) ON DELETE CASCADE,
    section_id  INT REFERENCES ember_schema.quiz_test_section (id) ON DELETE SET NULL,
    position    INT NOT NULL DEFAULT 0
);

CREATE TABLE ember_schema.quiz_test_member_access
(
    id        SERIAL PRIMARY KEY,
    test_id   INT          NOT NULL REFERENCES ember_schema.quiz_test (id) ON DELETE CASCADE,
    member_id INT          NOT NULL,
    opened_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    closes_at TIMESTAMPTZ,
    UNIQUE (test_id, member_id)
);

-- Frozen question set: populated when a test is activated
CREATE TABLE ember_schema.quiz_test_frozen_question
(
    id          SERIAL PRIMARY KEY,
    test_id     INT NOT NULL REFERENCES ember_schema.quiz_test (id) ON DELETE CASCADE,
    question_id INT NOT NULL REFERENCES ember_schema.quiz_question (id) ON DELETE CASCADE,
    section_id  INT REFERENCES ember_schema.quiz_test_section (id) ON DELETE SET NULL,
    position    INT NOT NULL DEFAULT 0
);

-- Quiz test access restrictions
CREATE TABLE ember_schema.quiz_test_role_restriction
(
    id      SERIAL PRIMARY KEY,
    test_id INT NOT NULL REFERENCES ember_schema.quiz_test (id) ON DELETE CASCADE,
    role_id INT NOT NULL,
    UNIQUE (test_id, role_id)
);

CREATE TABLE ember_schema.quiz_test_group_restriction
(
    id       SERIAL PRIMARY KEY,
    test_id  INT NOT NULL REFERENCES ember_schema.quiz_test (id) ON DELETE CASCADE,
    group_id INT NOT NULL,
    UNIQUE (test_id, group_id)
);

CREATE TABLE ember_schema.quiz_test_tag_restriction
(
    id      SERIAL PRIMARY KEY,
    test_id INT NOT NULL REFERENCES ember_schema.quiz_test (id) ON DELETE CASCADE,
    tag_id  INT NOT NULL,
    UNIQUE (test_id, tag_id)
);

-- AI provider configurations per station
CREATE TABLE ember_schema.station_ai_provider
(
    id        SERIAL PRIMARY KEY,
    station_id INT  NOT NULL REFERENCES ember_schema.station (id) ON DELETE CASCADE,
    provider  TEXT NOT NULL,
    api_key   TEXT NOT NULL,
    model     TEXT,
    UNIQUE (station_id, provider)
);

ALTER TABLE ember_schema.station ADD COLUMN ai_prompt TEXT;

-- Knowledge Base module

CREATE TABLE ember_schema.kb_folder
(
    id          SERIAL PRIMARY KEY,
    station_id  INT       NOT NULL REFERENCES ember_schema.station (id) ON DELETE CASCADE,
    parent_id   INT REFERENCES ember_schema.kb_folder (id) ON DELETE CASCADE,
    name        TEXT      NOT NULL,
    description TEXT      NOT NULL DEFAULT '',
    icon_url    TEXT,
    position    INT       NOT NULL DEFAULT 0,
    created_by  INT       NOT NULL REFERENCES ember_schema.station_member (id),
    created_at  TIMESTAMP NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE (station_id, parent_id, name)
);

CREATE TABLE ember_schema.kb_file
(
    id          SERIAL PRIMARY KEY,
    station_id  INT       NOT NULL REFERENCES ember_schema.station (id) ON DELETE CASCADE,
    folder_id   INT REFERENCES ember_schema.kb_folder (id) ON DELETE CASCADE,
    name        TEXT      NOT NULL,
    description TEXT      NOT NULL DEFAULT '',
    file_type   TEXT      NOT NULL,
    mime_type   TEXT,
    file_size   BIGINT    NOT NULL DEFAULT 0,
    icon_url    TEXT,
    youtube_url TEXT,
    link_url    TEXT,
    position    INT       NOT NULL DEFAULT 0,
    created_by  INT       NOT NULL REFERENCES ember_schema.station_member (id),
    created_at  TIMESTAMP NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE ember_schema.kb_file_content
(
    file_id      INT PRIMARY KEY REFERENCES ember_schema.kb_file (id) ON DELETE CASCADE,
    text_content TEXT
);

CREATE TABLE ember_schema.kb_file_version
(
    id         SERIAL PRIMARY KEY,
    file_id    INT       NOT NULL REFERENCES ember_schema.kb_file (id) ON DELETE CASCADE,
    patch      TEXT      NOT NULL,
    is_full    BOOLEAN   NOT NULL DEFAULT FALSE,
    version    INT       NOT NULL,
    created_by INT       NOT NULL REFERENCES ember_schema.station_member (id),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE (file_id, version)
);

CREATE TABLE ember_schema.kb_search_index
(
    file_id    INT PRIMARY KEY REFERENCES ember_schema.kb_file (id) ON DELETE CASCADE,
    search_text TSVECTOR NOT NULL
);

CREATE INDEX idx_kb_search ON ember_schema.kb_search_index USING GIN (search_text);

-- Knowledge base access restrictions
CREATE TABLE ember_schema.kb_access_restriction (
    id SERIAL PRIMARY KEY,
    folder_id INT REFERENCES ember_schema.kb_folder(id) ON DELETE CASCADE,
    file_id INT REFERENCES ember_schema.kb_file(id) ON DELETE CASCADE,
    role_id INT REFERENCES ember_schema.role(id) ON DELETE CASCADE,
    group_id INT REFERENCES ember_schema.member_group(id) ON DELETE CASCADE,
    tag_id INT REFERENCES ember_schema.user_tag(id) ON DELETE CASCADE,
    member_id INT REFERENCES ember_schema.station_member(id) ON DELETE CASCADE,
    CHECK (
        (folder_id IS NOT NULL AND file_id IS NULL) OR
        (folder_id IS NULL AND file_id IS NOT NULL)
    ),
    CHECK (
        (role_id IS NOT NULL)::int + (group_id IS NOT NULL)::int +
        (tag_id IS NOT NULL)::int + (member_id IS NOT NULL)::int = 1
    )
);

-- Knowledge base tags for files and folders
CREATE TABLE ember_schema.kb_tag
(
    id         SERIAL PRIMARY KEY,
    station_id INT  NOT NULL REFERENCES ember_schema.station (id) ON DELETE CASCADE,
    name       TEXT NOT NULL,
    UNIQUE (station_id, name)
);

CREATE TABLE ember_schema.kb_file_tag
(
    file_id INT NOT NULL REFERENCES ember_schema.kb_file (id) ON DELETE CASCADE,
    tag_id  INT NOT NULL REFERENCES ember_schema.kb_tag (id) ON DELETE CASCADE,
    PRIMARY KEY (file_id, tag_id)
);

CREATE TABLE ember_schema.kb_folder_tag
(
    folder_id INT NOT NULL REFERENCES ember_schema.kb_folder (id) ON DELETE CASCADE,
    tag_id    INT NOT NULL REFERENCES ember_schema.kb_tag (id) ON DELETE CASCADE,
    PRIMARY KEY (folder_id, tag_id)
);

-- Related files (further reading links between KB files)
CREATE TABLE ember_schema.kb_related_file
(
    source_file_id INT NOT NULL REFERENCES ember_schema.kb_file (id) ON DELETE CASCADE,
    target_file_id INT NOT NULL REFERENCES ember_schema.kb_file (id) ON DELETE CASCADE,
    position       INT NOT NULL DEFAULT 0,
    PRIMARY KEY (source_file_id, target_file_id),
    CHECK (source_file_id != target_file_id)
);
