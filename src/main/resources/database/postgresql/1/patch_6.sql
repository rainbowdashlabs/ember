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
    moved_by INTEGER REFERENCES ember_schema.station_member(id),
    federated_partner_id INTEGER REFERENCES ember_schema.federation_partner(id) ON DELETE SET NULL,
    federated_member_id INTEGER,
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
    actor_member_id INTEGER REFERENCES ember_schema.station_member(id),
    federated_partner_id INTEGER REFERENCES ember_schema.federation_partner(id) ON DELETE SET NULL,
    federated_member_id INTEGER,
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

-- Migrate federation_version from integer to text (hash-based versioning)
ALTER TABLE ember_schema.federation_partner
    ALTER COLUMN federation_version TYPE TEXT USING federation_version::TEXT;

ALTER TABLE ember_schema.federation_partner
    ALTER COLUMN federation_version SET DEFAULT '0';

-- ============================================================
-- Database documentation (COMMENT ON for all tables and columns)
-- ============================================================

-- ============================================================
-- Accounts & Authentication
-- ============================================================

COMMENT ON TABLE ember_schema.account IS 'User accounts. Each person has one account across all stations.';
COMMENT ON COLUMN ember_schema.account.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.account.email IS 'Unique email address for login. NULL for non-login members (e.g. children without email).';
COMMENT ON COLUMN ember_schema.account.first_name IS 'First name of the account holder.';
COMMENT ON COLUMN ember_schema.account.last_name IS 'Last name of the account holder.';
COMMENT ON COLUMN ember_schema.account.email_verified IS 'Whether the email address has been verified via confirmation token.';

COMMENT ON TABLE ember_schema.account_credential IS 'Password credentials for local authentication. One per account.';
COMMENT ON COLUMN ember_schema.account_credential.account_id IS 'References the account this credential belongs to.';
COMMENT ON COLUMN ember_schema.account_credential.password_hash IS 'Bcrypt-hashed password.';
COMMENT ON COLUMN ember_schema.account_credential.force_password_change IS 'If true, the user must change their password on next login.';

COMMENT ON TABLE ember_schema.account_external_auth IS 'OAuth/external identity provider links for an account.';
COMMENT ON COLUMN ember_schema.account_external_auth.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.account_external_auth.account_id IS 'References the account this external auth belongs to.';
COMMENT ON COLUMN ember_schema.account_external_auth.provider IS 'Identity provider name (e.g. google, github).';
COMMENT ON COLUMN ember_schema.account_external_auth.external_id IS 'Unique user ID from the external provider.';

COMMENT ON TABLE ember_schema.account_token IS 'One-time tokens for email verification, password reset, etc.';
COMMENT ON COLUMN ember_schema.account_token.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.account_token.account_id IS 'References the account this token belongs to.';
COMMENT ON COLUMN ember_schema.account_token.token IS 'Unique random token string.';
COMMENT ON COLUMN ember_schema.account_token.token_type IS 'Token purpose: verify_email, set_password, email_change, station_delete.';
COMMENT ON COLUMN ember_schema.account_token.metadata IS 'Optional context data (e.g. new email address for email_change tokens).';
COMMENT ON COLUMN ember_schema.account_token.expires_at IS 'Expiration timestamp after which the token is invalid.';
COMMENT ON COLUMN ember_schema.account_token.created_at IS 'When the token was created.';

COMMENT ON TABLE ember_schema.account_session IS 'Active login sessions (bearer tokens).';
COMMENT ON COLUMN ember_schema.account_session.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.account_session.account_id IS 'References the account this session belongs to.';
COMMENT ON COLUMN ember_schema.account_session.token IS 'Unique session bearer token.';
COMMENT ON COLUMN ember_schema.account_session.expires_at IS 'When the session expires.';
COMMENT ON COLUMN ember_schema.account_session.created_at IS 'When the session was created.';
COMMENT ON COLUMN ember_schema.account_session.user_agent IS 'Browser/client user agent string at login time.';
COMMENT ON COLUMN ember_schema.account_session.last_used_at IS 'Last time this session was used for an API request.';
COMMENT ON COLUMN ember_schema.account_session.location IS 'Approximate geographic location at login time.';

COMMENT ON TABLE ember_schema.account_role IS 'Global (instance-level) roles for an account (e.g. system admin).';
COMMENT ON COLUMN ember_schema.account_role.account_id IS 'References the account.';
COMMENT ON COLUMN ember_schema.account_role.role IS 'Global role name.';

-- ============================================================
-- Stations
-- ============================================================

COMMENT ON TABLE ember_schema.station IS 'A station (organization/club). Central entity that groups members, events, inventory, etc.';
COMMENT ON COLUMN ember_schema.station.id IS 'Auto-generated internal primary key.';
COMMENT ON COLUMN ember_schema.station.name IS 'Display name of the station.';
COMMENT ON COLUMN ember_schema.station.logo IS 'Station logo image data (binary).';
COMMENT ON COLUMN ember_schema.station.logo_content_type IS 'MIME type of the logo image (e.g. image/png).';
COMMENT ON COLUMN ember_schema.station.timezone IS 'IANA timezone for the station (e.g. Europe/Berlin).';
COMMENT ON COLUMN ember_schema.station.locale IS 'Locale/language for the station (e.g. de-DE).';
COMMENT ON COLUMN ember_schema.station.owner_member_id IS 'The station member who owns/created this station. NULL if unset.';
COMMENT ON COLUMN ember_schema.station.default_theme IS 'Default color theme for the station UI.';
COMMENT ON COLUMN ember_schema.station.allow_user_theme IS 'Whether individual users can override the station theme.';
COMMENT ON COLUMN ember_schema.station.custom_theme_colors IS 'Custom theme color overrides as JSONB.';
COMMENT ON COLUMN ember_schema.station.ai_prompt IS 'Custom AI system prompt for this station.';
COMMENT ON COLUMN ember_schema.station.uid IS 'Public UUID identifier for external/federation use. Avoids exposing sequential IDs.';
COMMENT ON COLUMN ember_schema.station.public_kb_mode IS 'Public knowledge base visibility mode: OFF, ALLOW_ALL, DENY_ALL.';
COMMENT ON COLUMN ember_schema.station.federation_private_key IS 'Private key for signing federation HTTP requests. Shared across all partners of this station.';
COMMENT ON COLUMN ember_schema.station.discovery_visibility IS 'Discovery visibility: NONE, FEDERATED, PUBLIC.';
COMMENT ON COLUMN ember_schema.station.discovery_description IS 'Description shown in station discovery listings.';
COMMENT ON COLUMN ember_schema.station.discovery_show_kb IS 'Whether to show the knowledge base link in discovery.';
COMMENT ON COLUMN ember_schema.station.public_calendar_enabled IS 'Whether the public calendar endpoint is enabled.';
COMMENT ON COLUMN ember_schema.station.default_feel IS 'Default UI feel: ROUNDED or CORNERS.';
COMMENT ON COLUMN ember_schema.station.allow_user_feel IS 'Whether individual users can override the station feel.';

-- ============================================================
-- Station Membership & Roles
-- ============================================================

COMMENT ON TABLE ember_schema.station_member IS 'Membership of an account in a station. An account can be a member of multiple stations.';
COMMENT ON COLUMN ember_schema.station_member.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.station_member.station_id IS 'References the station.';
COMMENT ON COLUMN ember_schema.station_member.account_id IS 'References the account. NULL for placeholder/non-login members.';
COMMENT ON COLUMN ember_schema.station_member.display_name IS 'Display name override. Empty string means use account name.';
COMMENT ON COLUMN ember_schema.station_member.former IS 'Whether this member has been archived/deactivated.';

COMMENT ON TABLE ember_schema.role IS 'Available roles that can be assigned to station members.';
COMMENT ON COLUMN ember_schema.role.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.role.name IS 'Unique role name (e.g. MEMBER, TEAM, MANAGER, ADMIN).';

COMMENT ON TABLE ember_schema.station_member_role IS 'Role assignments for station members.';
COMMENT ON COLUMN ember_schema.station_member_role.member_id IS 'References the station member.';
COMMENT ON COLUMN ember_schema.station_member_role.role_id IS 'References the role.';

COMMENT ON TABLE ember_schema.member_manager IS 'Member-manages-member relationships (e.g. legal guardian manages a minor).';
COMMENT ON COLUMN ember_schema.member_manager.manager_id IS 'The managing member (e.g. parent/guardian).';
COMMENT ON COLUMN ember_schema.member_manager.managed_id IS 'The managed member (e.g. child).';

-- ============================================================
-- Groups
-- ============================================================

COMMENT ON TABLE ember_schema.member_group IS 'Named groups of station members (e.g. age groups, teams).';
COMMENT ON COLUMN ember_schema.member_group.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.member_group.station_id IS 'References the station.';
COMMENT ON COLUMN ember_schema.member_group.name IS 'Group name, unique within the station.';

COMMENT ON TABLE ember_schema.member_group_entry IS 'Group membership assignments.';
COMMENT ON COLUMN ember_schema.member_group_entry.group_id IS 'References the group.';
COMMENT ON COLUMN ember_schema.member_group_entry.member_id IS 'References the station member.';

COMMENT ON TABLE ember_schema.member_group_role IS 'Roles granted to all members of a group.';
COMMENT ON COLUMN ember_schema.member_group_role.group_id IS 'References the group.';
COMMENT ON COLUMN ember_schema.member_group_role.role_id IS 'References the role granted to group members.';

-- ============================================================
-- Tags
-- ============================================================

COMMENT ON TABLE ember_schema.user_tag IS 'Tags for categorizing station members (e.g. skill tags, labels).';
COMMENT ON COLUMN ember_schema.user_tag.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.user_tag.station_id IS 'References the station.';
COMMENT ON COLUMN ember_schema.user_tag.name IS 'Tag name, unique within the station.';

COMMENT ON TABLE ember_schema.user_tag_entry IS 'Tag assignments to station members.';
COMMENT ON COLUMN ember_schema.user_tag_entry.tag_id IS 'References the tag.';
COMMENT ON COLUMN ember_schema.user_tag_entry.member_id IS 'References the station member.';

-- ============================================================
-- Registration Codes
-- ============================================================

COMMENT ON TABLE ember_schema.registration_code IS 'Invite codes for joining a station.';
COMMENT ON COLUMN ember_schema.registration_code.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.registration_code.station_id IS 'References the station this code is for.';
COMMENT ON COLUMN ember_schema.registration_code.code IS 'Unique invite code string.';
COMMENT ON COLUMN ember_schema.registration_code.max_uses IS 'Maximum number of times this code can be used. -1 = unlimited.';
COMMENT ON COLUMN ember_schema.registration_code.uses IS 'Current number of times this code has been used.';

COMMENT ON TABLE ember_schema.registration_code_group IS 'Groups that members are auto-added to when using a registration code.';
COMMENT ON COLUMN ember_schema.registration_code_group.code_id IS 'References the registration code.';
COMMENT ON COLUMN ember_schema.registration_code_group.group_id IS 'References the group to auto-assign.';

-- ============================================================
-- Member Profile Fields
-- ============================================================

COMMENT ON TABLE ember_schema.profile_field IS 'Configurable profile fields per station (e.g. phone, address, custom fields).';
COMMENT ON COLUMN ember_schema.profile_field.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.profile_field.station_id IS 'References the station.';
COMMENT ON COLUMN ember_schema.profile_field.name IS 'Field name/label.';
COMMENT ON COLUMN ember_schema.profile_field.field_type IS 'Data type: text, number, date, enum, boolean, composite.';
COMMENT ON COLUMN ember_schema.profile_field.config IS 'Type-specific configuration as JSONB (e.g. enum options).';
COMMENT ON COLUMN ember_schema.profile_field.position IS 'Display order position.';
COMMENT ON COLUMN ember_schema.profile_field.scope IS 'Field scope: member or guardian.';
COMMENT ON COLUMN ember_schema.profile_field.keep_on_archive IS 'Whether to retain the field value when a member is archived.';

COMMENT ON TABLE ember_schema.profile_field_value IS 'Stored values of profile fields per member.';
COMMENT ON COLUMN ember_schema.profile_field_value.member_id IS 'References the station member.';
COMMENT ON COLUMN ember_schema.profile_field_value.field_id IS 'References the profile field.';
COMMENT ON COLUMN ember_schema.profile_field_value.value IS 'Field value stored as JSONB.';

COMMENT ON TABLE ember_schema.profile_field_change IS 'Audit log of profile field value changes.';
COMMENT ON COLUMN ember_schema.profile_field_change.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.profile_field_change.field_id IS 'References the profile field that was changed.';
COMMENT ON COLUMN ember_schema.profile_field_change.member_id IS 'References the member whose profile was changed.';
COMMENT ON COLUMN ember_schema.profile_field_change.old_value IS 'Previous value (JSONB).';
COMMENT ON COLUMN ember_schema.profile_field_change.new_value IS 'New value (JSONB).';
COMMENT ON COLUMN ember_schema.profile_field_change.changed_by IS 'Member who made the change.';
COMMENT ON COLUMN ember_schema.profile_field_change.changed_at IS 'When the change occurred.';
COMMENT ON COLUMN ember_schema.profile_field_change.requires_acknowledgement IS 'Whether a manager must acknowledge this change.';

COMMENT ON TABLE ember_schema.profile_field_change_acknowledgement IS 'Manager acknowledgements of profile field changes.';
COMMENT ON COLUMN ember_schema.profile_field_change_acknowledgement.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.profile_field_change_acknowledgement.change_id IS 'References the profile field change being acknowledged.';
COMMENT ON COLUMN ember_schema.profile_field_change_acknowledgement.acknowledged_by IS 'Member who acknowledged the change.';
COMMENT ON COLUMN ember_schema.profile_field_change_acknowledgement.acknowledged_at IS 'When the acknowledgement occurred.';
COMMENT ON COLUMN ember_schema.profile_field_change_acknowledgement.comment IS 'Optional comment by the acknowledging member.';

-- ============================================================
-- Inventory
-- ============================================================

COMMENT ON TABLE ember_schema.inventory IS 'An inventory collection within a station (e.g. uniforms, tools).';
COMMENT ON COLUMN ember_schema.inventory.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.inventory.station_id IS 'References the station.';
COMMENT ON COLUMN ember_schema.inventory.name IS 'Inventory name, unique within the station.';
COMMENT ON COLUMN ember_schema.inventory.inventory_type IS 'Inventory type: EXTERNAL (lent to members), INTERNAL (station use), MIXED.';
COMMENT ON COLUMN ember_schema.inventory.has_sizes IS 'Whether items in this inventory have size variants.';

COMMENT ON TABLE ember_schema.inventory_size IS 'Size options for inventories that have sizes enabled.';
COMMENT ON COLUMN ember_schema.inventory_size.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.inventory_size.inventory_id IS 'References the inventory.';
COMMENT ON COLUMN ember_schema.inventory_size.label IS 'Size label (e.g. S, M, L, 42).';
COMMENT ON COLUMN ember_schema.inventory_size.position IS 'Display order position.';
COMMENT ON COLUMN ember_schema.inventory_size.note IS 'Optional note about this size.';

COMMENT ON TABLE ember_schema.inventory_item IS 'Individual inventory items that can be tracked and assigned.';
COMMENT ON COLUMN ember_schema.inventory_item.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.inventory_item.inventory_id IS 'References the inventory this item belongs to.';
COMMENT ON COLUMN ember_schema.inventory_item.internal_id IS 'Optional station-internal identifier (e.g. asset tag number).';
COMMENT ON COLUMN ember_schema.inventory_item.name IS 'Item name or description.';
COMMENT ON COLUMN ember_schema.inventory_item.size_id IS 'References the size variant. NULL if inventory has no sizes.';
COMMENT ON COLUMN ember_schema.inventory_item.metadata IS 'Additional item metadata as JSONB.';
COMMENT ON COLUMN ember_schema.inventory_item.assigned_to IS 'Member this item is currently assigned/lent to. NULL if unassigned.';
COMMENT ON COLUMN ember_schema.inventory_item.lost_at IS 'When the item was marked as lost. NULL if not lost.';
COMMENT ON COLUMN ember_schema.inventory_item.item_source IS 'Where the item came from (e.g. purchased, donated).';

