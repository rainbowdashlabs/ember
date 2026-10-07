CREATE TABLE IF NOT EXISTS ember_schema.signing_ca
(
    id                  INT         PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    serial_number       TEXT        NOT NULL,
    certificate         BYTEA       NOT NULL,
    wrapped_private_key BYTEA       NOT NULL,
    valid_until         TIMESTAMPTZ NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE ember_schema.signing_ca
    IS 'The installation''s own certificate authority, which issues the certificates the stations seal documents with. One row at most, created the first time any station seals a document.';
COMMENT ON COLUMN ember_schema.signing_ca.id
    IS 'Always 1, so the installation can hold only one authority.';
COMMENT ON COLUMN ember_schema.signing_ca.serial_number
    IS 'Serial number of the self-signed authority certificate, lower-case hexadecimal.';
COMMENT ON COLUMN ember_schema.signing_ca.certificate
    IS 'The self-signed authority certificate, DER encoded. Public: readers trust sealed documents through it.';
COMMENT ON COLUMN ember_schema.signing_ca.wrapped_private_key
    IS 'The authority''s private key, encrypted with a key derived from the installation''s at-rest encryption key. Never stored in clear; useless without that key file or setting.';
COMMENT ON COLUMN ember_schema.signing_ca.valid_until
    IS 'When the authority certificate expires, copied from the certificate.';
COMMENT ON COLUMN ember_schema.signing_ca.created_at
    IS 'When the authority was created.';

CREATE TABLE IF NOT EXISTS ember_schema.station_signing_key
(
    id                  SERIAL PRIMARY KEY,
    station_id          INT         NOT NULL REFERENCES ember_schema.station (id) ON DELETE CASCADE,
    serial_number       TEXT        NOT NULL UNIQUE,
    certificate         BYTEA       NOT NULL,
    wrapped_private_key BYTEA       NOT NULL,
    valid_until         TIMESTAMPTZ NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    retired_at          TIMESTAMPTZ
);

CREATE UNIQUE INDEX IF NOT EXISTS station_signing_key_active_idx
    ON ember_schema.station_signing_key (station_id)
    WHERE retired_at IS NULL;

COMMENT ON TABLE ember_schema.station_signing_key
    IS 'The keys a station seals documents with, each with a certificate issued by the installation''s authority. Created the first time a station seals a document; a station has at most one active key, and retired keys stay so older documents can still be traced to them.';
COMMENT ON COLUMN ember_schema.station_signing_key.id
    IS 'Primary key.';
COMMENT ON COLUMN ember_schema.station_signing_key.station_id
    IS 'The station that seals with this key. The keys go with the station when it is deleted.';
COMMENT ON COLUMN ember_schema.station_signing_key.serial_number
    IS 'Serial number of the station certificate, lower-case hexadecimal, unique among the certificates the authority issued.';
COMMENT ON COLUMN ember_schema.station_signing_key.certificate
    IS 'The station certificate issued by the installation''s authority, DER encoded. Public: it is embedded in every document the key seals.';
COMMENT ON COLUMN ember_schema.station_signing_key.wrapped_private_key
    IS 'The station''s private key, encrypted with a key derived from the installation''s at-rest encryption key. Never stored in clear; useless without that key file or setting.';
COMMENT ON COLUMN ember_schema.station_signing_key.valid_until
    IS 'When the station certificate expires, copied from the certificate.';
COMMENT ON COLUMN ember_schema.station_signing_key.created_at
    IS 'When the key was created.';
COMMENT ON COLUMN ember_schema.station_signing_key.retired_at
    IS 'When the key stopped being used for new seals; null while it is the station''s active key.';
