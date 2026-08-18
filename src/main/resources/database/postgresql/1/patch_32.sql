-- Further mail providers a station falls back to, in the order they are tried.
--
-- The station's own configuration stays the first provider it uses; this table holds the ones after
-- it. Each says how many attempts it gets before the next one takes over.
CREATE TABLE ember_schema.station_mail_provider
(
    id             SERIAL    PRIMARY KEY,
    station_id     INTEGER   NOT NULL REFERENCES ember_schema.station (id) ON DELETE CASCADE,
    position       INTEGER   NOT NULL DEFAULT 1,
    provider       TEXT      NOT NULL DEFAULT 'NONE',
    smtp_host      TEXT      NOT NULL DEFAULT '',
    smtp_port      INTEGER   NOT NULL DEFAULT 587,
    smtp_ssl       BOOLEAN   NOT NULL DEFAULT false,
    smtp_user      TEXT      NOT NULL DEFAULT '',
    smtp_password  TEXT      NOT NULL DEFAULT '',
    api_key        TEXT      NOT NULL DEFAULT '',
    sender_address TEXT      NOT NULL DEFAULT '',
    sender_name    TEXT      NOT NULL DEFAULT '',
    attempts       INTEGER   NOT NULL DEFAULT 2,
    created_at     TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE (station_id, position)
);

COMMENT ON TABLE ember_schema.station_mail_provider
    IS 'Further mail providers a station falls back to, after the one in its own mail configuration.';
COMMENT ON COLUMN ember_schema.station_mail_provider.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.station_mail_provider.station_id IS 'The station this provider belongs to.';
COMMENT ON COLUMN ember_schema.station_mail_provider.position IS 'Where in the order this provider is tried; the station configuration itself is 0.';
COMMENT ON COLUMN ember_schema.station_mail_provider.provider IS 'Which provider is used: SMTP, RAPIDMAIL, TWILIO, SWEEGO or BREVO.';
COMMENT ON COLUMN ember_schema.station_mail_provider.smtp_host IS 'Server hostname for a plain SMTP provider.';
COMMENT ON COLUMN ember_schema.station_mail_provider.smtp_port IS 'Server port for a plain SMTP provider.';
COMMENT ON COLUMN ember_schema.station_mail_provider.smtp_ssl IS 'Whether the connection uses direct SSL rather than STARTTLS.';
COMMENT ON COLUMN ember_schema.station_mail_provider.smtp_user IS 'Login name presented to the relay.';
COMMENT ON COLUMN ember_schema.station_mail_provider.smtp_password IS 'Password presented to the relay.';
COMMENT ON COLUMN ember_schema.station_mail_provider.api_key IS 'Key presented to a relay that authenticates with one.';
COMMENT ON COLUMN ember_schema.station_mail_provider.sender_address IS 'Address the mail is sent from.';
COMMENT ON COLUMN ember_schema.station_mail_provider.sender_name IS 'Display name the mail is sent under.';
COMMENT ON COLUMN ember_schema.station_mail_provider.attempts IS 'How many attempts this provider gets before the next one takes over.';
COMMENT ON COLUMN ember_schema.station_mail_provider.created_at IS 'When the provider was added.';

-- How far a queued mail has got through the chain.
ALTER TABLE ember_schema.email_queue
    ADD COLUMN attempts          INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN provider_position INTEGER NOT NULL DEFAULT 0;

COMMENT ON COLUMN ember_schema.email_queue.attempts
    IS 'How many times the provider currently in turn has tried this mail.';
COMMENT ON COLUMN ember_schema.email_queue.provider_position
    IS 'Which provider of the chain is currently in turn, counted from zero.';
