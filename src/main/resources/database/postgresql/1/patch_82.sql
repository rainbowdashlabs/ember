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
    wrapped_private_key BYTEA,
    valid_until         TIMESTAMPTZ NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    retired_at          TIMESTAMPTZ,
    revoked_at          TIMESTAMPTZ,
    revocation_reason   TEXT CHECK (revocation_reason IN ('KEY_COMPROMISE', 'SUPERSEDED', 'CESSATION_OF_OPERATION')),
    CONSTRAINT station_signing_key_revocation_check CHECK ((revoked_at IS NULL) = (revocation_reason IS NULL)),
    CONSTRAINT station_signing_key_revoked_retired_check CHECK (revoked_at IS NULL OR retired_at IS NOT NULL),
    CONSTRAINT station_signing_key_private_key_check CHECK ((wrapped_private_key IS NULL) = (station_id IS NULL)),
    CONSTRAINT station_signing_key_station_retired_check CHECK (station_id IS NOT NULL OR retired_at IS NOT NULL)
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
    IS 'The station''s private key, encrypted with a key derived from the installation''s at-rest encryption key. Never stored in clear; useless without that key file or setting. Null exactly when the station was deleted: its keys seal nothing more, so their private keys are destroyed, while revoking them and listing them on revocation lists needs only the authority''s key.';
COMMENT ON COLUMN ember_schema.station_signing_key.valid_until
    IS 'When the station certificate expires, copied from the certificate.';
COMMENT ON COLUMN ember_schema.station_signing_key.created_at
    IS 'When the key was created.';
COMMENT ON COLUMN ember_schema.station_signing_key.retired_at
    IS 'When the key stopped being used for new seals; null while it is the station''s active key. Deleting the station retires its active key at that moment.';
COMMENT ON COLUMN ember_schema.station_signing_key.revoked_at
    IS 'When the key was revoked, the date its authority''s revocation lists give for it; null while it is not revoked. A revoked key is always retired.';
COMMENT ON COLUMN ember_schema.station_signing_key.revocation_reason
    IS 'Why the key was revoked, as its authority''s revocation lists state it: KEY_COMPROMISE when the private key leaked, SUPERSEDED when it was replaced, CESSATION_OF_OPERATION when the station stopped sealing with it. Null while it is not revoked.';
COMMENT ON CONSTRAINT station_signing_key_private_key_check ON ember_schema.station_signing_key
    IS 'A key keeps its private key exactly as long as its station exists.';
COMMENT ON CONSTRAINT station_signing_key_station_retired_check ON ember_schema.station_signing_key
    IS 'A key of a deleted station is retired.';

CREATE OR REPLACE FUNCTION ember_schema.station_signing_key_station_gone() RETURNS TRIGGER
    LANGUAGE plpgsql
AS
$$
BEGIN
    NEW.retired_at := coalesce(NEW.retired_at, now());
    NEW.wrapped_private_key := NULL;
    RETURN NEW;
END;
$$;

COMMENT ON FUNCTION ember_schema.station_signing_key_station_gone()
    IS 'Retires a station key whose station is deleted, if it was still active, and destroys its private key. Runs on the update that empties station_id, which deleting the station performs on every one of its keys, so no way of deleting a station leaves a key behind that could still seal.';

CREATE TRIGGER station_signing_key_station_gone
    BEFORE UPDATE OF station_id
    ON ember_schema.station_signing_key
    FOR EACH ROW
    WHEN (OLD.station_id IS NOT NULL AND NEW.station_id IS NULL)
EXECUTE FUNCTION ember_schema.station_signing_key_station_gone();

ALTER TABLE ember_schema.member_document
    ADD COLUMN sealed BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE ember_schema.member_document
    ADD CONSTRAINT member_document_sealed_kept CHECK (NOT sealed OR keep_on_archive);

COMMENT ON COLUMN ember_schema.member_document.sealed
    IS 'Whether the document is sealed and therefore locked: it cannot be deleted, the members it is about cannot be changed, it is always kept when they leave, under their name where they are deleted, and its file is never replaced. It serves its current sealed version; a document sealed after it was filed keeps the file it was filed with beside its versions, since its signatures bind to that file and every version is built from it. Only the deletion of its station takes it away, or the retention sweep once the signatures on it no longer need keeping.';
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

CREATE INDEX IF NOT EXISTS member_document_version_sha256_idx
    ON ember_schema.member_document_version (sha256);

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
    IF EXISTS (SELECT 1 FROM ember_schema.station s WHERE s.id = OLD.station_id)
        AND NOT ember_schema.member_document_retention_over(OLD.id) THEN
        RAISE EXCEPTION 'Member document % is sealed and cannot be deleted', OLD.id
            USING ERRCODE = 'restrict_violation';
    END IF;
    RETURN OLD;
END;
$$;

COMMENT ON FUNCTION ember_schema.member_document_keep_sealed()
    IS 'Refuses deleting a sealed member document while its station exists, unless the retention of its signatures is over. Deleting the station still takes it, since the cascade from the station runs after the station row is gone.';

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

ALTER TABLE ember_schema.document_template
    ADD COLUMN signature_retention_months INTEGER NULL CHECK (signature_retention_months >= 0);

UPDATE ember_schema.document_template
SET signature_retention_months = 48
WHERE legal;

COMMENT ON COLUMN ember_schema.document_template.signature_retention_months IS
    'How many months the signatures asked for on a document of this template, their evidence and the signed document are kept after the member it is about has left or was deleted, as evidence for legal claims. NULL keeps them while the member is a member and for 12 months after the member was archived; deleting the member ends that at once. A legal template starts at 48 months: claims fall due within the three years of the regular limitation period, which runs from the end of the year they arose in.';

CREATE TABLE IF NOT EXISTS ember_schema.signing_request
(
    id                 SERIAL PRIMARY KEY,
    uid                UUID        NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    station_id         INTEGER     NOT NULL,
    generation_id      INTEGER     NULL REFERENCES ember_schema.document_generation (id) ON DELETE SET NULL,
    document_id        INTEGER     NULL REFERENCES ember_schema.member_document (id) ON DELETE SET NULL,
    member_id          INTEGER     NULL REFERENCES ember_schema.station_member (id) ON DELETE SET NULL,
    member_name        TEXT        NOT NULL,
    content_sha256     TEXT        NOT NULL CHECK (content_sha256 ~ '^[0-9a-f]{64}$'),
    state              TEXT        NOT NULL DEFAULT 'OPEN'
        CHECK (state IN ('OPEN', 'COMPLETE', 'WITHDRAWN', 'SUPERSEDED')),
    superseded_by      INTEGER     NULL,
    retention_months   INTEGER     NULL CHECK (retention_months >= 0),
    retain_until       TIMESTAMPTZ NULL,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by         INTEGER     NULL REFERENCES ember_schema.station_member (id) ON DELETE SET NULL,
    closed_at          TIMESTAMPTZ NULL,
    CONSTRAINT signing_request_station FOREIGN KEY (station_id)
        REFERENCES ember_schema.station (id) ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT signing_request_superseded_by FOREIGN KEY (superseded_by)
        REFERENCES ember_schema.signing_request (id) ON DELETE SET NULL DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT signing_request_closed CHECK ((state = 'OPEN') = (closed_at IS NULL)),
    CONSTRAINT signing_request_superseded CHECK (superseded_by IS NULL OR state = 'SUPERSEDED')
);

CREATE INDEX IF NOT EXISTS signing_request_station_idx ON ember_schema.signing_request (station_id);
CREATE INDEX IF NOT EXISTS signing_request_generation_idx ON ember_schema.signing_request (generation_id);
CREATE INDEX IF NOT EXISTS signing_request_document_idx ON ember_schema.signing_request (document_id);
CREATE INDEX IF NOT EXISTS signing_request_member_idx ON ember_schema.signing_request (member_id);
CREATE INDEX IF NOT EXISTS signing_request_retain_until_idx
    ON ember_schema.signing_request (retain_until)
    WHERE retain_until IS NOT NULL;