COMMENT ON TABLE ember_schema.inventory_item_history IS 'History of item assignments (lending/return log).';
COMMENT ON COLUMN ember_schema.inventory_item_history.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.inventory_item_history.item_id IS 'References the inventory item.';
COMMENT ON COLUMN ember_schema.inventory_item_history.member_id IS 'Member who had the item. NULL if member was deleted.';
COMMENT ON COLUMN ember_schema.inventory_item_history.member_name IS 'Snapshot of member name at the time of assignment.';
COMMENT ON COLUMN ember_schema.inventory_item_history.given_out IS 'When the item was given out.';
COMMENT ON COLUMN ember_schema.inventory_item_history.returned IS 'When the item was returned. NULL if still out.';

COMMENT ON TABLE ember_schema.inventory_requirement IS 'Required inventory items per role or group (e.g. every TEAM member needs 1 uniform).';
COMMENT ON COLUMN ember_schema.inventory_requirement.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.inventory_requirement.inventory_id IS 'References the inventory.';
COMMENT ON COLUMN ember_schema.inventory_requirement.role_id IS 'References the role this requirement applies to. NULL if group-based.';
COMMENT ON COLUMN ember_schema.inventory_requirement.group_id IS 'References the group this requirement applies to. NULL if role-based.';
COMMENT ON COLUMN ember_schema.inventory_requirement.quantity IS 'Number of items required.';
COMMENT ON COLUMN ember_schema.inventory_requirement.position IS 'Display order position.';

COMMENT ON TABLE ember_schema.inventory_check IS 'Equipment check sessions (verifying a member has all required items).';
COMMENT ON COLUMN ember_schema.inventory_check.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.inventory_check.station_id IS 'References the station.';
COMMENT ON COLUMN ember_schema.inventory_check.member_id IS 'Member being checked.';
COMMENT ON COLUMN ember_schema.inventory_check.checked_by IS 'Member who performed the check.';
COMMENT ON COLUMN ember_schema.inventory_check.checked_at IS 'When the check was performed.';

COMMENT ON TABLE ember_schema.inventory_check_item IS 'Individual item results within an equipment check.';
COMMENT ON COLUMN ember_schema.inventory_check_item.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.inventory_check_item.check_id IS 'References the equipment check session.';
COMMENT ON COLUMN ember_schema.inventory_check_item.item_id IS 'References the specific inventory item checked. NULL for inventory-level checks.';
COMMENT ON COLUMN ember_schema.inventory_check_item.inventory_id IS 'References the inventory. NULL for item-level checks.';
COMMENT ON COLUMN ember_schema.inventory_check_item.result IS 'Check result (e.g. OK, DAMAGED, MISSING).';
COMMENT ON COLUMN ember_schema.inventory_check_item.note IS 'Optional note about the check result.';

COMMENT ON TABLE ember_schema.inventory_check_lock IS 'Locks to prevent concurrent equipment checks on the same member.';
COMMENT ON COLUMN ember_schema.inventory_check_lock.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.inventory_check_lock.station_id IS 'References the station.';
COMMENT ON COLUMN ember_schema.inventory_check_lock.member_id IS 'Member being checked (unique constraint ensures one lock per member).';
COMMENT ON COLUMN ember_schema.inventory_check_lock.locked_by IS 'Member who holds the lock.';
COMMENT ON COLUMN ember_schema.inventory_check_lock.locked_at IS 'When the lock was acquired.';

-- ============================================================
-- Equipment Exchange & Procurement
-- ============================================================

COMMENT ON TABLE ember_schema.equipment_exchange_request IS 'Requests for exchanging inventory items (e.g. size change).';
COMMENT ON COLUMN ember_schema.equipment_exchange_request.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.equipment_exchange_request.station_id IS 'References the station.';
COMMENT ON COLUMN ember_schema.equipment_exchange_request.member_id IS 'Member requesting the exchange.';
COMMENT ON COLUMN ember_schema.equipment_exchange_request.item_id IS 'Current item to be exchanged. NULL if item was deleted.';
COMMENT ON COLUMN ember_schema.equipment_exchange_request.inventory_id IS 'References the inventory the exchange is for.';
COMMENT ON COLUMN ember_schema.equipment_exchange_request.old_size_id IS 'Current size of the item.';
COMMENT ON COLUMN ember_schema.equipment_exchange_request.new_size_id IS 'Requested new size.';
COMMENT ON COLUMN ember_schema.equipment_exchange_request.exchanged_item_id IS 'The replacement item after exchange is fulfilled.';
COMMENT ON COLUMN ember_schema.equipment_exchange_request.status IS 'Request status: ANNOUNCED, APPROVED, FULFILLED, REJECTED.';
COMMENT ON COLUMN ember_schema.equipment_exchange_request.reason IS 'Reason for the exchange request.';
COMMENT ON COLUMN ember_schema.equipment_exchange_request.created_by IS 'Member who created the request (may differ from member_id).';
COMMENT ON COLUMN ember_schema.equipment_exchange_request.created_at IS 'When the request was created.';
COMMENT ON COLUMN ember_schema.equipment_exchange_request.updated_at IS 'When the request was last updated.';

COMMENT ON TABLE ember_schema.equipment_exchange_log IS 'Status change history for equipment exchange requests.';
COMMENT ON COLUMN ember_schema.equipment_exchange_log.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.equipment_exchange_log.request_id IS 'References the exchange request.';
COMMENT ON COLUMN ember_schema.equipment_exchange_log.old_status IS 'Previous status.';
COMMENT ON COLUMN ember_schema.equipment_exchange_log.new_status IS 'New status.';
COMMENT ON COLUMN ember_schema.equipment_exchange_log.changed_by IS 'Member who changed the status.';
COMMENT ON COLUMN ember_schema.equipment_exchange_log.changed_at IS 'When the status was changed.';
COMMENT ON COLUMN ember_schema.equipment_exchange_log.note IS 'Optional note about the status change.';

COMMENT ON TABLE ember_schema.equipment_procurement IS 'Requests for procuring new equipment items for a member.';
COMMENT ON COLUMN ember_schema.equipment_procurement.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.equipment_procurement.station_id IS 'References the station.';
COMMENT ON COLUMN ember_schema.equipment_procurement.inventory_id IS 'References the inventory to procure from.';
COMMENT ON COLUMN ember_schema.equipment_procurement.member_id IS 'Member the procurement is for.';
COMMENT ON COLUMN ember_schema.equipment_procurement.size_id IS 'Requested size. NULL if inventory has no sizes.';
COMMENT ON COLUMN ember_schema.equipment_procurement.notes IS 'Additional notes about the procurement request.';
COMMENT ON COLUMN ember_schema.equipment_procurement.requested_at IS 'When the procurement was requested.';
COMMENT ON COLUMN ember_schema.equipment_procurement.fulfilled_at IS 'When the procurement was fulfilled. NULL if pending.';

-- ============================================================
-- Attendance
-- ============================================================

COMMENT ON TABLE ember_schema.attendance_template IS 'Attendance sheet templates defining which fields to track.';
COMMENT ON COLUMN ember_schema.attendance_template.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.attendance_template.station_id IS 'References the station.';
COMMENT ON COLUMN ember_schema.attendance_template.name IS 'Template name, unique within the station.';

COMMENT ON TABLE ember_schema.attendance_template_field IS 'Fields within an attendance template (e.g. instructor, topic).';
COMMENT ON COLUMN ember_schema.attendance_template_field.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.attendance_template_field.template_id IS 'References the attendance template.';
COMMENT ON COLUMN ember_schema.attendance_template_field.name IS 'Field name/label.';
COMMENT ON COLUMN ember_schema.attendance_template_field.field_type IS 'Data type: member, member_list, string, time, date, member_of_group, member_list_of_group.';
COMMENT ON COLUMN ember_schema.attendance_template_field.config IS 'Type-specific configuration as JSONB (e.g. group references).';
COMMENT ON COLUMN ember_schema.attendance_template_field.position IS 'Display order position.';

COMMENT ON TABLE ember_schema.attendance_template_group IS 'Groups whose members are expected to attend sessions using this template.';
COMMENT ON COLUMN ember_schema.attendance_template_group.template_id IS 'References the attendance template.';
COMMENT ON COLUMN ember_schema.attendance_template_group.group_id IS 'References the member group.';
COMMENT ON COLUMN ember_schema.attendance_template_group.position IS 'Display order position.';

COMMENT ON TABLE ember_schema.event_category IS 'Categories for station events (e.g. Training, Meeting).';
COMMENT ON COLUMN ember_schema.event_category.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.event_category.station_id IS 'References the station.';
COMMENT ON COLUMN ember_schema.event_category.name IS 'Category name, unique within the station.';
COMMENT ON COLUMN ember_schema.event_category.position IS 'Display order position.';
COMMENT ON COLUMN ember_schema.event_category.max_shown_events IS 'Maximum events to show per category in the overview. NULL = show all.';
COMMENT ON COLUMN ember_schema.event_category.public IS 'Whether events in this category are public by default.';

COMMENT ON TABLE ember_schema.station_event IS 'Events at a station (one-time or recurring).';
COMMENT ON COLUMN ember_schema.station_event.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.station_event.station_id IS 'References the station.';
COMMENT ON COLUMN ember_schema.station_event.name IS 'Event name/title.';
COMMENT ON COLUMN ember_schema.station_event.description IS 'Event description (markdown).';
COMMENT ON COLUMN ember_schema.station_event.event_type IS 'Event type: ONE_TIME or RECURRING.';
COMMENT ON COLUMN ember_schema.station_event.day_of_week IS 'Day of week for recurring events (1=Monday). NULL for one-time events.';
COMMENT ON COLUMN ember_schema.station_event.start_time IS 'Event start time.';
COMMENT ON COLUMN ember_schema.station_event.end_time IS 'Event end time.';
COMMENT ON COLUMN ember_schema.station_event.template_id IS 'References the attendance template used for this event.';
COMMENT ON COLUMN ember_schema.station_event.requires_registration IS 'Whether members must register before attending.';
COMMENT ON COLUMN ember_schema.station_event.registration_deadline IS 'Deadline for registration. NULL if no deadline.';
COMMENT ON COLUMN ember_schema.station_event.requires_confirmation IS 'Whether registrations need manager confirmation.';
COMMENT ON COLUMN ember_schema.station_event.category_id IS 'References the event category.';
COMMENT ON COLUMN ember_schema.station_event.restriction_mode IS 'How role/group/tag restrictions combine: AND or OR.';
COMMENT ON COLUMN ember_schema.station_event.public IS 'Public visibility override. NULL = inherit from category.';
COMMENT ON COLUMN ember_schema.station_event.registration_limit IS 'Max number of registrations. NULL = unlimited.';
COMMENT ON COLUMN ember_schema.station_event.deadline_notified IS 'Whether the registration deadline notification has been sent.';

COMMENT ON TABLE ember_schema.station_event_break IS 'Date ranges during which recurring events are suspended (e.g. holidays).';
COMMENT ON COLUMN ember_schema.station_event_break.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.station_event_break.station_id IS 'References the station.';
COMMENT ON COLUMN ember_schema.station_event_break.name IS 'Break name (e.g. Summer Holiday).';
COMMENT ON COLUMN ember_schema.station_event_break.start_date IS 'First day of the break.';
COMMENT ON COLUMN ember_schema.station_event_break.end_date IS 'Last day of the break.';

COMMENT ON TABLE ember_schema.attendance_session IS 'An actual attendance session instance (created from a template for a specific date/time).';
COMMENT ON COLUMN ember_schema.attendance_session.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.attendance_session.template_id IS 'References the attendance template.';
COMMENT ON COLUMN ember_schema.attendance_session.start_time IS 'Session start time.';
COMMENT ON COLUMN ember_schema.attendance_session.end_time IS 'Session end time.';
COMMENT ON COLUMN ember_schema.attendance_session.created_at IS 'When the session was created.';
COMMENT ON COLUMN ember_schema.attendance_session.event_id IS 'References the station event this session was created for.';
COMMENT ON COLUMN ember_schema.attendance_session.title IS 'Optional title override for this session.';

COMMENT ON TABLE ember_schema.attendance_session_field IS 'Field values for a specific attendance session (e.g. who was the instructor).';
COMMENT ON COLUMN ember_schema.attendance_session_field.session_id IS 'References the attendance session.';
COMMENT ON COLUMN ember_schema.attendance_session_field.field_id IS 'References the attendance template field.';
COMMENT ON COLUMN ember_schema.attendance_session_field.value IS 'Field value as JSONB.';

COMMENT ON TABLE ember_schema.attendance_entry IS 'Individual attendance records (one per member per session).';
COMMENT ON COLUMN ember_schema.attendance_entry.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.attendance_entry.session_id IS 'References the attendance session.';
COMMENT ON COLUMN ember_schema.attendance_entry.member_id IS 'References the station member.';
COMMENT ON COLUMN ember_schema.attendance_entry.check_in IS 'Actual check-in time. NULL if not yet checked in.';
COMMENT ON COLUMN ember_schema.attendance_entry.check_out IS 'Actual check-out time. NULL if not yet checked out.';
COMMENT ON COLUMN ember_schema.attendance_entry.status IS 'Attendance status: PRESENT, ABSENT, EXCUSED.';
COMMENT ON COLUMN ember_schema.attendance_entry.source IS 'How the entry was created: EXPECTED, MANUAL, REGISTRATION.';

COMMENT ON TABLE ember_schema.attendance_report_preset IS 'Saved attendance report configurations for quick access.';
COMMENT ON COLUMN ember_schema.attendance_report_preset.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.attendance_report_preset.station_id IS 'References the station.';
COMMENT ON COLUMN ember_schema.attendance_report_preset.name IS 'Preset name.';
COMMENT ON COLUMN ember_schema.attendance_report_preset.role_name IS 'Filter by role name. NULL for no role filter.';
COMMENT ON COLUMN ember_schema.attendance_report_preset.group_id IS 'Filter by group. NULL for no group filter.';
COMMENT ON COLUMN ember_schema.attendance_report_preset.period IS 'Report period: month, quarter, year.';
COMMENT ON COLUMN ember_schema.attendance_report_preset.rounding IS 'Time rounding mode: exact, 15min, 30min, hour.';

-- ============================================================
-- Member Absences
-- ============================================================

COMMENT ON TABLE ember_schema.member_absence IS 'Planned absences for station members.';
COMMENT ON COLUMN ember_schema.member_absence.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.member_absence.member_id IS 'References the station member.';
COMMENT ON COLUMN ember_schema.member_absence.absent_from IS 'First day of absence.';
COMMENT ON COLUMN ember_schema.member_absence.absent_until IS 'Last day of absence.';
COMMENT ON COLUMN ember_schema.member_absence.reason IS 'Optional reason for the absence.';
COMMENT ON COLUMN ember_schema.member_absence.created_by IS 'Member who created the absence record.';
COMMENT ON COLUMN ember_schema.member_absence.created_at IS 'When the absence was recorded.';

-- ============================================================
-- Event Registrations, Fields & Restrictions
-- ============================================================

COMMENT ON TABLE ember_schema.event_registration IS 'Member registrations for events (per date for recurring events).';
COMMENT ON COLUMN ember_schema.event_registration.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.event_registration.event_id IS 'References the station event.';
COMMENT ON COLUMN ember_schema.event_registration.member_id IS 'References the station member.';
COMMENT ON COLUMN ember_schema.event_registration.event_date IS 'Specific date of the event occurrence.';
COMMENT ON COLUMN ember_schema.event_registration.status IS 'Registration status: PENDING, CONFIRMED, CANCELLED.';
COMMENT ON COLUMN ember_schema.event_registration.created_by IS 'Member who created the registration.';
COMMENT ON COLUMN ember_schema.event_registration.created_at IS 'When the registration was created.';

COMMENT ON TABLE ember_schema.event_field_default IS 'Default values for attendance session fields when created from an event.';
COMMENT ON COLUMN ember_schema.event_field_default.event_id IS 'References the station event.';
COMMENT ON COLUMN ember_schema.event_field_default.field_id IS 'References the attendance template field.';
COMMENT ON COLUMN ember_schema.event_field_default.source IS 'Value source: VALUE, EVENT_NAME, EVENT_DESCRIPTION, EVENT_START_TIME, EVENT_END_TIME, EVENT_DATE.';
COMMENT ON COLUMN ember_schema.event_field_default.value IS 'Static value when source is VALUE. NULL otherwise.';

