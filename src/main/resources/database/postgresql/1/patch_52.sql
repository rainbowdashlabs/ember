-- Documents that arrive by mail.
--
-- Paperwork reaches a station as mail: the office sends a certificate, the practice sends a fitness
-- check, an archive address collects what everybody forwards to it. This lets a station connect a
-- mailbox once, say which senders it trusts, and have every attachment that passes become a document
-- by itself.
--
-- The mail is transport and not content. Nothing here stores a message body, and nothing here indexes
-- one. What is kept of a message is its envelope: who sent it, what it was called, and which files
-- hung off it.

-- ============================================================
-- The connection
-- ============================================================

-- One mailbox a station watches.
--
-- The password opens somebody's mail, so it is encrypted the way the remote storage credentials
-- already are: a random iv per encryption and the ciphertext beside it, with the key belonging to the
-- instance rather than to the row. A station moved to another instance therefore arrives with a
-- password it cannot decrypt, which is why the importer disables a mailbox it brought with it and asks
-- for the password again.
CREATE TABLE IF NOT EXISTS ember_schema.mail_mailbox
(
    id                  SERIAL PRIMARY KEY,
    station_id          INTEGER   NOT NULL REFERENCES ember_schema.station (id) ON DELETE CASCADE,
    name                TEXT      NOT NULL,
    host                TEXT      NOT NULL,
    port                INTEGER   NOT NULL,
    security            TEXT      NOT NULL DEFAULT 'SSL',
    username            TEXT      NOT NULL,
    password_iv         BYTEA     NOT NULL,
    password_ciphertext BYTEA     NOT NULL,
    folder              TEXT      NOT NULL DEFAULT 'INBOX',
    enabled             BOOLEAN   NOT NULL DEFAULT FALSE,
    interval_minutes    INTEGER   NOT NULL DEFAULT 15,
    import_from         TIMESTAMP NOT NULL DEFAULT now(),
    last_check_at       TIMESTAMP,
    last_error          TEXT,
    failure_count       INTEGER   NOT NULL DEFAULT 0,
    suspended           BOOLEAN   NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMP NOT NULL DEFAULT now()
);

COMMENT ON TABLE ember_schema.mail_mailbox
    IS 'A mailbox a station watches for paperwork. Read only as far as the envelope goes; message bodies are never stored.';
COMMENT ON COLUMN ember_schema.mail_mailbox.name
    IS 'What the station calls this mailbox, so a page listing several can tell them apart.';
COMMENT ON COLUMN ember_schema.mail_mailbox.security
    IS 'How the connection is secured: SSL, STARTTLS or NONE.';
COMMENT ON COLUMN ember_schema.mail_mailbox.password_iv
    IS 'The random initialisation vector of this password''s encryption. Never reused for a second encryption under the same key.';
COMMENT ON COLUMN ember_schema.mail_mailbox.password_ciphertext
    IS 'The mailbox password, encrypted with the instance credential key. Plaintext never leaves the method that opens the connection.';
COMMENT ON COLUMN ember_schema.mail_mailbox.enabled
    IS 'Whether the poller visits this mailbox. Off until a connection test has answered, and off again after a station transfer, whose password cannot be decrypted on the new instance.';
COMMENT ON COLUMN ember_schema.mail_mailbox.interval_minutes
    IS 'How often this mailbox is checked. Fifteen minutes is the default and the floor, so nobody points a one minute poll at a provider that will lock the account for it.';
COMMENT ON COLUMN ember_schema.mail_mailbox.import_from
    IS 'Mail older than this is ignored, which is what stops the first cycle of a ten year old archive address importing a decade of attachments. Defaults to the moment the mailbox was added.';
COMMENT ON COLUMN ember_schema.mail_mailbox.last_error
    IS 'What went wrong last, kept on the mailbox so it is answerable from the page rather than only from a log file.';
COMMENT ON COLUMN ember_schema.mail_mailbox.failure_count
    IS 'Consecutive failures, which drive the backoff and the suspension. Reset by a cycle that succeeds.';
COMMENT ON COLUMN ember_schema.mail_mailbox.suspended
    IS 'Whether repeated failure has taken this mailbox out of the rotation. Whoever holds the station mail permission is told when it happens.';

