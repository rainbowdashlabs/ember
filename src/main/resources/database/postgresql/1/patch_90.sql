-- An appointment can take over from another one: a date of a series edited on its own becomes an
-- appointment of its own, and a series edited from one date on ends there and goes on as a new one.
-- The new appointment says where it came from, and the series keeps the dates it gave away.

ALTER TABLE ember_schema.station_event
    ADD COLUMN continues_event_id INTEGER NULL REFERENCES ember_schema.station_event (id) ON DELETE SET NULL,
    ADD COLUMN continues_from     DATE    NULL,
    ADD COLUMN change_notice_due_at TIMESTAMPTZ NULL,
    ADD CONSTRAINT station_event_continues_names_a_date
        CHECK (continues_event_id IS NULL OR continues_from IS NOT NULL);

CREATE INDEX idx_station_event_continues_event_id
    ON ember_schema.station_event (continues_event_id)
    WHERE continues_event_id IS NOT NULL;

CREATE INDEX idx_station_event_change_notice_due_at
    ON ember_schema.station_event (change_notice_due_at)
    WHERE change_notice_due_at IS NOT NULL;

COMMENT ON COLUMN ember_schema.station_event.continues_event_id IS
    'The appointment this one took over from: the series a date was taken out of, or the series that ended where this one goes on. NULL for an appointment created on its own, or once that appointment is deleted.';
COMMENT ON COLUMN ember_schema.station_event.continues_from IS
    'The date of that appointment this one took over from: the single date taken out, or the first date of the continued series. NULL where continues_event_id never named one.';

COMMENT ON COLUMN ember_schema.station_event.change_notice_due_at IS
    'When an appointment that took over dates of another one became due to tell the people holding a place about it. Cleared once they are told, by the editor or, failing that, a few minutes later on its own. NULL for every other appointment.';

CREATE TABLE ember_schema.station_event_detached_date
(
    event_id    INTEGER     NOT NULL REFERENCES ember_schema.station_event (id) ON DELETE CASCADE,
    event_date  DATE        NOT NULL,
    detached_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (event_id, event_date)
);

COMMENT ON TABLE ember_schema.station_event_detached_date IS
    'A date a series no longer holds because it became an appointment of its own. The series skips it like a date it does not fall on; it is not called off.';
COMMENT ON COLUMN ember_schema.station_event_detached_date.event_id IS 'The series the date was taken out of.';
COMMENT ON COLUMN ember_schema.station_event_detached_date.event_date IS 'The date taken out, on the station''s clock.';
COMMENT ON COLUMN ember_schema.station_event_detached_date.detached_at IS 'When the date was taken out.';

-- Messages between people: conversations, their members and messages, reactions, blocks, and the
-- account's settings for messages.

ALTER TABLE ember_schema.account
    ADD COLUMN messages_guardian_allowed BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN messages_mail             BOOLEAN NOT NULL DEFAULT TRUE;

COMMENT ON COLUMN ember_schema.account.messages_guardian_allowed
    IS 'Whether a guardian turned messages on for this account. Only read while the account has a guardian at any station; an account without one can use messages regardless.';
COMMENT ON COLUMN ember_schema.account.messages_mail
    IS 'Whether the account is sent the daily mail about messages it has not read yet.';

CREATE TABLE IF NOT EXISTS ember_schema.conversation
(
    id               SERIAL PRIMARY KEY,
    uid              UUID        NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    kind             TEXT        NOT NULL CHECK (kind IN ('DIRECT', 'GROUP', 'STATION')),
    name             TEXT,
    owner_account_id INT REFERENCES ember_schema.account (id) ON DELETE SET NULL,
    direct_pair      TEXT UNIQUE,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_activity_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    station_id           INT REFERENCES ember_schema.station (id) ON DELETE CASCADE,
    audience             TEXT CHECK (audience IN ('STATION', 'GROUP', 'TAG')),
    audience_group_id    INT REFERENCES ember_schema.member_group (id) ON DELETE SET NULL,
    audience_tag_id      INT REFERENCES ember_schema.user_tag (id) ON DELETE SET NULL,
    include_guardians    BOOLEAN     NOT NULL DEFAULT FALSE,
    closed_at            TIMESTAMPTZ,
    closed_by_account_id INT REFERENCES ember_schema.account (id) ON DELETE SET NULL,
    CHECK (kind <> 'DIRECT' OR name IS NULL),
    CONSTRAINT conversation_station_shape CHECK (
        CASE kind
            WHEN 'STATION' THEN station_id IS NOT NULL AND audience IS NOT NULL AND name IS NOT NULL AND btrim(name) <> ''
                AND owner_account_id IS NULL AND direct_pair IS NULL
                AND (audience_group_id IS NULL OR audience = 'GROUP')
                AND (audience_tag_id IS NULL OR audience = 'TAG')
                AND (NOT include_guardians OR audience IN ('GROUP', 'TAG'))
                AND (closed_by_account_id IS NULL OR closed_at IS NOT NULL)
            ELSE station_id IS NULL AND audience IS NULL AND audience_group_id IS NULL AND audience_tag_id IS NULL
                AND NOT include_guardians AND closed_at IS NULL AND closed_by_account_id IS NULL
        END)
);

CREATE INDEX IF NOT EXISTS conversation_station_idx
    ON ember_schema.conversation (station_id)
    WHERE station_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS conversation_open_station_wide_idx
    ON ember_schema.conversation (station_id)
    WHERE audience = 'STATION' AND closed_at IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS conversation_open_group_idx
    ON ember_schema.conversation (audience_group_id)
    WHERE audience_group_id IS NOT NULL AND closed_at IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS conversation_open_tag_idx
    ON ember_schema.conversation (audience_tag_id)
    WHERE audience_tag_id IS NOT NULL AND closed_at IS NULL;

COMMENT ON TABLE ember_schema.conversation
    IS 'A conversation between accounts: a direct one between two people, a group, or a station chat. Direct conversations and groups belong to the people in them; a station chat belongs to its station, and its members follow the station, a group or a tag.';
COMMENT ON COLUMN ember_schema.conversation.id
    IS 'Primary key.';
