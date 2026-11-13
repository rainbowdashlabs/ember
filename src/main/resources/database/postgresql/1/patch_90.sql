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
    IS 'Whether the account is sent a mail about messages that stayed unread for a while.';

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
    mailed_at            TIMESTAMPTZ,
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
COMMENT ON COLUMN ember_schema.conversation_member.mailed_at
    IS 'When the account was last mailed about unread messages here. Cleared once it reads again, so one mail goes out per unread stretch.';

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
    IS 'When the account was last sent the daily mail about messages; only messages written after it go into the next one. NULL before the first.';

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