COMMENT ON TABLE ember_schema.signing_request IS
    'Signatures asked for on one generated document: the document as it was frozen, whom it is about and whether it still waits for signatures. The fields to sign are in signing_request_field. Kept after the member leaves or is deleted until retain_until, then deleted by a daily sweep with its evidence.';
COMMENT ON COLUMN ember_schema.signing_request.id IS 'Primary key.';
COMMENT ON COLUMN ember_schema.signing_request.uid IS
    'The request as it is known outside the database, which signing acts name.';
COMMENT ON COLUMN ember_schema.signing_request.station_id IS 'The station the document belongs to.';
COMMENT ON COLUMN ember_schema.signing_request.generation_id IS
    'The entry of the generation log the document came from. NULL once that entry is gone.';
COMMENT ON COLUMN ember_schema.signing_request.document_id IS
    'The member document the generated file was filed as, which signing seals. NULL once that document was deleted.';
COMMENT ON COLUMN ember_schema.signing_request.member_id IS
    'The member the document is about. NULL once that member was deleted.';
COMMENT ON COLUMN ember_schema.signing_request.member_name IS
    'The official name of the member the document is about, as it was when the request was made, so the request still says whom it is about once the member is gone.';
COMMENT ON COLUMN ember_schema.signing_request.content_sha256 IS
    'SHA-256 of the generated file every signature binds to, lower-case hexadecimal. It never changes; a corrected document is a new request.';
COMMENT ON COLUMN ember_schema.signing_request.state IS
    'OPEN while a field waits for a signature, COMPLETE once every field is signed, confirmed on paper, waived or withdrawn and at least one was signed or confirmed, WITHDRAWN when nothing was signed or confirmed, SUPERSEDED when a corrected document replaced it.';
COMMENT ON COLUMN ember_schema.signing_request.superseded_by IS
    'The request for the corrected document that replaced this one. NULL where none did, or once that request was deleted.';
COMMENT ON COLUMN ember_schema.signing_request.retention_months IS
    'How many months the request, its evidence and the signed document are kept after the member is gone, copied from the template when the request was made. NULL keeps them while the member is a member and for 12 months after the member was archived; deleting the member ends that at once.';
COMMENT ON COLUMN ember_schema.signing_request.retain_until IS
    'Until when the request is kept, set once the member has left or was deleted: the leaving date plus retention_months, or plus 12 months for an archived member where retention_months is NULL. Deleting the member moves it to that moment where retention_months is NULL. The daily sweep deletes it afterwards, with its evidence and a sealed document nothing else keeps. NULL while the member is a member.';
COMMENT ON COLUMN ember_schema.signing_request.created_at IS 'When the signatures were asked for.';
COMMENT ON COLUMN ember_schema.signing_request.created_by IS
    'The member who asked for the signatures. NULL where nobody did, or once they are gone.';
COMMENT ON COLUMN ember_schema.signing_request.closed_at IS
    'When the request stopped waiting: completed, withdrawn or superseded. NULL while it is open.';
COMMENT ON CONSTRAINT signing_request_closed ON ember_schema.signing_request IS
    'A request has a closing time exactly when it is no longer open.';
COMMENT ON CONSTRAINT signing_request_superseded ON ember_schema.signing_request IS
    'Only a superseded request names the request that replaced it.';
COMMENT ON CONSTRAINT signing_request_station ON ember_schema.signing_request IS
    'A request goes with its station. Checked at commit, because deleting a station also empties the member columns of a request it is about to delete, and a row changed twice in one statement is checked again against a station already gone.';
COMMENT ON CONSTRAINT signing_request_superseded_by ON ember_schema.signing_request IS
    'The request that replaced this one. Checked at commit for the same reason as signing_request_station.';

CREATE TABLE IF NOT EXISTS ember_schema.signing_request_field
(
    id              SERIAL PRIMARY KEY,
    request_id      INTEGER     NOT NULL,
    field_name      TEXT        NOT NULL CHECK (field_name ~ '^[A-Za-z0-9]+$'),
    role            TEXT        NOT NULL CHECK (role IN ('PARTICIPANT', 'GUARDIAN', 'ANY_GUARDIAN', 'ISSUER')),
    member_id       INTEGER     NULL REFERENCES ember_schema.station_member (id) ON DELETE SET NULL,
    signer_id       INTEGER     NULL REFERENCES ember_schema.station_member (id) ON DELETE SET NULL,
    signer_name     TEXT        NULL,
    capacity        TEXT        NOT NULL CHECK (capacity IN ('ACCOUNT_HOLDER', 'GUARDIAN', 'MEMBER_THROUGH_ACCOUNT')),
    statement       TEXT        NOT NULL,
    state           TEXT        NOT NULL DEFAULT 'OPEN'
        CHECK (state IN ('OPEN', 'SIGNED', 'PAPER_CONFIRMED', 'WAIVED', 'WITHDRAWN')),
    settled_at      TIMESTAMPTZ NULL,
    settled_by      INTEGER     NULL REFERENCES ember_schema.station_member (id) ON DELETE SET NULL,
    settled_by_name TEXT        NULL,
    CONSTRAINT signing_request_field_request FOREIGN KEY (request_id)
        REFERENCES ember_schema.signing_request (id) ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT signing_request_field_name UNIQUE (request_id, field_name),
    CONSTRAINT signing_request_field_settled CHECK ((state = 'OPEN') = (settled_at IS NULL)),
    CONSTRAINT signing_request_field_settled_by CHECK (state <> 'OPEN' OR (settled_by IS NULL AND settled_by_name IS NULL))
);

CREATE INDEX IF NOT EXISTS signing_request_field_member_idx ON ember_schema.signing_request_field (member_id);
CREATE INDEX IF NOT EXISTS signing_request_field_signer_idx ON ember_schema.signing_request_field (signer_id);
CREATE INDEX IF NOT EXISTS signing_request_field_settled_by_idx ON ember_schema.signing_request_field (settled_by);

COMMENT ON TABLE ember_schema.signing_request_field IS
    'One signature field of a signing request and who must sign it, resolved from the field''s role when the request was made: the member, a guardian at a place in the member''s order, any of the member''s guardians, or the issuer. Every field is required; the request is complete once none is open.';
COMMENT ON COLUMN ember_schema.signing_request_field.id IS 'Primary key.';
COMMENT ON COLUMN ember_schema.signing_request_field.request_id IS 'The signing request the field belongs to.';
COMMENT ON COLUMN ember_schema.signing_request_field.field_name IS
    'The name of the signature field in the document, which says who signs it: participant, guardian1, guardian2 and further guardians by place, anyGuardian or issuer.';
COMMENT ON COLUMN ember_schema.signing_request_field.role IS
    'Who signs: PARTICIPANT the member the document is about, GUARDIAN the guardian at the place the field name gives, ANY_GUARDIAN any one guardian of the member, ISSUER whoever issues the document.';
COMMENT ON COLUMN ember_schema.signing_request_field.member_id IS
    'The member the document is about, copied from the request so the field is part of that member''s data. NULL once that member was deleted.';
COMMENT ON COLUMN ember_schema.signing_request_field.signer_id IS
    'The member who must sign: the member themselves, the guardian at that place, or the issuer. NULL for a field any guardian may sign, where nobody held the place when the request was made, and once that member was deleted; such a field can still be confirmed on paper or waived.';
COMMENT ON COLUMN ember_schema.signing_request_field.signer_name IS
    'The official name of the member who must sign, as it was when the request was made. NULL where nobody is named.';
COMMENT ON COLUMN ember_schema.signing_request_field.capacity IS
    'In what capacity the field is signed: ACCOUNT_HOLDER by the signer with their own account, GUARDIAN by a guardian on behalf of the member, MEMBER_THROUGH_ACCOUNT by a member without a login of their own through a guardian''s account.';
COMMENT ON COLUMN ember_schema.signing_request_field.statement IS
    'The statement the signer confirms, exactly as it is shown to them, such as a guardian''s declaration that they hold custody of the member.';