CREATE INDEX IF NOT EXISTS idx_mail_mailbox_station ON ember_schema.mail_mailbox (station_id);

-- ============================================================
-- What is taken and how it is filed
-- ============================================================

-- One rule: which mail it takes, which of its files it wants, and how they are filed.
--
-- Rules are ordered and the first one that matches takes the message; the others do not run. What
-- matching means is the message alone, its senders and its subject and its attachment names. The type
-- and size bounds judge each attachment separately, because one message can carry both a PDF worth
-- having and a signature logo worth dropping.
CREATE TABLE IF NOT EXISTS ember_schema.mail_rule
(
    id                      SERIAL PRIMARY KEY,
    mailbox_id              INTEGER   NOT NULL REFERENCES ember_schema.mail_mailbox (id) ON DELETE CASCADE,
    name                    TEXT      NOT NULL,
    position                INTEGER   NOT NULL DEFAULT 0,
    enabled                 BOOLEAN   NOT NULL DEFAULT TRUE,
    subject_filter          TEXT,
    attachment_name_filter  TEXT,
    accepted_types          TEXT[]    NOT NULL DEFAULT ARRAY ['application/pdf'],
    min_size_bytes          BIGINT    NOT NULL DEFAULT 0,
    include_inline          BOOLEAN   NOT NULL DEFAULT FALSE,
    title_source            TEXT      NOT NULL DEFAULT 'SUBJECT',
    hidden                  BOOLEAN   NOT NULL DEFAULT FALSE,
    keep_on_archive         BOOLEAN   NOT NULL DEFAULT FALSE,
    read_subject_for_member BOOLEAN   NOT NULL DEFAULT FALSE,
    action                  TEXT      NOT NULL DEFAULT 'MARK_SEEN',
    move_to_folder          TEXT,
    lost_member             BOOLEAN   NOT NULL DEFAULT FALSE,
    created_at              TIMESTAMP NOT NULL DEFAULT now()
);

COMMENT ON TABLE ember_schema.mail_rule
    IS 'What a mailbox does with the mail it finds. Ordered, and the first rule that matches a message takes it.';
COMMENT ON COLUMN ember_schema.mail_rule.position
    IS 'Where this rule stands in the order. The first match wins, so the order is part of what the rule means.';
COMMENT ON COLUMN ember_schema.mail_rule.subject_filter
    IS 'Text the subject must contain, or null for any subject. Part of whether the rule takes the message.';
COMMENT ON COLUMN ember_schema.mail_rule.attachment_name_filter
    IS 'Text an attachment name must contain, which is how a station separates one sender''s PDFs from their photographs.';
COMMENT ON COLUMN ember_schema.mail_rule.accepted_types
    IS 'The content types this rule files. Decided by sniffing the bytes, never by the file name or by what the mail claims the part is.';
COMMENT ON COLUMN ember_schema.mail_rule.min_size_bytes
    IS 'How big a file has to be to count as a document, which is what keeps the store from filling with signature logos.';
COMMENT ON COLUMN ember_schema.mail_rule.include_inline
    IS 'Whether embedded pictures count as files. Off by default, because every mail with a signature embeds a crest.';
COMMENT ON COLUMN ember_schema.mail_rule.title_source
    IS 'Where the document title comes from: SUBJECT or FILE_NAME.';
COMMENT ON COLUMN ember_schema.mail_rule.read_subject_for_member
    IS 'Whether a name typed into the subject binds the document to that member. A guess about a line a human wrote, so it is off unless a rule asks for it, and an ambiguous subject binds nobody.';
COMMENT ON COLUMN ember_schema.mail_rule.action
    IS 'What happens to the message once its attachments are dealt with: NOTHING, MARK_SEEN, FLAG or MOVE. Deleting is not offered, because moving does the same thing and can be undone by whoever finds out.';
COMMENT ON COLUMN ember_schema.mail_rule.move_to_folder
    IS 'Where MOVE puts the message. Null for every other action.';
COMMENT ON COLUMN ember_schema.mail_rule.lost_member
    IS 'Whether this rule dropped a member it could no longer file under. Said on the page, because a rule that silently stops working is worse than one that says what happened to it.';

