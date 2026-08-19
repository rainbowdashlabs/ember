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
