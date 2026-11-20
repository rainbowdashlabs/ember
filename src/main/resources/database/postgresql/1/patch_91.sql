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
COMMENT ON COLUMN ember_schema.push_subscription.created_at IS 'When the browser was last registered or renewed; the cap on devices drops the oldest first.';
COMMENT ON COLUMN ember_schema.push_subscription.last_success_at IS 'When a push last reached the push service.';
COMMENT ON COLUMN ember_schema.push_subscription.failure_count IS
    'Pushes in a row the push service refused; past a limit the subscription is dropped.';

CREATE TABLE ember_schema.push_browser
(
    account_id INTEGER     NOT NULL REFERENCES ember_schema.account (id) ON DELETE CASCADE,
    endpoint   TEXT        NOT NULL,
    allowed    BOOLEAN     NOT NULL,
    changed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (account_id, endpoint)
);

COMMENT ON TABLE ember_schema.push_browser IS
    'Browsers an account allowed push on, or removed from it by hand. Outlives the sign-in, so a browser is only registered again without asking where its account allowed it and did not remove it since.';
COMMENT ON COLUMN ember_schema.push_browser.account_id IS 'The account that allowed or removed the browser.';
COMMENT ON COLUMN ember_schema.push_browser.endpoint IS
    'The address of the push service of the browser, as it was registered.';
COMMENT ON COLUMN ember_schema.push_browser.allowed IS
    'True once the account allowed push on the browser, false once it removed the browser by hand.';
COMMENT ON COLUMN ember_schema.push_browser.changed_at IS
    'When the browser was last registered, allowed or removed. Six months later, a browser that is not registered any more is forgotten.';

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

COMMENT ON COLUMN ember_schema.user_settings.email_enabled IS
    'Whether this membership gets notifications by mail at all. On unless switched off, which takes effect once the station or the instance can send mail.';

ALTER TABLE ember_schema.user_notification_settings
    ALTER COLUMN email_enabled SET DEFAULT TRUE;

ALTER TABLE ember_schema.cluster_member
    ALTER COLUMN email_enabled SET DEFAULT TRUE;

COMMENT ON COLUMN ember_schema.cluster_member.email_enabled IS
    'Whether this person gets the association''s gathered notifications by mail. On unless switched off, the same as a station membership''s mail setting.';

-- A station keeps one map pin per place text its appointments name, such as "Gerätehaus". The pin is
-- found or corrected once and shown on every appointment naming that place. The field value itself
-- stays plain text; the pin is looked up by the text in its normal form.

CREATE TABLE ember_schema.station_place
(
    station_id INTEGER       NOT NULL REFERENCES ember_schema.station (id) ON DELETE CASCADE,
    place_key  TEXT          NOT NULL,
    place_text TEXT          NOT NULL,
    latitude   NUMERIC(9, 6) NOT NULL CHECK (latitude BETWEEN -90 AND 90),
    longitude  NUMERIC(9, 6) NOT NULL CHECK (longitude BETWEEN -180 AND 180),
    precision  TEXT          NULL CHECK (precision IN ('EXACT', 'STREET', 'AREA')),
    updated_at TIMESTAMPTZ   NOT NULL DEFAULT now(),
    PRIMARY KEY (station_id, place_key)
);

COMMENT ON TABLE ember_schema.station_place IS
    'Map pins for the place texts a station''s appointments name, one per station and place text. Rows no appointment names any more are harmless and stay.';
COMMENT ON COLUMN ember_schema.station_place.station_id IS
    'The station the place belongs to. Deleting the station deletes its places.';
COMMENT ON COLUMN ember_schema.station_place.place_key IS
    'The place text in its normal form: trimmed, in lower case, every run of whitespace made one space. Appointments find their pin by it.';
COMMENT ON COLUMN ember_schema.station_place.place_text IS
    'The place text as it was last saved, for display.';
COMMENT ON COLUMN ember_schema.station_place.latitude IS
    'WGS-84 latitude of the pin in degrees, [-90, 90].';
COMMENT ON COLUMN ember_schema.station_place.longitude IS
    'WGS-84 longitude of the pin in degrees, [-180, 180].';
COMMENT ON COLUMN ember_schema.station_place.precision IS
    'How closely the address search matched when it placed the pin: EXACT, STREET or AREA. NULL when the pin was placed or moved by hand.';
COMMENT ON COLUMN ember_schema.station_place.updated_at IS
    'When the pin was last saved.';

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
    IS 'The association profile field a field check or an age check reads. Emptied when the association deletes the field; a rule with such a check is not run and keeps its members until it is changed.';
COMMENT ON COLUMN ember_schema.member_rule_node.ref_group_id
    IS 'The group a group check asks about. A group in use by a rule cannot be deleted.';
COMMENT ON COLUMN ember_schema.member_rule_node.ref_tag_id
    IS 'The tag a tag check asks about. A tag in use by a rule cannot be deleted.';
