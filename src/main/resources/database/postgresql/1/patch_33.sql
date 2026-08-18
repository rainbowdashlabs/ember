-- The secret a provider gives us so we can check that a report really came from it.
--
-- This is the provider's secret, not ours: our own key lives in station_webhook_key and travels in
-- the address. A provider that signs its calls, as Sweego does, hands out a secret instead, and a
-- station keeps one per provider it uses.
CREATE TABLE ember_schema.station_provider_secret
(
    station_id INTEGER   NOT NULL REFERENCES ember_schema.station (id) ON DELETE CASCADE,
    provider   TEXT      NOT NULL,
    secret     TEXT      NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    PRIMARY KEY (station_id, provider)
);

COMMENT ON TABLE ember_schema.station_provider_secret
    IS 'Signing secrets a mail provider issued to a station, used to verify its delivery reports.';
COMMENT ON COLUMN ember_schema.station_provider_secret.station_id IS 'The station the secret belongs to.';
COMMENT ON COLUMN ember_schema.station_provider_secret.provider IS 'Which provider issued it: SMTP, RAPIDMAIL, TWILIO, SWEEGO or BREVO.';
COMMENT ON COLUMN ember_schema.station_provider_secret.secret IS 'The secret as the provider issued it.';
COMMENT ON COLUMN ember_schema.station_provider_secret.updated_at IS 'When the secret was last set.';
