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