COMMENT ON COLUMN ember_schema.member_rule_node.value
    IS 'What a check compares with: answers, user types, expiry states, a number or range, a text, a date or a duration. NULL where the operator needs nothing.';
COMMENT ON COLUMN ember_schema.member_rule_node.reference_date
    IS 'The day a date-relative check counts from, such as an age on a fixed date. NULL means the current day.';

-- Waiting lists: a sign-up for a full appointment or event group waits in order for a place. The new
-- status QUEUED marks it on the registration; the order lives in one list per appointment date and one
-- per event group, which holds members of this station and of partner stations alike.

ALTER TABLE ember_schema.event_group_registration
    DROP CONSTRAINT IF EXISTS event_group_registration_status_check,
    ADD CONSTRAINT event_group_registration_status_check
        CHECK (status IN ('PENDING', 'ACCEPTED', 'DENIED', 'DECLINED', 'WITHDRAWN', 'QUEUED'));

ALTER TABLE ember_schema.event_group_partner_registration
    DROP CONSTRAINT IF EXISTS event_group_partner_registration_status_check,
    ADD CONSTRAINT event_group_partner_registration_status_check
        CHECK (status IN ('PENDING', 'ACCEPTED', 'DENIED', 'DECLINED', 'WITHDRAWN', 'QUEUED'));

COMMENT ON COLUMN ember_schema.event_registration.status
    IS 'PENDING, ACCEPTED, DENIED, DECLINED, WITHDRAWN, or QUEUED while waiting on the waiting list for a place.';
COMMENT ON COLUMN ember_schema.event_federation_registration.status
    IS 'PENDING, ACCEPTED, DENIED, DECLINED, WITHDRAWN, or QUEUED while waiting on the waiting list for a place.';
COMMENT ON COLUMN ember_schema.event_group_registration.status
    IS 'PENDING, ACCEPTED, DENIED, DECLINED, WITHDRAWN, or QUEUED while waiting on the waiting list, as for an appointment sign-up.';
COMMENT ON COLUMN ember_schema.event_group_partner_registration.status
    IS 'PENDING, ACCEPTED, DENIED, DECLINED, WITHDRAWN or QUEUED, as for a member of this station.';

ALTER TABLE ember_schema.station_event
    ADD COLUMN queue_order TEXT NOT NULL DEFAULT 'TIME' CHECK (queue_order IN ('TIME', 'FAIRNESS'));
ALTER TABLE ember_schema.event_template
    ADD COLUMN queue_order TEXT NOT NULL DEFAULT 'TIME' CHECK (queue_order IN ('TIME', 'FAIRNESS'));
ALTER TABLE ember_schema.event_group
    ADD COLUMN queue_order TEXT NOT NULL DEFAULT 'TIME' CHECK (queue_order IN ('TIME', 'FAIRNESS')),
    ADD COLUMN part_registration_limit INT CHECK (part_registration_limit IS NULL OR part_registration_limit > 0);
ALTER TABLE ember_schema.event_group_set
    ADD COLUMN maximum INT,
    ADD CONSTRAINT event_group_set_maximum_range CHECK (maximum IS NULL OR (maximum >= 1 AND maximum >= minimum));

COMMENT ON COLUMN ember_schema.station_event.queue_order
    IS 'How the waiting list of a full date is ordered: TIME (oldest sign-up first) or FAIRNESS (highest fairness score first, ties by time).';
COMMENT ON COLUMN ember_schema.event_template.queue_order
    IS 'The waiting list order an appointment created from the template starts with: TIME or FAIRNESS.';
COMMENT ON COLUMN ember_schema.event_group.queue_order
    IS 'How the waiting list of the event group is ordered: TIME (oldest sign-up first) or FAIRNESS (highest fairness score first, ties by time).';
COMMENT ON COLUMN ember_schema.event_group.part_registration_limit
    IS 'How many places each part has, for parts without a limit of their own; NULL for no limit. Counts accepted registrations of the part.';
COMMENT ON COLUMN ember_schema.event_group_set.maximum
    IS 'How many parts of the set a sign-up may choose at most; NULL for no maximum. Never below the minimum.';

UPDATE ember_schema.station_event
SET registration_limit = NULL
WHERE event_group_set_id IS NOT NULL
  AND registration_limit IS NOT NULL;

CREATE TABLE ember_schema.registration_queue
(
    id                         SERIAL PRIMARY KEY,
    event_id                   INT           NOT NULL REFERENCES ember_schema.station_event (id) ON DELETE CASCADE,
    event_date                 DATE          NOT NULL,
    registration_id            INT UNIQUE REFERENCES ember_schema.event_registration (id) ON DELETE CASCADE,
    federation_registration_id INT UNIQUE REFERENCES ember_schema.event_federation_registration (id) ON DELETE CASCADE,
    position                   INT           NOT NULL CHECK (position >= 1),
    score                      NUMERIC(10, 2),
    joined_at                  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT registration_queue_one_owner CHECK (num_nonnulls(registration_id, federation_registration_id) = 1),
    CONSTRAINT registration_queue_position_unique UNIQUE (event_id, event_date, position) DEFERRABLE INITIALLY DEFERRED
);