CREATE INDEX IF NOT EXISTS idx_mail_rule_mailbox ON ember_schema.mail_rule (mailbox_id, position);

-- The senders a rule trusts, one per row.
--
-- Two forms and no more: one address, or every address at one domain written as '*@domain'. No general
-- pattern language, because a language wide enough to write '.*' in is one somebody eventually writes
-- '.*' in, and this is the check deciding whether a stranger can put files into a station's store.
-- Subdomains are not implied. A rule with no row here matches nothing, because the natural reading of
-- an empty filter is 'everything' and here that would be an open door.
CREATE TABLE IF NOT EXISTS ember_schema.mail_rule_sender
(
    id      SERIAL PRIMARY KEY,
    rule_id INTEGER NOT NULL REFERENCES ember_schema.mail_rule (id) ON DELETE CASCADE,
    pattern TEXT    NOT NULL,
    UNIQUE (rule_id, pattern)
);

COMMENT ON TABLE ember_schema.mail_rule_sender
    IS 'Which senders a rule accepts. An address, or every address at a domain. This is the boundary that actually holds, so it is kept narrow on purpose.';

-- The words a rule puts on what it files.
CREATE TABLE IF NOT EXISTS ember_schema.mail_rule_tag
(
    rule_id INTEGER NOT NULL REFERENCES ember_schema.mail_rule (id) ON DELETE CASCADE,
    name    TEXT    NOT NULL,
    PRIMARY KEY (rule_id, name)
);

COMMENT ON TABLE ember_schema.mail_rule_tag
    IS 'Tags every document this rule files is given. Names rather than references, resolved against the station''s tags as the document is written.';

-- The members a rule always files under.
--
-- Filing under nobody is the ordinary outcome, so this is for the rule that always files the same way:
-- the school's letters to whoever leads the youth group. A member marked former or deleted is dropped
-- from the rule rather than taking it with them.
CREATE TABLE IF NOT EXISTS ember_schema.mail_rule_member
(
    rule_id   INTEGER NOT NULL REFERENCES ember_schema.mail_rule (id) ON DELETE CASCADE,
    member_id INTEGER NOT NULL REFERENCES ember_schema.station_member (id) ON DELETE CASCADE,
    PRIMARY KEY (rule_id, member_id)
);

COMMENT ON TABLE ember_schema.mail_rule_member
    IS 'Members every document this rule files is bound to. Optional, and the usual answer is none.';

-- ============================================================
-- What was already dealt with
-- ============================================================

-- One row per message per mailbox, saying it was dealt with whatever came of it.
--
-- This is what survives a connection dropping after the import but before the flags were written,
-- which is the failure that otherwise imports everything twice. It outlives the readable log, because a
-- mail redelivered after the log was pruned would otherwise become a second document.
--
-- A message with no identifier of its own is not unusual in the wild, so one is made from the fields of
-- its envelope. Falling back to the attachment hash would be wrong here: it would call an empty resend
-- handled.
CREATE TABLE IF NOT EXISTS ember_schema.mail_handled_message
(
    id         SERIAL PRIMARY KEY,
    mailbox_id INTEGER   NOT NULL REFERENCES ember_schema.mail_mailbox (id) ON DELETE CASCADE,
    message_id TEXT      NOT NULL,
    derived    BOOLEAN   NOT NULL DEFAULT FALSE,
    handled_at TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE (mailbox_id, message_id)
);

COMMENT ON TABLE ember_schema.mail_handled_message
    IS 'Messages this mailbox has already dealt with. Kept for as long as the documents they produced, which is longer than the readable log.';
COMMENT ON COLUMN ember_schema.mail_handled_message.derived
    IS 'Whether the identifier was made from the envelope because the message carried none of its own.';