COMMENT ON COLUMN ember_schema.signing_request_field.state IS
    'OPEN while it waits for a signature, SIGNED once a signing act filled it (its evidence is in signing_evidence), PAPER_CONFIRMED when a manager confirmed a signature on paper, WAIVED when a manager let it go, WITHDRAWN when the signature is no longer asked for.';
COMMENT ON COLUMN ember_schema.signing_request_field.settled_at IS 'When the field stopped being open. NULL while it is open.';
COMMENT ON COLUMN ember_schema.signing_request_field.settled_by IS
    'The member who settled the field: the manager who confirmed, waived or withdrew it, or the member whose account confirmed the signature. NULL while it is open, where nobody did, and once they are gone.';
COMMENT ON COLUMN ember_schema.signing_request_field.settled_by_name IS
    'The official name of the member who settled the field, as it was then, kept once they are gone. NULL while it is open and where nobody settled it.';
COMMENT ON CONSTRAINT signing_request_field_request ON ember_schema.signing_request_field IS
    'A field goes with its request. Checked at commit, because deleting a station empties several member columns of a field whose request the same statement deletes.';
COMMENT ON CONSTRAINT signing_request_field_settled ON ember_schema.signing_request_field IS
    'A field has a settling time exactly when it is no longer open.';
COMMENT ON CONSTRAINT signing_request_field_settled_by ON ember_schema.signing_request_field IS
    'An open field names nobody who settled it.';

CREATE TABLE IF NOT EXISTS ember_schema.signing_evidence
(
    id                      SERIAL PRIMARY KEY,
    field_id                INTEGER     NOT NULL UNIQUE,
    signature_level         TEXT        NOT NULL CHECK (signature_level IN ('SIMPLE', 'ADVANCED', 'QUALIFIED')),
    proof                   TEXT        NOT NULL CHECK (proof IN ('PASSKEY', 'SECURITY_KEY', 'TOTP', 'PASSWORD')),
    bound                   BOOLEAN     NOT NULL,
    capacity                TEXT        NOT NULL CHECK (capacity IN ('ACCOUNT_HOLDER', 'GUARDIAN', 'MEMBER_THROUGH_ACCOUNT')),
    challenge_account_id    INTEGER     NOT NULL,
    challenge_member_id     INTEGER     NULL,
    account_member_id       INTEGER     NULL REFERENCES ember_schema.station_member (id) ON DELETE SET NULL,
    member_id               INTEGER     NULL REFERENCES ember_schema.station_member (id) ON DELETE SET NULL,
    account_holder_name     TEXT        NOT NULL,
    member_name             TEXT        NULL,
    guardian_position       INTEGER     NULL,
    guardian_linked_at      TIMESTAMPTZ NULL,
    guardian_linked_by_name TEXT        NULL,
    field_name              TEXT        NOT NULL,
    statement               TEXT        NOT NULL,
    content_sha256          TEXT        NOT NULL CHECK (content_sha256 ~ '^[0-9a-f]{64}$'),
    entry_fields            TEXT[]      NOT NULL DEFAULT '{}',
    entry_values            TEXT[]      NOT NULL DEFAULT '{}',
    nonce                   BYTEA       NOT NULL,
    signed_at               TIMESTAMPTZ NOT NULL,
    truncated_ip            TEXT        NULL,
    user_agent              TEXT        NULL,
    relying_party_id        TEXT        NULL,
    challenge               BYTEA       NULL,
    credential_id           BYTEA       NULL,
    credential_public_key   BYTEA       NULL,
    client_data_json        BYTEA       NULL,
    authenticator_data      BYTEA       NULL,
    signature               BYTEA       NULL,
    user_verified           BOOLEAN     NULL,
    signature_count         BIGINT      NULL,
    sealed_sha256           TEXT        NULL CHECK (sealed_sha256 ~ '^[0-9a-f]{64}$'),
    recorded_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT signing_evidence_field FOREIGN KEY (field_id)
        REFERENCES ember_schema.signing_request_field (id) ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT signing_evidence_bound CHECK (bound = (proof IN ('PASSKEY', 'SECURITY_KEY'))),
    CONSTRAINT signing_evidence_webauthn CHECK (
        bound = (relying_party_id IS NOT NULL AND challenge IS NOT NULL AND credential_id IS NOT NULL
            AND credential_public_key IS NOT NULL AND client_data_json IS NOT NULL
            AND authenticator_data IS NOT NULL AND signature IS NOT NULL AND user_verified IS NOT NULL
            AND signature_count IS NOT NULL)
        AND (bound OR num_nonnulls(relying_party_id, challenge, credential_id, credential_public_key,
                                   client_data_json, authenticator_data, signature, user_verified,
                                   signature_count) = 0)),
    CONSTRAINT signing_evidence_member CHECK ((capacity = 'ACCOUNT_HOLDER') = (challenge_member_id IS NULL)),
    CONSTRAINT signing_evidence_guardian CHECK (capacity <> 'ACCOUNT_HOLDER' OR guardian_position IS NULL),
    CONSTRAINT signing_evidence_entries CHECK (cardinality(entry_fields) = cardinality(entry_values))
);

CREATE INDEX IF NOT EXISTS signing_evidence_account_member_idx ON ember_schema.signing_evidence (account_member_id);
CREATE INDEX IF NOT EXISTS signing_evidence_member_idx ON ember_schema.signing_evidence (member_id);
CREATE INDEX IF NOT EXISTS signing_evidence_unsealed_idx
    ON ember_schema.signing_evidence (recorded_at)
    WHERE sealed_sha256 IS NULL;

COMMENT ON INDEX ember_schema.signing_evidence_unsealed_idx IS
    'Finds the acts no sealed version carries yet, which the sweep seals, the one waiting longest first.';

COMMENT ON TABLE ember_schema.signing_evidence IS
    'The record of one signing act on one signature field, kept faithfully with everything a reader needs to check it again without Ember: who confirmed with which proof, for whom, what they read and confirmed, when and from where, and for a passkey or security key the whole answer and the public key it verifies under. Names and the guardian link are copied as they were, since the account, the member and the link may all be gone when somebody reads it.';
COMMENT ON COLUMN ember_schema.signing_evidence.id IS 'Primary key.';
COMMENT ON COLUMN ember_schema.signing_evidence.field_id IS 'The signature field the act filled; a field is signed once.';
COMMENT ON COLUMN ember_schema.signing_evidence.signature_level IS
    'The legal level the signature reached: SIMPLE for Ember''s own signing, ADVANCED or QUALIFIED only through a provider outside Ember.';
COMMENT ON COLUMN ember_schema.signing_evidence.proof IS
    'What the account holder confirmed with: PASSKEY or SECURITY_KEY, which sign the challenge, or TOTP or PASSWORD, which only prove presence at that moment.';
COMMENT ON COLUMN ember_schema.signing_evidence.bound IS
    'Whether the proof itself is bound to the document, which only a passkey or security key is, rather than only recorded next to it.';
COMMENT ON COLUMN ember_schema.signing_evidence.capacity IS
    'In what capacity the act was given: ACCOUNT_HOLDER for themselves, GUARDIAN on behalf of the member, MEMBER_THROUGH_ACCOUNT by the member through the account of somebody else.';
COMMENT ON COLUMN ember_schema.signing_evidence.challenge_account_id IS
    'The id of the account whose step-up confirmed the act, as the challenge was computed from it. Kept as it was and never followed, so the challenge can be computed again after the account is gone or the station moved.';
COMMENT ON COLUMN ember_schema.signing_evidence.challenge_member_id IS
    'The id of the member signed for or signing through the account, as the challenge was computed from it. Kept as it was and never followed. NULL exactly when the account holder signed for themselves.';
COMMENT ON COLUMN ember_schema.signing_evidence.account_member_id IS
    'The member at the station whose account confirmed the act. NULL once that member was deleted.';
COMMENT ON COLUMN ember_schema.signing_evidence.member_id IS
    'The member a guardian signed for, or who signed through the account. NULL where the account holder signed for themselves, and once that member was deleted.';
COMMENT ON COLUMN ember_schema.signing_evidence.account_holder_name IS
    'The official name of the account holder whose step-up confirmed the act, as it was at signing.';