COMMENT ON TABLE ember_schema.registration_queue
    IS 'The waiting list of one appointment date: sign-ups with status QUEUED in the order they move up in. Parts of an event group have none; their event group keeps the list.';
COMMENT ON COLUMN ember_schema.registration_queue.id
    IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.registration_queue.event_id
    IS 'The appointment waited for.';
COMMENT ON COLUMN ember_schema.registration_queue.event_date
    IS 'The date of the appointment waited for; a series keeps one list per date.';
COMMENT ON COLUMN ember_schema.registration_queue.registration_id
    IS 'The waiting sign-up of a member of this station; NULL for a partner''s member.';
COMMENT ON COLUMN ember_schema.registration_queue.federation_registration_id
    IS 'The waiting sign-up of a partner station''s member; NULL for a member of this station.';
COMMENT ON COLUMN ember_schema.registration_queue.position
    IS 'The order of the list, lowest moves up next. Gaps are allowed; the position a member is shown is the rank.';
COMMENT ON COLUMN ember_schema.registration_queue.score
    IS 'The fairness score the sign-up had when it joined the list, kept whatever the order so a list changed to fairness later can still use it.';
COMMENT ON COLUMN ember_schema.registration_queue.joined_at
    IS 'When the sign-up joined the list.';

CREATE TABLE ember_schema.event_group_queue
(
    id                 SERIAL PRIMARY KEY,
    event_group_id     INT         NOT NULL REFERENCES ember_schema.event_group (id) ON DELETE CASCADE,
    sign_up_id         INT UNIQUE REFERENCES ember_schema.event_group_registration (id) ON DELETE CASCADE,
    partner_sign_up_id INT UNIQUE REFERENCES ember_schema.event_group_partner_registration (id) ON DELETE CASCADE,
    position           INT         NOT NULL CHECK (position >= 1),
    score              NUMERIC(10, 2),
    joined_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT event_group_queue_one_owner CHECK (num_nonnulls(sign_up_id, partner_sign_up_id) = 1),
    CONSTRAINT event_group_queue_position_unique UNIQUE (event_group_id, position) DEFERRABLE INITIALLY DEFERRED
);

COMMENT ON TABLE ember_schema.event_group_queue
    IS 'The waiting list of an event group: sign-ups with status QUEUED, each with the parts it chose, in the order they move up in.';
COMMENT ON COLUMN ember_schema.event_group_queue.id
    IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.event_group_queue.event_group_id
    IS 'The event group waited for.';
COMMENT ON COLUMN ember_schema.event_group_queue.sign_up_id
    IS 'The waiting sign-up of a member of this station; NULL for a partner''s member.';
COMMENT ON COLUMN ember_schema.event_group_queue.partner_sign_up_id
    IS 'The waiting sign-up of a partner station''s member; NULL for a member of this station.';
COMMENT ON COLUMN ember_schema.event_group_queue.position
    IS 'The order of the list, lowest moves up first. Gaps are allowed; the position a member is shown is the rank.';
COMMENT ON COLUMN ember_schema.event_group_queue.score
    IS 'The fairness score the sign-up had when it joined the list, kept whatever the order so a list changed to fairness later can still use it.';
COMMENT ON COLUMN ember_schema.event_group_queue.joined_at
    IS 'When the sign-up joined the list.';

-- A sign-up that leaves QUEUED leaves its waiting list, whoever writes the status: moving up, leaving,
-- a decline, a denial or a date going away. Only the writer that puts a sign-up on a list adds rows.

CREATE OR REPLACE FUNCTION ember_schema.registration_queue_leave() RETURNS TRIGGER
    LANGUAGE plpgsql
AS
$$
BEGIN
    IF TG_TABLE_NAME = 'event_registration' THEN
        DELETE FROM ember_schema.registration_queue WHERE registration_id = NEW.id;
    ELSE
        DELETE FROM ember_schema.registration_queue WHERE federation_registration_id = NEW.id;
    END IF;
    RETURN NULL;
END;
$$;

COMMENT ON FUNCTION ember_schema.registration_queue_leave()
    IS 'Takes an appointment sign-up off its waiting list once its status is no longer QUEUED.';

CREATE TRIGGER event_registration_queue_leave
    AFTER UPDATE OF status
    ON ember_schema.event_registration
    FOR EACH ROW
    WHEN (OLD.status = 'QUEUED' AND NEW.status <> 'QUEUED')
EXECUTE FUNCTION ember_schema.registration_queue_leave();