COMMENT ON TABLE ember_schema.event_field IS 'Custom fields attached to events (displayed in event detail view).';
COMMENT ON COLUMN ember_schema.event_field.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.event_field.event_id IS 'References the station event.';
COMMENT ON COLUMN ember_schema.event_field.name IS 'Field name/label.';
COMMENT ON COLUMN ember_schema.event_field.value IS 'Field value.';
COMMENT ON COLUMN ember_schema.event_field.position IS 'Display order position.';
COMMENT ON COLUMN ember_schema.event_field.field_type IS 'Data type of the field (e.g. string, number, date).';
COMMENT ON COLUMN ember_schema.event_field.config IS 'Type-specific configuration as JSONB.';
COMMENT ON COLUMN ember_schema.event_field.overview IS 'Whether to show this field in the event overview/list.';
COMMENT ON COLUMN ember_schema.event_field.attendance_field_id IS 'References an attendance template field to auto-populate from.';
COMMENT ON COLUMN ember_schema.event_field.public IS 'Whether this field is visible on the public calendar.';

COMMENT ON TABLE ember_schema.event_restriction IS 'Unified access restrictions for events (role, group, tag, or member).';
COMMENT ON COLUMN ember_schema.event_restriction.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.event_restriction.event_id IS 'References the station event.';
COMMENT ON COLUMN ember_schema.event_restriction.role_id IS 'Required role. Exactly one of role_id/group_id/tag_id/member_id must be set.';
COMMENT ON COLUMN ember_schema.event_restriction.group_id IS 'Required group membership.';
COMMENT ON COLUMN ember_schema.event_restriction.tag_id IS 'Required tag.';
COMMENT ON COLUMN ember_schema.event_restriction.member_id IS 'Specific member (always OR-connected, bypasses AND/OR mode).';

-- ============================================================
-- News
-- ============================================================

COMMENT ON TABLE ember_schema.news IS 'News articles published within a station.';
COMMENT ON COLUMN ember_schema.news.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.news.station_id IS 'References the station.';
COMMENT ON COLUMN ember_schema.news.title IS 'Article title.';
COMMENT ON COLUMN ember_schema.news.content_markdown IS 'Article content in Markdown format.';
COMMENT ON COLUMN ember_schema.news.content_html IS 'Pre-rendered HTML content.';
COMMENT ON COLUMN ember_schema.news.author_id IS 'Member who authored the article.';
COMMENT ON COLUMN ember_schema.news.published_at IS 'When the article was published. NULL if draft.';
COMMENT ON COLUMN ember_schema.news.created_at IS 'When the article was created.';
COMMENT ON COLUMN ember_schema.news.restriction_mode IS 'How role/group/tag restrictions combine: AND or OR.';

COMMENT ON TABLE ember_schema.news_comment IS 'Comments on news articles (threaded via parent_id).';
COMMENT ON COLUMN ember_schema.news_comment.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.news_comment.news_id IS 'References the news article.';
COMMENT ON COLUMN ember_schema.news_comment.parent_id IS 'Parent comment for threading. NULL for top-level comments.';
COMMENT ON COLUMN ember_schema.news_comment.author_id IS 'Member who wrote the comment.';
COMMENT ON COLUMN ember_schema.news_comment.content IS 'Comment text.';
COMMENT ON COLUMN ember_schema.news_comment.created_at IS 'When the comment was created.';
COMMENT ON COLUMN ember_schema.news_comment.updated_at IS 'When the comment was last edited.';
COMMENT ON COLUMN ember_schema.news_comment.deleted IS 'Soft-delete flag. Content is hidden but threading is preserved.';

COMMENT ON TABLE ember_schema.news_restriction IS 'Unified access restrictions for news articles.';
COMMENT ON COLUMN ember_schema.news_restriction.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.news_restriction.news_id IS 'References the news article.';
COMMENT ON COLUMN ember_schema.news_restriction.role_id IS 'Required role. Exactly one of role_id/group_id/tag_id/member_id must be set.';
COMMENT ON COLUMN ember_schema.news_restriction.group_id IS 'Required group membership.';
COMMENT ON COLUMN ember_schema.news_restriction.tag_id IS 'Required tag.';
COMMENT ON COLUMN ember_schema.news_restriction.member_id IS 'Specific member.';

COMMENT ON TABLE ember_schema.news_acknowledgement IS 'Tracks which members have read a news article.';
COMMENT ON COLUMN ember_schema.news_acknowledgement.news_id IS 'References the news article.';
COMMENT ON COLUMN ember_schema.news_acknowledgement.member_id IS 'References the station member.';
COMMENT ON COLUMN ember_schema.news_acknowledgement.created_at IS 'When the member acknowledged/read the article.';

-- ============================================================
-- Notifications
-- ============================================================

COMMENT ON TABLE ember_schema.notification IS 'In-app notifications for station members.';
COMMENT ON COLUMN ember_schema.notification.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.notification.member_id IS 'References the station member this notification is for.';
COMMENT ON COLUMN ember_schema.notification.type IS 'Notification type (e.g. NEWS_PUBLISHED, EVENT_REMINDER).';
COMMENT ON COLUMN ember_schema.notification.data IS 'Notification payload as JSONB (type-specific data).';
COMMENT ON COLUMN ember_schema.notification.created_at IS 'When the notification was created.';
COMMENT ON COLUMN ember_schema.notification.acknowledged_at IS 'When the notification was read/dismissed. NULL if unread.';
COMMENT ON COLUMN ember_schema.notification.emailed_at IS 'When the notification was sent via email. NULL if not emailed.';

-- ============================================================
-- User Settings & Notification Preferences
-- ============================================================

COMMENT ON TABLE ember_schema.user_settings IS 'Per-member settings within a station.';
COMMENT ON COLUMN ember_schema.user_settings.member_id IS 'References the station member.';
COMMENT ON COLUMN ember_schema.user_settings.email_enabled IS 'Whether email notifications are enabled.';
COMMENT ON COLUMN ember_schema.user_settings.theme IS 'Selected color theme.';
COMMENT ON COLUMN ember_schema.user_settings.dark_mode IS 'Dark mode preference: light, dark, system.';
COMMENT ON COLUMN ember_schema.user_settings.feel IS 'UI feel preference: ROUNDED or CORNERS.';

COMMENT ON TABLE ember_schema.user_notification_settings IS 'Per-type notification preferences for a member.';
COMMENT ON COLUMN ember_schema.user_notification_settings.member_id IS 'References the station member.';
COMMENT ON COLUMN ember_schema.user_notification_settings.notification_type IS 'Notification type name.';
COMMENT ON COLUMN ember_schema.user_notification_settings.app_enabled IS 'Whether in-app notifications are enabled for this type.';
COMMENT ON COLUMN ember_schema.user_notification_settings.email_enabled IS 'Whether email notifications are enabled for this type.';
COMMENT ON COLUMN ember_schema.user_notification_settings.feed_enabled IS 'Whether this type appears in RSS/Atom/iCal feeds.';

-- ============================================================
-- Saved Filters
-- ============================================================

COMMENT ON TABLE ember_schema.saved_filter IS 'User-saved table filter presets (e.g. member list filters).';
COMMENT ON COLUMN ember_schema.saved_filter.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.saved_filter.account_id IS 'References the account.';
COMMENT ON COLUMN ember_schema.saved_filter.table_type IS 'Identifier for which table/view the filter applies to.';
COMMENT ON COLUMN ember_schema.saved_filter.name IS 'Filter preset name.';
COMMENT ON COLUMN ember_schema.saved_filter.filter_data IS 'Serialized filter configuration as JSONB.';
COMMENT ON COLUMN ember_schema.saved_filter.position IS 'Display order position.';

-- ============================================================
-- Email
-- ============================================================

COMMENT ON TABLE ember_schema.email_queue IS 'Outgoing email queue processed by the email worker.';
COMMENT ON COLUMN ember_schema.email_queue.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.email_queue.station_id IS 'References the sending station. NULL for system emails.';
COMMENT ON COLUMN ember_schema.email_queue.recipient IS 'Recipient email address.';
COMMENT ON COLUMN ember_schema.email_queue.subject IS 'Email subject line.';
COMMENT ON COLUMN ember_schema.email_queue.body IS 'Email body (HTML).';
COMMENT ON COLUMN ember_schema.email_queue.created_at IS 'When the email was queued.';
COMMENT ON COLUMN ember_schema.email_queue.status IS 'Delivery status: PENDING, SENT, FAILED.';

COMMENT ON TABLE ember_schema.email_daily_count IS 'Global daily email send count for rate limiting.';
COMMENT ON COLUMN ember_schema.email_daily_count.day IS 'Date (primary key).';
COMMENT ON COLUMN ember_schema.email_daily_count.count IS 'Number of emails sent on this date.';

COMMENT ON TABLE ember_schema.station_mail_config IS 'Email provider configuration per station.';
COMMENT ON COLUMN ember_schema.station_mail_config.station_id IS 'References the station.';
COMMENT ON COLUMN ember_schema.station_mail_config.provider IS 'Email provider: NONE, SMTP, RAPIDMAIL.';
COMMENT ON COLUMN ember_schema.station_mail_config.smtp_host IS 'SMTP server hostname.';
COMMENT ON COLUMN ember_schema.station_mail_config.smtp_port IS 'SMTP server port.';
COMMENT ON COLUMN ember_schema.station_mail_config.smtp_ssl IS 'Whether to use SSL/TLS for SMTP.';
COMMENT ON COLUMN ember_schema.station_mail_config.smtp_user IS 'SMTP authentication username.';
COMMENT ON COLUMN ember_schema.station_mail_config.smtp_password IS 'SMTP authentication password.';
COMMENT ON COLUMN ember_schema.station_mail_config.sender_address IS 'Sender email address (From header).';
COMMENT ON COLUMN ember_schema.station_mail_config.sender_name IS 'Sender display name.';
COMMENT ON COLUMN ember_schema.station_mail_config.api_key IS 'API key for non-SMTP providers.';
COMMENT ON COLUMN ember_schema.station_mail_config.provider_name IS 'Display name of the email provider.';
COMMENT ON COLUMN ember_schema.station_mail_config.provider_url IS 'URL of the email provider portal.';
COMMENT ON COLUMN ember_schema.station_mail_config.daily_limit IS 'Max emails per day for this station.';
COMMENT ON COLUMN ember_schema.station_mail_config.monthly_limit IS 'Max emails per month for this station.';
COMMENT ON COLUMN ember_schema.station_mail_config.updated_at IS 'When the configuration was last updated.';

COMMENT ON TABLE ember_schema.station_email_count IS 'Per-station daily email send count for rate limiting.';
COMMENT ON COLUMN ember_schema.station_email_count.station_id IS 'References the station.';
COMMENT ON COLUMN ember_schema.station_email_count.day IS 'Date.';
COMMENT ON COLUMN ember_schema.station_email_count.count IS 'Number of emails sent on this date.';

-- ============================================================
-- Station Applications
-- ============================================================

COMMENT ON TABLE ember_schema.station_application IS 'Applications to create a new station (admin approval workflow).';
COMMENT ON COLUMN ember_schema.station_application.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.station_application.first_name IS 'Applicant first name.';
COMMENT ON COLUMN ember_schema.station_application.last_name IS 'Applicant last name.';
COMMENT ON COLUMN ember_schema.station_application.email IS 'Applicant email address.';
COMMENT ON COLUMN ember_schema.station_application.station_name IS 'Requested station name.';
COMMENT ON COLUMN ember_schema.station_application.introduction IS 'Applicant introduction/reason for creating a station.';
COMMENT ON COLUMN ember_schema.station_application.verification_token IS 'Token for email verification of the application.';
COMMENT ON COLUMN ember_schema.station_application.status IS 'Application status: unverified, pending, approved, denied.';
COMMENT ON COLUMN ember_schema.station_application.deny_reason IS 'Reason for denial if status is denied.';
COMMENT ON COLUMN ember_schema.station_application.created_at IS 'When the application was submitted.';
COMMENT ON COLUMN ember_schema.station_application.resolved_at IS 'When the application was approved or denied.';

-- ============================================================
-- Forms / Polls
-- ============================================================

COMMENT ON TABLE ember_schema.form IS 'Forms/polls/surveys created within a station.';
COMMENT ON COLUMN ember_schema.form.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.form.station_id IS 'References the station.';
COMMENT ON COLUMN ember_schema.form.title IS 'Form title.';
COMMENT ON COLUMN ember_schema.form.description IS 'Form description.';
COMMENT ON COLUMN ember_schema.form.status IS 'Form status: DRAFT, OPEN, CLOSED.';
COMMENT ON COLUMN ember_schema.form.shuffle_questions IS 'Whether to randomize question order for each respondent.';
COMMENT ON COLUMN ember_schema.form.allow_edit IS 'Whether respondents can edit their submitted responses.';
COMMENT ON COLUMN ember_schema.form.start_at IS 'Scheduled open time. NULL for manual open.';
COMMENT ON COLUMN ember_schema.form.end_at IS 'Scheduled close time. NULL for manual close.';
COMMENT ON COLUMN ember_schema.form.closed_at IS 'Actual close time. NULL if not yet closed.';
COMMENT ON COLUMN ember_schema.form.created_by IS 'Member who created the form.';
COMMENT ON COLUMN ember_schema.form.created_at IS 'When the form was created.';
COMMENT ON COLUMN ember_schema.form.updated_at IS 'When the form was last updated.';
COMMENT ON COLUMN ember_schema.form.restriction_mode IS 'How role/group/tag restrictions combine: AND or OR.';
COMMENT ON COLUMN ember_schema.form.forced IS 'Whether members are required to fill out this form.';

COMMENT ON TABLE ember_schema.form_question IS 'Questions within a form.';
COMMENT ON COLUMN ember_schema.form_question.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.form_question.form_id IS 'References the form.';
COMMENT ON COLUMN ember_schema.form_question.position IS 'Display order position.';
COMMENT ON COLUMN ember_schema.form_question.question_type IS 'Question type (e.g. TEXT, SINGLE_CHOICE, MULTIPLE_CHOICE, SCALE, IMAGE_TEXT).';
COMMENT ON COLUMN ember_schema.form_question.title IS 'Question text.';
COMMENT ON COLUMN ember_schema.form_question.description IS 'Additional description/help text for the question.';
COMMENT ON COLUMN ember_schema.form_question.required IS 'Whether answering this question is mandatory.';
COMMENT ON COLUMN ember_schema.form_question.shuffle IS 'Whether to randomize answer options.';
COMMENT ON COLUMN ember_schema.form_question.config IS 'Type-specific configuration as JSONB (e.g. options, scale range).';

COMMENT ON TABLE ember_schema.form_response IS 'A member''s response to a form (one per member per form).';
COMMENT ON COLUMN ember_schema.form_response.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.form_response.form_id IS 'References the form.';
COMMENT ON COLUMN ember_schema.form_response.member_id IS 'Member who the response is for.';
COMMENT ON COLUMN ember_schema.form_response.submitted_by IS 'Member who submitted the response (may differ for guardians).';
COMMENT ON COLUMN ember_schema.form_response.submitted_at IS 'When the response was submitted.';
COMMENT ON COLUMN ember_schema.form_response.updated_at IS 'When the response was last updated.';

COMMENT ON TABLE ember_schema.form_answer IS 'Individual answers within a form response.';
COMMENT ON COLUMN ember_schema.form_answer.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.form_answer.response_id IS 'References the form response.';
COMMENT ON COLUMN ember_schema.form_answer.question_id IS 'References the form question.';
COMMENT ON COLUMN ember_schema.form_answer.value IS 'Answer value as JSONB.';