COMMENT ON COLUMN ember_schema.signing_evidence.member_name IS
    'The official name of the member signed for or signing through the account, as it was at signing. NULL where the account holder signed for themselves.';
COMMENT ON COLUMN ember_schema.signing_evidence.guardian_position IS
    'Where the account holder stood in the member''s order of guardians at signing, counted from 0, when they acted as guardian or lent their account. NULL where the account holder signed for themselves.';
COMMENT ON COLUMN ember_schema.signing_evidence.guardian_linked_at IS
    'When the account holder was linked to the member as guardian, as recorded at signing. NULL where nothing was recorded or no guardian was involved.';
COMMENT ON COLUMN ember_schema.signing_evidence.guardian_linked_by_name IS
    'The official name of the member who made that guardian link, as it was at signing. NULL where nobody did or no guardian was involved.';
COMMENT ON COLUMN ember_schema.signing_evidence.field_name IS 'The name of the signature field the act filled.';
COMMENT ON COLUMN ember_schema.signing_evidence.statement IS 'The statement the signer confirmed, exactly as shown.';
COMMENT ON COLUMN ember_schema.signing_evidence.content_sha256 IS
    'SHA-256 of the frozen document the signer read, lower-case hexadecimal.';
COMMENT ON COLUMN ember_schema.signing_evidence.entry_fields IS
    'The names of the fields the signer typed values into, in the order the challenge took them.';
COMMENT ON COLUMN ember_schema.signing_evidence.entry_values IS
    'What the signer typed into those fields, exactly as typed, in the same order.';
COMMENT ON COLUMN ember_schema.signing_evidence.nonce IS 'The nonce issued when the act started, part of the challenge.';
COMMENT ON COLUMN ember_schema.signing_evidence.signed_at IS 'When the confirmation was accepted, by the server''s clock.';
COMMENT ON COLUMN ember_schema.signing_evidence.truncated_ip IS
    'The client''s address with its host part zeroed. NULL where it was not known.';
COMMENT ON COLUMN ember_schema.signing_evidence.user_agent IS 'The browser''s user agent. NULL where it sent none.';
COMMENT ON COLUMN ember_schema.signing_evidence.relying_party_id IS
    'For a passkey or security key: the relying party id the credential is bound to. NULL for every other proof.';
COMMENT ON COLUMN ember_schema.signing_evidence.challenge IS
    'For a passkey or security key: the challenge the authenticator signed, computed from the act. NULL for every other proof.';
COMMENT ON COLUMN ember_schema.signing_evidence.credential_id IS
    'For a passkey or security key: the id of the credential that signed. NULL for every other proof.';
COMMENT ON COLUMN ember_schema.signing_evidence.credential_public_key IS
    'For a passkey or security key: the credential''s public key, COSE encoded, as it was on file at signing, which the signature verifies under after the credential is gone. NULL for every other proof.';
COMMENT ON COLUMN ember_schema.signing_evidence.client_data_json IS
    'For a passkey or security key: the client data the browser collected. NULL for every other proof.';
COMMENT ON COLUMN ember_schema.signing_evidence.authenticator_data IS
    'For a passkey or security key: the authenticator data. NULL for every other proof.';
COMMENT ON COLUMN ember_schema.signing_evidence.signature IS
    'For a passkey or security key: the authenticator''s signature over the authenticator data and the hash of the client data. NULL for every other proof.';
COMMENT ON COLUMN ember_schema.signing_evidence.user_verified IS
    'For a passkey or security key: whether the authenticator verified its user. NULL for every other proof.';
COMMENT ON COLUMN ember_schema.signing_evidence.signature_count IS
    'For a passkey or security key: the authenticator''s signature counter at the act. NULL for every other proof.';
COMMENT ON COLUMN ember_schema.signing_evidence.sealed_sha256 IS
    'SHA-256 of the first sealed version of the document that carries the act, lower-case hexadecimal. NULL until a sealed version carries it; such acts are sealed again by a sweep.';
COMMENT ON COLUMN ember_schema.signing_evidence.recorded_at IS 'When the evidence was stored.';
COMMENT ON CONSTRAINT signing_evidence_field ON ember_schema.signing_evidence IS
    'Evidence goes with its field. Checked at commit, because deleting a station empties both member columns of evidence whose field the same statement deletes.';
COMMENT ON CONSTRAINT signing_evidence_bound ON ember_schema.signing_evidence IS
    'Only a passkey or security key binds the proof to the document.';
COMMENT ON CONSTRAINT signing_evidence_webauthn ON ember_schema.signing_evidence IS
    'A passkey or security key act carries its whole answer, and no other act carries any of it.';
COMMENT ON CONSTRAINT signing_evidence_member ON ember_schema.signing_evidence IS
    'A member is named in the challenge exactly when the account holder did not sign for themselves.';
COMMENT ON CONSTRAINT signing_evidence_guardian ON ember_schema.signing_evidence IS
    'A guardian link is recorded only where somebody acted through a guardian''s account.';
COMMENT ON CONSTRAINT signing_evidence_entries ON ember_schema.signing_evidence IS
    'Every typed value has its field.';

CREATE OR REPLACE FUNCTION ember_schema.member_document_retention_over(sealed_document_id INTEGER) RETURNS BOOLEAN
    LANGUAGE sql
    STABLE
AS
$$
SELECT EXISTS (SELECT 1
               FROM ember_schema.signing_request r
               WHERE r.document_id = sealed_document_id
                 AND r.retain_until <= now())
           AND NOT EXISTS (SELECT 1
                           FROM ember_schema.signing_request r
                           WHERE r.document_id = sealed_document_id
                             AND (r.retain_until IS NULL OR r.retain_until > now()))
           AND NOT EXISTS (SELECT 1
                           FROM ember_schema.member_document_member m
                                    JOIN ember_schema.station_member sm ON sm.id = m.member_id
                           WHERE m.document_id = sealed_document_id
                             AND NOT sm.former);
$$;

COMMENT ON FUNCTION ember_schema.member_document_retention_over(INTEGER)
    IS 'Whether a sealed member document no longer needs keeping: some signing request on it has passed its retain_until, none still keeps it, and none of the members it is bound to is still a current member of the station. Only then may the retention sweep delete the document while its station exists.';

COMMENT ON COLUMN ember_schema.webauthn_challenge.purpose IS
    'Which ceremony minted the challenge: REGISTRATION, SECOND_FACTOR_ASSERTION, PASSKEY_SIGN_IN, PASSKEY_TRIAL, STEPUP_ASSERTION, DEVICE_ENROLLMENT or SIGNING (a started signing act, spent once by its completion). A challenge is only spendable at the finish of its own ceremony.';

ALTER TABLE ember_schema.inventory_item
    ADD COLUMN owner_station_uid UUID;

UPDATE ember_schema.inventory_item ii
SET owner_station_uid = s.uid
FROM ember_schema.station s
WHERE s.id = ii.owner_station_id;

ALTER TABLE ember_schema.inventory_item
    DROP CONSTRAINT IF EXISTS chk_inventory_item_owner;

ALTER TABLE ember_schema.inventory_item
    ADD CONSTRAINT chk_inventory_item_owner CHECK (
        CASE owner_kind
            WHEN 'STATION' THEN owner_cluster_id IS NULL AND owner_station_id IS NULL
                AND owner_station_uid IS NULL AND loan_request_item_id IS NULL
            WHEN 'CLUSTER' THEN owner_station_id IS NULL AND owner_station_uid IS NULL
                AND loan_request_item_id IS NULL
            WHEN 'PARTNER_STATION' THEN owner_cluster_id IS NULL
                AND owner_station_uid IS NOT NULL AND loan_request_item_id IS NOT NULL
            ELSE FALSE
            END
        );

COMMENT ON COLUMN ember_schema.inventory_item.owner_station_id
    IS 'The partner station that owns the item while that station runs on this installation, null for every other owner. Only ever set for PARTNER_STATION, and empty for an owner on another installation, which owner_station_uid names.';
