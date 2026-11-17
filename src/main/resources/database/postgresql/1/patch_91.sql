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