COMMENT ON TABLE ember_schema.form_restriction IS 'Unified access restrictions for forms.';
COMMENT ON COLUMN ember_schema.form_restriction.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.form_restriction.form_id IS 'References the form.';
COMMENT ON COLUMN ember_schema.form_restriction.role_id IS 'Required role. Exactly one of role_id/group_id/tag_id/member_id must be set.';
COMMENT ON COLUMN ember_schema.form_restriction.group_id IS 'Required group membership.';
COMMENT ON COLUMN ember_schema.form_restriction.tag_id IS 'Required tag.';
COMMENT ON COLUMN ember_schema.form_restriction.member_id IS 'Specific member.';

-- ============================================================
-- GDPR
-- ============================================================

COMMENT ON TABLE ember_schema.gdpr_consent IS 'Consent records for GDPR compliance.';
COMMENT ON COLUMN ember_schema.gdpr_consent.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.gdpr_consent.account_id IS 'References the account.';
COMMENT ON COLUMN ember_schema.gdpr_consent.consent_version IS 'Version of the consent form that was agreed to.';
COMMENT ON COLUMN ember_schema.gdpr_consent.ip_address IS 'IP address at the time of consent.';
COMMENT ON COLUMN ember_schema.gdpr_consent.country IS 'Country determined from IP at the time of consent.';
COMMENT ON COLUMN ember_schema.gdpr_consent.user_agent IS 'Browser user agent at the time of consent.';
COMMENT ON COLUMN ember_schema.gdpr_consent.privacy_version IS 'Version of the privacy policy agreed to.';
COMMENT ON COLUMN ember_schema.gdpr_consent.tos_version IS 'Version of the terms of service agreed to.';
COMMENT ON COLUMN ember_schema.gdpr_consent.consented_at IS 'When consent was given.';

-- ============================================================
-- Station Modules
-- ============================================================

COMMENT ON TABLE ember_schema.station_disabled_module IS 'Modules disabled for a station (enabled by default, rows here disable them).';
COMMENT ON COLUMN ember_schema.station_disabled_module.station_id IS 'References the station.';
COMMENT ON COLUMN ember_schema.station_disabled_module.module IS 'Module name (e.g. QUIZ, KNOWLEDGE_BASE, FEDERATION).';

-- ============================================================
-- Lost and Found
-- ============================================================

COMMENT ON TABLE ember_schema.lost_and_found_item IS 'Lost items found at the station, waiting to be claimed.';
COMMENT ON COLUMN ember_schema.lost_and_found_item.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.lost_and_found_item.station_id IS 'References the station.';
COMMENT ON COLUMN ember_schema.lost_and_found_item.description IS 'Description of the found item.';
COMMENT ON COLUMN ember_schema.lost_and_found_item.found_at IS 'Date the item was found.';
COMMENT ON COLUMN ember_schema.lost_and_found_item.claimed_by IS 'Member who claimed the item. NULL if unclaimed.';
COMMENT ON COLUMN ember_schema.lost_and_found_item.claimed_at IS 'When the item was claimed.';
COMMENT ON COLUMN ember_schema.lost_and_found_item.created_by IS 'Member who registered the found item.';
COMMENT ON COLUMN ember_schema.lost_and_found_item.created_at IS 'When the item was registered.';

-- ============================================================
-- Transfer Tokens
-- ============================================================

COMMENT ON TABLE ember_schema.transfer_token IS 'One-time tokens for transferring station ownership.';
COMMENT ON COLUMN ember_schema.transfer_token.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.transfer_token.station_id IS 'References the station to be transferred.';
COMMENT ON COLUMN ember_schema.transfer_token.token IS 'Unique transfer token string.';
COMMENT ON COLUMN ember_schema.transfer_token.created_at IS 'When the token was created.';
COMMENT ON COLUMN ember_schema.transfer_token.expires_at IS 'When the token expires.';
COMMENT ON COLUMN ember_schema.transfer_token.used IS 'Whether the token has been used.';

-- ============================================================
-- Application Settings
-- ============================================================

COMMENT ON TABLE ember_schema.application_setting IS 'Global application-level settings (key-value pairs).';
COMMENT ON COLUMN ember_schema.application_setting.key IS 'Setting name (primary key).';
COMMENT ON COLUMN ember_schema.application_setting.value IS 'Setting value as text.';

-- ============================================================
-- Waiting List
-- ============================================================

COMMENT ON TABLE ember_schema.waiting_list IS 'Waiting lists for station membership.';
COMMENT ON COLUMN ember_schema.waiting_list.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.waiting_list.station_id IS 'References the station.';
COMMENT ON COLUMN ember_schema.waiting_list.name IS 'Waiting list name, unique within the station.';
COMMENT ON COLUMN ember_schema.waiting_list.description IS 'Description of the waiting list.';
COMMENT ON COLUMN ember_schema.waiting_list.scoring_formula IS 'Optional formula for calculating priority scores.';
COMMENT ON COLUMN ember_schema.waiting_list.confirm_interval_days IS 'Days between confirmation reminders.';
COMMENT ON COLUMN ember_schema.waiting_list.visible_fields IS 'JSONB array of field IDs visible to applicants.';
COMMENT ON COLUMN ember_schema.waiting_list.testing_group_id IS 'Group to add members to during testing phase.';
COMMENT ON COLUMN ember_schema.waiting_list.join_group_id IS 'Group to add members to when they join the station.';
COMMENT ON COLUMN ember_schema.waiting_list.join_role_id IS 'Role to assign to members when they join the station.';
COMMENT ON COLUMN ember_schema.waiting_list.attendance_threshold IS 'Number of attendance sessions required during testing phase.';
COMMENT ON COLUMN ember_schema.waiting_list.created_at IS 'When the waiting list was created.';

COMMENT ON TABLE ember_schema.waiting_list_field IS 'Custom fields on a waiting list application form.';
COMMENT ON COLUMN ember_schema.waiting_list_field.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.waiting_list_field.list_id IS 'References the waiting list.';
COMMENT ON COLUMN ember_schema.waiting_list_field.name IS 'Field name/label.';
COMMENT ON COLUMN ember_schema.waiting_list_field.field_type IS 'Data type of the field.';
COMMENT ON COLUMN ember_schema.waiting_list_field.config IS 'Type-specific configuration as JSONB.';
COMMENT ON COLUMN ember_schema.waiting_list_field.position IS 'Display order position.';
COMMENT ON COLUMN ember_schema.waiting_list_field.required IS 'Whether this field is required.';

COMMENT ON TABLE ember_schema.waiting_list_invite IS 'Invite codes for joining a waiting list.';
COMMENT ON COLUMN ember_schema.waiting_list_invite.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.waiting_list_invite.list_id IS 'References the waiting list.';
COMMENT ON COLUMN ember_schema.waiting_list_invite.code IS 'Unique invite code string.';
COMMENT ON COLUMN ember_schema.waiting_list_invite.max_uses IS 'Maximum number of uses.';
COMMENT ON COLUMN ember_schema.waiting_list_invite.uses IS 'Current number of uses.';
COMMENT ON COLUMN ember_schema.waiting_list_invite.expires_at IS 'Expiration timestamp. NULL for no expiration.';
COMMENT ON COLUMN ember_schema.waiting_list_invite.created_at IS 'When the invite was created.';

COMMENT ON TABLE ember_schema.waiting_list_entry IS 'Individual entries (applicants) on a waiting list.';
COMMENT ON COLUMN ember_schema.waiting_list_entry.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.waiting_list_entry.list_id IS 'References the waiting list.';
COMMENT ON COLUMN ember_schema.waiting_list_entry.firstname IS 'Applicant first name.';
COMMENT ON COLUMN ember_schema.waiting_list_entry.lastname IS 'Applicant last name.';
COMMENT ON COLUMN ember_schema.waiting_list_entry.parent_name IS 'Parent/guardian name (for minors).';
COMMENT ON COLUMN ember_schema.waiting_list_entry.email IS 'Applicant email address.';
COMMENT ON COLUMN ember_schema.waiting_list_entry.access_token IS 'Unique token for the applicant to access their entry.';
COMMENT ON COLUMN ember_schema.waiting_list_entry.status IS 'Entry status: WAITING, INVITED, TESTING, JOINED, WITHDRAWN.';
COMMENT ON COLUMN ember_schema.waiting_list_entry.confirmed_at IS 'When the applicant last confirmed their interest.';
COMMENT ON COLUMN ember_schema.waiting_list_entry.reminder_sent_at IS 'When the last confirmation reminder was sent.';
COMMENT ON COLUMN ember_schema.waiting_list_entry.created_at IS 'When the entry was created.';
COMMENT ON COLUMN ember_schema.waiting_list_entry.notes IS 'Internal notes about this entry.';
COMMENT ON COLUMN ember_schema.waiting_list_entry.member_id IS 'References station member if the applicant was converted.';
COMMENT ON COLUMN ember_schema.waiting_list_entry.invited_at IS 'When the applicant was invited.';
COMMENT ON COLUMN ember_schema.waiting_list_entry.testing_at IS 'When the applicant entered testing phase.';
COMMENT ON COLUMN ember_schema.waiting_list_entry.joined_at IS 'When the applicant joined the station.';
COMMENT ON COLUMN ember_schema.waiting_list_entry.withdrawn_at IS 'When the applicant withdrew.';
COMMENT ON COLUMN ember_schema.waiting_list_entry.attendance_count IS 'Number of attendance sessions during testing phase.';

COMMENT ON TABLE ember_schema.waiting_list_entry_value IS 'Custom field values for waiting list entries.';
COMMENT ON COLUMN ember_schema.waiting_list_entry_value.entry_id IS 'References the waiting list entry.';
COMMENT ON COLUMN ember_schema.waiting_list_entry_value.field_id IS 'References the waiting list field.';
COMMENT ON COLUMN ember_schema.waiting_list_entry_value.value IS 'Field value as JSONB.';

-- ============================================================
-- Quiz Module
-- ============================================================

COMMENT ON TABLE ember_schema.quiz_catalog IS 'Question catalogs (pools of quiz questions).';
COMMENT ON COLUMN ember_schema.quiz_catalog.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.quiz_catalog.station_id IS 'References the station.';
COMMENT ON COLUMN ember_schema.quiz_catalog.name IS 'Catalog name.';
COMMENT ON COLUMN ember_schema.quiz_catalog.description IS 'Catalog description.';
COMMENT ON COLUMN ember_schema.quiz_catalog.training_enabled IS 'Whether members can use this catalog for self-training.';
COMMENT ON COLUMN ember_schema.quiz_catalog.created_at IS 'When the catalog was created.';
COMMENT ON COLUMN ember_schema.quiz_catalog.updated_at IS 'When the catalog was last updated.';

COMMENT ON TABLE ember_schema.quiz_category IS 'Station-scoped categories for organizing quiz questions.';
COMMENT ON COLUMN ember_schema.quiz_category.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.quiz_category.station_id IS 'References the station.';
COMMENT ON COLUMN ember_schema.quiz_category.name IS 'Category name.';
COMMENT ON COLUMN ember_schema.quiz_category.description IS 'Category description.';
COMMENT ON COLUMN ember_schema.quiz_category.position IS 'Display order position.';

COMMENT ON TABLE ember_schema.quiz_question IS 'Individual quiz questions within a catalog.';
COMMENT ON COLUMN ember_schema.quiz_question.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.quiz_question.catalog_id IS 'References the quiz catalog.';
COMMENT ON COLUMN ember_schema.quiz_question.category_id IS 'References the quiz category. NULL for uncategorized.';
COMMENT ON COLUMN ember_schema.quiz_question.question_type IS 'Question type (e.g. SINGLE_CHOICE, MULTIPLE_CHOICE, TEXT).';
COMMENT ON COLUMN ember_schema.quiz_question.title IS 'Question text.';
COMMENT ON COLUMN ember_schema.quiz_question.description IS 'Additional description/explanation.';
COMMENT ON COLUMN ember_schema.quiz_question.image_url IS 'Optional image URL for the question.';
COMMENT ON COLUMN ember_schema.quiz_question.points IS 'Points awarded for a correct answer (supports fractional values).';
COMMENT ON COLUMN ember_schema.quiz_question.auto_points IS 'Whether points are auto-calculated or manually assigned.';
COMMENT ON COLUMN ember_schema.quiz_question.config IS 'Type-specific configuration as JSONB (e.g. answer options, correct answers).';
COMMENT ON COLUMN ember_schema.quiz_question.position IS 'Display order position within the catalog.';
COMMENT ON COLUMN ember_schema.quiz_question.created_at IS 'When the question was created.';
COMMENT ON COLUMN ember_schema.quiz_question.updated_at IS 'When the question was last updated.';

COMMENT ON TABLE ember_schema.quiz_test IS 'Quiz tests assembled from catalog questions.';
COMMENT ON COLUMN ember_schema.quiz_test.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.quiz_test.station_id IS 'References the station.';
COMMENT ON COLUMN ember_schema.quiz_test.title IS 'Test title.';
COMMENT ON COLUMN ember_schema.quiz_test.description IS 'Test description.';
COMMENT ON COLUMN ember_schema.quiz_test.status IS 'Test status: DRAFT, ACTIVE, CLOSED.';
COMMENT ON COLUMN ember_schema.quiz_test.time_limit IS 'Time limit in minutes. NULL for no limit.';
COMMENT ON COLUMN ember_schema.quiz_test.shuffle IS 'Whether to randomize question order.';
COMMENT ON COLUMN ember_schema.quiz_test.start_at IS 'Scheduled test start time.';
COMMENT ON COLUMN ember_schema.quiz_test.end_at IS 'Scheduled test end time.';
COMMENT ON COLUMN ember_schema.quiz_test.created_by IS 'Member who created the test.';
COMMENT ON COLUMN ember_schema.quiz_test.created_at IS 'When the test was created.';
COMMENT ON COLUMN ember_schema.quiz_test.updated_at IS 'When the test was last updated.';
COMMENT ON COLUMN ember_schema.quiz_test.restriction_mode IS 'How role/group/tag restrictions combine: AND or OR.';
COMMENT ON COLUMN ember_schema.quiz_test.forced IS 'Whether members are required to take this test.';

COMMENT ON TABLE ember_schema.quiz_test_section IS 'Sections within a quiz test for organizing questions.';
COMMENT ON COLUMN ember_schema.quiz_test_section.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.quiz_test_section.test_id IS 'References the quiz test.';
COMMENT ON COLUMN ember_schema.quiz_test_section.title IS 'Section title.';
COMMENT ON COLUMN ember_schema.quiz_test_section.description IS 'Section description.';
COMMENT ON COLUMN ember_schema.quiz_test_section.position IS 'Display order position.';

COMMENT ON TABLE ember_schema.quiz_test_section_source IS 'Question sources for a test section (pull N questions from a catalog/category).';
COMMENT ON COLUMN ember_schema.quiz_test_section_source.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.quiz_test_section_source.section_id IS 'References the test section.';
COMMENT ON COLUMN ember_schema.quiz_test_section_source.catalog_id IS 'References the quiz catalog to pull questions from.';
COMMENT ON COLUMN ember_schema.quiz_test_section_source.category_id IS 'References the quiz category to filter by. NULL for all categories.';
COMMENT ON COLUMN ember_schema.quiz_test_section_source.question_count IS 'Number of questions to pull from this source.';

COMMENT ON TABLE ember_schema.quiz_test_attempt IS 'A member''s attempt at taking a quiz test.';
COMMENT ON COLUMN ember_schema.quiz_test_attempt.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.quiz_test_attempt.test_id IS 'References the quiz test.';
COMMENT ON COLUMN ember_schema.quiz_test_attempt.member_id IS 'References the station member.';
COMMENT ON COLUMN ember_schema.quiz_test_attempt.status IS 'Attempt status: IN_PROGRESS, SUBMITTED, GRADED.';
COMMENT ON COLUMN ember_schema.quiz_test_attempt.started_at IS 'When the attempt was started.';
COMMENT ON COLUMN ember_schema.quiz_test_attempt.submitted_at IS 'When the attempt was submitted.';
COMMENT ON COLUMN ember_schema.quiz_test_attempt.graded_at IS 'When the attempt was graded.';
COMMENT ON COLUMN ember_schema.quiz_test_attempt.graded_by IS 'Member who graded the attempt.';
COMMENT ON COLUMN ember_schema.quiz_test_attempt.total_points IS 'Total points achieved.';
COMMENT ON COLUMN ember_schema.quiz_test_attempt.max_points IS 'Maximum achievable points.';