COMMENT ON COLUMN ember_schema.conversation.uid
    IS 'The identity the conversation is addressed by.';
COMMENT ON COLUMN ember_schema.conversation.kind
    IS 'DIRECT for exactly two accounts, GROUP for several, STATION for a station chat whose members follow its audience.';
COMMENT ON COLUMN ember_schema.conversation.name
    IS 'The name a group or station chat was given; NULL for a direct conversation and for a group named after its members. A station chat always has one.';
COMMENT ON COLUMN ember_schema.conversation.owner_account_id
    IS 'The account that owns a group: it alone removes people and renames it. NULL for a direct conversation and once the owner is gone.';
COMMENT ON COLUMN ember_schema.conversation.direct_pair
    IS 'For a direct conversation, the two account ids, the lower first, joined by a colon, which keeps one conversation per pair. Cleared when either account is deleted.';
COMMENT ON COLUMN ember_schema.conversation.created_at
    IS 'When the conversation was started.';
COMMENT ON COLUMN ember_schema.conversation.last_activity_at
    IS 'When anything was last written in it, which orders the list of conversations.';
COMMENT ON COLUMN ember_schema.conversation.station_id
    IS 'The station a station chat belongs to. NULL for direct conversations and groups, which belong to no station.';
COMMENT ON COLUMN ember_schema.conversation.audience
    IS 'Whom a station chat is for: STATION for everybody at the station but trial members, GROUP for the members of a group, TAG for the members with a tag. NULL for every other kind.';
COMMENT ON COLUMN ember_schema.conversation.audience_group_id
    IS 'The group whose members a GROUP station chat is for. NULL for every other chat, and once the group is deleted; a chat left without its group is closed.';
COMMENT ON COLUMN ember_schema.conversation.audience_tag_id
    IS 'The tag whose members a TAG station chat is for. NULL for every other chat, and once the tag is deleted; a chat left without its tag is closed.';
COMMENT ON COLUMN ember_schema.conversation.include_guardians
    IS 'Whether the guardians of the people in a group or tag station chat are members of it as well. Always false for every other conversation.';
COMMENT ON COLUMN ember_schema.conversation.closed_at
    IS 'When a station chat was closed: it stays readable for its members and takes nothing new. NULL while open and for every other kind.';
COMMENT ON COLUMN ember_schema.conversation.closed_by_account_id
    IS 'The account that closed a station chat. NULL while open, when it closed because its group or tag was deleted, and once that account is deleted.';
COMMENT ON CONSTRAINT conversation_station_shape ON ember_schema.conversation
    IS 'A station chat names its station, audience and name and has no owner or pair; a group or tag id only goes with that audience, and guardians only with a group or tag. Every other kind leaves the station chat columns empty.';

CREATE TABLE IF NOT EXISTS ember_schema.message
(
    id                        SERIAL PRIMARY KEY,
    conversation_id           INT         NOT NULL REFERENCES ember_schema.conversation (id) ON DELETE CASCADE,
    author_account_id         INT REFERENCES ember_schema.account (id) ON DELETE SET NULL,
    content                   TEXT        NOT NULL DEFAULT '',
    reply_to_id               INT REFERENCES ember_schema.message (id) ON DELETE SET NULL,
    system_kind               TEXT CHECK (system_kind IN ('ADDED', 'LEFT', 'REMOVED', 'RENAMED', 'OWNER_PASSED', 'OPENED', 'CLOSED', 'REOPENED')),
    system_subject_account_id INT REFERENCES ember_schema.account (id) ON DELETE SET NULL,
    created_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
    edited_at                 TIMESTAMPTZ,
    deleted_at                TIMESTAMPTZ,
    removed_by_station        BOOLEAN     NOT NULL DEFAULT FALSE,
    deleted_by_account_id     INT REFERENCES ember_schema.account (id) ON DELETE SET NULL,
    CHECK (deleted_at IS NOT NULL OR (NOT removed_by_station AND deleted_by_account_id IS NULL))
);

CREATE INDEX IF NOT EXISTS message_conversation_idx
    ON ember_schema.message (conversation_id, id);

COMMENT ON TABLE ember_schema.message
    IS 'One message in a conversation, or a quiet system line saying who joined, left, was removed, or renamed a group, or who opened, renamed, closed or reopened a station chat.';
COMMENT ON COLUMN ember_schema.message.id
    IS 'Primary key, which also orders the messages of a conversation.';
COMMENT ON COLUMN ember_schema.message.conversation_id
    IS 'The conversation the message was written in.';
COMMENT ON COLUMN ember_schema.message.author_account_id
    IS 'The account that wrote it, or that did what a system line says. NULL once that account is deleted; the message stays for the others.';
COMMENT ON COLUMN ember_schema.message.content
    IS 'The text, plain with line breaks and mentions. Empty once deleted and for system lines; a RENAMED line holds the new name.';
COMMENT ON COLUMN ember_schema.message.reply_to_id
    IS 'The message this one answers, in the same conversation. NULL for no answer and once that message is gone.';
COMMENT ON COLUMN ember_schema.message.system_kind
    IS 'What a system line says: ADDED, LEFT, REMOVED, RENAMED or OWNER_PASSED in a group, OPENED, RENAMED, CLOSED or REOPENED in a station chat. NULL for a message somebody wrote.';
COMMENT ON COLUMN ember_schema.message.system_subject_account_id
    IS 'The account a system line is about: who was added, removed, or became the owner. NULL otherwise and once that account is deleted.';
COMMENT ON COLUMN ember_schema.message.created_at
    IS 'When it was written.';
COMMENT ON COLUMN ember_schema.message.edited_at
    IS 'When its author last changed it; NULL if never.';
COMMENT ON COLUMN ember_schema.message.deleted_at
    IS 'When its author or the station deleted it; the text is emptied then. NULL while it stands.';
COMMENT ON COLUMN ember_schema.message.removed_by_station
    IS 'Whether the station removed it from a station chat rather than its author deleting it, which everybody sees as removed by the station.';
COMMENT ON COLUMN ember_schema.message.deleted_by_account_id
    IS 'The account that deleted it: its author, or the moderator who removed it for the station. NULL while it stands and once that account is deleted.';