CREATE TRIGGER event_federation_registration_queue_leave
    AFTER UPDATE OF status
    ON ember_schema.event_federation_registration
    FOR EACH ROW
    WHEN (OLD.status = 'QUEUED' AND NEW.status <> 'QUEUED')
EXECUTE FUNCTION ember_schema.registration_queue_leave();

CREATE OR REPLACE FUNCTION ember_schema.event_group_queue_leave() RETURNS TRIGGER
    LANGUAGE plpgsql
AS
$$
BEGIN
    IF TG_TABLE_NAME = 'event_group_registration' THEN
        DELETE FROM ember_schema.event_group_queue WHERE sign_up_id = NEW.id;
    ELSE
        DELETE FROM ember_schema.event_group_queue WHERE partner_sign_up_id = NEW.id;
    END IF;
    RETURN NULL;
END;
$$;

COMMENT ON FUNCTION ember_schema.event_group_queue_leave()
    IS 'Takes an event group sign-up off its waiting list once its status is no longer QUEUED.';

CREATE TRIGGER event_group_registration_queue_leave
    AFTER UPDATE OF status
    ON ember_schema.event_group_registration
    FOR EACH ROW
    WHEN (OLD.status = 'QUEUED' AND NEW.status <> 'QUEUED')
EXECUTE FUNCTION ember_schema.event_group_queue_leave();

CREATE TRIGGER event_group_partner_registration_queue_leave
    AFTER UPDATE OF status
    ON ember_schema.event_group_partner_registration
    FOR EACH ROW
    WHEN (OLD.status = 'QUEUED' AND NEW.status <> 'QUEUED')
EXECUTE FUNCTION ember_schema.event_group_queue_leave();

CREATE TABLE IF NOT EXISTS ember_schema.event_federation_registration_answer
(
    federation_registration_id INT  NOT NULL REFERENCES ember_schema.event_federation_registration (id) ON DELETE CASCADE,
    field_id                   INT  NOT NULL REFERENCES ember_schema.event_registration_field (id) ON DELETE CASCADE,
    value                      TEXT NOT NULL DEFAULT '',
    PRIMARY KEY (federation_registration_id, field_id)
);

CREATE INDEX IF NOT EXISTS idx_event_federation_registration_answer_field
    ON ember_schema.event_federation_registration_answer (field_id);

COMMENT ON TABLE ember_schema.event_federation_registration_answer
    IS 'A partner station''s member''s answer to one registration question of a shared appointment, given when the partner signed them up.';
COMMENT ON COLUMN ember_schema.event_federation_registration_answer.federation_registration_id
    IS 'The partner''s registration the answer belongs to; the answer is removed with it.';
COMMENT ON COLUMN ember_schema.event_federation_registration_answer.field_id
    IS 'The question answered.';
COMMENT ON COLUMN ember_schema.event_federation_registration_answer.value
    IS 'The answer, stored as text in the same shape the answers of this station''s own members use.';

-- A sign-up deadline is one rule: none, a fixed instant, or a span before the start of each date,
-- either to the minute or down to the midnight that begins that day. It replaces the absolute
-- deadline beside the "closes N days before" count on appointments, and the hours offset beside the
-- day count on templates. A fixed deadline wins where an appointment carried both.

ALTER TABLE ember_schema.station_event
    ADD COLUMN registration_deadline_kind           TEXT NOT NULL DEFAULT 'NONE'
        CHECK (registration_deadline_kind IN ('NONE', 'FIXED', 'RELATIVE')),
    ADD COLUMN registration_deadline_offset_minutes INT CHECK (registration_deadline_offset_minutes >= 0),
    ADD COLUMN registration_deadline_anchor         TEXT
        CHECK (registration_deadline_anchor IN ('EXACT', 'MIDNIGHT'));

UPDATE ember_schema.station_event
SET registration_deadline_kind = 'FIXED'
WHERE registration_deadline IS NOT NULL;

UPDATE ember_schema.station_event
SET registration_deadline_kind           = 'RELATIVE',
    registration_deadline_offset_minutes = GREATEST(0, LEAST(registration_close_days, 3650)) * 1440,
    registration_deadline_anchor         = 'MIDNIGHT'
WHERE registration_deadline IS NULL
  AND registration_close_days IS NOT NULL;

ALTER TABLE ember_schema.station_event
    ADD CONSTRAINT station_event_registration_deadline_rule CHECK (
        (registration_deadline_kind = 'NONE' AND registration_deadline IS NULL
            AND registration_deadline_offset_minutes IS NULL AND registration_deadline_anchor IS NULL)
        OR (registration_deadline_kind = 'FIXED' AND registration_deadline IS NOT NULL
            AND registration_deadline_offset_minutes IS NULL AND registration_deadline_anchor IS NULL)
        OR (registration_deadline_kind = 'RELATIVE' AND registration_deadline IS NULL
            AND registration_deadline_offset_minutes IS NOT NULL AND registration_deadline_anchor IS NOT NULL));

