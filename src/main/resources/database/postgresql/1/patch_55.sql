-- Per-station send limits
ALTER TABLE ember_schema.station_mail_config ADD COLUMN IF NOT EXISTS daily_limit INTEGER NOT NULL DEFAULT 100;
ALTER TABLE ember_schema.station_mail_config ADD COLUMN IF NOT EXISTS monthly_limit INTEGER NOT NULL DEFAULT 2000;

-- Per-station email send counts
CREATE TABLE ember_schema.station_email_count (
    station_id INTEGER NOT NULL REFERENCES ember_schema.station(id) ON DELETE CASCADE,
    day        DATE    NOT NULL,
    count      INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (station_id, day)
);
