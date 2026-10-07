CREATE TABLE IF NOT EXISTS ember_schema.signing_ca
(
    id                  SERIAL PRIMARY KEY,
    serial_number       TEXT        NOT NULL UNIQUE,
    certificate         BYTEA       NOT NULL,
    wrapped_private_key BYTEA       NOT NULL,
    valid_until         TIMESTAMPTZ NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    retired_at          TIMESTAMPTZ,
    crl_number          BIGINT      NOT NULL DEFAULT 0,
    crl                 BYTEA,
    crl_issued_at       TIMESTAMPTZ,
    CONSTRAINT signing_ca_crl_issued_check CHECK ((crl IS NULL) = (crl_issued_at IS NULL))
);

CREATE UNIQUE INDEX IF NOT EXISTS signing_ca_active_idx
    ON ember_schema.signing_ca ((retired_at IS NULL))
    WHERE retired_at IS NULL;

COMMENT ON TABLE ember_schema.signing_ca
    IS 'The installation''s own certificate authorities, which issue the certificates the stations seal documents with. The first is created the first time any station seals a document; at most one is active, and a retired authority stays so the station certificates it issued and the documents sealed with them can still be traced to it.';
COMMENT ON COLUMN ember_schema.signing_ca.id
    IS 'Primary key.';
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
COMMENT ON COLUMN ember_schema.signing_ca.retired_at
    IS 'When a newer authority took over issuing station certificates; null while this one is the active authority.';
COMMENT ON COLUMN ember_schema.signing_ca.crl_number
    IS 'Number of the newest revocation list this authority issued, counting up from 1 with every list; 0 before the first.';
COMMENT ON COLUMN ember_schema.signing_ca.crl
    IS 'The newest revocation list this authority issued, DER encoded, kept so it is not signed anew for every reader. Public. Emptied when one of its station keys is revoked, so the next reader gets a list that names it.';
COMMENT ON COLUMN ember_schema.signing_ca.crl_issued_at
    IS 'When the stored revocation list was issued, the thisUpdate it carries, kept so its age is known without reading the list. Null exactly when no list is stored.';

CREATE TABLE IF NOT EXISTS ember_schema.station_signing_key
(
    id                  SERIAL PRIMARY KEY,
    station_id          INT         REFERENCES ember_schema.station (id) ON DELETE SET NULL,
    signing_ca_id       INT         NOT NULL REFERENCES ember_schema.signing_ca (id),
    serial_number       TEXT        NOT NULL UNIQUE,
    certificate         BYTEA       NOT NULL,
    wrapped_private_key BYTEA       NOT NULL,
    valid_until         TIMESTAMPTZ NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    retired_at          TIMESTAMPTZ,
    revoked_at          TIMESTAMPTZ,
    revocation_reason   TEXT CHECK (revocation_reason IN ('KEY_COMPROMISE', 'SUPERSEDED', 'CESSATION_OF_OPERATION')),
    CONSTRAINT station_signing_key_revocation_check CHECK ((revoked_at IS NULL) = (revocation_reason IS NULL)),
    CONSTRAINT station_signing_key_revoked_retired_check CHECK (revoked_at IS NULL OR retired_at IS NOT NULL)
);

CREATE UNIQUE INDEX IF NOT EXISTS station_signing_key_active_idx
    ON ember_schema.station_signing_key (station_id)
    WHERE retired_at IS NULL;

CREATE INDEX IF NOT EXISTS station_signing_key_station_idx
    ON ember_schema.station_signing_key (station_id);

CREATE INDEX IF NOT EXISTS station_signing_key_signing_ca_idx
    ON ember_schema.station_signing_key (signing_ca_id);

COMMENT ON TABLE ember_schema.station_signing_key
    IS 'The keys a station seals documents with, each with a certificate issued by one of the installation''s authorities. Created the first time a station seals a document; a station has at most one active key, and retired keys stay so older documents can still be traced to them. Keys outlive their station, so its revocations are never lost.';
COMMENT ON COLUMN ember_schema.station_signing_key.id
    IS 'Primary key.';
COMMENT ON COLUMN ember_schema.station_signing_key.station_id
    IS 'The station that seals with this key. Null once the station was deleted: the key stays, so a revoked key stays on its authority''s revocation lists and a key of a deleted station can still be revoked.';
COMMENT ON COLUMN ember_schema.station_signing_key.signing_ca_id
    IS 'The authority that issued the station certificate, which completes its chain even after a newer authority took over.';
COMMENT ON COLUMN ember_schema.station_signing_key.serial_number
    IS 'Serial number of the station certificate, lower-case hexadecimal, unique among the certificates the authorities issued.';
