-- ============================================================
-- Accounts & Authentication
-- ============================================================

CREATE TABLE ember_schema.account
(
    id                 SERIAL PRIMARY KEY,
    email              TEXT    NOT NULL UNIQUE,
    first_name         TEXT    NOT NULL,
    last_name          TEXT    NOT NULL DEFAULT '',
    email_verified     BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE ember_schema.account_credential
(
    account_id            INTEGER PRIMARY KEY REFERENCES ember_schema.account (id) ON DELETE CASCADE,
    password_hash         TEXT    NOT NULL,
    force_password_change BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE ember_schema.account_external_auth
(
    id          SERIAL PRIMARY KEY,
    account_id  INTEGER NOT NULL REFERENCES ember_schema.account (id) ON DELETE CASCADE,
    provider    TEXT    NOT NULL,
    external_id TEXT    NOT NULL,
    UNIQUE (provider, external_id)
);

CREATE INDEX idx_account_external_auth_account ON ember_schema.account_external_auth (account_id);

CREATE TABLE ember_schema.account_token
(
    id         SERIAL PRIMARY KEY,
    account_id INTEGER   NOT NULL REFERENCES ember_schema.account (id) ON DELETE CASCADE,
    token      TEXT      NOT NULL UNIQUE,
    token_type TEXT      NOT NULL, -- verify_email, set_password, email_change, station_delete
    metadata   TEXT,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_account_token_account ON ember_schema.account_token (account_id);
CREATE INDEX idx_account_token_type ON ember_schema.account_token (account_id, token_type);

CREATE TABLE ember_schema.account_session
(
    id           SERIAL PRIMARY KEY,
    account_id   INTEGER   NOT NULL REFERENCES ember_schema.account (id) ON DELETE CASCADE,
    token        TEXT      NOT NULL UNIQUE,
    expires_at   TIMESTAMP NOT NULL,
    created_at   TIMESTAMP NOT NULL DEFAULT now(),
    user_agent   TEXT,
    last_used_at TIMESTAMP NOT NULL DEFAULT now(),
    location     TEXT
);

CREATE INDEX idx_account_session_account ON ember_schema.account_session (account_id);

CREATE TABLE ember_schema.account_role
(
    account_id INTEGER NOT NULL REFERENCES ember_schema.account (id) ON DELETE CASCADE,
    role       TEXT    NOT NULL,
    PRIMARY KEY (account_id, role)
);

-- ============================================================
-- Stations
-- ============================================================

CREATE TABLE ember_schema.station
(
    id                SERIAL PRIMARY KEY,
    name              TEXT    NOT NULL,
    logo              BYTEA,
    logo_content_type TEXT,
    timezone          TEXT    NOT NULL DEFAULT 'Europe/Berlin',
    locale            TEXT    NOT NULL DEFAULT 'de-DE',
    owner_member_id   INTEGER
);

-- ============================================================
-- Station Membership & Roles
-- ============================================================

CREATE TABLE ember_schema.station_member
(
    id           SERIAL PRIMARY KEY,
    station_id   INTEGER NOT NULL REFERENCES ember_schema.station (id) ON DELETE CASCADE,
    account_id   INTEGER REFERENCES ember_schema.account (id) ON DELETE CASCADE,
    display_name        TEXT    NOT NULL DEFAULT '',
    former              BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE (station_id, account_id)
);

ALTER TABLE ember_schema.station
    ADD CONSTRAINT fk_station_owner FOREIGN KEY (owner_member_id) REFERENCES ember_schema.station_member (id) ON DELETE SET NULL;

CREATE INDEX idx_station_member_account ON ember_schema.station_member (account_id);
CREATE INDEX idx_station_member_former ON ember_schema.station_member (station_id, former);

CREATE TABLE ember_schema.role
(
    id   SERIAL PRIMARY KEY,
    name TEXT NOT NULL UNIQUE
);

INSERT INTO ember_schema.role (name)
VALUES ('LOGIN'),
       ('MEMBER'),
       ('GUARDIAN'),
       ('TEAM'),
       ('ATTENDENCE_MANAGEMENT'),
       ('ATTENDENCE_EXPORT_MANAGER'),
       ('INVENTORY_MANAGEMENT'),
       ('EVENT_MANAGEMENT'),
       ('MEMBER_MANAGEMENT'),
       ('NEWS_MANAGEMENT'),
       ('POLL_MANAGEMENT'),
       ('LOST_AND_FOUND_MANAGEMENT'),
       ('MANAGER'),
       ('ADMIN');

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

CREATE TABLE ember_schema.member_group_role
(
    group_id INTEGER NOT NULL REFERENCES ember_schema.member_group (id) ON DELETE CASCADE,
    role_id  INTEGER NOT NULL REFERENCES ember_schema.role (id) ON DELETE CASCADE,
    PRIMARY KEY (group_id, role_id)
);

-- ============================================================
-- Tags
-- ============================================================

CREATE TABLE ember_schema.user_tag
(
    id         SERIAL PRIMARY KEY,
    station_id INTEGER NOT NULL REFERENCES ember_schema.station (id) ON DELETE CASCADE,
    name       TEXT    NOT NULL,
    UNIQUE (station_id, name)
);

CREATE TABLE ember_schema.user_tag_entry
(
    tag_id    INTEGER NOT NULL REFERENCES ember_schema.user_tag (id) ON DELETE CASCADE,
    member_id INTEGER NOT NULL REFERENCES ember_schema.station_member (id) ON DELETE CASCADE,
    PRIMARY KEY (tag_id, member_id)
);

-- ============================================================
-- Registration Codes
-- ============================================================

CREATE TABLE ember_schema.registration_code
(
    id         SERIAL PRIMARY KEY,
    station_id INTEGER NOT NULL REFERENCES ember_schema.station (id) ON DELETE CASCADE,
    code       TEXT    NOT NULL UNIQUE,
    max_uses   INTEGER NOT NULL DEFAULT -1, -- -1 = unlimited
    uses       INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE ember_schema.registration_code_group
(
    code_id  INTEGER NOT NULL REFERENCES ember_schema.registration_code (id) ON DELETE CASCADE,
    group_id INTEGER NOT NULL REFERENCES ember_schema.member_group (id) ON DELETE CASCADE,
    PRIMARY KEY (code_id, group_id)
);

-- ============================================================
-- Member Profile Fields (per-station, configurable)
-- ============================================================

CREATE TABLE ember_schema.profile_field
(
    id              SERIAL PRIMARY KEY,
    station_id      INTEGER NOT NULL REFERENCES ember_schema.station (id) ON DELETE CASCADE,
    name            TEXT    NOT NULL,
    field_type      TEXT    NOT NULL, -- text, number, date, enum, boolean, composite
    config          JSONB   NOT NULL DEFAULT '{}',
    position        INTEGER NOT NULL DEFAULT 0,
    scope           TEXT    NOT NULL DEFAULT 'member',
    keep_on_archive BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE (station_id, scope, name)
);

CREATE TABLE ember_schema.profile_field_value
(
    member_id INTEGER NOT NULL REFERENCES ember_schema.station_member (id) ON DELETE CASCADE,
    field_id  INTEGER NOT NULL REFERENCES ember_schema.profile_field (id) ON DELETE CASCADE,
    value     JSONB   NOT NULL DEFAULT '{}',
    PRIMARY KEY (member_id, field_id)
);

CREATE TABLE ember_schema.profile_field_change
(
    id                       SERIAL PRIMARY KEY,
    field_id                 INTEGER   NOT NULL REFERENCES ember_schema.profile_field (id) ON DELETE CASCADE,
    member_id                INTEGER   NOT NULL REFERENCES ember_schema.station_member (id) ON DELETE CASCADE,
    old_value                JSONB,
    new_value                JSONB,
    changed_by               INTEGER   NOT NULL REFERENCES ember_schema.station_member (id) ON DELETE CASCADE,
    changed_at               TIMESTAMP NOT NULL DEFAULT now(),
    requires_acknowledgement BOOLEAN   NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_profile_field_change_member ON ember_schema.profile_field_change (member_id);
CREATE INDEX idx_profile_field_change_field ON ember_schema.profile_field_change (field_id);
CREATE INDEX idx_profile_field_change_changed_at ON ember_schema.profile_field_change (member_id, changed_at DESC);

CREATE TABLE ember_schema.profile_field_change_acknowledgement
(
    id              SERIAL PRIMARY KEY,
    change_id       INTEGER   NOT NULL REFERENCES ember_schema.profile_field_change (id) ON DELETE CASCADE,
    acknowledged_by INTEGER   NOT NULL REFERENCES ember_schema.station_member (id) ON DELETE CASCADE,
    acknowledged_at TIMESTAMP NOT NULL DEFAULT now(),
    comment         TEXT,
    UNIQUE (change_id, acknowledged_by)
);

CREATE INDEX idx_profile_field_change_ack_change ON ember_schema.profile_field_change_acknowledgement (change_id);

-- ============================================================
-- Inventory
-- ============================================================

CREATE TABLE ember_schema.inventory
(
    id             SERIAL PRIMARY KEY,
    station_id     INTEGER NOT NULL REFERENCES ember_schema.station (id) ON DELETE CASCADE,
    name           TEXT    NOT NULL,
    inventory_type TEXT    NOT NULL, -- EXTERNAL, INTERNAL, MIXED
    has_sizes      BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE (station_id, name)
);

CREATE TABLE ember_schema.inventory_size
(
    id           SERIAL PRIMARY KEY,
    inventory_id INTEGER NOT NULL REFERENCES ember_schema.inventory (id) ON DELETE CASCADE,
    label        TEXT    NOT NULL,
    position     INTEGER NOT NULL DEFAULT 0,
    note         TEXT    NOT NULL DEFAULT '',
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
    assigned_to  INTEGER REFERENCES ember_schema.station_member (id) ON DELETE SET NULL,
    lost_at      TIMESTAMP,
    item_source  TEXT
);

CREATE INDEX idx_inventory_item_inventory ON ember_schema.inventory_item (inventory_id);
CREATE INDEX idx_inventory_item_size ON ember_schema.inventory_item (size_id) WHERE size_id IS NOT NULL;
CREATE INDEX idx_inventory_item_assigned ON ember_schema.inventory_item (assigned_to) WHERE assigned_to IS NOT NULL;

CREATE TABLE ember_schema.inventory_item_history
(
    id          SERIAL PRIMARY KEY,
    item_id     INTEGER   NOT NULL REFERENCES ember_schema.inventory_item (id) ON DELETE CASCADE,
    member_id   INTEGER   REFERENCES ember_schema.station_member (id) ON DELETE SET NULL,
    member_name TEXT      NOT NULL DEFAULT '',
    given_out   TIMESTAMP NOT NULL DEFAULT NOW(),
    returned    TIMESTAMP
);

CREATE INDEX idx_inventory_item_history_item ON ember_schema.inventory_item_history (item_id);
CREATE INDEX idx_inventory_item_history_member ON ember_schema.inventory_item_history (member_id);

CREATE TABLE ember_schema.inventory_requirement
(
    id           SERIAL PRIMARY KEY,
    inventory_id INTEGER NOT NULL REFERENCES ember_schema.inventory (id) ON DELETE CASCADE,
    role_id      INTEGER REFERENCES ember_schema.role (id) ON DELETE CASCADE,
    group_id     INTEGER REFERENCES ember_schema.member_group (id) ON DELETE CASCADE,
    quantity     INTEGER NOT NULL DEFAULT 1,
    position     INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT chk_role_or_group CHECK (role_id IS NOT NULL OR group_id IS NOT NULL),
    UNIQUE (inventory_id, role_id, group_id)
);

CREATE TABLE ember_schema.inventory_check
(
    id         SERIAL PRIMARY KEY,
    station_id INTEGER     NOT NULL REFERENCES ember_schema.station (id) ON DELETE CASCADE,
    member_id  INTEGER     NOT NULL REFERENCES ember_schema.station_member (id) ON DELETE CASCADE,
    checked_by INTEGER     NOT NULL REFERENCES ember_schema.station_member (id) ON DELETE CASCADE,
    checked_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_inventory_check_member ON ember_schema.inventory_check (member_id);
CREATE INDEX idx_inventory_check_station ON ember_schema.inventory_check (station_id);

CREATE TABLE ember_schema.inventory_check_item
(
    id           SERIAL PRIMARY KEY,
    check_id     INTEGER NOT NULL REFERENCES ember_schema.inventory_check (id) ON DELETE CASCADE,
    item_id      INTEGER REFERENCES ember_schema.inventory_item (id) ON DELETE CASCADE,
    inventory_id INTEGER REFERENCES ember_schema.inventory (id) ON DELETE CASCADE,
    result       TEXT    NOT NULL,
    note         TEXT    NOT NULL DEFAULT '',
    CONSTRAINT inventory_check_item_check_id_item_id_key UNIQUE NULLS NOT DISTINCT (check_id, item_id, inventory_id)
);

CREATE TABLE ember_schema.inventory_check_lock
(
    id         SERIAL PRIMARY KEY,
    station_id INTEGER     NOT NULL REFERENCES ember_schema.station (id) ON DELETE CASCADE,
    member_id  INTEGER     NOT NULL REFERENCES ember_schema.station_member (id) ON DELETE CASCADE UNIQUE,
    locked_by  INTEGER     NOT NULL REFERENCES ember_schema.station_member (id) ON DELETE CASCADE,
    locked_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ============================================================
-- Equipment Exchange & Procurement
-- ============================================================

CREATE TABLE ember_schema.equipment_exchange_request
(
    id               SERIAL PRIMARY KEY,
    station_id       INTEGER   NOT NULL REFERENCES ember_schema.station (id) ON DELETE CASCADE,
    member_id        INTEGER   NOT NULL REFERENCES ember_schema.station_member (id) ON DELETE CASCADE,
    item_id          INTEGER   REFERENCES ember_schema.inventory_item (id) ON DELETE SET NULL,
    inventory_id     INTEGER   NOT NULL REFERENCES ember_schema.inventory (id) ON DELETE CASCADE,
    old_size_id      INTEGER   REFERENCES ember_schema.inventory_size (id) ON DELETE SET NULL,
    new_size_id      INTEGER   REFERENCES ember_schema.inventory_size (id) ON DELETE SET NULL,
    exchanged_item_id INTEGER  REFERENCES ember_schema.inventory_item (id) ON DELETE SET NULL,
    status           TEXT      NOT NULL DEFAULT 'ANNOUNCED',
    reason           TEXT      NOT NULL DEFAULT '',
    created_by       INTEGER   REFERENCES ember_schema.station_member (id) ON DELETE SET NULL,
    created_at       TIMESTAMP NOT NULL DEFAULT now(),
    updated_at       TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_exchange_request_station ON ember_schema.equipment_exchange_request (station_id);
CREATE INDEX idx_exchange_request_member ON ember_schema.equipment_exchange_request (member_id);

CREATE TABLE ember_schema.equipment_exchange_log
(
    id         SERIAL PRIMARY KEY,
    request_id INTEGER   NOT NULL REFERENCES ember_schema.equipment_exchange_request (id) ON DELETE CASCADE,
    old_status TEXT      NOT NULL,
    new_status TEXT      NOT NULL,
    changed_by INTEGER   NOT NULL REFERENCES ember_schema.station_member (id) ON DELETE CASCADE,
    changed_at TIMESTAMP NOT NULL DEFAULT now(),
    note       TEXT      NOT NULL DEFAULT ''
);

CREATE TABLE ember_schema.equipment_procurement
(
    id           SERIAL PRIMARY KEY,
    station_id   INTEGER   NOT NULL REFERENCES ember_schema.station (id) ON DELETE CASCADE,
    inventory_id INTEGER   NOT NULL REFERENCES ember_schema.inventory (id) ON DELETE CASCADE,
    member_id    INTEGER   NOT NULL REFERENCES ember_schema.station_member (id) ON DELETE CASCADE,
    size_id      INTEGER   REFERENCES ember_schema.inventory_size (id) ON DELETE SET NULL,
    notes        TEXT      NOT NULL DEFAULT '',
    requested_at TIMESTAMP NOT NULL DEFAULT now(),
    fulfilled_at TIMESTAMP
);

CREATE INDEX idx_procurement_station ON ember_schema.equipment_procurement (station_id);

-- ============================================================
-- Attendance
-- ============================================================

CREATE TABLE ember_schema.attendance_template
(
    id         SERIAL PRIMARY KEY,
    station_id INTEGER NOT NULL REFERENCES ember_schema.station (id) ON DELETE CASCADE,
    name       TEXT    NOT NULL,
    UNIQUE (station_id, name)
);

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

CREATE TABLE ember_schema.attendance_template_group
(
    template_id INTEGER NOT NULL REFERENCES ember_schema.attendance_template (id) ON DELETE CASCADE,
    group_id    INTEGER NOT NULL REFERENCES ember_schema.member_group (id) ON DELETE CASCADE,
    position    INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (template_id, group_id)
);

-- Event category and station_event must be created before attendance_session (FK dependency)
CREATE TABLE ember_schema.event_category
(
    id         SERIAL PRIMARY KEY,
    station_id INTEGER NOT NULL REFERENCES ember_schema.station (id) ON DELETE CASCADE,
    name       TEXT    NOT NULL,
    position   INTEGER NOT NULL DEFAULT 0,
    UNIQUE (station_id, name)
);

CREATE TABLE ember_schema.station_event
(
    id                     SERIAL PRIMARY KEY,
    station_id             INTEGER                  NOT NULL REFERENCES ember_schema.station (id) ON DELETE CASCADE,
    name                   TEXT                     NOT NULL,
    description            TEXT,
    event_type             TEXT                     NOT NULL DEFAULT 'ONE_TIME',
    day_of_week            INTEGER,
    start_time             TIMESTAMP WITH TIME ZONE NOT NULL,
    end_time               TIMESTAMP WITH TIME ZONE NOT NULL,
    template_id            INTEGER REFERENCES ember_schema.attendance_template (id) ON DELETE SET NULL,
    requires_registration  BOOLEAN                  NOT NULL DEFAULT FALSE,
    registration_deadline  TIMESTAMP,
    requires_confirmation  BOOLEAN                  NOT NULL DEFAULT FALSE,
    category_id            INTEGER REFERENCES ember_schema.event_category (id) ON DELETE SET NULL
);

CREATE INDEX idx_station_event_station ON ember_schema.station_event (station_id);

CREATE TABLE ember_schema.station_event_break
(
    id         SERIAL PRIMARY KEY,
    station_id INTEGER NOT NULL REFERENCES ember_schema.station (id) ON DELETE CASCADE,
    name       TEXT    NOT NULL,
    start_date DATE    NOT NULL,
    end_date   DATE    NOT NULL
);

CREATE INDEX idx_station_event_break_station ON ember_schema.station_event_break (station_id);
CREATE INDEX idx_station_event_break_dates ON ember_schema.station_event_break (start_date, end_date);

CREATE TABLE ember_schema.attendance_session
(
    id          SERIAL PRIMARY KEY,
    template_id INTEGER   NOT NULL REFERENCES ember_schema.attendance_template (id) ON DELETE CASCADE,
    start_time  TIMESTAMP NOT NULL,
    end_time    TIMESTAMP NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT now(),
    event_id    INTEGER   REFERENCES ember_schema.station_event (id) ON DELETE SET NULL,
    title       TEXT
);

CREATE INDEX idx_attendance_session_template ON ember_schema.attendance_session (template_id);
CREATE INDEX idx_attendance_session_created ON ember_schema.attendance_session (created_at);

CREATE TABLE ember_schema.attendance_session_field
(
    session_id INTEGER NOT NULL REFERENCES ember_schema.attendance_session (id) ON DELETE CASCADE,
    field_id   INTEGER NOT NULL REFERENCES ember_schema.attendance_template_field (id) ON DELETE CASCADE,
    value      JSONB   NOT NULL DEFAULT '{}',
    PRIMARY KEY (session_id, field_id)
);

CREATE TABLE ember_schema.attendance_entry
(
    id         SERIAL PRIMARY KEY,
    session_id INTEGER NOT NULL REFERENCES ember_schema.attendance_session (id) ON DELETE CASCADE,
    member_id  INTEGER NOT NULL REFERENCES ember_schema.station_member (id) ON DELETE CASCADE,
    check_in   TIMESTAMP,
    check_out  TIMESTAMP,
    status     TEXT    NOT NULL DEFAULT 'PRESENT',
    source     TEXT    NOT NULL DEFAULT 'EXPECTED',
    UNIQUE (session_id, member_id)
);

CREATE INDEX idx_attendance_entry_member ON ember_schema.attendance_entry (member_id);

CREATE TABLE ember_schema.attendance_report_preset
(
    id         SERIAL PRIMARY KEY,
    station_id INTEGER NOT NULL REFERENCES ember_schema.station (id) ON DELETE CASCADE,
    name       TEXT    NOT NULL,
    role_name  TEXT,
    group_id   INTEGER REFERENCES ember_schema.member_group (id) ON DELETE CASCADE,
    period     TEXT    NOT NULL DEFAULT 'month',
    rounding   TEXT    NOT NULL DEFAULT 'exact'
);

CREATE INDEX idx_attendance_report_preset_station ON ember_schema.attendance_report_preset (station_id);

-- ============================================================
-- Member Absences
-- ============================================================

CREATE TABLE ember_schema.member_absence
(
    id           SERIAL PRIMARY KEY,
    member_id    INTEGER   NOT NULL REFERENCES ember_schema.station_member (id) ON DELETE CASCADE,
    absent_from  DATE      NOT NULL DEFAULT CURRENT_DATE,
    absent_until DATE      NOT NULL,
    reason       TEXT,
    created_by   INTEGER   REFERENCES ember_schema.station_member (id) ON DELETE SET NULL,
    created_at   TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_member_absence_member ON ember_schema.member_absence (member_id);
CREATE INDEX idx_member_absence_until ON ember_schema.member_absence (absent_until);

-- ============================================================
-- Event Registrations, Restrictions & Fields
-- ============================================================

CREATE TABLE ember_schema.event_registration
(
    id         SERIAL PRIMARY KEY,
    event_id   INTEGER   NOT NULL REFERENCES ember_schema.station_event (id) ON DELETE CASCADE,
    member_id  INTEGER   NOT NULL REFERENCES ember_schema.station_member (id) ON DELETE CASCADE,
    event_date DATE      NOT NULL,
    status     TEXT      NOT NULL DEFAULT 'PENDING',
    created_by INTEGER   REFERENCES ember_schema.station_member (id) ON DELETE SET NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE (event_id, member_id, event_date)
);

CREATE INDEX idx_event_registration_event ON ember_schema.event_registration (event_id);
CREATE INDEX idx_event_registration_member ON ember_schema.event_registration (member_id);

CREATE TABLE ember_schema.event_role_restriction
(
    event_id INTEGER NOT NULL REFERENCES ember_schema.station_event (id) ON DELETE CASCADE,
    role_id  INTEGER NOT NULL REFERENCES ember_schema.role (id) ON DELETE CASCADE,
    PRIMARY KEY (event_id, role_id)
);

CREATE TABLE ember_schema.event_group_restriction
(
    event_id INTEGER NOT NULL REFERENCES ember_schema.station_event (id) ON DELETE CASCADE,
    group_id INTEGER NOT NULL REFERENCES ember_schema.member_group (id) ON DELETE CASCADE,
    PRIMARY KEY (event_id, group_id)
);

CREATE TABLE ember_schema.event_field_default
(
    event_id INTEGER NOT NULL REFERENCES ember_schema.station_event (id) ON DELETE CASCADE,
    field_id INTEGER NOT NULL REFERENCES ember_schema.attendance_template_field (id) ON DELETE CASCADE,
    source   TEXT    NOT NULL, -- VALUE, EVENT_NAME, EVENT_DESCRIPTION, EVENT_START_TIME, EVENT_END_TIME, EVENT_DATE
    value    TEXT,             -- only used when source = 'VALUE'
    PRIMARY KEY (event_id, field_id)
);

CREATE TABLE ember_schema.event_field
(
    id       SERIAL PRIMARY KEY,
    event_id INTEGER NOT NULL REFERENCES ember_schema.station_event (id) ON DELETE CASCADE,
    name     TEXT    NOT NULL,
    value    TEXT    NOT NULL DEFAULT '',
    position INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX idx_event_field_event ON ember_schema.event_field (event_id);

-- ============================================================
-- News
-- ============================================================

CREATE TABLE ember_schema.news
(
    id               SERIAL PRIMARY KEY,
    station_id       INTEGER   NOT NULL REFERENCES ember_schema.station (id) ON DELETE CASCADE,
    title            TEXT      NOT NULL,
    content_markdown TEXT      NOT NULL,
    content_html     TEXT      NOT NULL,
    author_id        INTEGER   NOT NULL REFERENCES ember_schema.station_member (id) ON DELETE CASCADE,
    published_at     TIMESTAMP,
    created_at       TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE ember_schema.news_group_restriction
(
    news_id  INTEGER NOT NULL REFERENCES ember_schema.news (id) ON DELETE CASCADE,
    group_id INTEGER NOT NULL REFERENCES ember_schema.member_group (id) ON DELETE CASCADE,
    PRIMARY KEY (news_id, group_id)
);

CREATE TABLE ember_schema.news_comment
(
    id         SERIAL PRIMARY KEY,
    news_id    INTEGER   NOT NULL REFERENCES ember_schema.news (id) ON DELETE CASCADE,
    parent_id  INTEGER   REFERENCES ember_schema.news_comment (id) ON DELETE CASCADE,
    author_id  INTEGER   NOT NULL REFERENCES ember_schema.station_member (id) ON DELETE CASCADE,
    content    TEXT      NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_news_comment_news ON ember_schema.news_comment (news_id);
CREATE INDEX idx_news_comment_parent ON ember_schema.news_comment (parent_id);

-- ============================================================
-- Notifications
-- ============================================================

CREATE TABLE ember_schema.notification
(
    id              SERIAL PRIMARY KEY,
    member_id       INTEGER   NOT NULL REFERENCES ember_schema.station_member (id) ON DELETE CASCADE,
    type            TEXT      NOT NULL,
    data            JSONB     NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    acknowledged_at TIMESTAMP,
    emailed_at      TIMESTAMPTZ
);

CREATE INDEX idx_notification_member ON ember_schema.notification (member_id);
CREATE INDEX idx_notification_unacknowledged ON ember_schema.notification (member_id) WHERE acknowledged_at IS NULL;

-- ============================================================
-- User Settings & Notification Preferences
-- ============================================================

CREATE TABLE ember_schema.user_settings
(
    member_id     INTEGER PRIMARY KEY REFERENCES ember_schema.station_member (id) ON DELETE CASCADE,
    email_enabled BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE ember_schema.user_notification_settings
(
    member_id         INTEGER NOT NULL REFERENCES ember_schema.station_member (id) ON DELETE CASCADE,
    notification_type TEXT    NOT NULL,
    app_enabled       BOOLEAN NOT NULL DEFAULT TRUE,
    email_enabled     BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (member_id, notification_type)
);

-- ============================================================
-- Saved Filters
-- ============================================================

CREATE TABLE ember_schema.saved_filter
(
    id          SERIAL PRIMARY KEY,
    account_id  INTEGER NOT NULL REFERENCES ember_schema.account (id) ON DELETE CASCADE,
    table_type  TEXT    NOT NULL,
    name        TEXT    NOT NULL,
    filter_data JSONB   NOT NULL DEFAULT '{}',
    position    INTEGER NOT NULL DEFAULT 0,
    UNIQUE (account_id, table_type, name)
);

-- ============================================================
-- Email
-- ============================================================

CREATE TABLE ember_schema.email_queue
(
    id         SERIAL PRIMARY KEY,
    station_id INTEGER   REFERENCES ember_schema.station (id) ON DELETE SET NULL,
    recipient  TEXT      NOT NULL,
    subject    TEXT      NOT NULL,
    body       TEXT      NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    status     TEXT      NOT NULL DEFAULT 'PENDING'
);

CREATE INDEX idx_email_queue_status ON ember_schema.email_queue (status);

CREATE TABLE ember_schema.email_daily_count
(
    day   DATE    PRIMARY KEY,
    count INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE ember_schema.station_mail_config
(
    station_id     INTEGER PRIMARY KEY REFERENCES ember_schema.station (id) ON DELETE CASCADE,
    provider       TEXT      NOT NULL DEFAULT 'NONE', -- NONE, SMTP, RAPIDMAIL
    smtp_host      TEXT      NOT NULL DEFAULT '',
    smtp_port      INTEGER   NOT NULL DEFAULT 587,
    smtp_ssl       BOOLEAN   NOT NULL DEFAULT false,
    smtp_user      TEXT      NOT NULL DEFAULT '',
    smtp_password  TEXT      NOT NULL DEFAULT '',
    sender_address TEXT      NOT NULL DEFAULT '',
    sender_name    TEXT      NOT NULL DEFAULT '',
    api_key        TEXT      NOT NULL DEFAULT '',
    provider_name  TEXT      NOT NULL DEFAULT '',
    provider_url   TEXT      NOT NULL DEFAULT '',
    daily_limit    INTEGER   NOT NULL DEFAULT 100,
    monthly_limit  INTEGER   NOT NULL DEFAULT 2000,
    updated_at     TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE ember_schema.station_email_count
(
    station_id INTEGER NOT NULL REFERENCES ember_schema.station (id) ON DELETE CASCADE,
    day        DATE    NOT NULL,
    count      INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (station_id, day)
);

-- ============================================================
-- Station Applications
-- ============================================================

CREATE TABLE ember_schema.station_application
(
    id                 SERIAL PRIMARY KEY,
    first_name         TEXT        NOT NULL,
    last_name          TEXT        NOT NULL,
    email              TEXT        NOT NULL,
    station_name       TEXT        NOT NULL,
    introduction       TEXT        NOT NULL DEFAULT '',
    verification_token TEXT,
    status             TEXT        NOT NULL DEFAULT 'unverified',
    deny_reason        TEXT,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    resolved_at        TIMESTAMPTZ
);

CREATE INDEX idx_station_application_status ON ember_schema.station_application (status);

-- ============================================================
-- Forms / Polls
-- ============================================================

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

-- ============================================================
-- GDPR
-- ============================================================

CREATE TABLE ember_schema.gdpr_consent
(
    id              SERIAL PRIMARY KEY,
    account_id      INTEGER   NOT NULL REFERENCES ember_schema.account (id) ON DELETE CASCADE,
    consent_version TEXT      NOT NULL,
    ip_address      TEXT,
    country         TEXT,
    user_agent      TEXT,
    privacy_version TEXT,
    tos_version     TEXT,
    consented_at    TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_gdpr_consent_account ON ember_schema.gdpr_consent (account_id);

-- ============================================================
-- Station Modules
-- ============================================================

CREATE TABLE ember_schema.station_disabled_module
(
    station_id INTEGER NOT NULL REFERENCES ember_schema.station (id) ON DELETE CASCADE,
    module     TEXT    NOT NULL,
    PRIMARY KEY (station_id, module)
);

-- ============================================================
-- Lost and Found
-- ============================================================

CREATE TABLE ember_schema.lost_and_found_item
(
    id                 SERIAL PRIMARY KEY,
    station_id         INTEGER   NOT NULL REFERENCES ember_schema.station (id) ON DELETE CASCADE,
    description        TEXT,
    found_at           DATE      NOT NULL DEFAULT CURRENT_DATE,
    claimed_by         INTEGER   REFERENCES ember_schema.station_member (id) ON DELETE SET NULL,
    claimed_at         TIMESTAMP,
    created_by         INTEGER   NOT NULL REFERENCES ember_schema.station_member (id),
    created_at         TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_lost_found_station ON ember_schema.lost_and_found_item (station_id);

-- ============================================================
-- Transfer Tokens
-- ============================================================

CREATE TABLE ember_schema.transfer_token
(
    id         SERIAL PRIMARY KEY,
    station_id INTEGER   NOT NULL REFERENCES ember_schema.station (id) ON DELETE CASCADE,
    token      TEXT      NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    expires_at TIMESTAMP NOT NULL,
    used       BOOLEAN   NOT NULL DEFAULT FALSE
);
