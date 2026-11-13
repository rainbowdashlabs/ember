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
    kind             TEXT        NOT NULL CHECK (kind IN ('DIRECT', 'GROUP')),
    name             TEXT,
    owner_account_id INT REFERENCES ember_schema.account (id) ON DELETE SET NULL,
    direct_pair      TEXT UNIQUE,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_activity_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (kind = 'GROUP' OR name IS NULL)
);

COMMENT ON TABLE ember_schema.conversation
    IS 'A conversation between accounts: a direct one between two people, or a group. It belongs to the people in it, not to a station.';
COMMENT ON COLUMN ember_schema.conversation.id
    IS 'Primary key.';
COMMENT ON COLUMN ember_schema.conversation.uid
    IS 'The identity the conversation is addressed by.';
COMMENT ON COLUMN ember_schema.conversation.kind
    IS 'DIRECT for exactly two accounts, GROUP for several.';
COMMENT ON COLUMN ember_schema.conversation.name
    IS 'The name a group was given; NULL for a direct conversation and for a group named after its members.';
COMMENT ON COLUMN ember_schema.conversation.owner_account_id
    IS 'The account that owns a group: it alone removes people and renames it. NULL for a direct conversation and once the owner is gone.';
COMMENT ON COLUMN ember_schema.conversation.direct_pair
    IS 'For a direct conversation, the two account ids, the lower first, joined by a colon, which keeps one conversation per pair. Cleared when either account is deleted.';
COMMENT ON COLUMN ember_schema.conversation.created_at
    IS 'When the conversation was started.';
COMMENT ON COLUMN ember_schema.conversation.last_activity_at
    IS 'When anything was last written in it, which orders the list of conversations.';