COMMENT ON TABLE ember_schema.quiz_test_answer IS 'Individual answers within a quiz test attempt.';
COMMENT ON COLUMN ember_schema.quiz_test_answer.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.quiz_test_answer.attempt_id IS 'References the test attempt.';
COMMENT ON COLUMN ember_schema.quiz_test_answer.question_id IS 'References the quiz question.';
COMMENT ON COLUMN ember_schema.quiz_test_answer.section_id IS 'References the test section this answer belongs to.';
COMMENT ON COLUMN ember_schema.quiz_test_answer.answer IS 'Answer value as JSONB.';
COMMENT ON COLUMN ember_schema.quiz_test_answer.points IS 'Points awarded for this answer.';
COMMENT ON COLUMN ember_schema.quiz_test_answer.graded IS 'Whether this answer has been graded.';
COMMENT ON COLUMN ember_schema.quiz_test_answer.position IS 'Display order position.';

COMMENT ON TABLE ember_schema.quiz_test_attempt_question IS 'Questions assigned to a specific test attempt (may differ per member due to shuffling/random selection).';
COMMENT ON COLUMN ember_schema.quiz_test_attempt_question.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.quiz_test_attempt_question.attempt_id IS 'References the test attempt.';
COMMENT ON COLUMN ember_schema.quiz_test_attempt_question.question_id IS 'References the quiz question.';
COMMENT ON COLUMN ember_schema.quiz_test_attempt_question.section_id IS 'References the test section.';
COMMENT ON COLUMN ember_schema.quiz_test_attempt_question.position IS 'Display order position.';

COMMENT ON TABLE ember_schema.quiz_test_member_access IS 'Per-member access windows for quiz tests (individual deadline overrides).';
COMMENT ON COLUMN ember_schema.quiz_test_member_access.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.quiz_test_member_access.test_id IS 'References the quiz test.';
COMMENT ON COLUMN ember_schema.quiz_test_member_access.member_id IS 'References the station member.';
COMMENT ON COLUMN ember_schema.quiz_test_member_access.opened_at IS 'When the access window opens.';
COMMENT ON COLUMN ember_schema.quiz_test_member_access.closes_at IS 'When the access window closes. NULL for no expiration.';

COMMENT ON TABLE ember_schema.quiz_test_frozen_question IS 'Frozen question set for an activated test (snapshot to prevent changes during active test).';
COMMENT ON COLUMN ember_schema.quiz_test_frozen_question.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.quiz_test_frozen_question.test_id IS 'References the quiz test.';
COMMENT ON COLUMN ember_schema.quiz_test_frozen_question.question_id IS 'References the quiz question.';
COMMENT ON COLUMN ember_schema.quiz_test_frozen_question.section_id IS 'References the test section.';
COMMENT ON COLUMN ember_schema.quiz_test_frozen_question.position IS 'Display order position.';

COMMENT ON TABLE ember_schema.quiz_test_restriction IS 'Unified access restrictions for quiz tests.';
COMMENT ON COLUMN ember_schema.quiz_test_restriction.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.quiz_test_restriction.test_id IS 'References the quiz test.';
COMMENT ON COLUMN ember_schema.quiz_test_restriction.role_id IS 'Required role. Exactly one of role_id/group_id/tag_id/member_id must be set.';
COMMENT ON COLUMN ember_schema.quiz_test_restriction.group_id IS 'Required group membership.';
COMMENT ON COLUMN ember_schema.quiz_test_restriction.tag_id IS 'Required tag.';
COMMENT ON COLUMN ember_schema.quiz_test_restriction.member_id IS 'Specific member.';

-- ============================================================
-- AI Provider
-- ============================================================

COMMENT ON TABLE ember_schema.station_ai_provider IS 'AI provider configurations per station (for AI-assisted features).';
COMMENT ON COLUMN ember_schema.station_ai_provider.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.station_ai_provider.station_id IS 'References the station.';
COMMENT ON COLUMN ember_schema.station_ai_provider.provider IS 'AI provider name (e.g. openai, anthropic).';
COMMENT ON COLUMN ember_schema.station_ai_provider.api_key IS 'API key for the provider.';
COMMENT ON COLUMN ember_schema.station_ai_provider.model IS 'Model identifier to use. NULL for provider default.';

-- ============================================================
-- Knowledge Base
-- ============================================================

COMMENT ON TABLE ember_schema.kb_folder IS 'Folders in the knowledge base (hierarchical via parent_id).';
COMMENT ON COLUMN ember_schema.kb_folder.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.kb_folder.station_id IS 'References the station.';
COMMENT ON COLUMN ember_schema.kb_folder.parent_id IS 'Parent folder for nesting. NULL for root-level folders.';
COMMENT ON COLUMN ember_schema.kb_folder.name IS 'Folder name, unique within parent.';
COMMENT ON COLUMN ember_schema.kb_folder.description IS 'Folder description.';
COMMENT ON COLUMN ember_schema.kb_folder.icon_url IS 'Optional custom icon URL.';
COMMENT ON COLUMN ember_schema.kb_folder.position IS 'Display order position.';
COMMENT ON COLUMN ember_schema.kb_folder.created_by IS 'Member who created the folder.';
COMMENT ON COLUMN ember_schema.kb_folder.created_at IS 'When the folder was created.';
COMMENT ON COLUMN ember_schema.kb_folder.updated_at IS 'When the folder was last updated.';
COMMENT ON COLUMN ember_schema.kb_folder.restriction_mode IS 'How role/group/tag restrictions combine: AND or OR.';

COMMENT ON TABLE ember_schema.kb_file IS 'Files/documents in the knowledge base.';
COMMENT ON COLUMN ember_schema.kb_file.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.kb_file.station_id IS 'References the station.';
COMMENT ON COLUMN ember_schema.kb_file.folder_id IS 'References the parent folder. NULL for root-level files.';
COMMENT ON COLUMN ember_schema.kb_file.name IS 'File/document name.';
COMMENT ON COLUMN ember_schema.kb_file.description IS 'File description.';
COMMENT ON COLUMN ember_schema.kb_file.file_type IS 'File type: DOCUMENT, UPLOAD, YOUTUBE, LINK.';
COMMENT ON COLUMN ember_schema.kb_file.mime_type IS 'MIME type for uploaded files.';
COMMENT ON COLUMN ember_schema.kb_file.file_size IS 'File size in bytes.';
COMMENT ON COLUMN ember_schema.kb_file.icon_url IS 'Optional custom icon URL.';
COMMENT ON COLUMN ember_schema.kb_file.youtube_url IS 'YouTube URL for YOUTUBE type files.';
COMMENT ON COLUMN ember_schema.kb_file.link_url IS 'External URL for LINK type files.';
COMMENT ON COLUMN ember_schema.kb_file.position IS 'Display order position.';
COMMENT ON COLUMN ember_schema.kb_file.created_by IS 'Member who created the file.';
COMMENT ON COLUMN ember_schema.kb_file.created_at IS 'When the file was created.';
COMMENT ON COLUMN ember_schema.kb_file.updated_at IS 'When the file was last updated.';
COMMENT ON COLUMN ember_schema.kb_file.source_file_id IS 'Original file ID if copied from a federation partner.';
COMMENT ON COLUMN ember_schema.kb_file.source_station_id IS 'Original station ID (internal) if copied from a federation partner.';
COMMENT ON COLUMN ember_schema.kb_file.restriction_mode IS 'How role/group/tag restrictions combine: AND or OR.';

COMMENT ON TABLE ember_schema.kb_file_content IS 'Text content for DOCUMENT type KB files (editable markdown).';
COMMENT ON COLUMN ember_schema.kb_file_content.file_id IS 'References the KB file.';
COMMENT ON COLUMN ember_schema.kb_file_content.text_content IS 'Current text content (markdown).';

COMMENT ON TABLE ember_schema.kb_file_version IS 'Version history for KB file content (diff-based).';
COMMENT ON COLUMN ember_schema.kb_file_version.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.kb_file_version.file_id IS 'References the KB file.';
COMMENT ON COLUMN ember_schema.kb_file_version.patch IS 'Unified diff patch or full content snapshot.';
COMMENT ON COLUMN ember_schema.kb_file_version.is_full IS 'Whether this version stores full content (true) or a diff patch (false).';
COMMENT ON COLUMN ember_schema.kb_file_version.version IS 'Version number (sequential per file).';
COMMENT ON COLUMN ember_schema.kb_file_version.created_by IS 'Member who created this version.';
COMMENT ON COLUMN ember_schema.kb_file_version.created_at IS 'When this version was created.';

COMMENT ON TABLE ember_schema.kb_search_index IS 'Full-text search index for KB files.';
COMMENT ON COLUMN ember_schema.kb_search_index.file_id IS 'References the KB file.';
COMMENT ON COLUMN ember_schema.kb_search_index.search_text IS 'Precomputed tsvector for full-text search.';

COMMENT ON TABLE ember_schema.kb_access_restriction IS 'Access restrictions for KB folders and files (role, group, tag, or member).';
COMMENT ON COLUMN ember_schema.kb_access_restriction.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.kb_access_restriction.folder_id IS 'References the KB folder. Exactly one of folder_id/file_id must be set.';
COMMENT ON COLUMN ember_schema.kb_access_restriction.file_id IS 'References the KB file.';
COMMENT ON COLUMN ember_schema.kb_access_restriction.role_id IS 'Required role. Exactly one of role_id/group_id/tag_id/member_id must be set.';
COMMENT ON COLUMN ember_schema.kb_access_restriction.group_id IS 'Required group membership.';
COMMENT ON COLUMN ember_schema.kb_access_restriction.tag_id IS 'Required tag.';
COMMENT ON COLUMN ember_schema.kb_access_restriction.member_id IS 'Specific member.';

COMMENT ON TABLE ember_schema.kb_tag IS 'Tags for organizing KB files and folders.';
COMMENT ON COLUMN ember_schema.kb_tag.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.kb_tag.station_id IS 'References the station.';
COMMENT ON COLUMN ember_schema.kb_tag.name IS 'Tag name, unique within the station.';

COMMENT ON TABLE ember_schema.kb_file_tag IS 'Tag assignments to KB files.';
COMMENT ON COLUMN ember_schema.kb_file_tag.file_id IS 'References the KB file.';
COMMENT ON COLUMN ember_schema.kb_file_tag.tag_id IS 'References the KB tag.';

COMMENT ON TABLE ember_schema.kb_folder_tag IS 'Tag assignments to KB folders.';
COMMENT ON COLUMN ember_schema.kb_folder_tag.folder_id IS 'References the KB folder.';
COMMENT ON COLUMN ember_schema.kb_folder_tag.tag_id IS 'References the KB tag.';

COMMENT ON TABLE ember_schema.kb_related_file IS 'Related file links between KB files (further reading).';
COMMENT ON COLUMN ember_schema.kb_related_file.source_file_id IS 'The file that links to another.';
COMMENT ON COLUMN ember_schema.kb_related_file.target_file_id IS 'The linked/related file.';
COMMENT ON COLUMN ember_schema.kb_related_file.position IS 'Display order position.';

COMMENT ON TABLE ember_schema.kb_public_visibility IS 'Per-folder/file public visibility overrides for the knowledge base.';
COMMENT ON COLUMN ember_schema.kb_public_visibility.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.kb_public_visibility.folder_id IS 'References the KB folder. Exactly one of folder_id/file_id must be set.';
COMMENT ON COLUMN ember_schema.kb_public_visibility.file_id IS 'References the KB file.';
COMMENT ON COLUMN ember_schema.kb_public_visibility.visible IS 'Whether the item is publicly visible.';

COMMENT ON TABLE ember_schema.kb_favourite IS 'User bookmarks/favourites for KB files.';
COMMENT ON COLUMN ember_schema.kb_favourite.member_id IS 'References the station member.';
COMMENT ON COLUMN ember_schema.kb_favourite.file_id IS 'References the KB file.';
COMMENT ON COLUMN ember_schema.kb_favourite.created_at IS 'When the favourite was added.';

-- ============================================================
-- Test Protocol
-- ============================================================

COMMENT ON TABLE ember_schema.test_protocol IS 'Test protocol templates (evaluation blueprints for practical exams).';
COMMENT ON COLUMN ember_schema.test_protocol.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.test_protocol.station_id IS 'References the station.';
COMMENT ON COLUMN ember_schema.test_protocol.name IS 'Protocol name.';
COMMENT ON COLUMN ember_schema.test_protocol.description IS 'Protocol description.';
COMMENT ON COLUMN ember_schema.test_protocol.pass_threshold IS 'Minimum points to pass. NULL if no threshold.';
COMMENT ON COLUMN ember_schema.test_protocol.created_at IS 'When the protocol was created.';
COMMENT ON COLUMN ember_schema.test_protocol.updated_at IS 'When the protocol was last updated.';

COMMENT ON TABLE ember_schema.test_protocol_section IS 'Sections within a test protocol (hierarchical via parent_id for subsections).';
COMMENT ON COLUMN ember_schema.test_protocol_section.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.test_protocol_section.protocol_id IS 'References the test protocol.';
COMMENT ON COLUMN ember_schema.test_protocol_section.parent_id IS 'Parent section for nesting. NULL for top-level sections.';
COMMENT ON COLUMN ember_schema.test_protocol_section.name IS 'Section name.';
COMMENT ON COLUMN ember_schema.test_protocol_section.description IS 'Section description.';
COMMENT ON COLUMN ember_schema.test_protocol_section.max_points IS 'Maximum achievable points for this section. NULL if not scored.';
COMMENT ON COLUMN ember_schema.test_protocol_section.pass_threshold IS 'Minimum points to pass this section. NULL if no threshold.';
COMMENT ON COLUMN ember_schema.test_protocol_section.position IS 'Display order position.';

COMMENT ON TABLE ember_schema.test_protocol_item IS 'Individual checkable items within a test protocol section.';
COMMENT ON COLUMN ember_schema.test_protocol_item.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.test_protocol_item.section_id IS 'References the protocol section.';
COMMENT ON COLUMN ember_schema.test_protocol_item.label IS 'Item label/description to check.';
COMMENT ON COLUMN ember_schema.test_protocol_item.description IS 'Additional description.';
COMMENT ON COLUMN ember_schema.test_protocol_item.points IS 'Points awarded when this item is checked.';
COMMENT ON COLUMN ember_schema.test_protocol_item.position IS 'Display order position.';

COMMENT ON TABLE ember_schema.test_protocol_run IS 'A live test run (examination session using a protocol template).';
COMMENT ON COLUMN ember_schema.test_protocol_run.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.test_protocol_run.protocol_id IS 'References the test protocol template.';
COMMENT ON COLUMN ember_schema.test_protocol_run.station_id IS 'References the station.';
COMMENT ON COLUMN ember_schema.test_protocol_run.name IS 'Run name/title.';
COMMENT ON COLUMN ember_schema.test_protocol_run.test_date IS 'Date of the test.';
COMMENT ON COLUMN ember_schema.test_protocol_run.status IS 'Run status: OPEN, CLOSED.';
COMMENT ON COLUMN ember_schema.test_protocol_run.created_by IS 'Member who created the run.';
COMMENT ON COLUMN ember_schema.test_protocol_run.created_at IS 'When the run was created.';

COMMENT ON TABLE ember_schema.test_protocol_run_member IS 'Members being tested in a protocol run.';
COMMENT ON COLUMN ember_schema.test_protocol_run_member.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.test_protocol_run_member.run_id IS 'References the protocol run.';
COMMENT ON COLUMN ember_schema.test_protocol_run_member.member_id IS 'References the station member being tested.';
COMMENT ON COLUMN ember_schema.test_protocol_run_member.locked_by IS 'Member who has locked this evaluation for editing.';
COMMENT ON COLUMN ember_schema.test_protocol_run_member.locked_at IS 'When the evaluation was locked.';
COMMENT ON COLUMN ember_schema.test_protocol_run_member.completed IS 'Whether the evaluation is complete.';
COMMENT ON COLUMN ember_schema.test_protocol_run_member.total_score IS 'Total accumulated score.';

