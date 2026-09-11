-- Documents get a permission for the ones that name a member, and mail rules stop naming members at all.
--
-- The first two document permissions borrowed the member permission for the hard half of the question:
-- a document naming a member was readable by whoever could read that member. That coupled the store to
-- a permission about people, and it made the binding itself the gate, which a holder of the edit
-- permission was allowed to delete. The three new rows finish the split, so reaching member paperwork
-- is its own permission rather than a consequence of another one.
--
-- Granted by implication rather than by migration, the way the first two were: MEMBER_READ implies
-- DOCUMENT_READ_MEMBER and MEMBER_EDIT implies DOCUMENT_EDIT_MEMBER in the permission enum, so every
-- role keeps exactly what it holds today and no station needs a data migration.
INSERT INTO ember_schema.station_permission (name)
VALUES ('DOCUMENT_READ_MEMBER'),
       ('DOCUMENT_EDIT_MEMBER'),
       ('DOCUMENT_MANAGER')
ON CONFLICT (name) DO NOTHING;

-- A mail rule used to carry the members its attachments belong to, as ids taken from the request.
-- Nothing checked which station those members lived in, so a rule in one station could file documents
-- onto members of another. An id is also nothing anybody picks: the subject line carries the name, and
-- reading it there is scoped to the station by the candidate list it is matched against.
DROP TABLE IF EXISTS ember_schema.mail_rule_member;

-- The mark saying a rule lost a member it could no longer file under goes with them. It was set when a
-- member left and the rule that named them was rewritten underneath somebody, which cannot happen to a
-- rule that names nobody in the first place.
ALTER TABLE ember_schema.mail_rule
    DROP COLUMN IF EXISTS lost_member;

-- A mailbox can have the sender's signature checked here instead of taking a header on trust.
--
-- The address a rule matches is written by whoever sent the message, so a domain publishing no strict
-- policy can be written into a From line by anybody. Checking the DKIM signature answers that, with one
-- condition that is the whole point of it: the signing domain has to be the domain the address claims.
-- A signature on its own proves only that somebody signed, and whoever sends the forgery can sign it
-- with a domain of their own.
--
-- Off by default, because a station whose correspondents do not sign their mail would turn it on and
-- refuse the very post it collects.
ALTER TABLE ember_schema.mail_mailbox
    ADD COLUMN IF NOT EXISTS verify_dkim BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN ember_schema.mail_mailbox.verify_dkim
    IS 'Whether a message has to carry a DKIM signature that verifies and whose signing domain is the one the sender address claims. Off by default: only a station whose correspondents sign their mail can turn it on without refusing what it came for.';

-- Three more things that can become of an attachment, all of them from the check above.
COMMENT ON COLUMN ember_schema.mail_import_entry.outcome
    IS 'IMPORTED, DUPLICATE, SENDER_NOT_ALLOWED, NO_RULE_MATCHED, TYPE_NOT_ALLOWED, TOO_LARGE, TOO_SMALL, NO_ATTACHMENT, QUOTA_EXCEEDED, AUTHENTICATION_FAILED, NO_SIGNATURE, SIGNATURE_NOT_ALIGNED, SIGNATURE_FAILED or FAILED.';