COMMENT ON COLUMN ember_schema.station_event.registration_deadline
    IS 'The instant sign-up closes for a FIXED deadline, on every date of a series at once; NULL for any other kind.';
COMMENT ON COLUMN ember_schema.station_event.registration_deadline_kind
    IS 'Which sign-up deadline applies: NONE, FIXED (registration_deadline) or RELATIVE (a span before the start of each date).';
COMMENT ON COLUMN ember_schema.station_event.registration_deadline_offset_minutes
    IS 'For a RELATIVE deadline, how many minutes before the start of each date sign-up closes; NULL for any other kind.';
COMMENT ON COLUMN ember_schema.station_event.registration_deadline_anchor
    IS 'For a RELATIVE deadline, EXACT closes at start minus the offset, MIDNIGHT at the midnight that begins that day on the station''s clock; NULL for any other kind.';

-- Which dates have had their deadline pass, so the sweep tells the managers about the sign-ups still
-- waiting once per date. It replaces the single flag on the appointment, which a series could not use.

CREATE TABLE IF NOT EXISTS ember_schema.event_deadline_expired
(
    event_id        INT         NOT NULL REFERENCES ember_schema.station_event (id) ON DELETE CASCADE,
    occurrence_date DATE        NOT NULL,
    handled_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (event_id, occurrence_date)
);

COMMENT ON TABLE ember_schema.event_deadline_expired
    IS 'The dates of an appointment whose sign-up deadline has passed and was handled, so each is handled once.';
COMMENT ON COLUMN ember_schema.event_deadline_expired.event_id
    IS 'The appointment; the rows go with it.';
COMMENT ON COLUMN ember_schema.event_deadline_expired.occurrence_date
    IS 'The date whose deadline passed, on the station''s clock.';
COMMENT ON COLUMN ember_schema.event_deadline_expired.handled_at
    IS 'When the passed deadline was handled.';

INSERT INTO ember_schema.event_deadline_expired(event_id, occurrence_date)
SELECT e.id, (e.start_time AT TIME ZONE s.timezone)::DATE
FROM ember_schema.station_event e
         JOIN ember_schema.station s ON s.id = e.station_id
WHERE e.deadline_notified
  AND e.event_type = 'ONE_TIME'
ON CONFLICT DO NOTHING;

ALTER TABLE ember_schema.station_event
    DROP COLUMN registration_close_days,
    DROP COLUMN deadline_notified;

ALTER TABLE ember_schema.event_deadline_reminder_sent
    ADD COLUMN occurrence_date DATE;

UPDATE ember_schema.event_deadline_reminder_sent r
SET occurrence_date = (e.start_time AT TIME ZONE s.timezone)::DATE
FROM ember_schema.station_event e
         JOIN ember_schema.station s ON s.id = e.station_id
WHERE e.id = r.event_id;

ALTER TABLE ember_schema.event_deadline_reminder_sent
    ALTER COLUMN occurrence_date SET NOT NULL,
    DROP CONSTRAINT event_deadline_reminder_sent_pkey,
    ADD PRIMARY KEY (event_id, occurrence_date, days_before);

COMMENT ON TABLE ember_schema.event_deadline_reminder_sent
    IS 'Which closing warnings have gone out for each date of an appointment, so a sweep every few minutes warns once per date.';
COMMENT ON COLUMN ember_schema.event_deadline_reminder_sent.occurrence_date
    IS 'The date whose sign-up deadline the warning was about, on the station''s clock.';

ALTER TABLE ember_schema.event_template
    ADD COLUMN registration_deadline_kind           TEXT NOT NULL DEFAULT 'NONE'
        CHECK (registration_deadline_kind IN ('NONE', 'RELATIVE')),
    ADD COLUMN registration_deadline_offset_minutes INT CHECK (registration_deadline_offset_minutes >= 0),
    ADD COLUMN registration_deadline_anchor         TEXT
        CHECK (registration_deadline_anchor IN ('EXACT', 'MIDNIGHT'));

UPDATE ember_schema.event_template
SET registration_deadline_kind           = 'RELATIVE',
    registration_deadline_offset_minutes = GREATEST(0, LEAST(registration_close_days, 3650)) * 1440,
    registration_deadline_anchor         = 'MIDNIGHT'
WHERE registration_close_days IS NOT NULL;

UPDATE ember_schema.event_template
SET registration_deadline_kind           = 'RELATIVE',
    registration_deadline_offset_minutes = GREATEST(0, LEAST(EXTRACT(EPOCH FROM registration_deadline_offset) / 60, 3650 * 1440))::INT,
    registration_deadline_anchor         = 'EXACT'
WHERE registration_close_days IS NULL
  AND registration_deadline_offset IS NOT NULL;