COMMENT ON TABLE ember_schema.test_protocol_run_section_done IS 'Tracks which top-level sections have been evaluated for a member.';
COMMENT ON COLUMN ember_schema.test_protocol_run_section_done.run_member_id IS 'References the run member.';
COMMENT ON COLUMN ember_schema.test_protocol_run_section_done.section_id IS 'References the protocol section.';
COMMENT ON COLUMN ember_schema.test_protocol_run_section_done.done_by IS 'Member who evaluated this section.';
COMMENT ON COLUMN ember_schema.test_protocol_run_section_done.done_at IS 'When the section was evaluated.';

COMMENT ON TABLE ember_schema.test_protocol_run_check IS 'Individual checkbox results within a protocol run for a member.';
COMMENT ON COLUMN ember_schema.test_protocol_run_check.run_member_id IS 'References the run member.';
COMMENT ON COLUMN ember_schema.test_protocol_run_check.item_id IS 'References the protocol item.';
COMMENT ON COLUMN ember_schema.test_protocol_run_check.checked IS 'Whether the item was checked/passed.';
COMMENT ON COLUMN ember_schema.test_protocol_run_check.checked_by IS 'Member who performed the check.';
COMMENT ON COLUMN ember_schema.test_protocol_run_check.checked_at IS 'When the check was performed.';

-- ============================================================
-- Federation
-- ============================================================

COMMENT ON TABLE ember_schema.federation_partner IS 'Federation partnerships between stations (local or cross-instance).';
COMMENT ON COLUMN ember_schema.federation_partner.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.federation_partner.station_id IS 'References the local station.';
COMMENT ON COLUMN ember_schema.federation_partner.partner_station_id IS 'UUID of the partner station.';
COMMENT ON COLUMN ember_schema.federation_partner.invite_code IS 'One-time invite code for establishing the partnership.';
COMMENT ON COLUMN ember_schema.federation_partner.public_key IS 'This station''s public key for the partnership.';
COMMENT ON COLUMN ember_schema.federation_partner.partner_public_key IS 'Partner station''s public key for verifying requests.';
COMMENT ON COLUMN ember_schema.federation_partner.status IS 'Partnership status: PENDING, ACTIVE, REVOKED.';
COMMENT ON COLUMN ember_schema.federation_partner.federation_version IS 'Protocol version hash for compatibility checking.';
COMMENT ON COLUMN ember_schema.federation_partner.created_at IS 'When the partnership was created.';
COMMENT ON COLUMN ember_schema.federation_partner.updated_at IS 'When the partnership was last updated.';
COMMENT ON COLUMN ember_schema.federation_partner.remote_host IS 'Remote instance base URL for cross-instance federation. NULL for same-instance.';
COMMENT ON COLUMN ember_schema.federation_partner.webhook_url IS 'Webhook URL for push notifications to the partner.';
COMMENT ON COLUMN ember_schema.federation_partner.last_sync_at IS 'When the last sync with this partner occurred.';

COMMENT ON TABLE ember_schema.federation_capability IS 'Capabilities shared per federation partner (what is shared and in which direction).';
COMMENT ON COLUMN ember_schema.federation_capability.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.federation_capability.partner_id IS 'References the federation partner.';
COMMENT ON COLUMN ember_schema.federation_capability.capability IS 'Capability name (e.g. KB, QUIZ, PROTOCOL, INVENTORY, EVENTS, BOARDS).';
COMMENT ON COLUMN ember_schema.federation_capability.direction IS 'Sharing direction: OUTBOUND (we share), INBOUND (we receive).';
COMMENT ON COLUMN ember_schema.federation_capability.enabled IS 'Whether this capability is currently enabled.';

COMMENT ON TABLE ember_schema.federation_kb_share IS 'Knowledge base content shared with federation partners.';
COMMENT ON COLUMN ember_schema.federation_kb_share.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.federation_kb_share.station_id IS 'References the station sharing the content.';
COMMENT ON COLUMN ember_schema.federation_kb_share.file_id IS 'References the KB file. Exactly one of file_id/folder_id must be set.';
COMMENT ON COLUMN ember_schema.federation_kb_share.folder_id IS 'References the KB folder.';
COMMENT ON COLUMN ember_schema.federation_kb_share.share_scope IS 'Scope: ALL_PARTNERS or SPECIFIC.';

COMMENT ON TABLE ember_schema.federation_kb_share_target IS 'Specific partners a KB share is targeted to (when scope is SPECIFIC).';
COMMENT ON COLUMN ember_schema.federation_kb_share_target.share_id IS 'References the KB share.';
COMMENT ON COLUMN ember_schema.federation_kb_share_target.partner_id IS 'References the federation partner.';

COMMENT ON TABLE ember_schema.federation_quiz_share IS 'Quiz catalogs shared with federation partners.';
COMMENT ON COLUMN ember_schema.federation_quiz_share.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.federation_quiz_share.station_id IS 'References the station sharing the catalog.';
COMMENT ON COLUMN ember_schema.federation_quiz_share.catalog_id IS 'References the quiz catalog.';
COMMENT ON COLUMN ember_schema.federation_quiz_share.share_scope IS 'Scope: ALL_PARTNERS or SPECIFIC.';

COMMENT ON TABLE ember_schema.federation_quiz_share_target IS 'Specific partners a quiz share is targeted to.';
COMMENT ON COLUMN ember_schema.federation_quiz_share_target.share_id IS 'References the quiz share.';
COMMENT ON COLUMN ember_schema.federation_quiz_share_target.partner_id IS 'References the federation partner.';

COMMENT ON TABLE ember_schema.federation_protocol_share IS 'Test protocols shared with federation partners.';
COMMENT ON COLUMN ember_schema.federation_protocol_share.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.federation_protocol_share.station_id IS 'References the station sharing the protocol.';
COMMENT ON COLUMN ember_schema.federation_protocol_share.protocol_id IS 'References the test protocol.';
COMMENT ON COLUMN ember_schema.federation_protocol_share.share_scope IS 'Scope: ALL_PARTNERS or SPECIFIC.';

COMMENT ON TABLE ember_schema.federation_protocol_share_target IS 'Specific partners a protocol share is targeted to.';
COMMENT ON COLUMN ember_schema.federation_protocol_share_target.share_id IS 'References the protocol share.';
COMMENT ON COLUMN ember_schema.federation_protocol_share_target.partner_id IS 'References the federation partner.';

COMMENT ON TABLE ember_schema.federation_metadata_cache IS 'Cached metadata for federated content (for browsing when remote is offline).';
COMMENT ON COLUMN ember_schema.federation_metadata_cache.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.federation_metadata_cache.partner_id IS 'References the federation partner.';
COMMENT ON COLUMN ember_schema.federation_metadata_cache.content_type IS 'Type of cached content (e.g. KB_FILE, QUIZ_CATALOG).';
COMMENT ON COLUMN ember_schema.federation_metadata_cache.remote_id IS 'ID of the content on the remote station.';
COMMENT ON COLUMN ember_schema.federation_metadata_cache.title IS 'Cached title.';
COMMENT ON COLUMN ember_schema.federation_metadata_cache.description IS 'Cached description.';
COMMENT ON COLUMN ember_schema.federation_metadata_cache.extra_data IS 'Additional cached data as JSONB.';
COMMENT ON COLUMN ember_schema.federation_metadata_cache.cached_at IS 'When the cache entry was last updated.';

COMMENT ON TABLE ember_schema.federation_inventory_share IS 'Inventory items/collections shared with federation partners for lending.';
COMMENT ON COLUMN ember_schema.federation_inventory_share.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.federation_inventory_share.station_id IS 'References the station sharing the inventory.';
COMMENT ON COLUMN ember_schema.federation_inventory_share.inventory_id IS 'References the inventory. Exactly one of inventory_id/item_id must be set.';
COMMENT ON COLUMN ember_schema.federation_inventory_share.item_id IS 'References the specific item.';
COMMENT ON COLUMN ember_schema.federation_inventory_share.share_scope IS 'Scope: ALL_PARTNERS or SPECIFIC.';

COMMENT ON TABLE ember_schema.federation_inventory_share_target IS 'Specific partners an inventory share is targeted to.';
COMMENT ON COLUMN ember_schema.federation_inventory_share_target.share_id IS 'References the inventory share.';
COMMENT ON COLUMN ember_schema.federation_inventory_share_target.partner_id IS 'References the federation partner.';

COMMENT ON TABLE ember_schema.federation_lending_request IS 'Requests to borrow inventory items from a partner station.';
COMMENT ON COLUMN ember_schema.federation_lending_request.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.federation_lending_request.requesting_station_id IS 'Station requesting to borrow.';
COMMENT ON COLUMN ember_schema.federation_lending_request.owning_station_id IS 'Station that owns the items.';
COMMENT ON COLUMN ember_schema.federation_lending_request.status IS 'Request status: REQUESTED, APPROVED, HANDED_OVER, RETURNED, REJECTED, CANCELLED.';
COMMENT ON COLUMN ember_schema.federation_lending_request.requested_date_from IS 'Requested start date for the lending period.';
COMMENT ON COLUMN ember_schema.federation_lending_request.requested_date_to IS 'Requested end date for the lending period.';
COMMENT ON COLUMN ember_schema.federation_lending_request.created_by IS 'Member who created the request.';
COMMENT ON COLUMN ember_schema.federation_lending_request.created_at IS 'When the request was created.';
COMMENT ON COLUMN ember_schema.federation_lending_request.updated_at IS 'When the request was last updated.';

COMMENT ON TABLE ember_schema.federation_lending_request_item IS 'Individual items requested in a lending request.';
COMMENT ON COLUMN ember_schema.federation_lending_request_item.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.federation_lending_request_item.request_id IS 'References the lending request.';
COMMENT ON COLUMN ember_schema.federation_lending_request_item.inventory_id IS 'References the inventory (for requesting any item from it).';
COMMENT ON COLUMN ember_schema.federation_lending_request_item.item_id IS 'References a specific item.';
COMMENT ON COLUMN ember_schema.federation_lending_request_item.quantity IS 'Number of items requested.';
COMMENT ON COLUMN ember_schema.federation_lending_request_item.assigned_item_id IS 'Specific item assigned by the owning station to fulfill the request.';

COMMENT ON TABLE ember_schema.federation_lending_message IS 'Chat messages in a lending request conversation.';
COMMENT ON COLUMN ember_schema.federation_lending_message.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.federation_lending_message.request_id IS 'References the lending request.';
COMMENT ON COLUMN ember_schema.federation_lending_message.sender_station_id IS 'Station that sent the message.';
COMMENT ON COLUMN ember_schema.federation_lending_message.sender_member_id IS 'Member who sent the message. NULL for system messages.';
COMMENT ON COLUMN ember_schema.federation_lending_message.message IS 'Message text.';
COMMENT ON COLUMN ember_schema.federation_lending_message.is_system IS 'Whether this is an auto-generated system/status message.';
COMMENT ON COLUMN ember_schema.federation_lending_message.created_at IS 'When the message was sent.';

COMMENT ON TABLE ember_schema.federation_inventory_block IS 'Date ranges during which inventory items are blocked from lending.';
COMMENT ON COLUMN ember_schema.federation_inventory_block.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.federation_inventory_block.station_id IS 'References the station.';
COMMENT ON COLUMN ember_schema.federation_inventory_block.inventory_id IS 'References the inventory (block all items). NULL for item-level blocks.';
COMMENT ON COLUMN ember_schema.federation_inventory_block.item_id IS 'References a specific item. NULL for inventory-level blocks.';
COMMENT ON COLUMN ember_schema.federation_inventory_block.block_from IS 'Start date of the block period.';
COMMENT ON COLUMN ember_schema.federation_inventory_block.block_to IS 'End date of the block period.';
COMMENT ON COLUMN ember_schema.federation_inventory_block.reason IS 'Reason for the block.';

COMMENT ON TABLE ember_schema.federation_change_log IS 'Change log for federation sync polling (tracks what changed since last sync).';
COMMENT ON COLUMN ember_schema.federation_change_log.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.federation_change_log.station_id IS 'References the station.';
COMMENT ON COLUMN ember_schema.federation_change_log.content_type IS 'Type of content that changed (e.g. KB, QUIZ, PROTOCOL).';
COMMENT ON COLUMN ember_schema.federation_change_log.content_id IS 'ID of the changed content.';
COMMENT ON COLUMN ember_schema.federation_change_log.change_type IS 'Type of change: CREATED, UPDATED, DELETED.';
COMMENT ON COLUMN ember_schema.federation_change_log.changed_at IS 'When the change occurred.';

COMMENT ON TABLE ember_schema.federation_invite_token IS 'Pre-generated federation invite tokens for station discovery.';
COMMENT ON COLUMN ember_schema.federation_invite_token.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.federation_invite_token.station_id IS 'References the station that generated the token.';
COMMENT ON COLUMN ember_schema.federation_invite_token.token IS 'Unique invite token string.';
COMMENT ON COLUMN ember_schema.federation_invite_token.created_at IS 'When the token was generated.';

COMMENT ON TABLE ember_schema.federation_member_name_cache IS 'Cached display names for remote federation members.';
COMMENT ON COLUMN ember_schema.federation_member_name_cache.partner_id IS 'References the federation partner.';
COMMENT ON COLUMN ember_schema.federation_member_name_cache.remote_member_id IS 'Member ID on the remote station (as text).';
COMMENT ON COLUMN ember_schema.federation_member_name_cache.display_name IS 'Cached display name.';
COMMENT ON COLUMN ember_schema.federation_member_name_cache.cached_at IS 'When the name was last cached.';

-- ============================================================
-- Role Hierarchy
-- ============================================================

COMMENT ON TABLE ember_schema.role_hierarchy IS 'Role inclusion hierarchy. A parent role transitively grants all child roles.';
COMMENT ON COLUMN ember_schema.role_hierarchy.parent_role_id IS 'The including role (e.g. MANAGER).';
COMMENT ON COLUMN ember_schema.role_hierarchy.child_role_id IS 'The included role (e.g. TEAM).';

-- ============================================================
-- API Request Log
-- ============================================================

COMMENT ON TABLE ember_schema.api_request_log IS 'HTTP API request log for monitoring and diagnostics.';
COMMENT ON COLUMN ember_schema.api_request_log.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.api_request_log.method IS 'HTTP method (GET, POST, etc.).';
COMMENT ON COLUMN ember_schema.api_request_log.path IS 'Request path.';
COMMENT ON COLUMN ember_schema.api_request_log.status_code IS 'HTTP response status code.';
COMMENT ON COLUMN ember_schema.api_request_log.duration_ms IS 'Request processing time in milliseconds.';
COMMENT ON COLUMN ember_schema.api_request_log.created_at IS 'When the request was made.';

-- ============================================================
-- Event Layouts
-- ============================================================

COMMENT ON TABLE ember_schema.event_layout IS 'Reusable event field layouts for batch event creation.';
COMMENT ON COLUMN ember_schema.event_layout.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.event_layout.station_id IS 'References the station.';
COMMENT ON COLUMN ember_schema.event_layout.name IS 'Layout name, unique within the station.';

COMMENT ON TABLE ember_schema.event_layout_field IS 'Fields within an event layout template.';
COMMENT ON COLUMN ember_schema.event_layout_field.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.event_layout_field.layout_id IS 'References the event layout.';
COMMENT ON COLUMN ember_schema.event_layout_field.name IS 'Field name/label.';
COMMENT ON COLUMN ember_schema.event_layout_field.field_type IS 'Data type of the field.';
COMMENT ON COLUMN ember_schema.event_layout_field.config IS 'Type-specific configuration as JSONB.';
COMMENT ON COLUMN ember_schema.event_layout_field.position IS 'Display order position.';
COMMENT ON COLUMN ember_schema.event_layout_field.overview IS 'Whether to show this field in the event overview.';
COMMENT ON COLUMN ember_schema.event_layout_field.attendance_field_id IS 'References an attendance template field to auto-populate from.';

-- ============================================================
-- Event Templates
-- ============================================================