COMMENT ON COLUMN ember_schema.inventory_item.owner_station_uid
    IS 'The partner station that owns the item, by the uid it carries on every installation. Set exactly for PARTNER_STATION, whether the owner runs on this installation or on another one, and kept when the owner or the borrower moves to another installation.';
COMMENT ON CONSTRAINT chk_inventory_item_owner ON ember_schema.inventory_item
    IS 'Each owner kind carries only the pointers that belong to it: a borrowed copy always names its owner by uid and the loan line it came on, a station''s own item names no owner, and an association''s item names no partner station.';

ALTER TABLE ember_schema.station
    ADD COLUMN moved_away_at TIMESTAMPTZ,
    ADD COLUMN moved_to      TEXT,
    ADD CONSTRAINT station_moved_to_check CHECK (moved_away_at IS NOT NULL OR moved_to IS NULL);

COMMENT ON COLUMN ember_schema.station.moved_away_at
    IS 'When the station finished moving to another installation. The row left here is a read-only copy under the same uid, which this installation no longer treats as running here: its partners reach the station at its new address. Null for every station that runs here.';
COMMENT ON COLUMN ember_schema.station.moved_to
    IS 'The address of the installation the station moved to, as the destination named itself. Null while the station runs here, and where the destination named no address.';
COMMENT ON CONSTRAINT station_moved_to_check ON ember_schema.station
    IS 'Only a station that moved away names where it went.';

UPDATE ember_schema.federation_lending_request_item ri
SET label = coalesce((SELECT a.name FROM ember_schema.inventory_art a WHERE a.id = ri.art_id),
                     (SELECT i.name FROM ember_schema.inventory_item i WHERE i.id = ri.item_id),
                     (SELECT v.name FROM ember_schema.inventory v WHERE v.id = ri.inventory_id),
                     '')
WHERE ri.label = '';

COMMENT ON COLUMN ember_schema.federation_lending_message.sender_member_id
    IS 'The member who sent the message, where that member is at a station of this installation. NULL for system messages, for a message from a station on another installation, for a partner''s message that arrived with a station moving here, and once the member is gone.';

COMMENT ON COLUMN ember_schema.federation_lending_request_item.label
    IS 'What the line asks for, in words, as it was named when it was asked for: the kind of thing, the piece or the inventory it names, and on the copy of a request to a station on another installation, what the lending station called it. Kept when the gear it names is gone or on another installation, so the line still says what it was. Empty only where nothing named it.';

ALTER TABLE ember_schema.account_2fa_webauthn
    ADD COLUMN key_stamp_token   BYTEA       NULL,
    ADD COLUMN key_stamped_at    TIMESTAMPTZ NULL,
    ADD COLUMN key_stamp_service TEXT        NULL,
    ADD COLUMN key_stamp_kind    TEXT        NULL
        CHECK (key_stamp_kind IN ('AT_REGISTRATION', 'AFTER_REGISTRATION', 'AT_FIRST_SIGNING')),
    ADD CONSTRAINT account_2fa_webauthn_key_stamp
        CHECK (num_nonnulls(key_stamp_token, key_stamped_at, key_stamp_service, key_stamp_kind) IN (0, 4));

COMMENT ON COLUMN ember_schema.account_2fa_webauthn.key_stamp_token IS
    'RFC 3161 timestamp token over the SHA-256 of public_key_cose, DER encoded, proving the key existed at key_stamped_at. NULL until a timestamp service gave one; the first token stays.';
COMMENT ON COLUMN ember_schema.account_2fa_webauthn.key_stamped_at IS
    'The time key_stamp_token states. NULL while the key has no timestamp.';
COMMENT ON COLUMN ember_schema.account_2fa_webauthn.key_stamp_service IS
    'The address of the timestamp service that gave key_stamp_token. NULL while the key has no timestamp.';
COMMENT ON COLUMN ember_schema.account_2fa_webauthn.key_stamp_kind IS
    'How the key got its timestamp: AT_REGISTRATION right after the credential was registered, AFTER_REGISTRATION by the daily retry for a credential no timestamp service stamped then or registered before key timestamps, AT_FIRST_SIGNING during the first signing act that reached a timestamp service. Only AT_REGISTRATION fixes the key as the one registered. NULL while the key has no timestamp.';
COMMENT ON CONSTRAINT account_2fa_webauthn_key_stamp ON ember_schema.account_2fa_webauthn IS
    'A key timestamp is recorded whole or not at all.';

ALTER TABLE ember_schema.signing_evidence
    ADD COLUMN credential_key_stamp_token   BYTEA       NULL,
    ADD COLUMN credential_key_stamped_at    TIMESTAMPTZ NULL,
    ADD COLUMN credential_key_stamp_service TEXT        NULL,
    ADD COLUMN credential_key_stamp_kind    TEXT        NULL
        CHECK (credential_key_stamp_kind IN ('AT_REGISTRATION', 'AFTER_REGISTRATION', 'AT_FIRST_SIGNING')),
    ADD CONSTRAINT signing_evidence_key_stamp
        CHECK (num_nonnulls(credential_key_stamp_token, credential_key_stamped_at, credential_key_stamp_service,
                            credential_key_stamp_kind) IN (0, 4)
            AND (bound OR credential_key_stamp_token IS NULL));

COMMENT ON COLUMN ember_schema.signing_evidence.credential_key_stamp_token IS
    'For a passkey or security key: the RFC 3161 timestamp token over the SHA-256 of credential_public_key as the credential held it at the act, DER encoded. NULL for every other proof, and for a key that had no timestamp and got none at the act (not stamped).';
COMMENT ON COLUMN ember_schema.signing_evidence.credential_key_stamped_at IS
    'The time credential_key_stamp_token states. NULL where the key was not stamped.';
COMMENT ON COLUMN ember_schema.signing_evidence.credential_key_stamp_service IS
    'The address of the timestamp service that gave credential_key_stamp_token. NULL where the key was not stamped.';
COMMENT ON COLUMN ember_schema.signing_evidence.credential_key_stamp_kind IS
    'How the key got its timestamp, as the credential recorded it: AT_REGISTRATION, AFTER_REGISTRATION (by the daily retry) or AT_FIRST_SIGNING (during the first signing act that reached a timestamp service, possibly this one). NULL where the key was not stamped.';
COMMENT ON CONSTRAINT signing_evidence_key_stamp ON ember_schema.signing_evidence IS
    'A key timestamp is recorded whole or not at all, and only for a passkey or security key.';

ALTER TABLE ember_schema.document_template
    ADD COLUMN signed_copy_attached BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN ember_schema.document_template.signed_copy_attached IS
    'Whether the copy a signer gets by mail after signing a document of this template carries the sealed PDF. Off by default, since consent forms may hold health data and mail is no place for it; the mail always carries the SHA-256 of the sealed version and a link to it.';

ALTER TABLE ember_schema.signing_request
    ADD COLUMN copy_attached BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN ember_schema.signing_request.copy_attached IS
    'Whether the copy each signer gets by mail carries the sealed PDF, copied from the template when the request was made.';

ALTER TABLE ember_schema.signing_request_field
    ADD COLUMN reminded_at    TIMESTAMPTZ NULL,
    ADD COLUMN reminders_sent INTEGER     NOT NULL DEFAULT 0 CHECK (reminders_sent >= 0),
    ADD COLUMN sealed_sha256  TEXT        NULL CHECK (sealed_sha256 ~ '^[0-9a-f]{64}$'),
    ADD CONSTRAINT signing_request_field_sealed CHECK (state <> 'OPEN' OR sealed_sha256 IS NULL);

CREATE INDEX IF NOT EXISTS signing_request_field_unsealed_idx
    ON ember_schema.signing_request_field (settled_at)
    WHERE state <> 'OPEN' AND sealed_sha256 IS NULL;

COMMENT ON COLUMN ember_schema.signing_request_field.reminded_at IS
    'When the people asked to sign the field were last reminded of it. NULL before the first reminder.';
COMMENT ON COLUMN ember_schema.signing_request_field.reminders_sent IS
    'How many reminders went out for the field. A field is reminded of a week after it was asked for and every week after that, three times at most.';