CREATE TABLE IF NOT EXISTS ember_schema.message
(
    id                        SERIAL PRIMARY KEY,
    conversation_id           INT         NOT NULL REFERENCES ember_schema.conversation (id) ON DELETE CASCADE,
    author_account_id         INT REFERENCES ember_schema.account (id) ON DELETE SET NULL,
    content                   TEXT        NOT NULL DEFAULT '',
    reply_to_id               INT REFERENCES ember_schema.message (id) ON DELETE SET NULL,
    system_kind               TEXT CHECK (system_kind IN ('ADDED', 'LEFT', 'REMOVED', 'RENAMED', 'OWNER_PASSED')),
    system_subject_account_id INT REFERENCES ember_schema.account (id) ON DELETE SET NULL,
    created_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
    edited_at                 TIMESTAMPTZ,
    deleted_at                TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS message_conversation_idx
    ON ember_schema.message (conversation_id, id);

COMMENT ON TABLE ember_schema.message
    IS 'One message in a conversation, or a quiet system line saying who joined, left, was removed, or renamed a group.';
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
    IS 'What a system line says: ADDED, LEFT, REMOVED, RENAMED or OWNER_PASSED. NULL for a message somebody wrote.';
COMMENT ON COLUMN ember_schema.message.system_subject_account_id
    IS 'The account a system line is about: who was added, removed, or became the owner. NULL otherwise and once that account is deleted.';
COMMENT ON COLUMN ember_schema.message.created_at
    IS 'When it was written.';
COMMENT ON COLUMN ember_schema.message.edited_at
    IS 'When its author last changed it; NULL if never.';
COMMENT ON COLUMN ember_schema.message.deleted_at
    IS 'When its author deleted it; the text is emptied then. NULL while it stands.';

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

-- Courses: one-time appointments that belong together, such as the practice days and the test of a
-- training course. The course holds the sign-up, its rules and who may see and join it; every part
-- stays an ordinary appointment and a sign-up still writes one registration per chosen part.

CREATE TABLE IF NOT EXISTS ember_schema.event_course
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
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_event_course_station
    ON ember_schema.event_course (station_id);

COMMENT ON TABLE ember_schema.event_course
    IS 'A course: one-time appointments of a station that belong together and are signed up for once.';
COMMENT ON COLUMN ember_schema.event_course.id
    IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.event_course.station_id
    IS 'The station the course and all its parts belong to.';
COMMENT ON COLUMN ember_schema.event_course.name
    IS 'The course name, shown on every part.';
COMMENT ON COLUMN ember_schema.event_course.description
    IS 'What the course is about, as Markdown.';
COMMENT ON COLUMN ember_schema.event_course.registration_limit
    IS 'How many people the course takes; NULL for no limit.';
COMMENT ON COLUMN ember_schema.event_course.registration_deadline
    IS 'The instant members can no longer sign up or change their parts; NULL for no deadline. Unlike the appointment deadline it carries its time zone.';
COMMENT ON COLUMN ember_schema.event_course.requires_confirmation
    IS 'Whether a manager confirms each sign-up before it holds a place.';
COMMENT ON COLUMN ember_schema.event_course.restriction_mode
    IS 'How the entries of event_course_restriction combine: AND or OR.';
COMMENT ON COLUMN ember_schema.event_course.view_restriction_mode
    IS 'How the entries of event_course_view_restriction combine: AND or OR.';
COMMENT ON COLUMN ember_schema.event_course.deadline_notified
    IS 'Whether managers were told that the deadline passed with sign-ups still waiting for a decision.';
COMMENT ON COLUMN ember_schema.event_course.cancelled
    IS 'Whether the whole course was called off; its parts are called off with it.';
COMMENT ON COLUMN ember_schema.event_course.cancelled_at
    IS 'When the course was called off; NULL while it takes place.';
COMMENT ON COLUMN ember_schema.event_course.cancel_reason
    IS 'Why the course was called off, as told to the people signed up; NULL for no reason given.';
COMMENT ON COLUMN ember_schema.event_course.created_at
    IS 'When the course was created.';

CREATE TABLE IF NOT EXISTS ember_schema.event_course_set
(
    id        SERIAL PRIMARY KEY,
    course_id INT  NOT NULL REFERENCES ember_schema.event_course (id) ON DELETE CASCADE,
    name      TEXT NOT NULL,
    minimum   INT  NOT NULL DEFAULT 1 CHECK (minimum >= 0),
    position  INT  NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_event_course_set_course
    ON ember_schema.event_course_set (course_id);

COMMENT ON TABLE ember_schema.event_course_set
    IS 'A named group of parts of a course with the number of them a participant has to attend, such as "Practice: at least 2".';
COMMENT ON COLUMN ember_schema.event_course_set.id
    IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.event_course_set.course_id
    IS 'The course the set belongs to.';
COMMENT ON COLUMN ember_schema.event_course_set.name
    IS 'The set name shown to members.';
COMMENT ON COLUMN ember_schema.event_course_set.minimum
    IS 'How many parts of the set a sign-up has to choose; all of them makes every part mandatory.';
COMMENT ON COLUMN ember_schema.event_course_set.position
    IS 'Sort order of the set within the course.';

ALTER TABLE ember_schema.station_event
    ADD COLUMN course_set_id INT REFERENCES ember_schema.event_course_set (id),
    ADD CONSTRAINT station_event_course_part_one_time CHECK (course_set_id IS NULL OR event_type = 'ONE_TIME');

CREATE INDEX IF NOT EXISTS idx_station_event_course_set_id
    ON ember_schema.station_event (course_set_id) WHERE course_set_id IS NOT NULL;

COMMENT ON COLUMN ember_schema.station_event.course_set_id
    IS 'The course set this one-time appointment is a part of, and through it the course; NULL for an appointment of its own. A set or course cannot be removed while parts still point at it, so a part never loses its course unnoticed.';

CREATE TABLE IF NOT EXISTS ember_schema.event_course_registration
(
    id            SERIAL PRIMARY KEY,
    course_id     INT         NOT NULL REFERENCES ember_schema.event_course (id) ON DELETE CASCADE,
    member_id     INT         NOT NULL REFERENCES ember_schema.station_member (id) ON DELETE CASCADE,
    status        TEXT        NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'ACCEPTED', 'DENIED', 'DECLINED', 'WITHDRAWN')),
    registered_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by    INT REFERENCES ember_schema.station_member (id) ON DELETE SET NULL,
    UNIQUE (course_id, member_id)
);

CREATE INDEX IF NOT EXISTS idx_event_course_registration_member
    ON ember_schema.event_course_registration (member_id);

COMMENT ON TABLE ember_schema.event_course_registration
    IS 'One member''s sign-up for a course. The chosen parts carry an event_registration each with the same status.';
COMMENT ON COLUMN ember_schema.event_course_registration.id
    IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.event_course_registration.course_id
    IS 'The course signed up for.';
COMMENT ON COLUMN ember_schema.event_course_registration.member_id
    IS 'The member signed up.';
COMMENT ON COLUMN ember_schema.event_course_registration.status
    IS 'PENDING, ACCEPTED, DENIED, DECLINED or WITHDRAWN, as for an appointment sign-up.';
COMMENT ON COLUMN ember_schema.event_course_registration.registered_at
    IS 'When the member signed up, or last signed up again after withdrawing.';
COMMENT ON COLUMN ember_schema.event_course_registration.created_by
    IS 'The member who signed somebody else up; NULL when members signed up themselves.';

