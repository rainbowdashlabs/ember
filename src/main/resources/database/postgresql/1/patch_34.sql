-- One ordered list of mail providers per owner, worked from the top, with a daily allowance each.
--
-- Sending used to be a provider plus a list of fallbacks, kept in two places with two shapes: a
-- station's own provider in station_mail_config, everything after it in station_mail_provider.
-- Reading the order meant knowing where the seam was, and the first provider could not be tested,
-- reordered or given a webhook address the way the others could. There is now one list.
--
-- The allowance moves with it. Free tiers are sold by the day, so a limit belongs to the provider
-- that has one rather than to the station as a whole: a list whose first provider is spent moves
-- to the next instead of holding the mail until tomorrow.

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

-- The station's own provider becomes the first entry of its list. Its daily limit comes with it;
-- the monthly one does not, having been a station-wide cap rather than any provider's allowance.
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

-- Counting moves to the queue, which now records when a mail actually left. A per-provider count
-- cannot be read from a per-station counter, and a mail written yesterday and sent today belongs
-- to today.
DROP TABLE ember_schema.station_email_count;

ALTER TABLE ember_schema.email_queue
    ADD COLUMN sent_at TIMESTAMP;

COMMENT ON COLUMN ember_schema.email_queue.sent_at
    IS 'When the mail was handed to a provider, which is what the per-provider daily count reads.';

CREATE INDEX idx_email_queue_sent_at ON ember_schema.email_queue (sent_at, provider_position);
