-- Forms/Polls feature
INSERT INTO ember_schema.role (name) VALUES ('POLL_MANAGEMENT') ON CONFLICT DO NOTHING;

CREATE TABLE ember_schema.form
(
    id                SERIAL PRIMARY KEY,
    station_id        INTEGER   NOT NULL REFERENCES ember_schema.station (id) ON DELETE CASCADE,
    title             TEXT      NOT NULL,
    description       TEXT      NOT NULL DEFAULT '',
    status            TEXT      NOT NULL DEFAULT 'DRAFT',
    shuffle_questions BOOLEAN   NOT NULL DEFAULT FALSE,
    allow_edit        BOOLEAN   NOT NULL DEFAULT TRUE,
    start_at          TIMESTAMP,
    end_at            TIMESTAMP,
    closed_at         TIMESTAMP,
    created_by        INTEGER   NOT NULL REFERENCES ember_schema.station_member (id) ON DELETE CASCADE,
    created_at        TIMESTAMP NOT NULL DEFAULT now(),
    updated_at        TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_form_station ON ember_schema.form (station_id);

CREATE TABLE ember_schema.form_question
(
    id            SERIAL PRIMARY KEY,
    form_id       INTEGER NOT NULL REFERENCES ember_schema.form (id) ON DELETE CASCADE,
    position      INTEGER NOT NULL,
    question_type TEXT    NOT NULL,
    title         TEXT    NOT NULL,
    description   TEXT    NOT NULL DEFAULT '',
    required      BOOLEAN NOT NULL DEFAULT FALSE,
    shuffle       BOOLEAN NOT NULL DEFAULT FALSE,
    config        JSONB   NOT NULL DEFAULT '{}'
);

CREATE INDEX idx_form_question_form ON ember_schema.form_question (form_id);

CREATE TABLE ember_schema.form_response
(
    id           SERIAL PRIMARY KEY,
    form_id      INTEGER   NOT NULL REFERENCES ember_schema.form (id) ON DELETE CASCADE,
    member_id    INTEGER   NOT NULL REFERENCES ember_schema.station_member (id) ON DELETE CASCADE,
    submitted_by INTEGER   NOT NULL REFERENCES ember_schema.station_member (id) ON DELETE CASCADE,
    submitted_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at   TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE (form_id, member_id)
);

CREATE INDEX idx_form_response_form ON ember_schema.form_response (form_id);

CREATE TABLE ember_schema.form_answer
(
    id          SERIAL PRIMARY KEY,
    response_id INTEGER NOT NULL REFERENCES ember_schema.form_response (id) ON DELETE CASCADE,
    question_id INTEGER NOT NULL REFERENCES ember_schema.form_question (id) ON DELETE CASCADE,
    value       JSONB   NOT NULL DEFAULT '{}',
    UNIQUE (response_id, question_id)
);

CREATE TABLE ember_schema.form_role_restriction
(
    form_id INTEGER NOT NULL REFERENCES ember_schema.form (id) ON DELETE CASCADE,
    role_id INTEGER NOT NULL REFERENCES ember_schema.role (id) ON DELETE CASCADE,
    PRIMARY KEY (form_id, role_id)
);

CREATE TABLE ember_schema.form_group_restriction
(
    form_id  INTEGER NOT NULL REFERENCES ember_schema.form (id) ON DELETE CASCADE,
    group_id INTEGER NOT NULL REFERENCES ember_schema.member_group (id) ON DELETE CASCADE,
    PRIMARY KEY (form_id, group_id)
);

CREATE TABLE ember_schema.form_tag_restriction
(
    form_id INTEGER NOT NULL REFERENCES ember_schema.form (id) ON DELETE CASCADE,
    tag_id  INTEGER NOT NULL REFERENCES ember_schema.user_tag (id) ON DELETE CASCADE,
    PRIMARY KEY (form_id, tag_id)
);