CREATE TABLE IF NOT EXISTS ember_schema.conversation_member
(
    conversation_id      INT         NOT NULL REFERENCES ember_schema.conversation (id) ON DELETE CASCADE,
    account_id           INT         NOT NULL REFERENCES ember_schema.account (id) ON DELETE CASCADE,
    joined_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    left_at              TIMESTAMPTZ,
    last_read_message_id INT REFERENCES ember_schema.message (id) ON DELETE SET NULL,
    muted                BOOLEAN     NOT NULL DEFAULT FALSE,
    PRIMARY KEY (conversation_id, account_id)
);

CREATE INDEX IF NOT EXISTS conversation_member_account_idx
    ON ember_schema.conversation_member (account_id)
    WHERE left_at IS NULL;

COMMENT ON TABLE ember_schema.conversation_member
    IS 'An account taking part in a conversation, with how far it has read.';
COMMENT ON COLUMN ember_schema.conversation_member.conversation_id
    IS 'The conversation.';
COMMENT ON COLUMN ember_schema.conversation_member.account_id
    IS 'The account taking part.';
COMMENT ON COLUMN ember_schema.conversation_member.joined_at
    IS 'When the account started or was added, the last time if it came back.';
COMMENT ON COLUMN ember_schema.conversation_member.left_at
    IS 'When the account left or was removed; NULL while it takes part. A direct conversation that was left comes back with the next message.';
COMMENT ON COLUMN ember_schema.conversation_member.last_read_message_id
    IS 'The last message the account has read, which unread counts start after. NULL before anything was read.';
COMMENT ON COLUMN ember_schema.conversation_member.muted
    IS 'Whether the account muted the conversation: no badge count and no mail.';

CREATE TABLE IF NOT EXISTS ember_schema.message_reaction
(
    message_id INT         NOT NULL REFERENCES ember_schema.message (id) ON DELETE CASCADE,
    account_id INT         NOT NULL REFERENCES ember_schema.account (id) ON DELETE CASCADE,
    emoji      TEXT        NOT NULL CHECK (length(emoji) BETWEEN 1 AND 32),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (message_id, account_id, emoji)
);

COMMENT ON TABLE ember_schema.message_reaction
    IS 'An emoji somebody reacted to a message with, at most once per emoji and message.';
COMMENT ON COLUMN ember_schema.message_reaction.message_id
    IS 'The message reacted to.';
COMMENT ON COLUMN ember_schema.message_reaction.account_id
    IS 'The account that reacted.';
COMMENT ON COLUMN ember_schema.message_reaction.emoji
    IS 'The standard Unicode emoji reacted with.';
COMMENT ON COLUMN ember_schema.message_reaction.created_at
    IS 'When the reaction was added.';

CREATE TABLE IF NOT EXISTS ember_schema.account_block
(
    blocker_account_id INT         NOT NULL REFERENCES ember_schema.account (id) ON DELETE CASCADE,
    blocked_account_id INT         NOT NULL REFERENCES ember_schema.account (id) ON DELETE CASCADE,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (blocker_account_id, blocked_account_id),
    CHECK (blocker_account_id <> blocked_account_id)
);

CREATE INDEX IF NOT EXISTS account_block_blocked_idx
    ON ember_schema.account_block (blocked_account_id);

COMMENT ON TABLE ember_schema.account_block
    IS 'An account that blocked another for messages. The blocked account cannot start a direct conversation with the blocker or add them to a group, and is not told.';
COMMENT ON COLUMN ember_schema.account_block.blocker_account_id
    IS 'The account that blocked.';
COMMENT ON COLUMN ember_schema.account_block.blocked_account_id
    IS 'The account that was blocked.';
COMMENT ON COLUMN ember_schema.account_block.created_at
    IS 'When the block was set.';

-- Event groups: one-time appointments that belong together, such as the practice days and the test of the
-- Jugendflamme. The event group holds the sign-up, its rules and who may see and join it; every part
-- stays an ordinary appointment and a sign-up still writes one registration per chosen part.

CREATE TABLE IF NOT EXISTS ember_schema.event_group
(
    id                    SERIAL PRIMARY KEY,
    station_id            INT         NOT NULL REFERENCES ember_schema.station (id) ON DELETE CASCADE,
    name                  TEXT        NOT NULL,
    description           TEXT,
    registration_limit    INT CHECK (registration_limit IS NULL OR registration_limit > 0),
    registration_deadline TIMESTAMPTZ,
    requires_confirmation BOOLEAN     NOT NULL DEFAULT FALSE,
    restriction_mode      TEXT        NOT NULL DEFAULT 'AND' CHECK (restriction_mode IN ('AND', 'OR')),
    view_restriction_mode TEXT        NOT NULL DEFAULT 'AND' CHECK (view_restriction_mode IN ('AND', 'OR')),
    deadline_notified     BOOLEAN     NOT NULL DEFAULT FALSE,
    cancelled             BOOLEAN     NOT NULL DEFAULT FALSE,
    cancelled_at          TIMESTAMPTZ,
    cancel_reason         TEXT,
    color                 TEXT CHECK (color IS NULL OR color ~ '^#[0-9a-f]{6}$'),
    template_id           INT REFERENCES ember_schema.attendance_template (id) ON DELETE SET NULL,
    category_id           INT REFERENCES ember_schema.event_category (id) ON DELETE SET NULL,
    min_registrations     INT,
    threshold_days        INT,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT event_group_minimum_complete
        CHECK ((min_registrations IS NULL AND threshold_days IS NULL)
            OR (min_registrations >= 1 AND threshold_days >= 0))
);

CREATE INDEX IF NOT EXISTS idx_event_group_station
    ON ember_schema.event_group (station_id);

COMMENT ON TABLE ember_schema.event_group
    IS 'An event group: one-time appointments of a station that belong together and are signed up for once.';
COMMENT ON COLUMN ember_schema.event_group.id
    IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.event_group.station_id
    IS 'The station the event group and all its parts belong to.';
COMMENT ON COLUMN ember_schema.event_group.name
    IS 'The event group name, shown on every part.';