-- One row per attachment, which is the grain everything about this feature is answered at.
--
-- A mail carrying three scans produces three documents and three of these. One can be imported while
-- the next is refused for its type and the third is a duplicate, and a log that could only say one
-- thing per message could not report that.
--
-- The hash is a key and not a detail: these same bytes arriving again in this station are a duplicate,
-- whatever message they came in. That is what catches the forward, which gets a new message identifier
-- and would otherwise pass. So the hash stays when the readable half is pruned.
CREATE TABLE IF NOT EXISTS ember_schema.mail_import_entry
(
    id              SERIAL PRIMARY KEY,
    mailbox_id      INTEGER   NOT NULL REFERENCES ember_schema.mail_mailbox (id) ON DELETE CASCADE,
    station_id      INTEGER   NOT NULL REFERENCES ember_schema.station (id) ON DELETE CASCADE,
    rule_id         INTEGER            REFERENCES ember_schema.mail_rule (id) ON DELETE SET NULL,
    message_id      TEXT,
    sender          TEXT,
    subject         TEXT,
    attachment_name TEXT,
    content_hash    TEXT,
    outcome         TEXT      NOT NULL,
    reason          TEXT,
    document_id     INTEGER            REFERENCES ember_schema.member_document (id) ON DELETE SET NULL,
    pruned          BOOLEAN   NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP NOT NULL DEFAULT now()
);

COMMENT ON TABLE ember_schema.mail_import_entry
    IS 'What happened to every attachment that was looked at. A rule that quietly imports nothing is the likeliest complaint, and this is the only thing that answers it.';
COMMENT ON COLUMN ember_schema.mail_import_entry.rule_id
    IS 'Which rule took the message, which is the question anybody debugging an import actually asks. Null where no rule matched.';
COMMENT ON COLUMN ember_schema.mail_import_entry.content_hash
    IS 'The hash of the attachment bytes. A key rather than a detail: the same bytes again in this station are a duplicate however they arrived, which is what catches a forward. Survives the pruning.';
COMMENT ON COLUMN ember_schema.mail_import_entry.outcome
    IS 'IMPORTED, DUPLICATE, SENDER_NOT_ALLOWED, NO_RULE_MATCHED, TYPE_NOT_ALLOWED, TOO_LARGE, TOO_SMALL, NO_ATTACHMENT, QUOTA_EXCEEDED, AUTHENTICATION_FAILED or FAILED.';
COMMENT ON COLUMN ember_schema.mail_import_entry.reason
    IS 'The readable half of why, which is pruned after a year while the keys stay.';
COMMENT ON COLUMN ember_schema.mail_import_entry.pruned
    IS 'Whether the readable fields have been cleared by the pruning, leaving the row as a key only.';

CREATE INDEX IF NOT EXISTS idx_mail_import_entry_mailbox ON ember_schema.mail_import_entry (mailbox_id, created_at DESC);
CREATE UNIQUE INDEX IF NOT EXISTS idx_mail_import_entry_hash
    ON ember_schema.mail_import_entry (station_id, content_hash)
    WHERE content_hash IS NOT NULL AND outcome = 'IMPORTED';

-- ============================================================
-- Where a document came from
-- ============================================================

-- A document filed by the importer has no uploader, because nobody uploaded it.
--
-- Without this the document detail shows an empty uploader and says nothing; with it, it can say the
-- document arrived by mail from that address on that day. It belongs to the document rather than to
-- the log: leaving it in the log would mean a document losing its own history on the day the log was
-- pruned.
CREATE TABLE IF NOT EXISTS ember_schema.member_document_mail_origin
(
    document_id INTEGER   NOT NULL PRIMARY KEY REFERENCES ember_schema.member_document (id) ON DELETE CASCADE,
    mailbox_id  INTEGER            REFERENCES ember_schema.mail_mailbox (id) ON DELETE SET NULL,
    sender      TEXT      NOT NULL,
    subject     TEXT,
    received_at TIMESTAMP NOT NULL
);

COMMENT ON TABLE ember_schema.member_document_mail_origin
    IS 'Where a document came from where it arrived by mail. Lives and dies with the document, so its history outlasts the import log.';
COMMENT ON COLUMN ember_schema.member_document_mail_origin.mailbox_id
    IS 'Which mailbox brought it, kept as a reference that may be cleared: a document does not stop having arrived by mail because the mailbox was later removed.';
COMMENT ON COLUMN ember_schema.member_document_mail_origin.received_at
    IS 'When the mail arrived, which is the date a reader means by asking when the document came in.';
