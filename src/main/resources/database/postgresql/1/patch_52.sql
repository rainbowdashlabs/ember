-- Per-station mail configuration
CREATE TABLE ember_schema.station_mail_config (
    station_id     INTEGER PRIMARY KEY REFERENCES ember_schema.station(id) ON DELETE CASCADE,
    provider       TEXT    NOT NULL DEFAULT 'NONE',  -- NONE, SMTP, RAPIDMAIL
    smtp_host      TEXT    NOT NULL DEFAULT '',
    smtp_port      INTEGER NOT NULL DEFAULT 587,
    smtp_ssl       BOOLEAN NOT NULL DEFAULT false,
    smtp_user      TEXT    NOT NULL DEFAULT '',
    smtp_password  TEXT    NOT NULL DEFAULT '',
    sender_address TEXT    NOT NULL DEFAULT '',
    sender_name    TEXT    NOT NULL DEFAULT '',
    api_key        TEXT    NOT NULL DEFAULT '',
    updated_at     TIMESTAMP NOT NULL DEFAULT now()
);