COMMENT ON COLUMN ember_schema.event_group.description
    IS 'What the event group is about, as Markdown.';
COMMENT ON COLUMN ember_schema.event_group.registration_limit
    IS 'How many people the event group takes; NULL for no limit.';
COMMENT ON COLUMN ember_schema.event_group.registration_deadline
    IS 'The instant members can no longer sign up or change their parts; NULL for no deadline. Unlike the appointment deadline it carries its time zone.';
COMMENT ON COLUMN ember_schema.event_group.requires_confirmation
    IS 'Whether a manager confirms each sign-up before it holds a place.';
COMMENT ON COLUMN ember_schema.event_group.restriction_mode
    IS 'How the entries of event_group_restriction combine: AND or OR.';
COMMENT ON COLUMN ember_schema.event_group.view_restriction_mode
    IS 'How the entries of event_group_view_restriction combine: AND or OR.';
COMMENT ON COLUMN ember_schema.event_group.deadline_notified
    IS 'Whether managers were told that the deadline passed with sign-ups still waiting for a decision.';
COMMENT ON COLUMN ember_schema.event_group.cancelled
    IS 'Whether the whole event group was called off; its parts are called off with it.';
COMMENT ON COLUMN ember_schema.event_group.cancelled_at
    IS 'When the event group was called off; NULL while it takes place.';
COMMENT ON COLUMN ember_schema.event_group.cancel_reason
    IS 'Why the event group was called off, as told to the people signed up; NULL for no reason given.';
COMMENT ON COLUMN ember_schema.event_group.color
    IS 'The colour that marks the parts of the event group in lists, as #rrggbb; NULL takes one picked by its id.';
COMMENT ON COLUMN ember_schema.event_group.template_id
    IS 'The attendance template a part without one of its own takes its attendance sheet from. NULL for none.';
COMMENT ON COLUMN ember_schema.event_group.category_id
    IS 'The category a part without one of its own is filed under. NULL for none.';
COMMENT ON COLUMN ember_schema.event_group.min_registrations
    IS 'How many sign-ups, of this station and of partner stations, must be accepted for the event group to take place; short of it some days before its first part, the whole event group is called off. Its parts keep no minimum of their own. NULL for no minimum.';
COMMENT ON COLUMN ember_schema.event_group.threshold_days
    IS 'How many days before the first part still taking place the minimum must be reached. Set exactly when the minimum is.';
COMMENT ON COLUMN ember_schema.event_group.created_at
    IS 'When the event group was created.';

CREATE TABLE IF NOT EXISTS ember_schema.event_group_set
(
    id             SERIAL PRIMARY KEY,
    event_group_id INT  NOT NULL REFERENCES ember_schema.event_group (id) ON DELETE CASCADE,
    name           TEXT NOT NULL,
    minimum        INT  NOT NULL DEFAULT 1 CHECK (minimum >= 0),
    position       INT  NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_event_group_set_event_group
    ON ember_schema.event_group_set (event_group_id);

COMMENT ON TABLE ember_schema.event_group_set
    IS 'A named group of parts of an event group with the number of them a participant has to attend, such as "Practice: at least 2".';
COMMENT ON COLUMN ember_schema.event_group_set.id
    IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.event_group_set.event_group_id
    IS 'The event group the set belongs to.';
COMMENT ON COLUMN ember_schema.event_group_set.name
    IS 'The set name shown to members.';
COMMENT ON COLUMN ember_schema.event_group_set.minimum
    IS 'How many parts of the set a sign-up has to choose; all of them makes every part mandatory.';
COMMENT ON COLUMN ember_schema.event_group_set.position
    IS 'Sort order of the set within the event group.';

ALTER TABLE ember_schema.station_event
    ADD COLUMN event_group_set_id INT REFERENCES ember_schema.event_group_set (id),
    ADD COLUMN event_group_required BOOLEAN NOT NULL DEFAULT FALSE,
    ADD CONSTRAINT station_event_event_group_part_one_time CHECK (event_group_set_id IS NULL OR event_type = 'ONE_TIME'),
    ADD CONSTRAINT station_event_event_group_required_part
        CHECK (NOT event_group_required OR event_group_set_id IS NOT NULL);

CREATE INDEX IF NOT EXISTS idx_station_event_event_group_set_id
    ON ember_schema.station_event (event_group_set_id) WHERE event_group_set_id IS NOT NULL;

COMMENT ON COLUMN ember_schema.station_event.event_group_set_id
    IS 'The event group set this one-time appointment is a part of, and through it the event group; NULL for an appointment of its own. A set or event group cannot be removed while parts still point at it, so a part never loses its event group unnoticed.';
COMMENT ON COLUMN ember_schema.station_event.event_group_required
    IS 'Whether the part has to be chosen by every sign-up of its event group while it takes place. It counts toward its block''s minimum. Always FALSE for an appointment that is no part of an event group.';

CREATE TABLE IF NOT EXISTS ember_schema.event_group_registration
(
    id             SERIAL PRIMARY KEY,
    event_group_id INT         NOT NULL REFERENCES ember_schema.event_group (id) ON DELETE CASCADE,
    member_id      INT         NOT NULL REFERENCES ember_schema.station_member (id) ON DELETE CASCADE,
    status         TEXT        NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'ACCEPTED', 'DENIED', 'DECLINED', 'WITHDRAWN')),
    registered_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by     INT REFERENCES ember_schema.station_member (id) ON DELETE SET NULL,
    UNIQUE (event_group_id, member_id)
);

CREATE INDEX IF NOT EXISTS idx_event_group_registration_member
    ON ember_schema.event_group_registration (member_id);

COMMENT ON TABLE ember_schema.event_group_registration
    IS 'One member''s sign-up for an event group. The chosen parts carry an event_registration each with the same status.';
COMMENT ON COLUMN ember_schema.event_group_registration.id
    IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.event_group_registration.event_group_id
    IS 'The event group signed up for.';
COMMENT ON COLUMN ember_schema.event_group_registration.member_id
    IS 'The member signed up.';
