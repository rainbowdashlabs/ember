-- What happened to an email after the relay accepted it.
--
-- The send status on email_queue only says whether the relay took the message. Everything after
-- that - delivered, bounced, blocked, deferred - happens between the relay and the receiving
-- server, and reaches us as a provider event rather than as the answer to our own send.
ALTER TABLE ember_schema.email_queue
    ADD COLUMN delivery_status     TEXT NOT NULL DEFAULT 'UNKNOWN',
    ADD COLUMN delivery_detail     TEXT,
    ADD COLUMN delivery_updated_at TIMESTAMP,
    ADD COLUMN provider_message_id TEXT;

CREATE INDEX idx_email_queue_delivery_status ON ember_schema.email_queue (delivery_status);
CREATE INDEX idx_email_queue_recipient ON ember_schema.email_queue (recipient);

COMMENT ON COLUMN ember_schema.email_queue.delivery_status
    IS 'What the provider reported after accepting the message: UNKNOWN, DELIVERED, SOFT_BOUNCE, HARD_BOUNCE, BLOCKED, SPAM, DEFERRED or ERROR.';
COMMENT ON COLUMN ember_schema.email_queue.delivery_detail
    IS 'The reason the provider gave for the delivery outcome, as received.';
COMMENT ON COLUMN ember_schema.email_queue.delivery_updated_at
    IS 'When the last delivery event for this email arrived.';
COMMENT ON COLUMN ember_schema.email_queue.provider_message_id
    IS 'The message id the provider assigned, learned from its delivery events.';

-- The key an external tool presents when it reports something to a single station.
--
-- The instance has one of these in its configuration; a station gets its own here, so a station
-- running its own mail provider points that provider at an address of its own and its reports can
-- only ever touch its own mail.
CREATE TABLE ember_schema.station_webhook_key
(
    station_id INTEGER   PRIMARY KEY REFERENCES ember_schema.station (id) ON DELETE CASCADE,
    key        TEXT      NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

COMMENT ON TABLE ember_schema.station_webhook_key
    IS 'Per-station key that authorises webhook reports from external tools for that station.';
COMMENT ON COLUMN ember_schema.station_webhook_key.station_id IS 'The station the key belongs to.';
COMMENT ON COLUMN ember_schema.station_webhook_key.key IS 'The secret presented in the webhook address.';
COMMENT ON COLUMN ember_schema.station_webhook_key.created_at IS 'When the key was generated.';
