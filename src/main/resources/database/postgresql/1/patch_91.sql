-- Push notifications: the key pair this installation signs every push with.

CREATE TABLE ember_schema.push_vapid_key
(
    id                  SMALLINT    NOT NULL PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    public_key          BYTEA       NOT NULL,
    wrapped_private_key BYTEA       NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE ember_schema.push_vapid_key IS
    'The one key pair this installation signs its push messages with. Browsers bind their subscriptions to the public key, so replacing it ends every subscription.';
COMMENT ON COLUMN ember_schema.push_vapid_key.id IS
    'Always 1: there is exactly one key pair per installation.';
COMMENT ON COLUMN ember_schema.push_vapid_key.public_key IS
    'The public key as an uncompressed P-256 point, 65 bytes, handed to browsers when they subscribe.';
COMMENT ON COLUMN ember_schema.push_vapid_key.wrapped_private_key IS
    'The private key, wrapped under the at-rest secret like the signing keys. Lost or unreadable, every subscription has to be made again.';
COMMENT ON COLUMN ember_schema.push_vapid_key.created_at IS
    'When the key pair was generated.';

-- The browsers that receive push messages, one row per browser an account allowed it on. A row goes
-- with the sign-in it was made in, so signing out, being signed out elsewhere or a session running
-- out stops the pushes to that browser.

CREATE TABLE ember_schema.push_subscription
(
    id              SERIAL PRIMARY KEY,
    account_id      INTEGER     NOT NULL REFERENCES ember_schema.account (id) ON DELETE CASCADE,
    session_id      INTEGER     NOT NULL REFERENCES ember_schema.account_session (id) ON DELETE CASCADE,
    endpoint        TEXT        NOT NULL UNIQUE,
    p256dh          BYTEA       NOT NULL,
    auth            BYTEA       NOT NULL,
    label           TEXT        NOT NULL DEFAULT '',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_success_at TIMESTAMPTZ,
    failure_count   INTEGER     NOT NULL DEFAULT 0
);

CREATE INDEX idx_push_subscription_account ON ember_schema.push_subscription (account_id);
CREATE INDEX idx_push_subscription_session ON ember_schema.push_subscription (session_id);

COMMENT ON TABLE ember_schema.push_subscription IS
    'Browsers an account allowed push messages on, each bound to the sign-in it was registered in.';
COMMENT ON COLUMN ember_schema.push_subscription.id IS 'Primary key.';
COMMENT ON COLUMN ember_schema.push_subscription.account_id IS 'The account the browser receives pushes for.';
COMMENT ON COLUMN ember_schema.push_subscription.session_id IS
    'The sign-in the browser registered in. When it ends, the subscription ends with it.';
COMMENT ON COLUMN ember_schema.push_subscription.endpoint IS
    'The address of the push service that delivers to this browser, issued by the browser.';
COMMENT ON COLUMN ember_schema.push_subscription.p256dh IS
    'The browser''s public key, 65 bytes, that every push is encrypted for.';
COMMENT ON COLUMN ember_schema.push_subscription.auth IS
    'The browser''s 16-byte secret that goes into every push''s encryption.';
COMMENT ON COLUMN ember_schema.push_subscription.label IS
    'A short name of the browser and device, such as "Firefox auf Android", so the owner recognises it.';
COMMENT ON COLUMN ember_schema.push_subscription.created_at IS 'When the browser was registered.';
COMMENT ON COLUMN ember_schema.push_subscription.last_success_at IS 'When a push last reached the push service.';
COMMENT ON COLUMN ember_schema.push_subscription.failure_count IS
    'Pushes in a row the push service refused; past a limit the subscription is dropped.';

-- Pushes waiting to be sent, written in the same statement as what they are about so none is lost
-- on a restart. A sent row stays for an hour, which is what the per-browser rate limit counts.

CREATE TABLE ember_schema.push_outbox
(
    id              BIGSERIAL PRIMARY KEY,
    subscription_id INTEGER     NOT NULL REFERENCES ember_schema.push_subscription (id) ON DELETE CASCADE,
    kind            TEXT        NOT NULL CHECK (kind IN ('TEST', 'NOTIFICATION', 'MESSAGE')),
    notification_id INTEGER     NULL REFERENCES ember_schema.notification (id) ON DELETE CASCADE,
    message_id      INTEGER     NULL REFERENCES ember_schema.message (id) ON DELETE CASCADE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    attempts        INTEGER     NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deferred        BOOLEAN     NOT NULL DEFAULT FALSE,
    sent_at         TIMESTAMPTZ NULL,
    CONSTRAINT push_outbox_names_its_notification CHECK ((kind = 'NOTIFICATION') = (notification_id IS NOT NULL)),
    CONSTRAINT push_outbox_names_its_message CHECK ((kind = 'MESSAGE') = (message_id IS NOT NULL))
);

CREATE INDEX idx_push_outbox_due ON ember_schema.push_outbox (next_attempt_at) WHERE sent_at IS NULL;
CREATE INDEX idx_push_outbox_sent ON ember_schema.push_outbox (subscription_id, sent_at) WHERE sent_at IS NOT NULL;
CREATE INDEX idx_push_outbox_notification ON ember_schema.push_outbox (notification_id);
CREATE INDEX idx_push_outbox_message ON ember_schema.push_outbox (message_id);

COMMENT ON TABLE ember_schema.push_outbox IS
    'Pushes waiting to go out, and those sent within the last hour for the rate limit.';
COMMENT ON COLUMN ember_schema.push_outbox.id IS 'Primary key.';
COMMENT ON COLUMN ember_schema.push_outbox.subscription_id IS 'The browser the push goes to.';
COMMENT ON COLUMN ember_schema.push_outbox.kind IS 'What the push is about: a test the owner asked for, a notification or a message.';
COMMENT ON COLUMN ember_schema.push_outbox.notification_id IS 'The notification the push is about, for kind NOTIFICATION.';
COMMENT ON COLUMN ember_schema.push_outbox.message_id IS 'The message the push is about, for kind MESSAGE.';
COMMENT ON COLUMN ember_schema.push_outbox.created_at IS 'When the push was queued.';
COMMENT ON COLUMN ember_schema.push_outbox.attempts IS 'How often sending was tried and the push service asked to wait.';
COMMENT ON COLUMN ember_schema.push_outbox.next_attempt_at IS 'When the push is due, or tried again.';
COMMENT ON COLUMN ember_schema.push_outbox.deferred IS
    'Held back by the rate limit; several held back for one browser go out as one summary.';
COMMENT ON COLUMN ember_schema.push_outbox.sent_at IS 'When the push service took it, empty while waiting.';

-- Whether a notification type also goes out as a push. On by default: whoever allowed push on a
-- device expects to receive something, and only types also shown in Ember are pushed.

ALTER TABLE ember_schema.user_notification_settings
    ADD COLUMN push_enabled BOOLEAN NOT NULL DEFAULT TRUE;

COMMENT ON COLUMN ember_schema.user_notification_settings.push_enabled IS
    'Whether this type is also pushed to the devices the member''s account allowed push on.';

ALTER TABLE ember_schema.cluster_member
    ADD COLUMN push_enabled BOOLEAN NOT NULL DEFAULT TRUE;

COMMENT ON COLUMN ember_schema.cluster_member.push_enabled IS
    'Whether the association''s notifications are also pushed to the devices the member''s account allowed push on.';

-- Whether new messages in the account's conversations are pushed to its devices. On by default,
-- like the daily message mail; muted conversations stay quiet either way.

ALTER TABLE ember_schema.account
    ADD COLUMN messages_push BOOLEAN NOT NULL DEFAULT TRUE;

COMMENT ON COLUMN ember_schema.account.messages_push IS
    'Whether new messages in the account''s conversations are pushed to the devices it allowed push on.';

ALTER TABLE ember_schema.user_settings
    ALTER COLUMN email_enabled SET DEFAULT TRUE;
UPDATE ember_schema.user_settings SET email_enabled = TRUE WHERE NOT email_enabled;

COMMENT ON COLUMN ember_schema.user_settings.email_enabled IS
    'Whether this membership gets notifications by mail at all. On unless switched off, which takes effect once the station or the instance can send mail.';

ALTER TABLE ember_schema.user_notification_settings
    ALTER COLUMN email_enabled SET DEFAULT TRUE;
UPDATE ember_schema.user_notification_settings SET email_enabled = TRUE WHERE NOT email_enabled;

ALTER TABLE ember_schema.cluster_member
    ALTER COLUMN email_enabled SET DEFAULT TRUE;
UPDATE ember_schema.cluster_member SET email_enabled = TRUE WHERE NOT email_enabled;

COMMENT ON COLUMN ember_schema.cluster_member.email_enabled IS
    'Whether this person gets the association''s gathered notifications by mail. On unless switched off, the same as a station membership''s mail setting.';

-- A group or a tag can take its members from a rule instead of a hand-kept list. A rule is a tree of
-- blocks (all of, any of, none of) and checks on member data. Who matches is written into the same
-- member_group_entry and user_tag_entry rows a hand-kept group or tag uses.

CREATE TABLE ember_schema.member_rule_node
(
    id               SERIAL PRIMARY KEY,
    station_id       INTEGER NOT NULL REFERENCES ember_schema.station (id) ON DELETE CASCADE,
    group_id         INTEGER NULL REFERENCES ember_schema.member_group (id) ON DELETE CASCADE,
    tag_id           INTEGER NULL REFERENCES ember_schema.user_tag (id) ON DELETE CASCADE,
    parent_id        INTEGER NULL REFERENCES ember_schema.member_rule_node (id) ON DELETE CASCADE,
    position         INTEGER NOT NULL DEFAULT 0,
    kind             TEXT    NOT NULL CHECK (kind IN ('ALL', 'ANY', 'NONE', 'CHECK')),
    subject          TEXT    NULL CHECK (subject IN ('GROUP', 'TAG', 'USER_TYPE', 'MEMBER_SINCE', 'AGE', 'FIELD',
                                                     'CLUSTER_FIELD')),
    operator         TEXT    NULL CHECK (operator IN ('IN', 'NOT_IN', 'ONE_OF', 'NONE_OF', 'AT_LEAST', 'LESS_THAN',
                                                      'BETWEEN', 'IS_YES', 'IS_NO', 'IS_NOT_YES', 'EMPTY',
                                                      'NOT_EMPTY', 'EQUALS', 'CONTAINS', 'EQ', 'LT', 'LE', 'GT',
                                                      'GE', 'BEFORE', 'ON_OR_AFTER', 'WITHIN_NEXT', 'WITHIN_LAST',
                                                      'EXPIRES_WITHIN', 'EXPIRED', 'STATE_IN')),
    field_id         INTEGER NULL REFERENCES ember_schema.profile_field (id),
    cluster_field_id INTEGER NULL REFERENCES ember_schema.cluster_profile_field (id) ON DELETE SET NULL,
    ref_group_id     INTEGER NULL REFERENCES ember_schema.member_group (id),
    ref_tag_id       INTEGER NULL REFERENCES ember_schema.user_tag (id),
    value            JSONB   NULL,
    reference_date   DATE    NULL,
    CONSTRAINT member_rule_node_root_names_its_owner
        CHECK ((parent_id IS NULL AND num_nonnulls(group_id, tag_id) = 1)
            OR (parent_id IS NOT NULL AND group_id IS NULL AND tag_id IS NULL)),
    CONSTRAINT member_rule_node_root_is_a_block
        CHECK (parent_id IS NOT NULL OR kind <> 'CHECK'),
    CONSTRAINT member_rule_node_check_says_what
        CHECK ((kind = 'CHECK') = (subject IS NOT NULL AND operator IS NOT NULL))
);

CREATE UNIQUE INDEX idx_member_rule_node_group ON ember_schema.member_rule_node (group_id) WHERE group_id IS NOT NULL;
CREATE UNIQUE INDEX idx_member_rule_node_tag ON ember_schema.member_rule_node (tag_id) WHERE tag_id IS NOT NULL;
CREATE INDEX idx_member_rule_node_station ON ember_schema.member_rule_node (station_id);
CREATE INDEX idx_member_rule_node_parent ON ember_schema.member_rule_node (parent_id) WHERE parent_id IS NOT NULL;
CREATE INDEX idx_member_rule_node_field ON ember_schema.member_rule_node (field_id) WHERE field_id IS NOT NULL;
CREATE INDEX idx_member_rule_node_cluster_field
    ON ember_schema.member_rule_node (cluster_field_id) WHERE cluster_field_id IS NOT NULL;
CREATE INDEX idx_member_rule_node_ref_group ON ember_schema.member_rule_node (ref_group_id) WHERE ref_group_id IS NOT NULL;
CREATE INDEX idx_member_rule_node_ref_tag ON ember_schema.member_rule_node (ref_tag_id) WHERE ref_tag_id IS NOT NULL;

COMMENT ON TABLE ember_schema.member_rule_node
    IS 'One node of the rule a dynamic group or tag takes its members from: a block joining its children, or a check on member data. The root names the group or tag; a group or tag with a root is kept by its rule and refuses hand edits.';
COMMENT ON COLUMN ember_schema.member_rule_node.id
    IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.member_rule_node.station_id
    IS 'The station the rule belongs to.';
COMMENT ON COLUMN ember_schema.member_rule_node.group_id
    IS 'On the root node of a group''s rule: the group the rule fills. NULL on every other node. Deleted with the group.';
COMMENT ON COLUMN ember_schema.member_rule_node.tag_id
    IS 'On the root node of a tag''s rule: the tag the rule fills. NULL on every other node. Deleted with the tag.';
COMMENT ON COLUMN ember_schema.member_rule_node.parent_id
    IS 'The block this node sits in. NULL on the root.';
COMMENT ON COLUMN ember_schema.member_rule_node.position
    IS 'The order of the node among the children of its block.';
COMMENT ON COLUMN ember_schema.member_rule_node.kind
    IS 'ALL (every child holds), ANY (at least one child holds), NONE (no child holds) or CHECK (a single check).';
COMMENT ON COLUMN ember_schema.member_rule_node.subject
    IS 'On a check: what it looks at (GROUP, TAG, USER_TYPE, MEMBER_SINCE, AGE, FIELD, CLUSTER_FIELD). NULL on a block.';
COMMENT ON COLUMN ember_schema.member_rule_node.operator
    IS 'On a check: how it compares, for example IN, ONE_OF, AT_LEAST, IS_NO, EMPTY or EXPIRES_WITHIN. NULL on a block.';
COMMENT ON COLUMN ember_schema.member_rule_node.field_id
    IS 'The station profile field a field check or an age check reads. A field in use by a rule cannot be deleted.';
COMMENT ON COLUMN ember_schema.member_rule_node.cluster_field_id
    IS 'The association profile field a field check or an age check reads. Emptied when the association deletes the field; such a check then matches nobody.';
COMMENT ON COLUMN ember_schema.member_rule_node.ref_group_id
    IS 'The group a group check asks about. A group in use by a rule cannot be deleted.';
COMMENT ON COLUMN ember_schema.member_rule_node.ref_tag_id
    IS 'The tag a tag check asks about. A tag in use by a rule cannot be deleted.';
COMMENT ON COLUMN ember_schema.member_rule_node.value
    IS 'What a check compares with: answers, user types, expiry states, a number or range, a text, a date or a duration. NULL where the operator needs nothing.';
COMMENT ON COLUMN ember_schema.member_rule_node.reference_date
    IS 'The day a date-relative check counts from, such as an age on a fixed date. NULL means the current day.';