COMMENT ON COLUMN ember_schema.signing_request_field.sealed_sha256 IS
    'SHA-256 of the first sealed version of the document that shows the field settled, lower-case hexadecimal. NULL while the field is open, and until a sealed version shows it settled; a sweep seals such a state where somebody signed the request electronically, since only then does the document carry a seal.';
COMMENT ON CONSTRAINT signing_request_field_sealed ON ember_schema.signing_request_field IS
    'Only a settled field can be shown settled by a sealed version.';
COMMENT ON INDEX ember_schema.signing_request_field_unsealed_idx IS
    'Finds the fields settled since the last sealed version of their document, which the sweep seals, the one waiting longest first.';

CREATE TABLE IF NOT EXISTS ember_schema.email_queue_attachment
(
    id           SERIAL PRIMARY KEY,
    email_id     INTEGER NOT NULL REFERENCES ember_schema.email_queue (id) ON DELETE CASCADE,
    file_name    TEXT    NOT NULL,
    content_type TEXT    NOT NULL,
    content      BYTEA   NOT NULL
);

CREATE INDEX IF NOT EXISTS email_queue_attachment_email_idx ON ember_schema.email_queue_attachment (email_id);

COMMENT ON TABLE ember_schema.email_queue_attachment IS
    'Files a queued email carries, such as the sealed PDF a signer gets as their own copy. Deleted as soon as the email was handed to a provider or failed for good, so a file never stays here longer than its email waits.';
COMMENT ON COLUMN ember_schema.email_queue_attachment.id IS 'Primary key.';
COMMENT ON COLUMN ember_schema.email_queue_attachment.email_id IS 'The queued email the file belongs to.';
COMMENT ON COLUMN ember_schema.email_queue_attachment.file_name IS 'The name the file carries in the email.';
COMMENT ON COLUMN ember_schema.email_queue_attachment.content_type IS 'The media type of the file, such as application/pdf.';
COMMENT ON COLUMN ember_schema.email_queue_attachment.content IS 'The file itself.';