COMMENT ON COLUMN ember_schema.station_signing_key.certificate
    IS 'The station certificate issued by the installation''s authority, DER encoded. Public: it is embedded in every document the key seals.';
COMMENT ON COLUMN ember_schema.station_signing_key.wrapped_private_key
    IS 'The station''s private key, encrypted with a key derived from the installation''s at-rest encryption key. Never stored in clear; useless without that key file or setting.';
COMMENT ON COLUMN ember_schema.station_signing_key.valid_until
    IS 'When the station certificate expires, copied from the certificate.';
COMMENT ON COLUMN ember_schema.station_signing_key.created_at
    IS 'When the key was created.';
COMMENT ON COLUMN ember_schema.station_signing_key.retired_at
    IS 'When the key stopped being used for new seals; null while it is the station''s active key, and still null for the last active key of a deleted station, which seals nothing more.';
COMMENT ON COLUMN ember_schema.station_signing_key.revoked_at
    IS 'When the key was revoked, the date its authority''s revocation lists give for it; null while it is not revoked. A revoked key is always retired.';
COMMENT ON COLUMN ember_schema.station_signing_key.revocation_reason
    IS 'Why the key was revoked, as its authority''s revocation lists state it: KEY_COMPROMISE when the private key leaked, SUPERSEDED when it was replaced, CESSATION_OF_OPERATION when the station stopped sealing with it. Null while it is not revoked.';

ALTER TABLE ember_schema.member_document
    ADD COLUMN sealed BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE ember_schema.member_document
    ADD CONSTRAINT member_document_sealed_kept CHECK (NOT sealed OR keep_on_archive);

COMMENT ON COLUMN ember_schema.member_document.sealed
    IS 'Whether the document is sealed and therefore locked: it cannot be deleted, the members it is about cannot be changed, it is always kept when they leave, under their name where they are deleted, and its file is never replaced. Its files are its sealed versions. Only the deletion of its station takes it away.';
COMMENT ON CONSTRAINT member_document_sealed_kept ON ember_schema.member_document
    IS 'A sealed document is always kept when its members leave.';

CREATE TABLE IF NOT EXISTS ember_schema.member_document_version
(
    id             SERIAL PRIMARY KEY,
    document_id    INTEGER     NOT NULL REFERENCES ember_schema.member_document (id) ON DELETE CASCADE,
    version        INTEGER     NOT NULL CHECK (version > 0),
    sha256         TEXT        NOT NULL CHECK (sha256 ~ '^[0-9a-f]{64}$'),
    size_bytes     BIGINT      NOT NULL,
    seal_level     TEXT        NOT NULL CHECK (seal_level IN ('BASELINE_B', 'BASELINE_T', 'BASELINE_LT')),
    timestamped_by TEXT,
    sealed_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    superseded_at  TIMESTAMPTZ,
    CONSTRAINT member_document_version_number UNIQUE (document_id, version),
    CONSTRAINT member_document_version_content UNIQUE (document_id, sha256),
    CONSTRAINT member_document_version_timestamp CHECK ((seal_level = 'BASELINE_B') = (timestamped_by IS NULL))
);

CREATE UNIQUE INDEX IF NOT EXISTS member_document_version_current_idx
    ON ember_schema.member_document_version (document_id)
    WHERE superseded_at IS NULL;

COMMENT ON TABLE ember_schema.member_document_version
    IS 'The sealed versions of a sealed member document, one row per sealed file. Each file is stored once under its SHA-256 and never replaced; a later signature adds a version that supersedes the one before, which stays. Exactly one version of a document is current.';
COMMENT ON COLUMN ember_schema.member_document_version.id
    IS 'Primary key.';
COMMENT ON COLUMN ember_schema.member_document_version.document_id
    IS 'The sealed document this is a version of. The versions go only with the document, which goes only with its station.';
COMMENT ON COLUMN ember_schema.member_document_version.version
    IS 'The number of the version within its document, counting up from 1 in the order they were sealed.';
COMMENT ON COLUMN ember_schema.member_document_version.sha256
    IS 'SHA-256 of the sealed file, lower-case hexadecimal. The file is stored under it, so the name of the file is a check of its bytes.';
COMMENT ON COLUMN ember_schema.member_document_version.size_bytes
    IS 'How large the sealed file is.';
COMMENT ON COLUMN ember_schema.member_document_version.seal_level
    IS 'The PAdES baseline level the seal reached: BASELINE_B without a timestamp, BASELINE_T with one, BASELINE_LT with the timestamp and the material to check both offline.';
