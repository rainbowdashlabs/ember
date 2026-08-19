ALTER TABLE ember_schema.station_mail_provider
    ADD COLUMN daily_limit   INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN provider_name TEXT    NOT NULL DEFAULT '',
    ADD COLUMN provider_url  TEXT    NOT NULL DEFAULT '';

COMMENT ON COLUMN ember_schema.station_mail_provider.daily_limit
    IS 'How many mails this provider may send in a day; 0 for no limit.';
COMMENT ON COLUMN ember_schema.station_mail_provider.provider_name
    IS 'The provider name shown to members of the station.';
COMMENT ON COLUMN ember_schema.station_mail_provider.provider_url
    IS 'The provider website shown to members of the station.';
COMMENT ON COLUMN ember_schema.station_mail_provider.position
    IS 'Where in the order this provider is tried, counted from zero.';

COMMENT ON TABLE ember_schema.station_mail_provider
    IS 'The mail providers a station sends through, in the order they are tried.';

INSERT INTO ember_schema.station_mail_provider
    (station_id, position, provider, smtp_host, smtp_port, smtp_ssl, smtp_user, smtp_password,
     api_key, sender_address, sender_name, attempts, daily_limit, provider_name, provider_url)
SELECT
    station_id, 0, provider, smtp_host, smtp_port, smtp_ssl, smtp_user, smtp_password,
    api_key, sender_address, sender_name, 2, daily_limit, provider_name, provider_url
FROM
    ember_schema.station_mail_config
WHERE
    provider <> 'NONE';

DROP TABLE ember_schema.station_mail_config;

DROP TABLE ember_schema.station_email_count;

ALTER TABLE ember_schema.email_queue
    ADD COLUMN sent_at TIMESTAMP;

COMMENT ON COLUMN ember_schema.email_queue.sent_at
    IS 'When the mail was handed to a provider, which is what the per-provider daily count reads.';

CREATE INDEX idx_email_queue_sent_at ON ember_schema.email_queue (sent_at, provider_position);

CREATE TABLE ember_schema.application_log
(
    id        BIGSERIAL PRIMARY KEY,
    logged_at TIMESTAMP NOT NULL,
    level     TEXT      NOT NULL,
    logger    TEXT      NOT NULL,
    thread    TEXT      NOT NULL DEFAULT '',
    message   TEXT      NOT NULL,
    throwable TEXT
);

COMMENT ON TABLE ember_schema.application_log
    IS 'Application log lines kept for reading and searching from the administration area.';
COMMENT ON COLUMN ember_schema.application_log.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.application_log.logged_at IS 'When the line was logged.';
COMMENT ON COLUMN ember_schema.application_log.level IS 'Severity: TRACE, DEBUG, INFO, WARN or ERROR.';
COMMENT ON COLUMN ember_schema.application_log.logger IS 'Which logger emitted the line.';
COMMENT ON COLUMN ember_schema.application_log.thread IS 'The thread the line was logged from.';
COMMENT ON COLUMN ember_schema.application_log.message IS 'The line itself, already formatted.';
COMMENT ON COLUMN ember_schema.application_log.throwable IS 'The stack trace, when the line carried one.';

CREATE INDEX idx_application_log_level_id ON ember_schema.application_log (level, id DESC);

CREATE INDEX idx_application_log_time ON ember_schema.application_log (logged_at);

ALTER TABLE ember_schema.account_session
    ADD COLUMN trusted_device BOOLEAN NOT NULL DEFAULT false;

COMMENT ON COLUMN ember_schema.account_session.trusted_device
    IS 'Whether the person signing in vouched for this machine, which grants the long session length.';

-- Which provider a receiving domain refuses outright.
--
-- Recorded only from a report of BLOCKED, which is the receiving side saying it refused our relay
-- rather than our message. A hard bounce is not enough: that usually means the address does not
-- exist, and shutting a provider out of a whole domain over one typo would cost far more than the
-- attempt it saves.
--
-- Kept per provider rather than per entry of the list, because what is on the block list is the
-- service and its addresses, not the position it happens to sit at.
CREATE TABLE ember_schema.mail_provider_block
(
    id               SERIAL    PRIMARY KEY,
    station_id       INTEGER   REFERENCES ember_schema.station (id) ON DELETE CASCADE,
    provider         TEXT      NOT NULL,
    recipient_domain TEXT      NOT NULL,
    reason           TEXT,
    first_blocked_at TIMESTAMP NOT NULL DEFAULT now(),
    last_blocked_at  TIMESTAMP NOT NULL DEFAULT now(),
    expires_at       TIMESTAMP NOT NULL
);

COMMENT ON TABLE ember_schema.mail_provider_block
    IS 'Which mail provider a receiving domain has refused outright, so it is not tried there again.';
COMMENT ON COLUMN ember_schema.mail_provider_block.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.mail_provider_block.station_id IS 'The station whose list this concerns, or null for the instance list.';
COMMENT ON COLUMN ember_schema.mail_provider_block.provider IS 'Which provider was refused.';
COMMENT ON COLUMN ember_schema.mail_provider_block.recipient_domain IS 'The receiving domain that refused it, lowercased.';
COMMENT ON COLUMN ember_schema.mail_provider_block.reason IS 'What the receiving side gave as the reason, when it gave one.';
COMMENT ON COLUMN ember_schema.mail_provider_block.first_blocked_at IS 'When this pairing was first refused.';
COMMENT ON COLUMN ember_schema.mail_provider_block.last_blocked_at IS 'When it was last refused, which pushes the expiry out.';
COMMENT ON COLUMN ember_schema.mail_provider_block.expires_at IS 'When the block lapses, because a block list entry is not forever.';

CREATE UNIQUE INDEX uq_mail_provider_block
    ON ember_schema.mail_provider_block (coalesce(station_id, 0), provider, recipient_domain);