CREATE TABLE IF NOT EXISTS ember_schema.event_document_submission
(
    id            SERIAL PRIMARY KEY,
    station_id    INTEGER     NOT NULL REFERENCES ember_schema.station (id) ON DELETE CASCADE,
    event_id      INTEGER     NOT NULL REFERENCES ember_schema.station_event (id) ON DELETE CASCADE,
    event_date    DATE        NOT NULL,
    template_id   INTEGER     NOT NULL REFERENCES ember_schema.document_template (id) ON DELETE CASCADE,
    member_id     INTEGER     NOT NULL REFERENCES ember_schema.station_member (id) ON DELETE CASCADE,
    document_id   INTEGER     NOT NULL REFERENCES ember_schema.member_document (id) ON DELETE CASCADE,
    state         TEXT        NOT NULL DEFAULT 'SUBMITTED' CHECK (state IN ('SUBMITTED', 'CONFIRMED', 'REJECTED')),
    submitted_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    submitted_by  INTEGER     NULL REFERENCES ember_schema.station_member (id) ON DELETE SET NULL,
    reviewed_at   TIMESTAMPTZ NULL,
    reviewed_by   INTEGER     NULL REFERENCES ember_schema.station_member (id) ON DELETE SET NULL,
    reject_reason TEXT        NULL,
    CONSTRAINT event_document_submission_reviewed CHECK ((state = 'SUBMITTED') = (reviewed_at IS NULL)),
    CONSTRAINT event_document_submission_reason CHECK ((state = 'REJECTED') = (reject_reason IS NOT NULL))
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_event_document_submission_standing
    ON ember_schema.event_document_submission (event_id, event_date, template_id, member_id)
    WHERE state <> 'REJECTED';
CREATE INDEX IF NOT EXISTS idx_event_document_submission_station
    ON ember_schema.event_document_submission (station_id);
CREATE INDEX IF NOT EXISTS idx_event_document_submission_template
    ON ember_schema.event_document_submission (template_id);
CREATE INDEX IF NOT EXISTS idx_event_document_submission_member
    ON ember_schema.event_document_submission (member_id);
CREATE INDEX IF NOT EXISTS idx_event_document_submission_document
    ON ember_schema.event_document_submission (document_id);
CREATE INDEX IF NOT EXISTS idx_event_document_submission_submitted_by
    ON ember_schema.event_document_submission (submitted_by);
CREATE INDEX IF NOT EXISTS idx_event_document_submission_reviewed_by
    ON ember_schema.event_document_submission (reviewed_by);

COMMENT ON TABLE ember_schema.event_document_submission IS
    'A signed paper copy of a document an appointment asks for, scanned and handed in for one participant and one date of the appointment. The scan is filed in the participant''s documents. It waits until somebody who manages the registrations confirms or turns it down; one handed in by such a manager is confirmed at once. A turned-down scan stays as history and a new one can be handed in.';
COMMENT ON COLUMN ember_schema.event_document_submission.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.event_document_submission.station_id IS 'The station of the appointment.';
COMMENT ON COLUMN ember_schema.event_document_submission.event_id IS 'The appointment that asks for the document.';
COMMENT ON COLUMN ember_schema.event_document_submission.event_date IS
    'The day of the appointment the scan is for, which tells the dates of a repeating appointment apart.';
COMMENT ON COLUMN ember_schema.event_document_submission.template_id IS
    'The document template the appointment asks for and whose signed copy was scanned.';
COMMENT ON COLUMN ember_schema.event_document_submission.member_id IS 'The participant the signed copy is for.';
COMMENT ON COLUMN ember_schema.event_document_submission.document_id IS
    'The scan, filed in the participant''s documents. Deleting the scan removes the submission with it, which opens the requirement again.';
COMMENT ON COLUMN ember_schema.event_document_submission.state IS
    'SUBMITTED while it waits for a manager, CONFIRMED once a manager confirmed it as the signed paper copy, REJECTED once a manager turned it down.';
COMMENT ON COLUMN ember_schema.event_document_submission.submitted_at IS 'When the scan was handed in.';
COMMENT ON COLUMN ember_schema.event_document_submission.submitted_by IS
    'The member who handed the scan in: the participant, a guardian or a manager. NULL once they are gone.';
COMMENT ON COLUMN ember_schema.event_document_submission.reviewed_at IS
    'When the scan was confirmed or turned down. NULL while it waits.';
COMMENT ON COLUMN ember_schema.event_document_submission.reviewed_by IS
    'The manager who confirmed or turned down the scan. NULL while it waits, and once they are gone.';
COMMENT ON COLUMN ember_schema.event_document_submission.reject_reason IS
    'Why the scan was turned down, as the manager wrote it and the participant was told. NULL unless it was turned down.';
COMMENT ON CONSTRAINT event_document_submission_reviewed ON ember_schema.event_document_submission IS
    'A scan has a review time exactly when it no longer waits.';
COMMENT ON CONSTRAINT event_document_submission_reason ON ember_schema.event_document_submission IS
    'A scan carries a reason exactly when it was turned down.';
COMMENT ON INDEX ember_schema.uq_event_document_submission_standing IS
    'At most one scan per participant, document and date waits or is confirmed; turned-down ones are kept beside it.';

ALTER TABLE ember_schema.signing_ca
    ADD COLUMN abandoned_at TIMESTAMPTZ NULL,
    ADD CONSTRAINT signing_ca_abandoned_retired_check CHECK (abandoned_at IS NULL OR retired_at IS NOT NULL);

COMMENT ON COLUMN ember_schema.signing_ca.abandoned_at IS
    'When an instance administrator gave the authority up because its private key no longer opened under the at-rest encryption key, for example after the key file was lost. Null while it is in use. A given-up authority is retired; its certificate stays published, and its last revocation list is served as it was, since nothing can sign a newer one.';
COMMENT ON CONSTRAINT signing_ca_abandoned_retired_check ON ember_schema.signing_ca IS
    'A given-up authority is retired.';

ALTER TABLE ember_schema.station_signing_key
    ADD COLUMN abandoned_at TIMESTAMPTZ NULL,
    ADD CONSTRAINT station_signing_key_abandoned_retired_check CHECK (abandoned_at IS NULL OR retired_at IS NOT NULL);

COMMENT ON COLUMN ember_schema.station_signing_key.abandoned_at IS
    'When an instance administrator gave the key up because its private key no longer opened under the at-rest encryption key, for example after the key file was lost. Null while it is in use. A given-up key is retired, so the station''s next seal gets a new key; its certificate stays published, and a revoked one stays on its authority''s revocation lists.';
COMMENT ON CONSTRAINT station_signing_key_abandoned_retired_check ON ember_schema.station_signing_key IS
    'A given-up key is retired.';

CREATE TABLE IF NOT EXISTS ember_schema.signing_key_recovery
(
    id                  SERIAL PRIMARY KEY,
    recovered_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    account_id          INT         REFERENCES ember_schema.account (id) ON DELETE SET NULL,
    authority_serials   TEXT[]      NOT NULL,
    station_key_serials TEXT[]      NOT NULL,
    CONSTRAINT signing_key_recovery_not_empty_check
        CHECK (cardinality(authority_serials) + cardinality(station_key_serials) > 0)
);

COMMENT ON TABLE ember_schema.signing_key_recovery IS
    'Audit trail of the signing keys an instance administrator gave up because they no longer opened under the at-rest encryption key. One row per recovery; nothing is ever given up without one. Kept for as long as the installation runs.';
COMMENT ON COLUMN ember_schema.signing_key_recovery.id IS
    'Primary key.';
COMMENT ON COLUMN ember_schema.signing_key_recovery.recovered_at IS
    'When the keys were given up.';
COMMENT ON COLUMN ember_schema.signing_key_recovery.account_id IS
    'The instance administrator who gave them up. Set to null when that account is deleted, so the row stays as proof of what was done.';
COMMENT ON COLUMN ember_schema.signing_key_recovery.authority_serials IS
    'Certificate serial numbers of the authorities given up, lower-case hexadecimal. Empty when every authority still opened.';
COMMENT ON COLUMN ember_schema.signing_key_recovery.station_key_serials IS
    'Certificate serial numbers of the station keys given up, lower-case hexadecimal. Empty when every station key still opened.';
COMMENT ON CONSTRAINT signing_key_recovery_not_empty_check ON ember_schema.signing_key_recovery IS
    'A recovery gives up at least one key.';

CREATE TABLE IF NOT EXISTS ember_schema.account_signature
(
    account_id             INTEGER PRIMARY KEY REFERENCES ember_schema.account (id) ON DELETE CASCADE,
    image_sha256           TEXT        NULL CHECK (image_sha256 ~ '^[0-9a-f]{64}$'),
    image_source           TEXT        NULL CHECK (image_source IN ('DRAWN', 'TYPED', 'UPLOADED')),
    image_saved_at         TIMESTAMPTZ NULL,
    auto_sign_consented_at TIMESTAMPTZ NULL,
    CONSTRAINT account_signature_image CHECK (num_nonnulls(image_sha256, image_source, image_saved_at) IN (0, 3))
);

COMMENT ON TABLE ember_schema.account_signature IS
    'A person''s own signature picture and whether they let letters they issue be signed with it without signing each by hand, one row per account. The picture itself is a transparent PNG in the account''s file store (images/signatures/signature.png); this row says which one it is.';
COMMENT ON COLUMN ember_schema.account_signature.account_id IS 'The account the signature belongs to.';
COMMENT ON COLUMN ember_schema.account_signature.image_sha256 IS
    'SHA-256 of the stored signature picture, lower-case hexadecimal. NULL while no picture is saved.';
COMMENT ON COLUMN ember_schema.account_signature.image_source IS
    'How the picture was made: DRAWN on the screen, TYPED as the name in a handwriting style, UPLOADED as a photo or scan cleaned to a transparent picture. NULL while no picture is saved.';
COMMENT ON COLUMN ember_schema.account_signature.image_saved_at IS 'When the picture was saved. NULL while no picture is saved.';
COMMENT ON COLUMN ember_schema.account_signature.auto_sign_consented_at IS
    'When the person agreed that letters naming them as the template''s issuer are signed with their picture and sealed by the station without asking each time. NULL while they have not agreed or took it back; taking it back stops it for every letter generated afterwards.';
COMMENT ON CONSTRAINT account_signature_image ON ember_schema.account_signature IS
    'A saved picture is recorded whole or not at all.';

CREATE TABLE IF NOT EXISTS ember_schema.issuer_signature
(
    id             SERIAL PRIMARY KEY,
    generation_id  INTEGER     NOT NULL UNIQUE,
    issuer_id      INTEGER     NULL REFERENCES ember_schema.station_member (id) ON DELETE SET NULL,
    consented_at   TIMESTAMPTZ NOT NULL,
    image_sha256   TEXT        NOT NULL CHECK (image_sha256 ~ '^[0-9a-f]{64}$'),
    seal_level     TEXT        NOT NULL CHECK (seal_level IN ('BASELINE_B', 'BASELINE_T', 'BASELINE_LT')),
    timestamped_by TEXT        NULL,
    signed_at      TIMESTAMPTZ NOT NULL,
    CONSTRAINT issuer_signature_generation FOREIGN KEY (generation_id)
        REFERENCES ember_schema.document_generation (id) ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT issuer_signature_timestamp CHECK ((seal_level = 'BASELINE_B') = (timestamped_by IS NULL))
);

CREATE INDEX IF NOT EXISTS issuer_signature_issuer_idx ON ember_schema.issuer_signature (issuer_id);

COMMENT ON TABLE ember_schema.issuer_signature IS
    'A generated letter the station signed for its issuer without asking them each time: the issuer''s signature picture drawn into the letter''s issuer field and the letter sealed with the station''s key before it was filed, under the issuer''s standing consent. One row per generated document that was signed so; the filed file and the SHA-256 in the generation log are the signed letter.';
COMMENT ON COLUMN ember_schema.issuer_signature.id IS 'Primary key.';
COMMENT ON COLUMN ember_schema.issuer_signature.generation_id IS 'The entry of the generation log for the letter. The record goes with it.';
COMMENT ON COLUMN ember_schema.issuer_signature.issuer_id IS
    'The member whose signature picture was drawn in, the template''s issuer. NULL once that member was deleted.';
COMMENT ON COLUMN ember_schema.issuer_signature.consented_at IS
    'When the issuer had agreed to letters being signed with their picture, as it stood when this letter was signed.';
COMMENT ON COLUMN ember_schema.issuer_signature.image_sha256 IS
    'SHA-256 of the signature picture that was drawn in, lower-case hexadecimal, as the account held it then.';
COMMENT ON COLUMN ember_schema.issuer_signature.seal_level IS
    'The PAdES baseline level the station''s seal on the letter reached: BASELINE_B without a timestamp, BASELINE_T with one, BASELINE_LT with the timestamp and the material to check both offline.';
COMMENT ON COLUMN ember_schema.issuer_signature.timestamped_by IS
    'The address of the timestamp service whose timestamp the letter carries; NULL for a seal without a timestamp.';
COMMENT ON COLUMN ember_schema.issuer_signature.signed_at IS 'When the picture was drawn in and the letter sealed.';
COMMENT ON CONSTRAINT issuer_signature_generation ON ember_schema.issuer_signature IS
    'The record goes with its generation log entry. Checked at commit, because deleting a station empties the issuer column of a record whose log entry the same statement deletes.';
COMMENT ON CONSTRAINT issuer_signature_timestamp ON ember_schema.issuer_signature IS
    'A timestamp service is named exactly when the seal carries a timestamp.';

ALTER TABLE ember_schema.signing_evidence
    ADD COLUMN mark_image  BYTEA NULL,
    ADD COLUMN mark_source TEXT  NULL CHECK (mark_source IN ('DRAWN', 'TYPED', 'UPLOADED', 'SAVED')),
    ADD CONSTRAINT signing_evidence_mark CHECK ((mark_image IS NULL) = (mark_source IS NULL));

COMMENT ON COLUMN ember_schema.signing_evidence.mark_image IS
    'The signature picture drawn into the signed field, a transparent PNG as the signer gave it at the act: their saved picture or one drawn on the spot. Every sealed version of the document draws it from here, so a later change of the saved picture never changes an earlier signature. NULL for acts recorded before signature pictures, whose field shows the name and date only.';
COMMENT ON COLUMN ember_schema.signing_evidence.mark_source IS
    'How mark_image came to the act: DRAWN, TYPED or UPLOADED when it was made for the act, SAVED when it was the picture the signer''s account kept. The evidence attached to each sealed version and its record page name it beside the picture''s SHA-256. NULL exactly when mark_image is.';
COMMENT ON CONSTRAINT signing_evidence_mark ON ember_schema.signing_evidence IS
    'A signature picture is recorded together with how it came to the act, or neither is.';

ALTER TABLE ember_schema.document_template_field
    ADD COLUMN statement TEXT NULL CHECK (statement IS NULL OR (kind = 'SIGNATURE' AND length(statement) <= 500));

COMMENT ON COLUMN ember_schema.document_template_field.statement IS
    'For SIGNATURE: what the signer of this field confirms, shown on the signing screen and bound into the signature. Copied into each signature request when it is made, so a later change of the template never changes what a signer was asked. NULL for the default statement of the field''s role in the template''s language, and for every other kind.';

ALTER TABLE ember_schema.member_document_version
    DROP CONSTRAINT member_document_version_seal_level_check,
    ADD CONSTRAINT member_document_version_seal_level_check
        CHECK (seal_level IN ('BASELINE_B', 'BASELINE_T', 'BASELINE_LT', 'BASELINE_LTA')),
    ADD COLUMN timestamp_valid_until TIMESTAMPTZ NULL,
    ADD CONSTRAINT member_document_version_timestamp_end CHECK ((timestamped_by IS NULL) = (timestamp_valid_until IS NULL));

CREATE INDEX IF NOT EXISTS member_document_version_timestamp_end_idx
    ON ember_schema.member_document_version (timestamp_valid_until)
    WHERE superseded_at IS NULL;

COMMENT ON COLUMN ember_schema.member_document_version.seal_level
    IS 'The PAdES baseline level the seal reached: BASELINE_B without a timestamp, BASELINE_T with one, BASELINE_LT with the timestamp and the material to check both offline, BASELINE_LTA with that material covered by a later document timestamp that renews the earlier ones.';
COMMENT ON COLUMN ember_schema.member_document_version.timestamped_by
    IS 'The address of the timestamp service whose newest timestamp the file carries; null for a seal without a timestamp.';
COMMENT ON COLUMN ember_schema.member_document_version.timestamp_valid_until
    IS 'The earliest end of validity among the certificates the newest timestamp of the file rests on: the timestamp service''s certificates and the root pinned for it. Before then a later timestamp has to cover it, which the renewal of timestamps adds as a new version when the operator switched it on. Null for a seal without a timestamp.';
COMMENT ON CONSTRAINT member_document_version_seal_level_check ON ember_schema.member_document_version
    IS 'The PAdES baseline levels Ember seals at.';
COMMENT ON CONSTRAINT member_document_version_timestamp_end ON ember_schema.member_document_version
    IS 'The end of the newest timestamp is known exactly when the file carries a timestamp.';
COMMENT ON INDEX ember_schema.member_document_version_timestamp_end_idx
    IS 'Finds the current versions whose newest timestamp is about to end, for the renewal of timestamps.';

DROP TRIGGER member_document_version_stays ON ember_schema.member_document_version;

CREATE TRIGGER member_document_version_stays
    BEFORE UPDATE OF document_id, version, sha256, size_bytes, seal_level, timestamped_by, timestamp_valid_until,
        sealed_at
    ON ember_schema.member_document_version
    FOR EACH ROW
EXECUTE FUNCTION ember_schema.member_document_refuse_sealed_change();

CREATE TABLE IF NOT EXISTS ember_schema.federation_partner_signing_ca
(
    id               SERIAL PRIMARY KEY,
    partner_id       INTEGER     NOT NULL REFERENCES ember_schema.federation_partner (id) ON DELETE CASCADE,
    sha256           TEXT        NOT NULL CHECK (sha256 ~ '^[0-9a-f]{64}$'),
    certificate      BYTEA       NOT NULL,
    active           BOOLEAN     NOT NULL,
    pin_kind         TEXT        NOT NULL CHECK (pin_kind IN ('FIRST_FETCH', 'ANNOUNCED')),
    pinned_at        TIMESTAMPTZ NOT NULL,
    pinned_statement TEXT        NOT NULL,
    pinned_signature TEXT        NOT NULL,
    last_listed_at   TIMESTAMPTZ NOT NULL,
    crl              BYTEA       NULL,
    crl_this_update  TIMESTAMPTZ NULL,
    crl_next_update  TIMESTAMPTZ NULL,
    CONSTRAINT federation_partner_signing_ca_pin UNIQUE (partner_id, sha256),
    CONSTRAINT federation_partner_signing_ca_crl CHECK (num_nonnulls(crl, crl_this_update, crl_next_update) IN (0, 3))
);

COMMENT ON TABLE ember_schema.federation_partner_signing_ca IS
    'The signing authorities of a federation partner''s installation, pinned to the partnership, so documents the partner seals and sends can be checked against them. One row per partnership and authority. An authority is only pinned from a statement the partner station signed with its federation key, made against a fresh challenge, so nobody between the two installations can swap one in. A pin is never dropped by a later statement: documents sealed under an authority the partner gave up keep being checked against it. Goes with the partnership.';
COMMENT ON COLUMN ember_schema.federation_partner_signing_ca.id IS 'Primary key.';
COMMENT ON COLUMN ember_schema.federation_partner_signing_ca.partner_id IS
    'The partnership the authority is pinned to, the row of the station that checks the partner''s documents.';
COMMENT ON COLUMN ember_schema.federation_partner_signing_ca.sha256 IS
    'SHA-256 of the authority''s certificate, lower-case hexadecimal.';
COMMENT ON COLUMN ember_schema.federation_partner_signing_ca.certificate IS 'The authority''s certificate, DER encoded.';
COMMENT ON COLUMN ember_schema.federation_partner_signing_ca.active IS
    'Whether the partner last stated it as the authority that issues new station certificates; false once retired or given up there.';
COMMENT ON COLUMN ember_schema.federation_partner_signing_ca.pin_kind IS
    'How it came to be pinned: FIRST_FETCH with the first statement taken from the partner, ANNOUNCED with a later one, after the partner''s installation renewed or re-issued its authority.';
COMMENT ON COLUMN ember_schema.federation_partner_signing_ca.pinned_at IS 'When it was pinned.';
COMMENT ON COLUMN ember_schema.federation_partner_signing_ca.pinned_statement IS
    'The text of the partner''s statement that pinned it, exactly as its signature covers it, as proof of where the pin came from.';
COMMENT ON COLUMN ember_schema.federation_partner_signing_ca.pinned_signature IS
    'The partner station''s Base64 signature over that statement, made with its federation key.';
COMMENT ON COLUMN ember_schema.federation_partner_signing_ca.last_listed_at IS
    'When a statement of the partner last named it.';
COMMENT ON COLUMN ember_schema.federation_partner_signing_ca.crl IS
    'The newest revocation list of the authority taken in, DER encoded and checked against the authority''s certificate. NULL while the partner stated none.';
COMMENT ON COLUMN ember_schema.federation_partner_signing_ca.crl_this_update IS
    'When that list was issued; a list older than the stored one is never taken. NULL while none was taken.';
COMMENT ON COLUMN ember_schema.federation_partner_signing_ca.crl_next_update IS
    'When that list says the next one is due; past it the partner is asked again before a check. NULL while none was taken.';
COMMENT ON CONSTRAINT federation_partner_signing_ca_pin ON ember_schema.federation_partner_signing_ca IS
    'An authority is pinned once per partnership.';
COMMENT ON CONSTRAINT federation_partner_signing_ca_crl ON ember_schema.federation_partner_signing_ca IS
    'A revocation list is stored together with its two dates or not at all.';