COMMENT ON COLUMN ember_schema.member_document_version.timestamped_by
    IS 'The address of the timestamp service whose timestamp the file carries; null for a seal without a timestamp.';
COMMENT ON COLUMN ember_schema.member_document_version.sealed_at
    IS 'When the version was filed.';
COMMENT ON COLUMN ember_schema.member_document_version.superseded_at
    IS 'When a later version took its place; null for the current version. A superseded version stays stored.';
COMMENT ON CONSTRAINT member_document_version_content ON ember_schema.member_document_version
    IS 'The same sealed file is never filed twice as versions of one document.';

CREATE OR REPLACE FUNCTION ember_schema.member_document_keep_sealed() RETURNS TRIGGER
    LANGUAGE plpgsql
AS
$$
BEGIN
    IF EXISTS (SELECT 1 FROM ember_schema.station s WHERE s.id = OLD.station_id) THEN
        RAISE EXCEPTION 'Member document % is sealed and cannot be deleted', OLD.id
            USING ERRCODE = 'restrict_violation';
    END IF;
    RETURN OLD;
END;
$$;

COMMENT ON FUNCTION ember_schema.member_document_keep_sealed()
    IS 'Refuses deleting a sealed member document while its station exists. Deleting the station still takes it, since the cascade from the station runs after the station row is gone.';

CREATE TRIGGER member_document_keep_sealed
    BEFORE DELETE
    ON ember_schema.member_document
    FOR EACH ROW
    WHEN (OLD.sealed)
EXECUTE FUNCTION ember_schema.member_document_keep_sealed();

CREATE OR REPLACE FUNCTION ember_schema.member_document_keep_sealed_part() RETURNS TRIGGER
    LANGUAGE plpgsql
AS
$$
BEGIN
    IF EXISTS (SELECT 1
               FROM ember_schema.member_document d
                        JOIN ember_schema.station s ON s.id = d.station_id
               WHERE d.id = OLD.document_id
                 AND d.sealed) THEN
        RAISE EXCEPTION 'Member document % is sealed, so its % rows cannot be deleted', OLD.document_id, TG_TABLE_NAME
            USING ERRCODE = 'restrict_violation';
    END IF;
    RETURN OLD;
END;
$$;

COMMENT ON FUNCTION ember_schema.member_document_keep_sealed_part()
    IS 'Refuses deleting a member binding or a version of a sealed member document while the document and its station exist, which keeps its members and its files as they were sealed. A member leaving turns their binding into their name instead. Deleting the station takes the document and its rows with it, in whichever order its cascades run.';

CREATE TRIGGER member_document_member_keep_sealed
    BEFORE DELETE
    ON ember_schema.member_document_member
    FOR EACH ROW
EXECUTE FUNCTION ember_schema.member_document_keep_sealed_part();

CREATE TRIGGER member_document_version_keep_sealed
    BEFORE DELETE
    ON ember_schema.member_document_version
    FOR EACH ROW
EXECUTE FUNCTION ember_schema.member_document_keep_sealed_part();

CREATE OR REPLACE FUNCTION ember_schema.member_document_refuse_sealed_change() RETURNS TRIGGER
    LANGUAGE plpgsql
AS
$$
BEGIN
    RAISE EXCEPTION 'A sealed member document stays as it was sealed (%)', TG_TABLE_NAME
        USING ERRCODE = 'restrict_violation';
END;
$$;

COMMENT ON FUNCTION ember_schema.member_document_refuse_sealed_change()
    IS 'Refuses a change the triggers calling it name: unsealing a member document, rewriting a sealed version, or taking back that a version was superseded.';

CREATE TRIGGER member_document_stays_sealed
    BEFORE UPDATE OF sealed
    ON ember_schema.member_document
    FOR EACH ROW
    WHEN (OLD.sealed AND NOT NEW.sealed)
EXECUTE FUNCTION ember_schema.member_document_refuse_sealed_change();

CREATE TRIGGER member_document_version_stays
    BEFORE UPDATE OF document_id, version, sha256, size_bytes, seal_level, timestamped_by, sealed_at
    ON ember_schema.member_document_version
    FOR EACH ROW
EXECUTE FUNCTION ember_schema.member_document_refuse_sealed_change();

CREATE TRIGGER member_document_version_stays_superseded
    BEFORE UPDATE OF superseded_at
    ON ember_schema.member_document_version
    FOR EACH ROW
    WHEN (OLD.superseded_at IS NOT NULL)
EXECUTE FUNCTION ember_schema.member_document_refuse_sealed_change();