COMMENT ON COLUMN ember_schema.event_group_registration.status
    IS 'PENDING, ACCEPTED, DENIED, DECLINED or WITHDRAWN, as for an appointment sign-up.';
COMMENT ON COLUMN ember_schema.event_group_registration.registered_at
    IS 'When the member signed up, or last signed up again after withdrawing.';
COMMENT ON COLUMN ember_schema.event_group_registration.created_by
    IS 'The member who signed somebody else up; NULL when members signed up themselves.';

ALTER TABLE ember_schema.event_registration
    ADD COLUMN group_registration_id INT REFERENCES ember_schema.event_group_registration (id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_event_registration_group_registration_id
    ON ember_schema.event_registration (group_registration_id) WHERE group_registration_id IS NOT NULL;

COMMENT ON COLUMN ember_schema.event_registration.group_registration_id
    IS 'The event group sign-up this registration for a part was chosen with; NULL for a registration of its own, and once the part was given up.';

ALTER TABLE ember_schema.event_registration_field
    ALTER COLUMN event_id DROP NOT NULL,
    ADD COLUMN event_group_id INT REFERENCES ember_schema.event_group (id) ON DELETE CASCADE,
    ADD CONSTRAINT event_registration_field_one_owner CHECK (num_nonnulls(event_id, event_group_id) = 1);

CREATE UNIQUE INDEX IF NOT EXISTS idx_event_registration_field_event_group_name
    ON ember_schema.event_registration_field (event_group_id, name) WHERE event_group_id IS NOT NULL;

COMMENT ON COLUMN ember_schema.event_registration_field.event_id
    IS 'The event asking the question; NULL for a question of an event group.';
COMMENT ON COLUMN ember_schema.event_registration_field.event_group_id
    IS 'The event group asking the question of everyone signing up for it; NULL for a question of an appointment. Exactly one of event_id and event_group_id is set.';

CREATE TABLE IF NOT EXISTS ember_schema.event_group_answer
(
    group_registration_id INT  NOT NULL REFERENCES ember_schema.event_group_registration (id) ON DELETE CASCADE,
    field_id              INT  NOT NULL REFERENCES ember_schema.event_registration_field (id) ON DELETE CASCADE,
    value                 TEXT NOT NULL DEFAULT '',
    PRIMARY KEY (group_registration_id, field_id)
);

CREATE INDEX IF NOT EXISTS idx_event_group_answer_field
    ON ember_schema.event_group_answer (field_id);

COMMENT ON TABLE ember_schema.event_group_answer
    IS 'One member''s answer to one question of an event group, given once with the event group sign-up rather than per part.';
COMMENT ON COLUMN ember_schema.event_group_answer.group_registration_id
    IS 'The event group sign-up the answer belongs to; the answer is removed with it.';
COMMENT ON COLUMN ember_schema.event_group_answer.field_id
    IS 'The question answered.';
COMMENT ON COLUMN ember_schema.event_group_answer.value
    IS 'The answer, stored as text in the same shape appointment answers use.';

CREATE TABLE IF NOT EXISTS ember_schema.event_group_restriction
(
    id             SERIAL PRIMARY KEY,
    event_group_id INT NOT NULL REFERENCES ember_schema.event_group (id) ON DELETE CASCADE,
    user_type      TEXT,
    group_id       INT REFERENCES ember_schema.member_group (id) ON DELETE CASCADE,
    tag_id         INT REFERENCES ember_schema.user_tag (id) ON DELETE CASCADE,
    member_id      INT REFERENCES ember_schema.station_member (id) ON DELETE CASCADE,
    CHECK (num_nonnulls(user_type, group_id, tag_id, member_id) = 1)
);

CREATE INDEX IF NOT EXISTS idx_event_group_restriction_event_group
    ON ember_schema.event_group_restriction (event_group_id);

COMMENT ON TABLE ember_schema.event_group_restriction
    IS 'Who may sign up for the event group. Empty means everybody who may see it. Applies to every part.';
COMMENT ON COLUMN ember_schema.event_group_restriction.id
    IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.event_group_restriction.event_group_id
    IS 'The event group restricted.';
COMMENT ON COLUMN ember_schema.event_group_restriction.user_type
    IS 'Required user type. Exactly one of user_type/group_id/tag_id/member_id must be set.';
COMMENT ON COLUMN ember_schema.event_group_restriction.group_id
    IS 'Required group membership.';
COMMENT ON COLUMN ember_schema.event_group_restriction.tag_id
    IS 'Required tag.';
COMMENT ON COLUMN ember_schema.event_group_restriction.member_id
    IS 'Specific member (always OR-connected, bypasses AND/OR mode).';

CREATE TABLE IF NOT EXISTS ember_schema.event_group_view_restriction
(
    id             SERIAL PRIMARY KEY,
    event_group_id INT NOT NULL REFERENCES ember_schema.event_group (id) ON DELETE CASCADE,
    user_type      TEXT,
    group_id       INT REFERENCES ember_schema.member_group (id) ON DELETE CASCADE,
    tag_id         INT REFERENCES ember_schema.user_tag (id) ON DELETE CASCADE,
    member_id      INT REFERENCES ember_schema.station_member (id) ON DELETE CASCADE,
    CHECK (num_nonnulls(user_type, group_id, tag_id, member_id) = 1)
);

CREATE INDEX IF NOT EXISTS idx_event_group_view_restriction_event_group
    ON ember_schema.event_group_view_restriction (event_group_id);

COMMENT ON TABLE ember_schema.event_group_view_restriction
    IS 'Who may know the event group exists. Empty means everybody. Applies to every part.';
COMMENT ON COLUMN ember_schema.event_group_view_restriction.id
    IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.event_group_view_restriction.event_group_id
    IS 'The event group restricted.';
COMMENT ON COLUMN ember_schema.event_group_view_restriction.user_type
    IS 'Required user type. Exactly one of user_type/group_id/tag_id/member_id must be set.';
COMMENT ON COLUMN ember_schema.event_group_view_restriction.group_id
    IS 'Required group membership.';
COMMENT ON COLUMN ember_schema.event_group_view_restriction.tag_id
    IS 'Required tag.';
COMMENT ON COLUMN ember_schema.event_group_view_restriction.member_id
    IS 'Specific member (always OR-connected, bypasses AND/OR mode).';

CREATE TABLE IF NOT EXISTS ember_schema.event_group_deadline_reminder_sent
(
    event_group_id INT         NOT NULL REFERENCES ember_schema.event_group (id) ON DELETE CASCADE,
    days_before    INT         NOT NULL,
    sent_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (event_group_id, days_before)
);

COMMENT ON TABLE ember_schema.event_group_deadline_reminder_sent
    IS 'Which warnings that sign-ups for an event group close soon have gone out, so a sweep every few minutes warns once.';
COMMENT ON COLUMN ember_schema.event_group_deadline_reminder_sent.event_group_id
    IS 'The event group warned about.';
COMMENT ON COLUMN ember_schema.event_group_deadline_reminder_sent.days_before
    IS 'How many days before the event group deadline this warning was the one for.';
COMMENT ON COLUMN ember_schema.event_group_deadline_reminder_sent.sent_at
    IS 'When the warning went out.';

CREATE TABLE IF NOT EXISTS ember_schema.event_group_share
(
    event_group_id INT  NOT NULL PRIMARY KEY REFERENCES ember_schema.event_group (id) ON DELETE CASCADE,
    scope          TEXT NOT NULL DEFAULT 'ALL_PARTNERS'
);

COMMENT ON TABLE ember_schema.event_group_share
    IS 'An event group shared with partner stations. Its parts carry the same appointment share, parts added later included.';
COMMENT ON COLUMN ember_schema.event_group_share.event_group_id
    IS 'The event group shared.';
COMMENT ON COLUMN ember_schema.event_group_share.scope
    IS 'ALL_PARTNERS, or SPECIFIC for the partners listed in event_group_share_target.';

CREATE TABLE IF NOT EXISTS ember_schema.event_group_share_target
(
    event_group_id INT NOT NULL REFERENCES ember_schema.event_group_share (event_group_id) ON DELETE CASCADE,
    partner_id     INT NOT NULL REFERENCES ember_schema.federation_partner (id) ON DELETE CASCADE,
    PRIMARY KEY (event_group_id, partner_id)
);

CREATE INDEX IF NOT EXISTS idx_event_group_share_target_partner
    ON ember_schema.event_group_share_target (partner_id);

COMMENT ON TABLE ember_schema.event_group_share_target
    IS 'The partners an event group is shared with where its share names them one by one.';
COMMENT ON COLUMN ember_schema.event_group_share_target.event_group_id
    IS 'The shared event group.';
COMMENT ON COLUMN ember_schema.event_group_share_target.partner_id
    IS 'A partner the event group is shared with.';

CREATE TABLE IF NOT EXISTS ember_schema.event_group_partner_registration
(
    id               SERIAL PRIMARY KEY,
    event_group_id   INT         NOT NULL REFERENCES ember_schema.event_group (id) ON DELETE CASCADE,
    partner_id       INT         NOT NULL REFERENCES ember_schema.federation_partner (id) ON DELETE CASCADE,
    remote_member_id UUID        NOT NULL,
    status           TEXT        NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'ACCEPTED', 'DENIED', 'DECLINED', 'WITHDRAWN')),
    registered_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (event_group_id, partner_id, remote_member_id)
);