ALTER TABLE ember_schema.event_registration
    ADD COLUMN course_registration_id INT REFERENCES ember_schema.event_course_registration (id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_event_registration_course_registration_id
    ON ember_schema.event_registration (course_registration_id) WHERE course_registration_id IS NOT NULL;

COMMENT ON COLUMN ember_schema.event_registration.course_registration_id
    IS 'The course sign-up this registration for a part was chosen with; NULL for a registration of its own, and once the part was given up.';

CREATE TABLE IF NOT EXISTS ember_schema.event_course_restriction
(
    id        SERIAL PRIMARY KEY,
    course_id INT NOT NULL REFERENCES ember_schema.event_course (id) ON DELETE CASCADE,
    user_type TEXT,
    group_id  INT REFERENCES ember_schema.member_group (id) ON DELETE CASCADE,
    tag_id    INT REFERENCES ember_schema.user_tag (id) ON DELETE CASCADE,
    member_id INT REFERENCES ember_schema.station_member (id) ON DELETE CASCADE,
    CHECK (num_nonnulls(user_type, group_id, tag_id, member_id) = 1)
);

CREATE INDEX IF NOT EXISTS idx_event_course_restriction_course
    ON ember_schema.event_course_restriction (course_id);

COMMENT ON TABLE ember_schema.event_course_restriction
    IS 'Who may sign up for the course. Empty means everybody who may see it. Applies to every part.';
COMMENT ON COLUMN ember_schema.event_course_restriction.id
    IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.event_course_restriction.course_id
    IS 'The course restricted.';
COMMENT ON COLUMN ember_schema.event_course_restriction.user_type
    IS 'Required user type. Exactly one of user_type/group_id/tag_id/member_id must be set.';
COMMENT ON COLUMN ember_schema.event_course_restriction.group_id
    IS 'Required group membership.';
COMMENT ON COLUMN ember_schema.event_course_restriction.tag_id
    IS 'Required tag.';
COMMENT ON COLUMN ember_schema.event_course_restriction.member_id
    IS 'Specific member (always OR-connected, bypasses AND/OR mode).';

CREATE TABLE IF NOT EXISTS ember_schema.event_course_view_restriction
(
    id        SERIAL PRIMARY KEY,
    course_id INT NOT NULL REFERENCES ember_schema.event_course (id) ON DELETE CASCADE,
    user_type TEXT,
    group_id  INT REFERENCES ember_schema.member_group (id) ON DELETE CASCADE,
    tag_id    INT REFERENCES ember_schema.user_tag (id) ON DELETE CASCADE,
    member_id INT REFERENCES ember_schema.station_member (id) ON DELETE CASCADE,
    CHECK (num_nonnulls(user_type, group_id, tag_id, member_id) = 1)
);

CREATE INDEX IF NOT EXISTS idx_event_course_view_restriction_course
    ON ember_schema.event_course_view_restriction (course_id);

COMMENT ON TABLE ember_schema.event_course_view_restriction
    IS 'Who may know the course exists. Empty means everybody. Applies to every part.';
COMMENT ON COLUMN ember_schema.event_course_view_restriction.id
    IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.event_course_view_restriction.course_id
    IS 'The course restricted.';
COMMENT ON COLUMN ember_schema.event_course_view_restriction.user_type
    IS 'Required user type. Exactly one of user_type/group_id/tag_id/member_id must be set.';
COMMENT ON COLUMN ember_schema.event_course_view_restriction.group_id
    IS 'Required group membership.';
COMMENT ON COLUMN ember_schema.event_course_view_restriction.tag_id
    IS 'Required tag.';
COMMENT ON COLUMN ember_schema.event_course_view_restriction.member_id
    IS 'Specific member (always OR-connected, bypasses AND/OR mode).';

CREATE TABLE IF NOT EXISTS ember_schema.event_course_deadline_reminder_sent
(
    course_id   INT         NOT NULL REFERENCES ember_schema.event_course (id) ON DELETE CASCADE,
    days_before INT         NOT NULL,
    sent_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (course_id, days_before)
);

COMMENT ON TABLE ember_schema.event_course_deadline_reminder_sent
    IS 'Which warnings that sign-ups for a course close soon have gone out, so a sweep every few minutes warns once.';
COMMENT ON COLUMN ember_schema.event_course_deadline_reminder_sent.course_id
    IS 'The course warned about.';
COMMENT ON COLUMN ember_schema.event_course_deadline_reminder_sent.days_before
    IS 'How many days before the course deadline this warning was the one for.';
COMMENT ON COLUMN ember_schema.event_course_deadline_reminder_sent.sent_at
    IS 'When the warning went out.';
