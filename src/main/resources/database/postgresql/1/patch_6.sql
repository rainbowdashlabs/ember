-- Board management role
INSERT INTO ember_schema.role (name) VALUES ('BOARD_MANAGER') ON CONFLICT (name) DO NOTHING;

-- Board
CREATE TABLE ember_schema.board (
    id SERIAL PRIMARY KEY,
    station_id INTEGER NOT NULL REFERENCES ember_schema.station(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    description TEXT,
    short_key TEXT NOT NULL,
    hide_done_after_days INTEGER NOT NULL DEFAULT 7,
    ticket_counter INTEGER NOT NULL DEFAULT 0,
    backlog_lane_id INTEGER,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    UNIQUE(station_id, short_key)
);

-- Lanes (ordered columns on the board)
CREATE TABLE ember_schema.board_lane (
    id SERIAL PRIMARY KEY,
    board_id INTEGER NOT NULL REFERENCES ember_schema.board(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    color TEXT,
    position INTEGER NOT NULL DEFAULT 0
);

-- Custom fields
CREATE TABLE ember_schema.board_field (
    id SERIAL PRIMARY KEY,
    board_id INTEGER NOT NULL REFERENCES ember_schema.board(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    field_type TEXT NOT NULL DEFAULT 'string',
    config JSONB DEFAULT '{}',
    position INTEGER NOT NULL DEFAULT 0
);

-- View access restrictions (empty = public to station)
CREATE TABLE ember_schema.board_view_access (
    id SERIAL PRIMARY KEY,
    board_id INTEGER NOT NULL REFERENCES ember_schema.board(id) ON DELETE CASCADE,
    role_id INTEGER,
    group_id INTEGER,
    tag_id INTEGER
);

-- Edit access restrictions (subset of view)
CREATE TABLE ember_schema.board_edit_access (
    id SERIAL PRIMARY KEY,
    board_id INTEGER NOT NULL REFERENCES ember_schema.board(id) ON DELETE CASCADE,
    role_id INTEGER,
    group_id INTEGER,
    tag_id INTEGER
);

-- Tickets
CREATE TABLE ember_schema.board_ticket (
    id SERIAL PRIMARY KEY,
    board_id INTEGER NOT NULL REFERENCES ember_schema.board(id) ON DELETE CASCADE,
    lane_id INTEGER NOT NULL REFERENCES ember_schema.board_lane(id),
    ticket_number INTEGER NOT NULL,
    title TEXT NOT NULL,
    description TEXT,
    assigned_member_id INTEGER REFERENCES ember_schema.station_member(id) ON DELETE SET NULL,
    priority TEXT NOT NULL DEFAULT 'MEDIUM',
    due_date DATE,
    position INTEGER NOT NULL DEFAULT 0,
    created_by INTEGER NOT NULL REFERENCES ember_schema.station_member(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    lane_entered_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    UNIQUE(board_id, ticket_number)
);

-- Ticket field values
CREATE TABLE ember_schema.board_ticket_field_value (
    ticket_id INTEGER NOT NULL REFERENCES ember_schema.board_ticket(id) ON DELETE CASCADE,
    field_id INTEGER NOT NULL REFERENCES ember_schema.board_field(id) ON DELETE CASCADE,
    value JSONB DEFAULT '{}',
    PRIMARY KEY (ticket_id, field_id)
);

-- Cross-board ticket links (same station only)
CREATE TABLE ember_schema.board_ticket_link (
    ticket_id INTEGER NOT NULL REFERENCES ember_schema.board_ticket(id) ON DELETE CASCADE,
    linked_ticket_id INTEGER NOT NULL REFERENCES ember_schema.board_ticket(id) ON DELETE CASCADE,
    link_type TEXT NOT NULL DEFAULT 'RELATES_TO',
    PRIMARY KEY (ticket_id, linked_ticket_id)
);

-- Checklist items per ticket
CREATE TABLE ember_schema.board_ticket_checklist_item (
    id SERIAL PRIMARY KEY,
    ticket_id INTEGER NOT NULL REFERENCES ember_schema.board_ticket(id) ON DELETE CASCADE,
    title TEXT NOT NULL,
    checked BOOLEAN NOT NULL DEFAULT FALSE,
    position INTEGER NOT NULL DEFAULT 0
);

-- Lane transition history
CREATE TABLE ember_schema.board_ticket_transition (
    id SERIAL PRIMARY KEY,
    ticket_id INTEGER NOT NULL REFERENCES ember_schema.board_ticket(id) ON DELETE CASCADE,
    from_lane_id INTEGER REFERENCES ember_schema.board_lane(id) ON DELETE SET NULL,
    to_lane_id INTEGER REFERENCES ember_schema.board_lane(id) ON DELETE SET NULL,
    moved_by INTEGER NOT NULL REFERENCES ember_schema.station_member(id),
    moved_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Comments (same pattern as event_comment)
CREATE TABLE ember_schema.board_ticket_comment (
    id SERIAL PRIMARY KEY,
    ticket_id INTEGER NOT NULL REFERENCES ember_schema.board_ticket(id) ON DELETE CASCADE,
    parent_id INTEGER REFERENCES ember_schema.board_ticket_comment(id) ON DELETE CASCADE,
    author_id INTEGER NOT NULL REFERENCES ember_schema.station_member(id),
    content TEXT NOT NULL,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE
);

-- Ticket watchers
CREATE TABLE ember_schema.board_ticket_watcher (
    ticket_id INTEGER NOT NULL REFERENCES ember_schema.board_ticket(id) ON DELETE CASCADE,
    member_id INTEGER NOT NULL REFERENCES ember_schema.station_member(id) ON DELETE CASCADE,
    PRIMARY KEY (ticket_id, member_id)
);

-- Weblinks per ticket
CREATE TABLE ember_schema.board_ticket_weblink (
    id SERIAL PRIMARY KEY,
    ticket_id INTEGER NOT NULL REFERENCES ember_schema.board_ticket(id) ON DELETE CASCADE,
    url TEXT NOT NULL,
    title TEXT NOT NULL DEFAULT '',
    position INTEGER NOT NULL DEFAULT 0
);

-- File attachments per ticket
CREATE TABLE ember_schema.board_ticket_attachment (
    id SERIAL PRIMARY KEY,
    ticket_id INTEGER NOT NULL REFERENCES ember_schema.board_ticket(id) ON DELETE CASCADE,
    filename TEXT NOT NULL,
    original_name TEXT NOT NULL,
    content_type TEXT NOT NULL DEFAULT 'application/octet-stream',
    size_bytes BIGINT NOT NULL DEFAULT 0,
    uploaded_by INTEGER NOT NULL REFERENCES ember_schema.station_member(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Labels per board
CREATE TABLE ember_schema.board_label (
    id SERIAL PRIMARY KEY,
    board_id INTEGER NOT NULL REFERENCES ember_schema.board(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    color TEXT NOT NULL DEFAULT '#6b7280'
);
CREATE UNIQUE INDEX idx_board_label_unique_name ON ember_schema.board_label(board_id, lower(name));

-- Label assignments per ticket (many-to-many)
CREATE TABLE ember_schema.board_ticket_label (
    ticket_id INTEGER NOT NULL REFERENCES ember_schema.board_ticket(id) ON DELETE CASCADE,
    label_id INTEGER NOT NULL REFERENCES ember_schema.board_label(id) ON DELETE CASCADE,
    PRIMARY KEY (ticket_id, label_id)
);

-- Knowledge base links per ticket
CREATE TABLE ember_schema.board_ticket_kb_link (
    id SERIAL PRIMARY KEY,
    ticket_id INTEGER NOT NULL REFERENCES ember_schema.board_ticket(id) ON DELETE CASCADE,
    kb_file_id INTEGER NOT NULL REFERENCES ember_schema.kb_file(id) ON DELETE CASCADE,
    UNIQUE(ticket_id, kb_file_id)
);

-- Ticket history log (priority changes, label assignments, etc.)
CREATE TABLE ember_schema.board_ticket_history (
    id SERIAL PRIMARY KEY,
    ticket_id INTEGER NOT NULL REFERENCES ember_schema.board_ticket(id) ON DELETE CASCADE,
    action TEXT NOT NULL,
    detail TEXT,
    actor_member_id INTEGER NOT NULL REFERENCES ember_schema.station_member(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Full-text search column (auto-updated via trigger)
ALTER TABLE ember_schema.board_ticket ADD COLUMN search_vector tsvector
    GENERATED ALWAYS AS (
        to_tsvector('german', coalesce(title, '') || ' ' || coalesce(description, ''))
    ) STORED;

-- Indexes
CREATE INDEX idx_board_station ON ember_schema.board(station_id);
CREATE INDEX idx_board_ticket_board ON ember_schema.board_ticket(board_id);
CREATE INDEX idx_board_ticket_lane ON ember_schema.board_ticket(lane_id);
CREATE INDEX idx_board_ticket_assigned ON ember_schema.board_ticket(assigned_member_id);
CREATE INDEX idx_board_ticket_checklist ON ember_schema.board_ticket_checklist_item(ticket_id);
CREATE INDEX idx_board_ticket_transition ON ember_schema.board_ticket_transition(ticket_id);
CREATE INDEX idx_board_ticket_comment ON ember_schema.board_ticket_comment(ticket_id);
CREATE INDEX idx_board_ticket_search ON ember_schema.board_ticket USING GIN(search_vector);
CREATE INDEX idx_board_ticket_attachment ON ember_schema.board_ticket_attachment(ticket_id);
CREATE INDEX idx_board_label_board ON ember_schema.board_label(board_id);
CREATE INDEX idx_board_ticket_label ON ember_schema.board_ticket_label(ticket_id);
CREATE INDEX idx_board_ticket_history ON ember_schema.board_ticket_history(ticket_id);

-- Forced forms and quizzes
ALTER TABLE ember_schema.form ADD COLUMN IF NOT EXISTS forced BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE ember_schema.quiz_test ADD COLUMN IF NOT EXISTS forced BOOLEAN NOT NULL DEFAULT FALSE;

-- =====================================================
-- Board Federation
-- =====================================================

-- Board sharing (which boards are shared with federation partners)
CREATE TABLE ember_schema.federation_board_share (
    id SERIAL PRIMARY KEY,
    board_id INTEGER NOT NULL REFERENCES ember_schema.board(id) ON DELETE CASCADE,
    UNIQUE(board_id)
);

-- Which partners the board is shared with + their access mode
CREATE TABLE ember_schema.federation_board_share_target (
    share_id INTEGER NOT NULL REFERENCES ember_schema.federation_board_share(id) ON DELETE CASCADE,
    partner_id INTEGER NOT NULL REFERENCES ember_schema.federation_partner(id) ON DELETE CASCADE,
    share_mode TEXT NOT NULL DEFAULT 'READ_ONLY', -- READ_ONLY, FULL
    PRIMARY KEY (share_id, partner_id)
);

-- Role-based edit restrictions for federated partners (on the owning station)
-- Only applies to FULL mode. Empty = all federated members can edit.
CREATE TABLE ember_schema.federation_board_edit_role (
    board_id INTEGER NOT NULL REFERENCES ember_schema.board(id) ON DELETE CASCADE,
    role_id INTEGER NOT NULL,
    PRIMARY KEY (board_id, role_id)
);

-- Federated ticket assignment (remote member assigned to a ticket on the owning station)
CREATE TABLE ember_schema.board_ticket_federated_assignee (
    ticket_id INTEGER NOT NULL REFERENCES ember_schema.board_ticket(id) ON DELETE CASCADE PRIMARY KEY,
    partner_id INTEGER NOT NULL REFERENCES ember_schema.federation_partner(id) ON DELETE CASCADE,
    remote_member_id TEXT NOT NULL
);

-- Federated comment authorship (comments created by remote members)
CREATE TABLE ember_schema.board_ticket_federated_comment_author (
    comment_id INTEGER NOT NULL REFERENCES ember_schema.board_ticket_comment(id) ON DELETE CASCADE PRIMARY KEY,
    partner_id INTEGER NOT NULL REFERENCES ember_schema.federation_partner(id) ON DELETE CASCADE,
    remote_member_id TEXT NOT NULL
);

-- Federated ticket creator (tickets created by remote members)
CREATE TABLE ember_schema.board_ticket_federated_creator (
    ticket_id INTEGER NOT NULL REFERENCES ember_schema.board_ticket(id) ON DELETE CASCADE PRIMARY KEY,
    partner_id INTEGER NOT NULL REFERENCES ember_schema.federation_partner(id) ON DELETE CASCADE,
    remote_member_id TEXT NOT NULL
);

-- Federated ticket watchers (remote members watching a ticket on the owning station)
CREATE TABLE ember_schema.board_ticket_federated_watcher (
    ticket_id INTEGER NOT NULL REFERENCES ember_schema.board_ticket(id) ON DELETE CASCADE,
    partner_id INTEGER NOT NULL REFERENCES ember_schema.federation_partner(id) ON DELETE CASCADE,
    remote_member_id TEXT NOT NULL,
    PRIMARY KEY (ticket_id, partner_id, remote_member_id)
);

-- Local access override for federated boards (partner station restricts its own members)
CREATE TABLE ember_schema.federation_board_local_view_override (
    id SERIAL PRIMARY KEY,
    partner_id INTEGER NOT NULL REFERENCES ember_schema.federation_partner(id) ON DELETE CASCADE,
    remote_board_id INTEGER NOT NULL,
    role_id INTEGER,
    group_id INTEGER,
    tag_id INTEGER
);
CREATE UNIQUE INDEX idx_fed_board_local_view_unique
    ON ember_schema.federation_board_local_view_override(partner_id, remote_board_id, COALESCE(role_id, -1), COALESCE(group_id, -1), COALESCE(tag_id, -1));

CREATE TABLE ember_schema.federation_board_local_edit_override (
    id SERIAL PRIMARY KEY,
    partner_id INTEGER NOT NULL REFERENCES ember_schema.federation_partner(id) ON DELETE CASCADE,
    remote_board_id INTEGER NOT NULL,
    role_id INTEGER,
    group_id INTEGER,
    tag_id INTEGER
);
CREATE UNIQUE INDEX idx_fed_board_local_edit_unique
    ON ember_schema.federation_board_local_edit_override(partner_id, remote_board_id, COALESCE(role_id, -1), COALESCE(group_id, -1), COALESCE(tag_id, -1));

-- User bookmarks for federated boards (appear in sidebar)
CREATE TABLE ember_schema.federation_board_bookmark (
    id SERIAL PRIMARY KEY,
    member_id INTEGER NOT NULL REFERENCES ember_schema.station_member(id) ON DELETE CASCADE,
    partner_id INTEGER NOT NULL REFERENCES ember_schema.federation_partner(id) ON DELETE CASCADE,
    remote_board_id INTEGER NOT NULL,
    remote_board_name TEXT NOT NULL,
    remote_board_short_key TEXT NOT NULL,
    share_mode TEXT NOT NULL DEFAULT 'READ_ONLY',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    UNIQUE(member_id, partner_id, remote_board_id)
);

-- Board federation indexes
CREATE INDEX idx_fed_board_share_target_partner ON ember_schema.federation_board_share_target(partner_id);
CREATE INDEX idx_fed_board_bookmark_member ON ember_schema.federation_board_bookmark(member_id);
CREATE INDEX idx_fed_board_bookmark_partner ON ember_schema.federation_board_bookmark(partner_id);
CREATE INDEX idx_board_ticket_fed_assignee_partner ON ember_schema.board_ticket_federated_assignee(partner_id);
CREATE INDEX idx_board_ticket_fed_watcher_partner ON ember_schema.board_ticket_federated_watcher(partner_id);