CREATE INDEX IF NOT EXISTS idx_event_group_partner_registration_partner
    ON ember_schema.event_group_partner_registration (partner_id, remote_member_id);

COMMENT ON TABLE ember_schema.event_group_partner_registration
    IS 'A partner station''s member signed up for a shared event group. The chosen parts carry an event_federation_registration each.';
COMMENT ON COLUMN ember_schema.event_group_partner_registration.id
    IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.event_group_partner_registration.event_group_id
    IS 'The event group signed up for.';
COMMENT ON COLUMN ember_schema.event_group_partner_registration.partner_id
    IS 'The partner station the member belongs to.';
COMMENT ON COLUMN ember_schema.event_group_partner_registration.remote_member_id
    IS 'The member at the partner station, by the id that station gave them.';
COMMENT ON COLUMN ember_schema.event_group_partner_registration.status
    IS 'PENDING, ACCEPTED, DENIED, DECLINED or WITHDRAWN, as for a member of this station.';
COMMENT ON COLUMN ember_schema.event_group_partner_registration.registered_at
    IS 'When the member signed up, or last signed up again after withdrawing.';

ALTER TABLE ember_schema.event_federation_registration
    ADD COLUMN group_partner_registration_id INT
        REFERENCES ember_schema.event_group_partner_registration (id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_event_federation_registration_group
    ON ember_schema.event_federation_registration (group_partner_registration_id)
    WHERE group_partner_registration_id IS NOT NULL;

COMMENT ON COLUMN ember_schema.event_federation_registration.group_partner_registration_id
    IS 'The partner''s event group sign-up this registration for a part was chosen with; NULL for a registration of its own, and once the part was given up.';

CREATE TABLE IF NOT EXISTS ember_schema.partner_event_group_signup
(
    member_id             INT         NOT NULL REFERENCES ember_schema.station_member (id) ON DELETE CASCADE,
    partner_station_uid   UUID        NOT NULL,
    remote_event_group_id INT         NOT NULL,
    signed_up_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (member_id, partner_station_uid, remote_event_group_id)
);

COMMENT ON TABLE ember_schema.partner_event_group_signup
    IS 'Members this station signed up for an event group a partner station holds, so news from that partner about the event group is believed for them only.';
COMMENT ON COLUMN ember_schema.partner_event_group_signup.member_id
    IS 'The member of this station signed up.';
COMMENT ON COLUMN ember_schema.partner_event_group_signup.partner_station_uid
    IS 'The partner station holding the event group.';
COMMENT ON COLUMN ember_schema.partner_event_group_signup.remote_event_group_id
    IS 'The event group, by its id at the partner station.';
COMMENT ON COLUMN ember_schema.partner_event_group_signup.signed_up_at
    IS 'When this station signed the member up.';

CREATE TABLE IF NOT EXISTS ember_schema.event_group_field
(
    id             SERIAL PRIMARY KEY,
    event_group_id INT     NOT NULL REFERENCES ember_schema.event_group (id) ON DELETE CASCADE,
    name           TEXT    NOT NULL,
    field_type     TEXT    NOT NULL DEFAULT 'TEXT',
    config         JSONB   NOT NULL DEFAULT '{}'::JSONB,
    value          TEXT    NOT NULL DEFAULT '',
    position       INT     NOT NULL DEFAULT 0,
    overview       BOOLEAN NOT NULL DEFAULT FALSE,
    "public"       BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_event_group_field_event_group
    ON ember_schema.event_group_field (event_group_id);

COMMENT ON TABLE ember_schema.event_group_field
    IS 'Fields of an event group, such as its place or meeting point. Every appointment of the group shows them; an appointment field of the same name replaces one.';
COMMENT ON COLUMN ember_schema.event_group_field.id
    IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.event_group_field.event_group_id
    IS 'The event group the field belongs to.';
COMMENT ON COLUMN ember_schema.event_group_field.name
    IS 'What the field is called; an appointment field with the same name, ignoring case, replaces it on that appointment.';
COMMENT ON COLUMN ember_schema.event_group_field.field_type
    IS 'The kind of value, one an appointment field takes; member kinds included, but never answered per date or tied to an attendance sheet.';
COMMENT ON COLUMN ember_schema.event_group_field.config
    IS 'The settings of the field, such as the options of a choice, as for an appointment field.';
COMMENT ON COLUMN ember_schema.event_group_field.value
    IS 'The value every appointment of the group shows unless it replaces the field. For a member field: the members entered for the whole group, which each appointment adjusts by its entries in event_group_field_entry.';
COMMENT ON COLUMN ember_schema.event_group_field.position
    IS 'Order among the group''s fields.';
COMMENT ON COLUMN ember_schema.event_group_field.overview
    IS 'Whether the value shows in lists of appointments.';
COMMENT ON COLUMN ember_schema.event_group_field."public"
    IS 'Whether readers outside the station, such as partner stations and the public calendar, see it.';

CREATE TABLE IF NOT EXISTS ember_schema.event_group_field_entry
(
    group_field_id INT  NOT NULL REFERENCES ember_schema.event_group_field (id) ON DELETE CASCADE,
    event_id       INT  NOT NULL REFERENCES ember_schema.station_event (id) ON DELETE CASCADE,
    member_id      INT  NOT NULL REFERENCES ember_schema.station_member (id) ON DELETE CASCADE,
    kind           TEXT NOT NULL CHECK (kind IN ('ENTERED', 'SKIPPED')),
    PRIMARY KEY (group_field_id, event_id, member_id)
);

CREATE INDEX IF NOT EXISTS idx_event_group_field_entry_event
    ON ember_schema.event_group_field_entry (event_id);
CREATE INDEX IF NOT EXISTS idx_event_group_field_entry_member
    ON ember_schema.event_group_field_entry (member_id);

COMMENT ON TABLE ember_schema.event_group_field_entry
    IS 'How one appointment of an event group differs from a member field of the group: a member entered for this appointment only, or a member entered for the whole group who skips this appointment.';
COMMENT ON COLUMN ember_schema.event_group_field_entry.group_field_id
    IS 'The member field of the event group.';
COMMENT ON COLUMN ember_schema.event_group_field_entry.event_id
    IS 'The appointment of the group the entry is about.';
COMMENT ON COLUMN ember_schema.event_group_field_entry.member_id
    IS 'The member entered or skipping.';
COMMENT ON COLUMN ember_schema.event_group_field_entry.kind
    IS 'ENTERED: in the field on this appointment only. SKIPPED: entered for the whole group but not on this appointment.';

-- Comments on an event group: its page carries a thread of its own, beside those of its parts.

ALTER TABLE ember_schema.comment
    ADD COLUMN event_group_id INT REFERENCES ember_schema.event_group (id) ON DELETE CASCADE;

ALTER TABLE ember_schema.comment
    DROP CONSTRAINT IF EXISTS comment_one_target;

ALTER TABLE ember_schema.comment
    ADD CONSTRAINT comment_one_target
        CHECK (num_nonnulls(event_id, news_id, kb_file_id, board_ticket_id, event_group_id) = 1);

CREATE INDEX IF NOT EXISTS idx_comment_event_group
    ON ember_schema.comment (event_group_id) WHERE event_group_id IS NOT NULL;

COMMENT ON TABLE ember_schema.comment
    IS 'Threaded comments on appointments, event groups, news entries, knowledge base files and board tickets. Exactly one target column is set, and that column says what the comment was written on.';
COMMENT ON COLUMN ember_schema.comment.event_group_id
    IS 'The event group the comment was written on, NULL for every other target.';

-- Date settings of an event group: the field pre-fills of its attendance template and the reminders.
-- Every part without a setting of its own follows the event group's, read live, so a change to the
-- event group reaches every part that has none of its own.

CREATE TABLE IF NOT EXISTS ember_schema.event_group_field_default
(
    event_group_id INT  NOT NULL REFERENCES ember_schema.event_group (id) ON DELETE CASCADE,
    field_id       INT  NOT NULL REFERENCES ember_schema.attendance_template_field (id) ON DELETE CASCADE,
    source         TEXT NOT NULL,
    value          TEXT,
    PRIMARY KEY (event_group_id, field_id)
);

COMMENT ON TABLE ember_schema.event_group_field_default
    IS 'The values an event group fills into the attendance sheet of a part that takes the event group''s attendance template.';
COMMENT ON COLUMN ember_schema.event_group_field_default.event_group_id
    IS 'The event group.';
COMMENT ON COLUMN ember_schema.event_group_field_default.field_id
    IS 'The field of the event group''s attendance template that is filled in.';
COMMENT ON COLUMN ember_schema.event_group_field_default.source
    IS 'Where the value comes from: VALUE for the value below, or a property of the part such as EVENT_NAME, EVENT_DESCRIPTION, EVENT_START_TIME or EVENT_END_TIME.';
COMMENT ON COLUMN ember_schema.event_group_field_default.value
    IS 'The value filled in where source is VALUE, NULL otherwise.';

CREATE TABLE IF NOT EXISTS ember_schema.event_group_reminder
(
    event_group_id INT NOT NULL REFERENCES ember_schema.event_group (id) ON DELETE CASCADE,
    days_before    INT NOT NULL CHECK (days_before > 0),
    PRIMARY KEY (event_group_id, days_before)
);

COMMENT ON TABLE ember_schema.event_group_reminder
    IS 'How many days before a part of an event group the people taking part are reminded, for every part without reminders of its own.';
COMMENT ON COLUMN ember_schema.event_group_reminder.event_group_id
    IS 'The event group.';
COMMENT ON COLUMN ember_schema.event_group_reminder.days_before
    IS 'How many days before the part the reminder goes out.';

-- Documents to bring and files of an event group. Every part shows the event group's entries before its
-- own, and a part cannot take one of the event group's entries off.

ALTER TABLE ember_schema.event_document_requirement
    ADD COLUMN event_group_id INT NULL REFERENCES ember_schema.event_group (id) ON DELETE CASCADE,
    DROP CONSTRAINT event_document_requirement_check,
    ADD CONSTRAINT event_document_requirement_one_owner
        CHECK (num_nonnulls(event_id, event_template_id, event_group_id) = 1);

CREATE UNIQUE INDEX IF NOT EXISTS uq_event_document_requirement_event_group
    ON ember_schema.event_document_requirement (event_group_id, template_id) WHERE event_group_id IS NOT NULL;

COMMENT ON COLUMN ember_schema.event_document_requirement.event_group_id IS
    'The event group that asks every part for the document, shown on each part before the part''s own. Exactly one of event_id, event_template_id and event_group_id is set.';

ALTER TABLE ember_schema.event_attachment
    ALTER COLUMN event_id DROP NOT NULL,
    ADD COLUMN event_group_id INT NULL REFERENCES ember_schema.event_group (id) ON DELETE CASCADE,
    ADD CONSTRAINT event_attachment_one_owner CHECK (num_nonnulls(event_id, event_group_id) = 1);

CREATE INDEX IF NOT EXISTS idx_event_attachment_event_group
    ON ember_schema.event_attachment (event_group_id, sort_order) WHERE event_group_id IS NOT NULL;

COMMENT ON COLUMN ember_schema.event_attachment.event_id IS
    'The appointment that hands the file over. NULL for a file of an event group.';
COMMENT ON COLUMN ember_schema.event_attachment.event_group_id IS
    'The event group that hands the file over on every part, before the part''s own files. NULL for a file of an appointment; exactly one of event_id and event_group_id is set.';

-- Station chats: who handles a station's chats, the messages members report there, and what the
-- daily mail about messages covers.

INSERT INTO ember_schema.station_permission (name)
VALUES ('MESSAGE_CHAT_MANAGE')
ON CONFLICT (name) DO NOTHING;

ALTER TABLE ember_schema.account
    ADD COLUMN messages_station_digest TEXT NOT NULL DEFAULT 'MENTIONS'
        CHECK (messages_station_digest IN ('MENTIONS', 'ALL')),
    ADD COLUMN message_digest_sent_at  TIMESTAMPTZ;

COMMENT ON COLUMN ember_schema.account.messages_station_digest
    IS 'What the daily mail about messages takes from station chats: MENTIONS for the messages that mention the account, ALL for every unread message.';
COMMENT ON COLUMN ember_schema.account.message_digest_sent_at
    IS 'When the newest message the account''s last daily mail about messages took was written; only messages written after it go into the next one. NULL before the first mail.';

CREATE TABLE IF NOT EXISTS ember_schema.message_report
(
    id                    SERIAL PRIMARY KEY,
    message_id            INT         NOT NULL REFERENCES ember_schema.message (id) ON DELETE CASCADE,
    reporter_account_id   INT REFERENCES ember_schema.account (id) ON DELETE SET NULL,
    reason                TEXT CHECK (length(reason) <= 500 AND btrim(reason) <> ''),
    reported_content      TEXT        NOT NULL,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    handled_at            TIMESTAMPTZ,
    handled_by_account_id INT REFERENCES ember_schema.account (id) ON DELETE SET NULL,
    outcome               TEXT CHECK (outcome IN ('DELETED', 'DISMISSED')),
    CHECK ((handled_at IS NULL) = (outcome IS NULL)),
    CHECK (handled_by_account_id IS NULL OR handled_at IS NOT NULL)
);

CREATE UNIQUE INDEX IF NOT EXISTS message_report_open_idx
    ON ember_schema.message_report (message_id, reporter_account_id)
    WHERE handled_at IS NULL;

COMMENT ON TABLE ember_schema.message_report
    IS 'A message of a station chat that a member reported to the people handling the station''s chats, and what became of the report.';
COMMENT ON COLUMN ember_schema.message_report.id
    IS 'Primary key.';
COMMENT ON COLUMN ember_schema.message_report.message_id
    IS 'The message reported.';
COMMENT ON COLUMN ember_schema.message_report.reporter_account_id
    IS 'The account that reported it. NULL once that account is deleted; the report stays for the station.';
COMMENT ON COLUMN ember_schema.message_report.reason
    IS 'What the reporter wrote about why, at most 500 characters. NULL where they gave no reason.';
COMMENT ON COLUMN ember_schema.message_report.reported_content
    IS 'The text of the message as it read when it was reported, so a later change to it does not change what was reported.';
COMMENT ON COLUMN ember_schema.message_report.created_at
    IS 'When it was reported.';
COMMENT ON COLUMN ember_schema.message_report.handled_at
    IS 'When the report was handled, by removing the message or dismissing it. NULL while it is open; one account has at most one open report per message.';
COMMENT ON COLUMN ember_schema.message_report.handled_by_account_id
    IS 'The account that handled the report. NULL while open and once that account is deleted.';
COMMENT ON COLUMN ember_schema.message_report.outcome
    IS 'How the report was handled: DELETED when the message was removed, DISMISSED when it stayed. NULL while open.';