ALTER TABLE ember_schema.event_template
    DROP COLUMN registration_close_days,
    DROP COLUMN registration_deadline_offset,
    ADD CONSTRAINT event_template_registration_deadline_rule CHECK (
        (registration_deadline_kind = 'NONE'
            AND registration_deadline_offset_minutes IS NULL AND registration_deadline_anchor IS NULL)
        OR (registration_deadline_kind = 'RELATIVE'
            AND registration_deadline_offset_minutes IS NOT NULL AND registration_deadline_anchor IS NOT NULL));

COMMENT ON COLUMN ember_schema.event_template.registration_deadline_kind
    IS 'Which sign-up deadline an appointment made from the template gets: NONE or RELATIVE. A template knows no date, so it holds no fixed deadline.';
COMMENT ON COLUMN ember_schema.event_template.registration_deadline_offset_minutes
    IS 'For a RELATIVE deadline, how many minutes before the start of each date sign-up closes; NULL otherwise.';
COMMENT ON COLUMN ember_schema.event_template.registration_deadline_anchor
    IS 'For a RELATIVE deadline, EXACT or MIDNIGHT, as on the appointment; NULL otherwise.';

ALTER TABLE ember_schema.event_group
    ADD COLUMN registration_deadline_kind           TEXT NOT NULL DEFAULT 'NONE'
        CHECK (registration_deadline_kind IN ('NONE', 'FIXED', 'RELATIVE')),
    ADD COLUMN registration_deadline_offset_minutes INT CHECK (registration_deadline_offset_minutes >= 0),
    ADD COLUMN registration_deadline_anchor         TEXT
        CHECK (registration_deadline_anchor IN ('EXACT', 'MIDNIGHT'));

UPDATE ember_schema.event_group
SET registration_deadline_kind = 'FIXED'
WHERE registration_deadline IS NOT NULL;

ALTER TABLE ember_schema.event_group
    ADD CONSTRAINT event_group_registration_deadline_rule CHECK (
        (registration_deadline_kind = 'NONE' AND registration_deadline IS NULL
            AND registration_deadline_offset_minutes IS NULL AND registration_deadline_anchor IS NULL)
        OR (registration_deadline_kind = 'FIXED' AND registration_deadline IS NOT NULL
            AND registration_deadline_offset_minutes IS NULL AND registration_deadline_anchor IS NULL)
        OR (registration_deadline_kind = 'RELATIVE' AND registration_deadline IS NULL
            AND registration_deadline_offset_minutes IS NOT NULL AND registration_deadline_anchor IS NOT NULL));

COMMENT ON COLUMN ember_schema.event_group.registration_deadline
    IS 'The instant members can no longer sign up or change their parts for a FIXED deadline; NULL for any other kind. Unlike the appointment deadline it carries its time zone.';
COMMENT ON COLUMN ember_schema.event_group.registration_deadline_kind
    IS 'Which sign-up deadline applies: NONE, FIXED (registration_deadline) or RELATIVE (a span before the start of the first part).';
COMMENT ON COLUMN ember_schema.event_group.registration_deadline_offset_minutes
    IS 'For a RELATIVE deadline, how many minutes before the start of the first part sign-up closes; NULL for any other kind.';
COMMENT ON COLUMN ember_schema.event_group.registration_deadline_anchor
    IS 'For a RELATIVE deadline, EXACT or MIDNIGHT on the station''s clock; NULL for any other kind.';

CREATE OR REPLACE FUNCTION ember_schema.registration_closes_at(kind TEXT, fixed_at TIMESTAMPTZ, offset_minutes INT,
                                                               anchor TEXT, starts TIMESTAMPTZ, zone TEXT)
    RETURNS TIMESTAMPTZ
    LANGUAGE sql
    STABLE
AS
$$
SELECT CASE kind
           WHEN 'FIXED' THEN fixed_at
           WHEN 'RELATIVE' THEN CASE
                                    WHEN anchor = 'MIDNIGHT' AND offset_minutes % 1440 = 0
                                        THEN ((starts AT TIME ZONE zone)::DATE - offset_minutes / 1440)::TIMESTAMP AT TIME ZONE zone
                                    WHEN anchor = 'MIDNIGHT'
                                        THEN date_trunc('day', (starts - make_interval(mins => offset_minutes)) AT TIME ZONE zone) AT TIME ZONE zone
                                    ELSE starts - make_interval(mins => offset_minutes)
               END
           END;
$$;

COMMENT ON FUNCTION ember_schema.registration_closes_at(TEXT, TIMESTAMPTZ, INT, TEXT, TIMESTAMPTZ, TEXT)
    IS 'When sign-up closes for a date starting at starts, under a deadline rule, on the station clock zone; NULL for no deadline. The same computation as the server''s RegistrationDeadline.';

CREATE OR REPLACE FUNCTION ember_schema.event_group_closes_at(group_id INT)
    RETURNS TIMESTAMPTZ
    LANGUAGE sql
    STABLE