COMMENT ON TABLE ember_schema.event_template IS 'Reusable event templates with pre-configured settings.';
COMMENT ON COLUMN ember_schema.event_template.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.event_template.station_id IS 'References the station.';
COMMENT ON COLUMN ember_schema.event_template.name IS 'Template name, unique within the station.';
COMMENT ON COLUMN ember_schema.event_template.title IS 'Default event title. NULL to not preset.';
COMMENT ON COLUMN ember_schema.event_template.description IS 'Default event description.';
COMMENT ON COLUMN ember_schema.event_template.category_id IS 'Default event category.';
COMMENT ON COLUMN ember_schema.event_template.event_type IS 'Default event type.';
COMMENT ON COLUMN ember_schema.event_template.requires_registration IS 'Default registration requirement.';
COMMENT ON COLUMN ember_schema.event_template.registration_deadline_offset IS 'Default registration deadline as interval before event start.';
COMMENT ON COLUMN ember_schema.event_template.requires_confirmation IS 'Default confirmation requirement.';
COMMENT ON COLUMN ember_schema.event_template.restriction_mode IS 'Default restriction mode.';
COMMENT ON COLUMN ember_schema.event_template.attendance_template_id IS 'Default attendance template.';
COMMENT ON COLUMN ember_schema.event_template.registration_limit IS 'Default registration limit.';

COMMENT ON TABLE ember_schema.event_template_field IS 'Fields within an event template.';
COMMENT ON COLUMN ember_schema.event_template_field.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.event_template_field.template_id IS 'References the event template.';
COMMENT ON COLUMN ember_schema.event_template_field.name IS 'Field name/label.';
COMMENT ON COLUMN ember_schema.event_template_field.field_type IS 'Data type of the field.';
COMMENT ON COLUMN ember_schema.event_template_field.config IS 'Type-specific configuration as JSONB.';
COMMENT ON COLUMN ember_schema.event_template_field.position IS 'Display order position.';
COMMENT ON COLUMN ember_schema.event_template_field.overview IS 'Whether to show this field in the event overview.';
COMMENT ON COLUMN ember_schema.event_template_field.public IS 'Whether this field is visible on the public calendar.';
COMMENT ON COLUMN ember_schema.event_template_field.attendance_field_id IS 'References an attendance template field to auto-populate from.';

COMMENT ON TABLE ember_schema.event_template_restriction IS 'Role restrictions for event templates.';
COMMENT ON COLUMN ember_schema.event_template_restriction.template_id IS 'References the event template.';
COMMENT ON COLUMN ember_schema.event_template_restriction.role_id IS 'Required role.';

-- ============================================================
-- Event Federation
-- ============================================================

COMMENT ON TABLE ember_schema.event_federation_share IS 'Events shared with federation partners.';
COMMENT ON COLUMN ember_schema.event_federation_share.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.event_federation_share.event_id IS 'References the station event.';
COMMENT ON COLUMN ember_schema.event_federation_share.scope IS 'Scope: ALL_PARTNERS or SPECIFIC.';

COMMENT ON TABLE ember_schema.event_federation_share_target IS 'Specific partners an event share is targeted to.';
COMMENT ON COLUMN ember_schema.event_federation_share_target.share_id IS 'References the event share.';
COMMENT ON COLUMN ember_schema.event_federation_share_target.partner_id IS 'References the federation partner.';

COMMENT ON TABLE ember_schema.event_federation_registration IS 'Registrations from federated partner members for shared events.';
COMMENT ON COLUMN ember_schema.event_federation_registration.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.event_federation_registration.event_id IS 'References the station event.';
COMMENT ON COLUMN ember_schema.event_federation_registration.partner_id IS 'References the federation partner.';
COMMENT ON COLUMN ember_schema.event_federation_registration.remote_member_id IS 'Member ID on the remote station (as text).';
COMMENT ON COLUMN ember_schema.event_federation_registration.event_date IS 'Specific date of the event occurrence.';
COMMENT ON COLUMN ember_schema.event_federation_registration.status IS 'Registration status: PENDING, CONFIRMED, CANCELLED.';
COMMENT ON COLUMN ember_schema.event_federation_registration.created_at IS 'When the registration was created.';

-- ============================================================
-- Comments
-- ============================================================

COMMENT ON TABLE ember_schema.event_comment IS 'Comments on station events (threaded via parent_id).';
COMMENT ON COLUMN ember_schema.event_comment.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.event_comment.event_id IS 'References the station event.';
COMMENT ON COLUMN ember_schema.event_comment.parent_id IS 'Parent comment for threading. NULL for top-level comments.';
COMMENT ON COLUMN ember_schema.event_comment.author_id IS 'Member who wrote the comment.';
COMMENT ON COLUMN ember_schema.event_comment.content IS 'Comment text.';
COMMENT ON COLUMN ember_schema.event_comment.deleted IS 'Soft-delete flag. Content is hidden but threading is preserved.';
COMMENT ON COLUMN ember_schema.event_comment.created_at IS 'When the comment was created.';
COMMENT ON COLUMN ember_schema.event_comment.updated_at IS 'When the comment was last edited.';

-- ============================================================
-- Entity Notes
-- ============================================================

COMMENT ON TABLE ember_schema.entity_note IS 'Shared markdown notes attached to any entity (events, tickets, etc.).';
COMMENT ON COLUMN ember_schema.entity_note.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.entity_note.entity_type IS 'Type of the entity (e.g. EVENT, NEWS).';
COMMENT ON COLUMN ember_schema.entity_note.entity_id IS 'ID of the entity.';
COMMENT ON COLUMN ember_schema.entity_note.station_id IS 'References the station.';
COMMENT ON COLUMN ember_schema.entity_note.content IS 'Current note content (markdown).';
COMMENT ON COLUMN ember_schema.entity_note.updated_by IS 'Member who last updated the note.';
COMMENT ON COLUMN ember_schema.entity_note.updated_at IS 'When the note was last updated.';

COMMENT ON TABLE ember_schema.entity_note_version IS 'Version history for entity notes (diff-based).';
COMMENT ON COLUMN ember_schema.entity_note_version.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.entity_note_version.note_id IS 'References the entity note.';
COMMENT ON COLUMN ember_schema.entity_note_version.diff_patch IS 'Unified diff patch.';
COMMENT ON COLUMN ember_schema.entity_note_version.author_id IS 'Member who created this version.';
COMMENT ON COLUMN ember_schema.entity_note_version.created_at IS 'When this version was created.';

-- ============================================================
-- User Feed Tokens
-- ============================================================

COMMENT ON TABLE ember_schema.user_feed_token IS 'Token-based authentication for iCal/RSS/Atom feeds (one token per member).';
COMMENT ON COLUMN ember_schema.user_feed_token.member_id IS 'References the station member.';
COMMENT ON COLUMN ember_schema.user_feed_token.token IS 'Unique feed access token (32-byte SecureRandom, URL-safe Base64).';
COMMENT ON COLUMN ember_schema.user_feed_token.created_at IS 'When the token was generated.';
COMMENT ON COLUMN ember_schema.user_feed_token.ical_polled_at IS 'When the iCal feed was last polled.';
COMMENT ON COLUMN ember_schema.user_feed_token.notification_polled_at IS 'When the notification RSS/Atom feed was last polled.';

-- ============================================================
-- Problem Reports
-- ============================================================

COMMENT ON TABLE ember_schema.problem_report IS 'User-submitted problem/bug reports from the frontend.';
COMMENT ON COLUMN ember_schema.problem_report.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.problem_report.station_id IS 'References the station.';
COMMENT ON COLUMN ember_schema.problem_report.member_id IS 'References the reporting member. NULL if member was deleted.';
COMMENT ON COLUMN ember_schema.problem_report.reporter_name IS 'Display name of the reporter at submission time.';
COMMENT ON COLUMN ember_schema.problem_report.message IS 'Problem description.';
COMMENT ON COLUMN ember_schema.problem_report.page_url IS 'URL of the page where the problem occurred.';
COMMENT ON COLUMN ember_schema.problem_report.user_roles IS 'Roles of the reporting user at submission time.';
COMMENT ON COLUMN ember_schema.problem_report.recent_requests IS 'Recent API requests as JSONB for debugging context.';
COMMENT ON COLUMN ember_schema.problem_report.browser_info IS 'Browser/user agent info.';
COMMENT ON COLUMN ember_schema.problem_report.screen_size IS 'Screen dimensions at submission time.';
COMMENT ON COLUMN ember_schema.problem_report.acknowledged IS 'Whether an admin has acknowledged the report.';
COMMENT ON COLUMN ember_schema.problem_report.created_at IS 'When the report was submitted.';

-- ============================================================
-- Boards
-- ============================================================

COMMENT ON TABLE ember_schema.board IS 'Kanban-style task boards within a station.';
COMMENT ON COLUMN ember_schema.board.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.board.station_id IS 'References the station.';
COMMENT ON COLUMN ember_schema.board.name IS 'Board name.';
COMMENT ON COLUMN ember_schema.board.description IS 'Board description.';
COMMENT ON COLUMN ember_schema.board.short_key IS 'Short key prefix for ticket numbers (e.g. DEV), unique within station.';
COMMENT ON COLUMN ember_schema.board.hide_done_after_days IS 'Days after which completed tickets are hidden from the board view.';
COMMENT ON COLUMN ember_schema.board.ticket_counter IS 'Auto-incrementing counter for generating ticket numbers.';
COMMENT ON COLUMN ember_schema.board.backlog_lane_id IS 'References the default backlog lane. NULL if not configured.';
COMMENT ON COLUMN ember_schema.board.created_at IS 'When the board was created.';

COMMENT ON TABLE ember_schema.board_lane IS 'Lanes (columns) on a kanban board.';
COMMENT ON COLUMN ember_schema.board_lane.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.board_lane.board_id IS 'References the board.';
COMMENT ON COLUMN ember_schema.board_lane.name IS 'Lane name (e.g. To Do, In Progress, Done).';
COMMENT ON COLUMN ember_schema.board_lane.color IS 'Lane header color (CSS color string).';
COMMENT ON COLUMN ember_schema.board_lane.position IS 'Display order position (left to right).';

COMMENT ON TABLE ember_schema.board_field IS 'Custom fields defined for a board (applied to all tickets).';
COMMENT ON COLUMN ember_schema.board_field.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.board_field.board_id IS 'References the board.';
COMMENT ON COLUMN ember_schema.board_field.name IS 'Field name/label.';
COMMENT ON COLUMN ember_schema.board_field.field_type IS 'Data type of the field.';
COMMENT ON COLUMN ember_schema.board_field.config IS 'Type-specific configuration as JSONB.';
COMMENT ON COLUMN ember_schema.board_field.position IS 'Display order position.';

COMMENT ON TABLE ember_schema.board_view_access IS 'View access restrictions for a board (empty = visible to all station members).';
COMMENT ON COLUMN ember_schema.board_view_access.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.board_view_access.board_id IS 'References the board.';
COMMENT ON COLUMN ember_schema.board_view_access.role_id IS 'Required role for viewing.';
COMMENT ON COLUMN ember_schema.board_view_access.group_id IS 'Required group for viewing.';
COMMENT ON COLUMN ember_schema.board_view_access.tag_id IS 'Required tag for viewing.';

COMMENT ON TABLE ember_schema.board_edit_access IS 'Edit access restrictions for a board (subset of viewers who can modify).';
COMMENT ON COLUMN ember_schema.board_edit_access.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.board_edit_access.board_id IS 'References the board.';
COMMENT ON COLUMN ember_schema.board_edit_access.role_id IS 'Required role for editing.';
COMMENT ON COLUMN ember_schema.board_edit_access.group_id IS 'Required group for editing.';
COMMENT ON COLUMN ember_schema.board_edit_access.tag_id IS 'Required tag for editing.';

COMMENT ON TABLE ember_schema.board_ticket IS 'Tickets (tasks/cards) on a kanban board.';
COMMENT ON COLUMN ember_schema.board_ticket.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.board_ticket.board_id IS 'References the board.';
COMMENT ON COLUMN ember_schema.board_ticket.lane_id IS 'References the current lane.';
COMMENT ON COLUMN ember_schema.board_ticket.ticket_number IS 'Sequential ticket number within the board.';
COMMENT ON COLUMN ember_schema.board_ticket.title IS 'Ticket title.';
COMMENT ON COLUMN ember_schema.board_ticket.description IS 'Ticket description (markdown).';
COMMENT ON COLUMN ember_schema.board_ticket.assigned_member_id IS 'Member assigned to the ticket. NULL if unassigned.';
COMMENT ON COLUMN ember_schema.board_ticket.priority IS 'Priority level: LOW, MEDIUM, HIGH, URGENT.';
COMMENT ON COLUMN ember_schema.board_ticket.due_date IS 'Due date. NULL if no deadline.';
COMMENT ON COLUMN ember_schema.board_ticket.position IS 'Display order position within the lane.';
COMMENT ON COLUMN ember_schema.board_ticket.created_by IS 'Member who created the ticket.';
COMMENT ON COLUMN ember_schema.board_ticket.created_at IS 'When the ticket was created.';
COMMENT ON COLUMN ember_schema.board_ticket.updated_at IS 'When the ticket was last updated.';
COMMENT ON COLUMN ember_schema.board_ticket.lane_entered_at IS 'When the ticket entered the current lane (for cycle time tracking).';
COMMENT ON COLUMN ember_schema.board_ticket.search_vector IS 'Auto-generated tsvector for full-text search on title and description.';

COMMENT ON TABLE ember_schema.board_ticket_field_value IS 'Custom field values for board tickets.';
COMMENT ON COLUMN ember_schema.board_ticket_field_value.ticket_id IS 'References the ticket.';
COMMENT ON COLUMN ember_schema.board_ticket_field_value.field_id IS 'References the board field.';
COMMENT ON COLUMN ember_schema.board_ticket_field_value.value IS 'Field value as JSONB.';

COMMENT ON TABLE ember_schema.board_ticket_link IS 'Cross-board ticket links (relationships between tickets within the same station).';
COMMENT ON COLUMN ember_schema.board_ticket_link.ticket_id IS 'Source ticket.';
COMMENT ON COLUMN ember_schema.board_ticket_link.linked_ticket_id IS 'Linked target ticket.';
COMMENT ON COLUMN ember_schema.board_ticket_link.link_type IS 'Link type: RELATES_TO, BLOCKS, BLOCKED_BY, DUPLICATES.';

COMMENT ON TABLE ember_schema.board_ticket_checklist_item IS 'Checklist items within a ticket.';
COMMENT ON COLUMN ember_schema.board_ticket_checklist_item.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.board_ticket_checklist_item.ticket_id IS 'References the ticket.';
COMMENT ON COLUMN ember_schema.board_ticket_checklist_item.title IS 'Checklist item text.';
COMMENT ON COLUMN ember_schema.board_ticket_checklist_item.checked IS 'Whether the item is completed.';
COMMENT ON COLUMN ember_schema.board_ticket_checklist_item.position IS 'Display order position.';

COMMENT ON TABLE ember_schema.board_ticket_transition IS 'Lane transition history for tickets (moved from lane A to lane B).';
COMMENT ON COLUMN ember_schema.board_ticket_transition.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.board_ticket_transition.ticket_id IS 'References the ticket.';
COMMENT ON COLUMN ember_schema.board_ticket_transition.from_lane_id IS 'Previous lane. NULL if ticket was just created.';
COMMENT ON COLUMN ember_schema.board_ticket_transition.to_lane_id IS 'New lane. NULL if ticket was archived.';
COMMENT ON COLUMN ember_schema.board_ticket_transition.moved_by IS 'Member who moved the ticket.';
COMMENT ON COLUMN ember_schema.board_ticket_transition.moved_at IS 'When the transition occurred.';

COMMENT ON TABLE ember_schema.board_ticket_comment IS 'Comments on board tickets (threaded via parent_id).';
COMMENT ON COLUMN ember_schema.board_ticket_comment.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.board_ticket_comment.ticket_id IS 'References the ticket.';
COMMENT ON COLUMN ember_schema.board_ticket_comment.parent_id IS 'Parent comment for threading. NULL for top-level comments.';
COMMENT ON COLUMN ember_schema.board_ticket_comment.author_id IS 'Member who wrote the comment.';
COMMENT ON COLUMN ember_schema.board_ticket_comment.content IS 'Comment text.';
COMMENT ON COLUMN ember_schema.board_ticket_comment.deleted IS 'Soft-delete flag.';
COMMENT ON COLUMN ember_schema.board_ticket_comment.created_at IS 'When the comment was created.';
COMMENT ON COLUMN ember_schema.board_ticket_comment.updated_at IS 'When the comment was last edited.';

