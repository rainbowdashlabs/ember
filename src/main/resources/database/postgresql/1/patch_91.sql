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
    kind            TEXT        NOT NULL CHECK (kind IN ('TEST', 'NOTIFICATION')),
    notification_id INTEGER     NULL REFERENCES ember_schema.notification (id) ON DELETE CASCADE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    attempts        INTEGER     NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deferred        BOOLEAN     NOT NULL DEFAULT FALSE,
    sent_at         TIMESTAMPTZ NULL,
    CONSTRAINT push_outbox_names_its_notification CHECK ((kind = 'NOTIFICATION') = (notification_id IS NOT NULL))
);

CREATE INDEX idx_push_outbox_due ON ember_schema.push_outbox (next_attempt_at) WHERE sent_at IS NULL;
CREATE INDEX idx_push_outbox_sent ON ember_schema.push_outbox (subscription_id, sent_at) WHERE sent_at IS NOT NULL;
CREATE INDEX idx_push_outbox_notification ON ember_schema.push_outbox (notification_id);

COMMENT ON TABLE ember_schema.push_outbox IS
    'Pushes waiting to go out, and those sent within the last hour for the rate limit.';
COMMENT ON COLUMN ember_schema.push_outbox.id IS 'Primary key.';
COMMENT ON COLUMN ember_schema.push_outbox.subscription_id IS 'The browser the push goes to.';
COMMENT ON COLUMN ember_schema.push_outbox.kind IS 'What the push is about: a test the owner asked for, or a notification.';
COMMENT ON COLUMN ember_schema.push_outbox.notification_id IS 'The notification the push is about, for kind NOTIFICATION.';
COMMENT ON COLUMN ember_schema.push_outbox.created_at IS 'When the push was queued.';
COMMENT ON COLUMN ember_schema.push_outbox.attempts IS 'How often sending was tried and the push service asked to wait.';
COMMENT ON COLUMN ember_schema.push_outbox.next_attempt_at IS 'When the push is due, or tried again.';
COMMENT ON COLUMN ember_schema.push_outbox.deferred IS
    'Held back by the rate limit; several held back for one browser go out as one summary.';
COMMENT ON COLUMN ember_schema.push_outbox.sent_at IS 'When the push service took it, empty while waiting.';