AS
$$
SELECT ember_schema.registration_closes_at(g.registration_deadline_kind, g.registration_deadline,
                                           g.registration_deadline_offset_minutes, g.registration_deadline_anchor,
                                           (SELECT min(e.start_time)
                                            FROM ember_schema.station_event e
                                                     JOIN ember_schema.event_group_set s ON s.id = e.event_group_set_id
                                            WHERE s.event_group_id = g.id),
                                           st.timezone)
FROM ember_schema.event_group g
         JOIN ember_schema.station st ON st.id = g.station_id
WHERE g.id = group_id;
$$;

COMMENT ON FUNCTION ember_schema.event_group_closes_at(INT)
    IS 'When sign-up for an event group closes: its fixed deadline, or its relative one counted from the start of its first part; NULL for no deadline or, for a relative one, no part yet.';

-- Every record that a deadline notice or warning went out remembers the instant it was about. A record
-- counts only while that instant is still the close, so a deadline that moves is handled again by
-- itself. Rows already there take the close as it stands now; a row whose close cannot be placed any
-- more is about nothing that could still close and goes.

ALTER TABLE ember_schema.event_deadline_expired
    ADD COLUMN closes_at TIMESTAMPTZ;

UPDATE ember_schema.event_deadline_expired x
SET closes_at = ember_schema.registration_closes_at(e.registration_deadline_kind, e.registration_deadline,
                                                    e.registration_deadline_offset_minutes,
                                                    e.registration_deadline_anchor, e.start_time, s.timezone)
FROM ember_schema.station_event e
         JOIN ember_schema.station s ON s.id = e.station_id
WHERE e.id = x.event_id
  AND e.event_type = 'ONE_TIME';

DELETE FROM ember_schema.event_deadline_expired WHERE closes_at IS NULL;

ALTER TABLE ember_schema.event_deadline_expired
    ALTER COLUMN closes_at SET NOT NULL;

COMMENT ON COLUMN ember_schema.event_deadline_expired.closes_at
    IS 'The deadline instant that passed; the record counts only while the date still closes at this instant.';

ALTER TABLE ember_schema.event_deadline_reminder_sent
    ADD COLUMN closes_at TIMESTAMPTZ;

UPDATE ember_schema.event_deadline_reminder_sent r
SET closes_at = ember_schema.registration_closes_at(e.registration_deadline_kind, e.registration_deadline,
                                                    e.registration_deadline_offset_minutes,
                                                    e.registration_deadline_anchor, e.start_time, s.timezone)
FROM ember_schema.station_event e
         JOIN ember_schema.station s ON s.id = e.station_id
WHERE e.id = r.event_id
  AND e.event_type = 'ONE_TIME';

DELETE FROM ember_schema.event_deadline_reminder_sent WHERE closes_at IS NULL;

ALTER TABLE ember_schema.event_deadline_reminder_sent
    ALTER COLUMN closes_at SET NOT NULL;

COMMENT ON COLUMN ember_schema.event_deadline_reminder_sent.closes_at
    IS 'The deadline instant the warning was about; the warning counts only while the date still closes at this instant.';

ALTER TABLE ember_schema.event_group_deadline_reminder_sent
    ADD COLUMN closes_at TIMESTAMPTZ;

UPDATE ember_schema.event_group_deadline_reminder_sent
SET closes_at = ember_schema.event_group_closes_at(event_group_id);

DELETE FROM ember_schema.event_group_deadline_reminder_sent WHERE closes_at IS NULL;

ALTER TABLE ember_schema.event_group_deadline_reminder_sent
    ALTER COLUMN closes_at SET NOT NULL;

COMMENT ON COLUMN ember_schema.event_group_deadline_reminder_sent.closes_at
    IS 'The deadline instant the warning was about; the warning counts only while the event group still closes at this instant.';

ALTER TABLE ember_schema.event_group
    ADD COLUMN deadline_notified_for TIMESTAMPTZ;

UPDATE ember_schema.event_group
SET deadline_notified_for = ember_schema.event_group_closes_at(id)
WHERE deadline_notified;

ALTER TABLE ember_schema.event_group
    DROP COLUMN deadline_notified;

COMMENT ON COLUMN ember_schema.event_group.deadline_notified_for
    IS 'The deadline instant whose passing the managers were told about; NULL while not told. The notice counts only while the event group still closes at this instant.';

-- A part of an event group takes the event group's close when it is read and keeps no deadline of its
-- own; the copies written so far go.

UPDATE ember_schema.station_event
SET registration_deadline                = NULL,
    registration_deadline_kind           = 'NONE',
    registration_deadline_offset_minutes = NULL,
    registration_deadline_anchor         = NULL
WHERE event_group_set_id IS NOT NULL;

COMMENT ON COLUMN ember_schema.event_group.threshold_days
    IS 'How many days before the first part, a called-off one included, the minimum must be reached, counted from midnight on the station''s clock. Set exactly when the minimum is.';