COMMENT ON TABLE ember_schema.board_ticket_watcher IS 'Members watching a ticket (receive notifications on changes).';
COMMENT ON COLUMN ember_schema.board_ticket_watcher.ticket_id IS 'References the ticket.';
COMMENT ON COLUMN ember_schema.board_ticket_watcher.member_id IS 'References the watching member.';

COMMENT ON TABLE ember_schema.board_ticket_weblink IS 'Web links attached to a ticket.';
COMMENT ON COLUMN ember_schema.board_ticket_weblink.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.board_ticket_weblink.ticket_id IS 'References the ticket.';
COMMENT ON COLUMN ember_schema.board_ticket_weblink.url IS 'Link URL.';
COMMENT ON COLUMN ember_schema.board_ticket_weblink.title IS 'Link display title.';
COMMENT ON COLUMN ember_schema.board_ticket_weblink.position IS 'Display order position.';

COMMENT ON TABLE ember_schema.board_ticket_attachment IS 'File attachments on board tickets.';
COMMENT ON COLUMN ember_schema.board_ticket_attachment.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.board_ticket_attachment.ticket_id IS 'References the ticket.';
COMMENT ON COLUMN ember_schema.board_ticket_attachment.filename IS 'Stored filename (may be hashed/unique).';
COMMENT ON COLUMN ember_schema.board_ticket_attachment.original_name IS 'Original upload filename.';
COMMENT ON COLUMN ember_schema.board_ticket_attachment.content_type IS 'MIME type of the attachment.';
COMMENT ON COLUMN ember_schema.board_ticket_attachment.size_bytes IS 'File size in bytes.';
COMMENT ON COLUMN ember_schema.board_ticket_attachment.uploaded_by IS 'Member who uploaded the file.';
COMMENT ON COLUMN ember_schema.board_ticket_attachment.created_at IS 'When the attachment was uploaded.';

COMMENT ON TABLE ember_schema.board_label IS 'Labels for categorizing tickets on a board.';
COMMENT ON COLUMN ember_schema.board_label.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.board_label.board_id IS 'References the board.';
COMMENT ON COLUMN ember_schema.board_label.name IS 'Label name (case-insensitive unique within board).';
COMMENT ON COLUMN ember_schema.board_label.color IS 'Label color (CSS color string).';

COMMENT ON TABLE ember_schema.board_ticket_label IS 'Label assignments to tickets (many-to-many).';
COMMENT ON COLUMN ember_schema.board_ticket_label.ticket_id IS 'References the ticket.';
COMMENT ON COLUMN ember_schema.board_ticket_label.label_id IS 'References the label.';

COMMENT ON TABLE ember_schema.board_ticket_kb_link IS 'Links from board tickets to knowledge base files.';
COMMENT ON COLUMN ember_schema.board_ticket_kb_link.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.board_ticket_kb_link.ticket_id IS 'References the ticket.';
COMMENT ON COLUMN ember_schema.board_ticket_kb_link.kb_file_id IS 'References the KB file.';

COMMENT ON TABLE ember_schema.board_ticket_history IS 'Audit log of ticket changes (priority, labels, assignments, etc.).';
COMMENT ON COLUMN ember_schema.board_ticket_history.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.board_ticket_history.ticket_id IS 'References the ticket.';
COMMENT ON COLUMN ember_schema.board_ticket_history.action IS 'Action type (e.g. PRIORITY_CHANGED, LABEL_ADDED, ASSIGNED).';
COMMENT ON COLUMN ember_schema.board_ticket_history.detail IS 'Human-readable detail of the change.';
COMMENT ON COLUMN ember_schema.board_ticket_history.actor_member_id IS 'Member who performed the action.';
COMMENT ON COLUMN ember_schema.board_ticket_history.created_at IS 'When the action occurred.';

-- ============================================================
-- Board Federation
-- ============================================================

COMMENT ON TABLE ember_schema.federation_board_share IS 'Boards shared with federation partners.';
COMMENT ON COLUMN ember_schema.federation_board_share.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.federation_board_share.board_id IS 'References the board being shared.';

COMMENT ON TABLE ember_schema.federation_board_share_target IS 'Partners a board is shared with, including their access mode.';
COMMENT ON COLUMN ember_schema.federation_board_share_target.share_id IS 'References the board share.';
COMMENT ON COLUMN ember_schema.federation_board_share_target.partner_id IS 'References the federation partner.';
COMMENT ON COLUMN ember_schema.federation_board_share_target.share_mode IS 'Access mode: READ_ONLY or FULL.';

COMMENT ON TABLE ember_schema.federation_board_edit_role IS 'Role-based edit restrictions for federated boards on the owning station. Only applies to FULL mode. Empty = all federated members can edit.';
COMMENT ON COLUMN ember_schema.federation_board_edit_role.board_id IS 'References the board.';
COMMENT ON COLUMN ember_schema.federation_board_edit_role.role_id IS 'Required role for editing.';

COMMENT ON TABLE ember_schema.board_ticket_federated_assignee IS 'Remote member assigned to a ticket on the owning station.';
COMMENT ON COLUMN ember_schema.board_ticket_federated_assignee.ticket_id IS 'References the ticket.';
COMMENT ON COLUMN ember_schema.board_ticket_federated_assignee.partner_id IS 'References the federation partner.';
COMMENT ON COLUMN ember_schema.board_ticket_federated_assignee.remote_member_id IS 'Member ID on the remote station (as text).';

COMMENT ON TABLE ember_schema.board_ticket_federated_comment_author IS 'Tracks authorship of comments created by remote federation members.';
COMMENT ON COLUMN ember_schema.board_ticket_federated_comment_author.comment_id IS 'References the ticket comment.';
COMMENT ON COLUMN ember_schema.board_ticket_federated_comment_author.partner_id IS 'References the federation partner.';
COMMENT ON COLUMN ember_schema.board_ticket_federated_comment_author.remote_member_id IS 'Member ID on the remote station (as text).';

COMMENT ON TABLE ember_schema.board_ticket_federated_creator IS 'Tracks tickets created by remote federation members.';
COMMENT ON COLUMN ember_schema.board_ticket_federated_creator.ticket_id IS 'References the ticket.';
COMMENT ON COLUMN ember_schema.board_ticket_federated_creator.partner_id IS 'References the federation partner.';
COMMENT ON COLUMN ember_schema.board_ticket_federated_creator.remote_member_id IS 'Member ID on the remote station (as text).';

COMMENT ON TABLE ember_schema.board_ticket_federated_watcher IS 'Remote federation members watching a ticket on the owning station.';
COMMENT ON COLUMN ember_schema.board_ticket_federated_watcher.ticket_id IS 'References the ticket.';
COMMENT ON COLUMN ember_schema.board_ticket_federated_watcher.partner_id IS 'References the federation partner.';
COMMENT ON COLUMN ember_schema.board_ticket_federated_watcher.remote_member_id IS 'Member ID on the remote station (as text).';

COMMENT ON TABLE ember_schema.federation_board_local_view_override IS 'Local access overrides for federated boards. The partner station restricts which of its own members can view a remote board.';
COMMENT ON COLUMN ember_schema.federation_board_local_view_override.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.federation_board_local_view_override.partner_id IS 'References the federation partner.';
COMMENT ON COLUMN ember_schema.federation_board_local_view_override.remote_board_id IS 'Board ID on the remote station.';
COMMENT ON COLUMN ember_schema.federation_board_local_view_override.role_id IS 'Required role for viewing.';
COMMENT ON COLUMN ember_schema.federation_board_local_view_override.group_id IS 'Required group for viewing.';
COMMENT ON COLUMN ember_schema.federation_board_local_view_override.tag_id IS 'Required tag for viewing.';

COMMENT ON TABLE ember_schema.federation_board_local_edit_override IS 'Local edit access overrides for federated boards. The partner station restricts which of its own members can edit a remote board.';
COMMENT ON COLUMN ember_schema.federation_board_local_edit_override.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.federation_board_local_edit_override.partner_id IS 'References the federation partner.';
COMMENT ON COLUMN ember_schema.federation_board_local_edit_override.remote_board_id IS 'Board ID on the remote station.';
COMMENT ON COLUMN ember_schema.federation_board_local_edit_override.role_id IS 'Required role for editing.';
COMMENT ON COLUMN ember_schema.federation_board_local_edit_override.group_id IS 'Required group for editing.';
COMMENT ON COLUMN ember_schema.federation_board_local_edit_override.tag_id IS 'Required tag for editing.';

COMMENT ON TABLE ember_schema.federation_board_bookmark IS 'User bookmarks for federated boards (appear in sidebar for quick access).';
COMMENT ON COLUMN ember_schema.federation_board_bookmark.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.federation_board_bookmark.member_id IS 'References the station member.';
COMMENT ON COLUMN ember_schema.federation_board_bookmark.partner_id IS 'References the federation partner.';
COMMENT ON COLUMN ember_schema.federation_board_bookmark.remote_board_id IS 'Board ID on the remote station.';
COMMENT ON COLUMN ember_schema.federation_board_bookmark.remote_board_name IS 'Cached board name from the remote station.';
COMMENT ON COLUMN ember_schema.federation_board_bookmark.remote_board_short_key IS 'Cached short key from the remote station.';
COMMENT ON COLUMN ember_schema.federation_board_bookmark.share_mode IS 'Access mode: READ_ONLY or FULL.';
COMMENT ON COLUMN ember_schema.federation_board_bookmark.created_at IS 'When the bookmark was created.';

-- ============================================================
-- Phase 10: Member UUIDs for federation identity
-- ============================================================

-- 10.1 Add UUID column to station_member
ALTER TABLE ember_schema.station_member ADD COLUMN uid UUID NOT NULL DEFAULT gen_random_uuid();
CREATE UNIQUE INDEX idx_station_member_uid ON ember_schema.station_member (station_id, uid);

-- 10.3 Migrate remote_member_id columns from TEXT to native UUID
ALTER TABLE ember_schema.board_ticket_federated_assignee
    ALTER COLUMN remote_member_id TYPE UUID USING remote_member_id::uuid;

ALTER TABLE ember_schema.board_ticket_federated_creator
    ALTER COLUMN remote_member_id TYPE UUID USING remote_member_id::uuid;

ALTER TABLE ember_schema.board_ticket_federated_watcher
    ALTER COLUMN remote_member_id TYPE UUID USING remote_member_id::uuid;

ALTER TABLE ember_schema.board_ticket_federated_comment_author
    ALTER COLUMN remote_member_id TYPE UUID USING remote_member_id::uuid;

ALTER TABLE ember_schema.event_federation_registration
    ALTER COLUMN remote_member_id TYPE UUID USING remote_member_id::uuid;

ALTER TABLE ember_schema.federation_member_name_cache
    ALTER COLUMN remote_member_id TYPE UUID USING remote_member_id::uuid;

-- Migrate federated_member_id columns from INTEGER to UUID
ALTER TABLE ember_schema.board_ticket_transition
    ALTER COLUMN federated_member_id TYPE UUID USING NULL;

ALTER TABLE ember_schema.board_ticket_history
    ALTER COLUMN federated_member_id TYPE UUID USING NULL;

COMMENT ON COLUMN ember_schema.station_member.uid IS 'Stable UUID for federation identity. Unique within a station.';
COMMENT ON COLUMN ember_schema.board_ticket_transition.federated_member_id IS 'Member UUID on the federated station that moved the ticket.';
COMMENT ON COLUMN ember_schema.board_ticket_history.federated_member_id IS 'Member UUID on the federated station that performed the action.';
COMMENT ON COLUMN ember_schema.federation_member_name_cache.remote_member_id IS 'Member UUID on the remote station.';
COMMENT ON COLUMN ember_schema.event_federation_registration.remote_member_id IS 'Member UUID on the remote station.';
COMMENT ON COLUMN ember_schema.board_ticket_federated_assignee.remote_member_id IS 'Member UUID on the remote station.';
COMMENT ON COLUMN ember_schema.board_ticket_federated_comment_author.remote_member_id IS 'Member UUID on the remote station.';
COMMENT ON COLUMN ember_schema.board_ticket_federated_creator.remote_member_id IS 'Member UUID on the remote station.';
COMMENT ON COLUMN ember_schema.board_ticket_federated_watcher.remote_member_id IS 'Member UUID on the remote station.';

-- Federated event comment author tracking
CREATE TABLE ember_schema.event_comment_federated_author (
    comment_id INTEGER NOT NULL REFERENCES ember_schema.event_comment(id) ON DELETE CASCADE PRIMARY KEY,
    partner_id INTEGER NOT NULL REFERENCES ember_schema.federation_partner(id) ON DELETE CASCADE,
    remote_member_id UUID NOT NULL
);

COMMENT ON TABLE ember_schema.event_comment_federated_author IS 'Maps event comments from federated users to their remote identity.';
COMMENT ON COLUMN ember_schema.event_comment_federated_author.comment_id IS 'References the local event comment.';
COMMENT ON COLUMN ember_schema.event_comment_federated_author.partner_id IS 'References the federation partner.';
COMMENT ON COLUMN ember_schema.event_comment_federated_author.remote_member_id IS 'Member UUID on the remote station.';

-- News federation sharing
CREATE TABLE ember_schema.news_federation_share (
    id              SERIAL PRIMARY KEY,
    news_id         INTEGER NOT NULL REFERENCES ember_schema.news(id) ON DELETE CASCADE,
    scope           TEXT NOT NULL DEFAULT 'ALL_PARTNERS',
    visibility_role TEXT NOT NULL DEFAULT 'MEMBER',
    UNIQUE(news_id)
);

CREATE TABLE ember_schema.news_federation_share_target (
    share_id   INTEGER NOT NULL REFERENCES ember_schema.news_federation_share(id) ON DELETE CASCADE,
    partner_id INTEGER NOT NULL REFERENCES ember_schema.federation_partner(id) ON DELETE CASCADE,
    PRIMARY KEY (share_id, partner_id)
);

-- Federated news comment author tracking
CREATE TABLE ember_schema.news_comment_federated_author (
    comment_id       INTEGER NOT NULL REFERENCES ember_schema.news_comment(id) ON DELETE CASCADE PRIMARY KEY,
    partner_id       INTEGER NOT NULL REFERENCES ember_schema.federation_partner(id) ON DELETE CASCADE,
    remote_member_id UUID NOT NULL
);

COMMENT ON TABLE ember_schema.news_federation_share IS 'Per-post federation sharing config for news.';
COMMENT ON COLUMN ember_schema.news_federation_share.scope IS 'ALL_PARTNERS or SPECIFIC.';
COMMENT ON COLUMN ember_schema.news_federation_share.visibility_role IS 'Minimum role on partner station: MEMBER, TEAM, or MANAGER.';
COMMENT ON TABLE ember_schema.news_comment_federated_author IS 'Maps news comments from federated users to their remote identity.';

-- KB file comments
CREATE TABLE ember_schema.kb_comment (
    id         SERIAL PRIMARY KEY,
    file_id    INTEGER     NOT NULL REFERENCES ember_schema.kb_file(id) ON DELETE CASCADE,
    parent_id  INTEGER              REFERENCES ember_schema.kb_comment(id) ON DELETE SET NULL,
    author_id  INTEGER     NOT NULL REFERENCES ember_schema.station_member(id) ON DELETE CASCADE,
    content    TEXT        NOT NULL,
    deleted    BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ
);

CREATE INDEX idx_kb_comment_file ON ember_schema.kb_comment(file_id);

-- Federated KB comment author tracking
CREATE TABLE ember_schema.kb_comment_federated_author (
    comment_id       INTEGER NOT NULL REFERENCES ember_schema.kb_comment(id) ON DELETE CASCADE PRIMARY KEY,
    partner_id       INTEGER NOT NULL REFERENCES ember_schema.federation_partner(id) ON DELETE CASCADE,
    remote_member_id UUID NOT NULL
);

COMMENT ON TABLE ember_schema.kb_comment IS 'Threaded comments on knowledge base files.';
COMMENT ON TABLE ember_schema.kb_comment_federated_author IS 'Maps KB comments from federated users to their remote identity.';