-- Places now go with the share: an arrangement with a partner the appointment's share no longer covers
-- is dropped when the share changes. The ones left behind by earlier unsharing go once here.

DELETE FROM ember_schema.event_partner_places p
WHERE NOT EXISTS (SELECT 1
                  FROM ember_schema.event_federation_share s
                  WHERE s.event_id = p.event_id
                    AND (s.scope = 'ALL_PARTNERS'
                      OR EXISTS (SELECT 1
                                 FROM ember_schema.event_federation_share_target t
                                 WHERE t.share_id = s.id
                                   AND t.partner_id = p.partner_id)));

-- What a partner station may do with a shared event group, the arrangement shared appointments already
-- have: places counted per event group sign-up, and whether the partner decides its own sign-ups.

CREATE TABLE ember_schema.event_group_partner_places
(
    event_group_id   INTEGER NOT NULL REFERENCES ember_schema.event_group (id) ON DELETE CASCADE,
    partner_id       INTEGER NOT NULL REFERENCES ember_schema.federation_partner (id) ON DELETE CASCADE,
    slot_budget      INTEGER NULL,
    partner_confirms BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (event_group_id, partner_id),
    CONSTRAINT event_group_partner_places_budget_is_not_negative CHECK (slot_budget IS NULL OR slot_budget >= 0),
    CONSTRAINT event_group_partner_places_budget_implies_deciding CHECK (slot_budget IS NULL OR partner_confirms)
);

COMMENT ON TABLE ember_schema.event_group_partner_places IS
    'What a partner station may do with a shared event group: how many of its members may hold a place, and whether it decides its own sign-ups. Only arrangements somebody made have a row; none means the host decides with no cap. Dropped for a partner the share no longer covers.';
COMMENT ON COLUMN ember_schema.event_group_partner_places.event_group_id IS
    'The shared event group.';
COMMENT ON COLUMN ember_schema.event_group_partner_places.partner_id IS
    'The host station''s own record of the partner station.';
COMMENT ON COLUMN ember_schema.event_group_partner_places.slot_budget IS
    'How many of this partner''s sign-ups may hold a place in the event group, counted per sign-up, not per part. NULL means no cap; a number means the partner decides.';
COMMENT ON COLUMN ember_schema.event_group_partner_places.partner_confirms IS
    'Whether this partner decides its own members'' sign-ups rather than the host. True for every row, since only such an arrangement is kept.';

-- The station holding an appointment or an event group tells the partner when it decides about one of
-- the partner's members. The partner keeps the last status it learnt and the host's instant of it, so a
-- notice that arrives late or twice changes nothing and tells nobody again.

ALTER TABLE ember_schema.partner_signup
    ADD COLUMN status            TEXT        NULL
        CONSTRAINT partner_signup_status_known
            CHECK (status IS NULL OR status IN ('PENDING', 'ACCEPTED', 'DENIED', 'DECLINED', 'WITHDRAWN', 'QUEUED')),
    ADD COLUMN status_changed_at TIMESTAMPTZ NULL;

COMMENT ON COLUMN ember_schema.partner_signup.status IS
    'The status the partner station last gave this sign-up, as it answered the sign-up or told a later decision; WITHDRAWN once the member withdrew here. NULL where it is not known.';
COMMENT ON COLUMN ember_schema.partner_signup.status_changed_at IS
    'When the partner station says that status was given, on its own clock. NULL until it told a decision; a notice not after this is ignored.';

ALTER TABLE ember_schema.partner_event_group_signup
    ADD COLUMN status            TEXT        NULL
        CONSTRAINT partner_event_group_signup_status_known
            CHECK (status IS NULL OR status IN ('PENDING', 'ACCEPTED', 'DENIED', 'DECLINED', 'WITHDRAWN', 'QUEUED')),
    ADD COLUMN status_changed_at TIMESTAMPTZ NULL;

COMMENT ON COLUMN ember_schema.partner_event_group_signup.status IS
    'The status the partner station last gave this event group sign-up, as it answered the sign-up, showed it or told a later decision; WITHDRAWN once the member withdrew here. NULL where it is not known.';
COMMENT ON COLUMN ember_schema.partner_event_group_signup.status_changed_at IS
    'When the partner station says that status was given, on its own clock. NULL until it told a decision; a notice not after this is ignored.';

COMMENT ON TABLE ember_schema.partner_signup IS
    'A member of this station signed up here for one date of a partner station''s appointment. The partner holds the registration itself; this is this station''s own word that it was given, so that a partner telling it which of its members are registered is believed only for members it signed up. A withdrawal keeps the row with status WITHDRAWN, so the partner''s last instant still holds for a sign-up given again.';
COMMENT ON TABLE ember_schema.partner_event_group_signup
    IS 'Members this station signed up for an event group a partner station holds, so news from that partner about the event group is believed for them only. A withdrawal keeps the row with status WITHDRAWN, so the partner''s last instant still holds for a sign-up given again.';
