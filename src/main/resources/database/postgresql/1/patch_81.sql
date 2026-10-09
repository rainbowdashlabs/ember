CREATE TABLE IF NOT EXISTS ember_schema.name_change_request
(
    id           SERIAL PRIMARY KEY,
    account_id   INT         NOT NULL REFERENCES ember_schema.account (id) ON DELETE CASCADE,
    first_name   TEXT        NOT NULL,
    last_name    TEXT        NOT NULL,
    requested_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    decided_by   INT REFERENCES ember_schema.account (id) ON DELETE SET NULL,
    decided_at   TIMESTAMPTZ,
    outcome      TEXT CHECK (outcome IN ('APPROVED', 'DENIED', 'WITHDRAWN')),
    reason       TEXT,
    CHECK ((decided_at IS NULL) = (outcome IS NULL))
);

CREATE UNIQUE INDEX IF NOT EXISTS name_change_request_open_idx
    ON ember_schema.name_change_request (account_id)
    WHERE decided_at IS NULL;

COMMENT ON TABLE ember_schema.name_change_request
    IS 'A register name a member asked for and that waits for a member manager. The name on the account only changes once a request is approved; a newer request replaces the open one.';
COMMENT ON COLUMN ember_schema.name_change_request.id
    IS 'Primary key.';
COMMENT ON COLUMN ember_schema.name_change_request.account_id
    IS 'The account whose name is to change.';
COMMENT ON COLUMN ember_schema.name_change_request.first_name
    IS 'The first name asked for.';
COMMENT ON COLUMN ember_schema.name_change_request.last_name
    IS 'The last name asked for.';
COMMENT ON COLUMN ember_schema.name_change_request.requested_at
    IS 'When the name was asked for, or last asked for again.';
COMMENT ON COLUMN ember_schema.name_change_request.decided_by
    IS 'The account that approved or denied it; null while open, for a withdrawal, and once that account is gone.';
COMMENT ON COLUMN ember_schema.name_change_request.decided_at
    IS 'When the request was approved, denied or withdrawn; null while it is open.';
COMMENT ON COLUMN ember_schema.name_change_request.outcome
    IS 'APPROVED, DENIED or WITHDRAWN; null while the request is open.';
COMMENT ON COLUMN ember_schema.name_change_request.reason
    IS 'Why a request was denied, shown to the member in the notification; optional.';

INSERT INTO ember_schema.station_permission (name)
VALUES ('DOCUMENT_TEMPLATE_EDIT')
ON CONFLICT (name) DO NOTHING;

ALTER TABLE ember_schema.member_manager
    ADD COLUMN position   INTEGER     NOT NULL DEFAULT 0,
    ADD COLUMN created_at TIMESTAMPTZ NULL,
    ADD COLUMN created_by INTEGER     NULL REFERENCES ember_schema.station_member (id) ON DELETE SET NULL;

UPDATE ember_schema.member_manager mm
SET position = ranked.position
FROM (SELECT manager_id,
             managed_id,
             ROW_NUMBER() OVER (PARTITION BY managed_id ORDER BY manager_id) - 1 AS position
      FROM ember_schema.member_manager) ranked
WHERE ranked.manager_id = mm.manager_id
  AND ranked.managed_id = mm.managed_id;

ALTER TABLE ember_schema.member_manager
    ALTER COLUMN created_at SET DEFAULT now();

COMMENT ON COLUMN ember_schema.member_manager.position IS
    'The order of the guardians of one member, counted from 0. The guardian at 0 is the first guardian, which documents name as guardian 1.';
COMMENT ON COLUMN ember_schema.member_manager.created_at IS
    'When the guardian was linked to the member. NULL for links made before this was recorded.';
COMMENT ON COLUMN ember_schema.member_manager.created_by IS
    'The member who linked the guardian. NULL where nobody did (an import, a waiting list, a link made before this was recorded) or once they are gone.';

ALTER TABLE ember_schema.profile_field
    DROP CONSTRAINT profile_field_type_known;
ALTER TABLE ember_schema.profile_field
    ADD CONSTRAINT profile_field_type_known CHECK (field_type IN (
        'TEXT', 'NUMBER', 'DATE', 'BOOLEAN', 'CHOICE', 'GENDER', 'AGE', 'BIRTH_DATE', 'EXPIRY_DATE', 'SECTION',
        'SPACER'));

ALTER TABLE ember_schema.cluster_profile_field
    DROP CONSTRAINT cluster_profile_field_type_known;
ALTER TABLE ember_schema.cluster_profile_field
    ADD CONSTRAINT cluster_profile_field_type_known CHECK (field_type IN (
        'TEXT', 'NUMBER', 'DATE', 'BOOLEAN', 'CHOICE', 'GENDER', 'AGE', 'EXPIRY_DATE', 'SECTION', 'SPACER'));

COMMENT ON COLUMN ember_schema.profile_field.field_type
    IS 'What kind of answer it takes, by its shared type name: TEXT, NUMBER, DATE, BOOLEAN, CHOICE, GENDER (a choice whose answers carry the pronouns documents use, one per station), AGE, BIRTH_DATE, EXPIRY_DATE, SECTION or SPACER.';

WITH renamed AS (SELECT id, jsonb_set(data, '{link,route}', '"documents-store"') AS data
                 FROM ember_schema.notification
                 WHERE data -> 'link' ->> 'route' = 'member-documents')
UPDATE ember_schema.notification n
SET data      = renamed.data,
    dedup_key = CASE WHEN n.dedup_key IS NOT NULL THEN md5(n.type || renamed.data::TEXT) END
FROM renamed
WHERE renamed.id = n.id;

CREATE TABLE IF NOT EXISTS ember_schema.document_template
(
    id                         SERIAL PRIMARY KEY,
    station_id                 INTEGER     NULL REFERENCES ember_schema.station (id) ON DELETE CASCADE,
    cluster_id                 INTEGER     NULL REFERENCES ember_schema.cluster (id) ON DELETE CASCADE,
    kind                       TEXT        NOT NULL DEFAULT 'LETTER' CHECK (kind IN ('LETTER', 'PDF')),
    name                       TEXT        NOT NULL,
    title_pattern              TEXT        NOT NULL,
    file_name_pattern          TEXT        NOT NULL,
    tags                       TEXT[]      NOT NULL DEFAULT '{}',
    hidden                     BOOLEAN     NOT NULL DEFAULT FALSE,
    keep_on_archive            BOOLEAN     NOT NULL DEFAULT FALSE,
    legal                      BOOLEAN     NOT NULL DEFAULT FALSE,
    for_appointments           BOOLEAN     NOT NULL DEFAULT FALSE,
    self_service               BOOLEAN     NOT NULL DEFAULT FALSE,
    self_service_cooldown_days INTEGER     NOT NULL DEFAULT 30 CHECK (self_service_cooldown_days >= 0),
    restriction_mode           TEXT        NOT NULL DEFAULT 'AND' CHECK (restriction_mode IN ('AND', 'OR')),
    language                   TEXT        NOT NULL DEFAULT 'DE' CHECK (language IN ('DE', 'EN')),
    issuer_id                  INTEGER     NULL REFERENCES ember_schema.station_member (id) ON DELETE SET NULL,
    issuer_function            TEXT        NULL,
    version                   INTEGER     NOT NULL DEFAULT 1,
    created_at                 TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by                 INTEGER     NULL REFERENCES ember_schema.account (id) ON DELETE SET NULL,
    updated_at                 TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by                 INTEGER     NULL REFERENCES ember_schema.account (id) ON DELETE SET NULL,
    archived_at                TIMESTAMPTZ NULL,
    CHECK (num_nonnulls(station_id, cluster_id) = 1)
);

CREATE INDEX IF NOT EXISTS idx_document_template_station ON ember_schema.document_template (station_id);
CREATE INDEX IF NOT EXISTS idx_document_template_cluster ON ember_schema.document_template (cluster_id);

COMMENT ON TABLE ember_schema.document_template IS
    'A template turned into a PDF for one member at a time, kept by a station (station_id) or by an association for all its stations (cluster_id). Never deleted, only archived, so every generated document keeps pointing at the template it came from.';
COMMENT ON COLUMN ember_schema.document_template.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.document_template.station_id IS
    'The station that owns the template. NULL for a template of an association.';
COMMENT ON COLUMN ember_schema.document_template.cluster_id IS
    'The association that owns the template, which its stations use but do not change. NULL for a template of a station.';
COMMENT ON COLUMN ember_schema.document_template.kind IS
    'What the template is made of: LETTER, a letterhead and a body written in Ember (document_template_letter), or PDF, an uploaded PDF filled in place (document_template_pdf).';
COMMENT ON COLUMN ember_schema.document_template.name IS 'What the template is called in the list of templates.';
COMMENT ON COLUMN ember_schema.document_template.title_pattern IS
    'The title a generated document is filed under, with placeholders such as {{today}} filled in when it is generated.';
COMMENT ON COLUMN ember_schema.document_template.file_name_pattern IS
    'The file name a generated document is filed under, with placeholders filled in when it is generated. The file always ends in .pdf.';
COMMENT ON COLUMN ember_schema.document_template.tags IS
    'The names of the document tags a generated document is filed with. Tags missing at the station are created when a document is filed.';
COMMENT ON COLUMN ember_schema.document_template.hidden IS
    'Whether a document generated by a manager is hidden from the member it names. Documents a member generates through self service are never hidden.';
COMMENT ON COLUMN ember_schema.document_template.keep_on_archive IS
    'Whether a generated document outlasts the membership of the member it names.';
COMMENT ON COLUMN ember_schema.document_template.legal IS
    'Whether the template makes a legal document. A legal template uses official names only and may not use the name a member is called by.';
COMMENT ON COLUMN ember_schema.document_template.for_appointments IS
    'Whether appointments may require the template as a document to bring. Only such a template names the values of an appointment, and it is always legal.';
COMMENT ON COLUMN ember_schema.document_template.self_service IS
    'Whether members of the audience may generate the document for themselves, and guardians for the members in their care. For a template of an association, whether it is offered for self service; each station then decides in document_template_station_use.';
COMMENT ON COLUMN ember_schema.document_template.self_service_cooldown_days IS
    'How many days must pass before a member may generate the document again through self service. 0 means no wait.';
COMMENT ON COLUMN ember_schema.document_template.restriction_mode IS
    'AND or OR, how the parts of document_template_restriction combine.';
COMMENT ON COLUMN ember_schema.document_template.language IS
    'DE or EN, the language the documents are written in: it picks the pronouns of the gender profile field, how dates are written, and the words the letter prints itself.';
COMMENT ON COLUMN ember_schema.document_template.issuer_id IS
    'The member of the station who issues the documents of a station''s template: their official name fills issuer.fullName and the signature field for the issuer is theirs. NULL where nobody is named, for a template of an association (each station names its own in document_template_station_use), and once that member was deleted, which the template then shows as missing.';
COMMENT ON COLUMN ember_schema.document_template.issuer_function IS
    'What the issuer does at the station, such as the title of their office, which fills issuer.function. NULL where none is given.';
COMMENT ON COLUMN ember_schema.document_template.version IS
    'Counts up with every change, so the generation log says which state of the template a document came from.';
COMMENT ON COLUMN ember_schema.document_template.created_at IS 'When the template was created.';
COMMENT ON COLUMN ember_schema.document_template.created_by IS 'The account that created the template. NULL once it is gone.';
COMMENT ON COLUMN ember_schema.document_template.updated_at IS 'When the template was last changed.';
COMMENT ON COLUMN ember_schema.document_template.updated_by IS 'The account that last changed the template. NULL once it is gone.';
COMMENT ON COLUMN ember_schema.document_template.archived_at IS
    'When the template was archived. An archived template generates nothing more and stays for the documents generated from it. NULL while in use.';

CREATE TABLE IF NOT EXISTS ember_schema.document_template_restriction
(
    id          SERIAL PRIMARY KEY,
    template_id INT NOT NULL REFERENCES ember_schema.document_template (id) ON DELETE CASCADE,
    user_type   TEXT,
    group_id    INT REFERENCES ember_schema.member_group (id) ON DELETE CASCADE,
    tag_id      INT REFERENCES ember_schema.user_tag (id) ON DELETE CASCADE,
    member_id   INT REFERENCES ember_schema.station_member (id) ON DELETE CASCADE,
    CHECK (num_nonnulls(user_type, group_id, tag_id, member_id) = 1)
);

CREATE INDEX IF NOT EXISTS idx_document_template_restriction_template
    ON ember_schema.document_template_restriction (template_id);

COMMENT ON TABLE ember_schema.document_template_restriction IS
    'Who may generate a self service template of a station for themselves. Empty means every member. A template of an association has none; each station sets its own audience in document_template_station_use_restriction. Shaped like event_restriction.';
COMMENT ON COLUMN ember_schema.document_template_restriction.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.document_template_restriction.template_id IS 'References the document template.';
COMMENT ON COLUMN ember_schema.document_template_restriction.user_type IS
    'Required user type. Exactly one of user_type/group_id/tag_id/member_id must be set.';
COMMENT ON COLUMN ember_schema.document_template_restriction.group_id IS 'Required group membership.';
COMMENT ON COLUMN ember_schema.document_template_restriction.tag_id IS 'Required tag.';
COMMENT ON COLUMN ember_schema.document_template_restriction.member_id IS
    'Specific member (always OR-connected, bypasses AND/OR mode).';

CREATE TABLE IF NOT EXISTS ember_schema.document_template_station_use
(
    id               SERIAL PRIMARY KEY,
    template_id      INTEGER     NOT NULL REFERENCES ember_schema.document_template (id) ON DELETE CASCADE,
    station_id       INTEGER     NOT NULL REFERENCES ember_schema.station (id) ON DELETE CASCADE,
    self_service     BOOLEAN     NOT NULL DEFAULT FALSE,
    restriction_mode TEXT        NOT NULL DEFAULT 'AND' CHECK (restriction_mode IN ('AND', 'OR')),
    issuer_id        INTEGER     NULL REFERENCES ember_schema.station_member (id) ON DELETE SET NULL,
    issuer_function  TEXT        NULL,
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (template_id, station_id)
);

CREATE INDEX IF NOT EXISTS idx_document_template_station_use_station
    ON ember_schema.document_template_station_use (station_id);

COMMENT ON TABLE ember_schema.document_template_station_use IS
    'How a station uses a template of its association. Its managers generate every template of the association in use; whether its members generate one through self service, and who of them, the station decides here. A template without a row is not offered for self service at that station.';
COMMENT ON COLUMN ember_schema.document_template_station_use.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.document_template_station_use.template_id IS 'The template of the association.';
COMMENT ON COLUMN ember_schema.document_template_station_use.station_id IS 'The station that uses it.';
COMMENT ON COLUMN ember_schema.document_template_station_use.self_service IS
    'Whether members of the station generate the document for themselves, and guardians for the members in their care. Only takes effect while the association offers the template for self service.';
COMMENT ON COLUMN ember_schema.document_template_station_use.restriction_mode IS
    'AND or OR, how the parts of document_template_station_use_restriction combine.';
COMMENT ON COLUMN ember_schema.document_template_station_use.issuer_id IS
    'The member of the station who issues the documents of the association''s template at this station, as document_template.issuer_id does for a station''s own template. NULL where the station named nobody, and once that member was deleted, which the template then shows as missing.';
COMMENT ON COLUMN ember_schema.document_template_station_use.issuer_function IS
    'What the issuer does at the station, which fills issuer.function. NULL where none is given.';
COMMENT ON COLUMN ember_schema.document_template_station_use.updated_at IS 'When the station last changed how it uses the template.';

CREATE TABLE IF NOT EXISTS ember_schema.document_template_station_use_restriction
(
    id        SERIAL PRIMARY KEY,
    use_id    INT NOT NULL REFERENCES ember_schema.document_template_station_use (id) ON DELETE CASCADE,
    user_type TEXT,
    group_id  INT REFERENCES ember_schema.member_group (id) ON DELETE CASCADE,
    tag_id    INT REFERENCES ember_schema.user_tag (id) ON DELETE CASCADE,
    member_id INT REFERENCES ember_schema.station_member (id) ON DELETE CASCADE,
    CHECK (num_nonnulls(user_type, group_id, tag_id, member_id) = 1)
);

CREATE INDEX IF NOT EXISTS idx_document_template_station_use_restriction_use
    ON ember_schema.document_template_station_use_restriction (use_id);

COMMENT ON TABLE ember_schema.document_template_station_use_restriction IS
    'Who of a station may generate a template of its association through self service. Empty means every member. Shaped like event_restriction.';
COMMENT ON COLUMN ember_schema.document_template_station_use_restriction.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.document_template_station_use_restriction.use_id IS
    'References how the station uses the template.';
COMMENT ON COLUMN ember_schema.document_template_station_use_restriction.user_type IS
    'Required user type. Exactly one of user_type/group_id/tag_id/member_id must be set.';
COMMENT ON COLUMN ember_schema.document_template_station_use_restriction.group_id IS 'Required group membership.';
COMMENT ON COLUMN ember_schema.document_template_station_use_restriction.tag_id IS 'Required tag.';
COMMENT ON COLUMN ember_schema.document_template_station_use_restriction.member_id IS
    'Specific member (always OR-connected, bypasses AND/OR mode).';

CREATE TABLE IF NOT EXISTS ember_schema.document_template_letter
(
    template_id INTEGER PRIMARY KEY REFERENCES ember_schema.document_template (id) ON DELETE CASCADE,
    header      JSONB   NOT NULL,
    footer      JSONB   NOT NULL,
    body        JSONB   NOT NULL,
    page        JSONB   NOT NULL
);

COMMENT ON TABLE ember_schema.document_template_letter IS
    'The content of a letter template: its header, its footer, its body and its page.';
COMMENT ON COLUMN ember_schema.document_template_letter.template_id IS 'References the document template.';
COMMENT ON COLUMN ember_schema.document_template_letter.header IS
    'The rows drawn at the top of every page, shaped like the rows of a page: each row up to three columns with their width, optionally with a line between the columns (columnLines), each holding a text with placeholders written as {{key}}, a picture from the media library by its content hash (an association''s template from the library of its home station) or the logo of the station the document is generated at, a divider line with its label, a gap, or blocks stacked in it. A block may carry a restriction saying which members it is printed for, and a guardianCondition (SECOND_GUARDIAN or NO_SECOND_GUARDIAN) on whether the member has a second guardian.';
COMMENT ON COLUMN ember_schema.document_template_letter.footer IS
    'The rows drawn at the bottom of every page, shaped like the header.';
COMMENT ON COLUMN ember_schema.document_template_letter.body IS
    'The rows of the letter itself, shaped like the header, and also holding signature lines (SIGNATURE: the signer in the settings, the text below the line as its content), each an empty signature field per person who signs. A block whose restriction or guardian condition does not match the member is left out, and a row left with nothing is dropped.';
COMMENT ON COLUMN ember_schema.document_template_letter.page IS
    'The page margins in millimetres, the body font size in points, and the family names of the uploaded fonts the body, the header and the footer are set in (absent for the default font, Liberation Sans). The paper is always A4.';

CREATE TABLE IF NOT EXISTS ember_schema.document_template_pdf_original
(
    id          SERIAL PRIMARY KEY,
    template_id INTEGER     NOT NULL REFERENCES ember_schema.document_template (id) ON DELETE CASCADE,
    file_name   TEXT        NOT NULL,
    size_bytes  BIGINT      NOT NULL,
    sha256      TEXT        NOT NULL,
    inspection  JSONB       NOT NULL,
    uploaded_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    uploaded_by INTEGER     NULL REFERENCES ember_schema.account (id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_document_template_pdf_original_template
    ON ember_schema.document_template_pdf_original (template_id);

COMMENT ON TABLE ember_schema.document_template_pdf_original IS
    'Every uploaded version of the PDF a PDF template fills in. The file is kept in the storage of the template''s owner under document-templates/<id>/original (an association''s in its home station), as it arrived, and never changed; a new upload is a new row.';
COMMENT ON COLUMN ember_schema.document_template_pdf_original.id IS 'Auto-generated primary key, also the storage key of the file.';
COMMENT ON COLUMN ember_schema.document_template_pdf_original.template_id IS 'The PDF template it was uploaded for.';
COMMENT ON COLUMN ember_schema.document_template_pdf_original.file_name IS 'The name the file was uploaded under.';
COMMENT ON COLUMN ember_schema.document_template_pdf_original.size_bytes IS 'The size of the file in bytes.';
COMMENT ON COLUMN ember_schema.document_template_pdf_original.sha256 IS 'The SHA-256 of the file as lowercase hex.';
COMMENT ON COLUMN ember_schema.document_template_pdf_original.inspection IS
    'What the PDF holds that fields care about, read at upload: each page with its crop box in points and its rotation, and the form fields it brings with their kind and where their first widget sits.';
COMMENT ON COLUMN ember_schema.document_template_pdf_original.uploaded_at IS 'When the file was uploaded.';
COMMENT ON COLUMN ember_schema.document_template_pdf_original.uploaded_by IS 'The account that uploaded the file. NULL once it is gone.';

CREATE TABLE IF NOT EXISTS ember_schema.document_template_pdf
(
    template_id INTEGER PRIMARY KEY REFERENCES ember_schema.document_template (id) ON DELETE CASCADE,
    original_id INTEGER NOT NULL REFERENCES ember_schema.document_template_pdf_original (id) ON DELETE CASCADE
);

COMMENT ON TABLE ember_schema.document_template_pdf IS
    'Which uploaded version of its PDF a PDF template fills now. A PDF template without a row has no PDF yet.';
COMMENT ON COLUMN ember_schema.document_template_pdf.template_id IS 'References the PDF template.';
COMMENT ON COLUMN ember_schema.document_template_pdf.original_id IS 'The uploaded PDF the template fills now.';

CREATE TABLE IF NOT EXISTS ember_schema.document_template_field
(
    id          SERIAL PRIMARY KEY,
    template_id INTEGER          NOT NULL REFERENCES ember_schema.document_template (id) ON DELETE CASCADE,
    position    INTEGER          NOT NULL,
    kind        TEXT             NOT NULL CHECK (kind IN ('TEXT', 'CHECK', 'SIGNATURE')),
    page        INTEGER          NOT NULL CHECK (page >= 1),
    x           DOUBLE PRECISION NOT NULL,
    y           DOUBLE PRECISION NOT NULL,
    width       DOUBLE PRECISION NOT NULL CHECK (width > 0),
    height      DOUBLE PRECISION NOT NULL CHECK (height > 0),
    text        TEXT             NULL,
    font_size   DOUBLE PRECISION NOT NULL,
    font_family TEXT             NULL,
    font_style  TEXT             NOT NULL DEFAULT 'REGULAR'
        CHECK (font_style IN ('REGULAR', 'BOLD', 'ITALIC', 'BOLD_ITALIC')),
    align       TEXT             NOT NULL CHECK (align IN ('LEFT', 'CENTER', 'RIGHT')),
    wrap        BOOLEAN          NOT NULL DEFAULT FALSE,
    role        TEXT             NULL CHECK (role IN ('PARTICIPANT', 'GUARDIAN_1', 'GUARDIAN_2', 'EACH_GUARDIAN', 'ANY_GUARDIAN', 'ISSUER')),
    without_line BOOLEAN         NOT NULL DEFAULT FALSE,
    print_text  BOOLEAN          NOT NULL DEFAULT FALSE,
    CHECK ((kind = 'SIGNATURE') = (role IS NOT NULL))
);

CREATE INDEX IF NOT EXISTS idx_document_template_field_template
    ON ember_schema.document_template_field (template_id, position);

COMMENT ON TABLE ember_schema.document_template_field IS
    'The fields drawn on the pages of a PDF template. They are kept across new uploads of the PDF, so a new version can be checked against them.';
COMMENT ON COLUMN ember_schema.document_template_field.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.document_template_field.template_id IS 'References the PDF template.';
COMMENT ON COLUMN ember_schema.document_template_field.position IS 'The order of the fields as the editor lists them, counted from 0.';
COMMENT ON COLUMN ember_schema.document_template_field.kind IS
    'TEXT prints a text with placeholders, CHECK prints a cross where its text says yes, SIGNATURE becomes an empty PDF signature field named after its role.';
COMMENT ON COLUMN ember_schema.document_template_field.page IS 'The page the field is on, counted from 1.';
COMMENT ON COLUMN ember_schema.document_template_field.x IS
    'The left edge in PDF points, in the user space of the page before rotation, crop box offset included.';
COMMENT ON COLUMN ember_schema.document_template_field.y IS
    'The bottom edge in PDF points, in the user space of the page before rotation, crop box offset included.';
COMMENT ON COLUMN ember_schema.document_template_field.width IS 'The width in PDF points, before rotation.';
COMMENT ON COLUMN ember_schema.document_template_field.height IS 'The height in PDF points, before rotation.';
COMMENT ON COLUMN ember_schema.document_template_field.text IS
    'The text with placeholders written as {{key}}: printed by TEXT, deciding the cross of CHECK, and for SIGNATURE the text under the line, printed only where print_text is set. NULL for a SIGNATURE without one.';
COMMENT ON COLUMN ember_schema.document_template_field.font_size IS 'The size of the text in points, the largest it is drawn at.';
COMMENT ON COLUMN ember_schema.document_template_field.font_family IS
    'The family name of the uploaded font the text is drawn in, as document_font names it, looked up among the fonts the station reaches. NULL for the default font, Liberation Sans.';
COMMENT ON COLUMN ember_schema.document_template_field.font_style IS
    'REGULAR, BOLD, ITALIC or BOLD_ITALIC: the style of the family the text is drawn in. A style the family lacks falls back to its regular one.';
COMMENT ON COLUMN ember_schema.document_template_field.align IS 'LEFT, CENTER or RIGHT: where the text sits across the field.';
COMMENT ON COLUMN ember_schema.document_template_field.wrap IS
    'Whether a long text runs onto further lines. Otherwise it stays on one line and shrinks to fit.';
COMMENT ON COLUMN ember_schema.document_template_field.role IS
    'Who signs in a SIGNATURE field: PARTICIPANT, GUARDIAN_1, GUARDIAN_2 (left out for a member with fewer than two guardians), EACH_GUARDIAN (the box shared out into one field per guardian of the member), ANY_GUARDIAN (one field any guardian may sign) or ISSUER. NULL for every other kind.';
COMMENT ON COLUMN ember_schema.document_template_field.without_line IS
    'For SIGNATURE: true where no line is drawn to sign on, because the PDF already has one. FALSE for every other kind.';
COMMENT ON COLUMN ember_schema.document_template_field.print_text IS
    'For SIGNATURE: true where its text is printed under the line; otherwise the text shows in the template editor only. FALSE for every other kind.';

CREATE TABLE IF NOT EXISTS ember_schema.document_template_form_binding
(
    template_id INTEGER NOT NULL REFERENCES ember_schema.document_template (id) ON DELETE CASCADE,
    field_name  TEXT    NOT NULL,
    text        TEXT    NOT NULL,
    PRIMARY KEY (template_id, field_name)
);

COMMENT ON TABLE ember_schema.document_template_form_binding IS
    'What the form fields an uploaded PDF brings are filled with. Form fields without a row keep what they show; all of them are flattened.';
COMMENT ON COLUMN ember_schema.document_template_form_binding.template_id IS 'References the PDF template.';
COMMENT ON COLUMN ember_schema.document_template_form_binding.field_name IS 'The fully qualified name of the form field in the PDF.';
COMMENT ON COLUMN ember_schema.document_template_form_binding.text IS
    'The text with placeholders written as {{key}}. A check box is ticked where the filled text says yes.';

CREATE TABLE IF NOT EXISTS ember_schema.document_generation
(
    id               SERIAL PRIMARY KEY,
    station_id       INTEGER     NOT NULL REFERENCES ember_schema.station (id) ON DELETE CASCADE,
    template_id      INTEGER     NOT NULL REFERENCES ember_schema.document_template (id) ON DELETE CASCADE,
    template_version INTEGER     NOT NULL,
    member_id        INTEGER     NULL REFERENCES ember_schema.station_member (id) ON DELETE SET NULL,
    generated_by     INTEGER     NULL REFERENCES ember_schema.station_member (id) ON DELETE SET NULL,
    generated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    self_service     BOOLEAN     NOT NULL,
    document_id      INTEGER     NULL REFERENCES ember_schema.member_document (id) ON DELETE SET NULL,
    file_sha256      TEXT        NOT NULL,
    pdf_original_id  INTEGER     NULL REFERENCES ember_schema.document_template_pdf_original (id) ON DELETE SET NULL,
    event_id         INTEGER     NULL REFERENCES ember_schema.station_event (id) ON DELETE SET NULL,
    event_date       DATE        NULL,
    issuer_id        INTEGER     NULL REFERENCES ember_schema.station_member (id) ON DELETE SET NULL,
    issuer_function  TEXT        NULL,
    issuer_fixed     BOOLEAN     NOT NULL DEFAULT FALSE,
    issuer_signs     BOOLEAN     NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_document_generation_template_member
    ON ember_schema.document_generation (template_id, member_id, generated_at);
CREATE INDEX IF NOT EXISTS idx_document_generation_station ON ember_schema.document_generation (station_id);

COMMENT ON TABLE ember_schema.document_generation IS
    'Every document generated from a template: which template and which state of it, about whom, by whom, when, and the file that came out.';
COMMENT ON COLUMN ember_schema.document_generation.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.document_generation.station_id IS
    'The station the document was filed at, the station of the member it is about, whoever owns the template.';
COMMENT ON COLUMN ember_schema.document_generation.template_id IS
    'The template the document was generated from, the station''s own or one of its association.';
COMMENT ON COLUMN ember_schema.document_generation.template_version IS
    'The version of the template at the time, which a later change of the template does not touch.';
COMMENT ON COLUMN ember_schema.document_generation.member_id IS
    'The member the document is about and was filed for. NULL once that member was deleted.';
COMMENT ON COLUMN ember_schema.document_generation.generated_by IS
    'The member who generated it: a manager, the member themselves or their guardian. NULL once they are gone.';
COMMENT ON COLUMN ember_schema.document_generation.generated_at IS 'When the document was generated.';
COMMENT ON COLUMN ember_schema.document_generation.self_service IS
    'Whether it was generated through self service, which is what the cooldown of the template counts.';
COMMENT ON COLUMN ember_schema.document_generation.document_id IS
    'The member document the file was filed as. NULL once that document was deleted.';
COMMENT ON COLUMN ember_schema.document_generation.file_sha256 IS
    'The SHA-256 of the generated file as lowercase hex, which ties the log to the exact bytes filed.';
COMMENT ON COLUMN ember_schema.document_generation.pdf_original_id IS
    'The uploaded PDF the document was filled from, which a later upload of a new version does not touch. NULL for a letter.';
COMMENT ON COLUMN ember_schema.document_generation.event_id IS
    'The appointment that requires the document and whose values it holds. NULL for a document generated on its own, and once the appointment was deleted.';
COMMENT ON COLUMN ember_schema.document_generation.event_date IS
    'The day of the appointment the document was generated for, which tells the dates of a repeating appointment apart. NULL where event_id never was set.';
COMMENT ON COLUMN ember_schema.document_generation.issuer_id IS
    'The member who issues the document, whose name it prints and to whom the signature field named issuer belongs. NULL where the document names no issuer or none could be named, and once that member was deleted.';
COMMENT ON COLUMN ember_schema.document_generation.issuer_function IS
    'What the issuer does at the station, as the document printed it. NULL where it names no issuer function.';
COMMENT ON COLUMN ember_schema.document_generation.issuer_fixed IS
    'Whether the issuer is the one the template names for the station, as for every document of self service and every document to bring, rather than another member a manager picked for this one document. A signature of the template''s issuer may be applied without the issuer signing each document by hand.';
COMMENT ON COLUMN ember_schema.document_generation.issuer_signs IS
    'Whether the document carries the empty signature field named issuer, which issuer_id is to sign.';

CREATE TABLE IF NOT EXISTS ember_schema.document_generation_subject
(
    generation_id INTEGER NOT NULL REFERENCES ember_schema.document_generation (id) ON DELETE CASCADE,
    member_id     INTEGER NOT NULL REFERENCES ember_schema.station_member (id) ON DELETE CASCADE,
    role          TEXT    NOT NULL CHECK (role IN ('MEMBER', 'GUARDIAN')),
    PRIMARY KEY (generation_id, member_id)
);

CREATE INDEX IF NOT EXISTS idx_document_generation_subject_member
    ON ember_schema.document_generation_subject (member_id);

COMMENT ON TABLE ember_schema.document_generation_subject IS
    'Every person whose data went into a generated document: the member it is about and the guardians it names.';
COMMENT ON COLUMN ember_schema.document_generation_subject.generation_id IS 'References the generated document.';
COMMENT ON COLUMN ember_schema.document_generation_subject.member_id IS 'A person whose data went into the document.';
COMMENT ON COLUMN ember_schema.document_generation_subject.role IS
    'MEMBER for the member the document is about, GUARDIAN for a guardian whose data it holds.';

INSERT INTO ember_schema.cluster_permission (name)
VALUES ('CLUSTER_DOCUMENT_TEMPLATE_EDIT')
ON CONFLICT (name) DO NOTHING;

CREATE TABLE IF NOT EXISTS ember_schema.document_font
(
    id              SERIAL PRIMARY KEY,
    station_id      INTEGER     NULL REFERENCES ember_schema.station (id) ON DELETE CASCADE,
    cluster_id      INTEGER     NULL REFERENCES ember_schema.cluster (id) ON DELETE CASCADE,
    family          TEXT        NOT NULL CHECK (length(btrim(family)) BETWEEN 1 AND 60),
    style           TEXT        NOT NULL CHECK (style IN ('REGULAR', 'BOLD', 'ITALIC', 'BOLD_ITALIC')),
    file_name       TEXT        NOT NULL,
    outline         TEXT        NOT NULL CHECK (outline IN ('TRUETYPE', 'CFF')),
    internal_family TEXT        NOT NULL,
    size_bytes      BIGINT      NOT NULL,
    sha256          TEXT        NOT NULL,
    uploaded_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    uploaded_by     INTEGER     NULL REFERENCES ember_schema.account (id) ON DELETE SET NULL,
    web_file_name   TEXT        NULL,
    web_format      TEXT        NULL CHECK (web_format IN ('WOFF2', 'WOFF', 'TRUETYPE', 'CFF')),
    web_size_bytes  BIGINT      NULL,
    web_sha256      TEXT        NULL,
    web_uploaded_at TIMESTAMPTZ NULL,
    CHECK (num_nonnulls(station_id, cluster_id) <= 1),
    CHECK (num_nulls(web_file_name, web_format, web_size_bytes, web_sha256, web_uploaded_at) IN (0, 5))
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_document_font_station
    ON ember_schema.document_font (station_id, lower(family), style) WHERE station_id IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_document_font_cluster
    ON ember_schema.document_font (cluster_id, lower(family), style) WHERE cluster_id IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_document_font_instance
    ON ember_schema.document_font (lower(family), style) WHERE station_id IS NULL AND cluster_id IS NULL;

COMMENT ON TABLE ember_schema.document_font IS
    'A font uploaded for documents, one file per family and style. Owned by the instance (station_id and cluster_id both NULL), an association (cluster_id) or a station (station_id). A station reaches its own fonts, its association''s and the instance''s; on a family name found at several, the nearest owner wins. The file is kept in the owner''s storage under fonts/<id>, an optional web version beside it under fonts/<id>-web. A browser receives either only in the template editor, the web version where there is one.';
COMMENT ON COLUMN ember_schema.document_font.id IS 'Auto-generated primary key, also the storage key of the file.';
COMMENT ON COLUMN ember_schema.document_font.station_id IS 'The station that owns the font. NULL for a font of an association or of the instance.';
COMMENT ON COLUMN ember_schema.document_font.cluster_id IS 'The association that owns the font. NULL for a font of a station or of the instance.';
COMMENT ON COLUMN ember_schema.document_font.family IS
    'The family name templates pick the font by, as the uploader gave it. Unique per owner and style, ignoring case.';
COMMENT ON COLUMN ember_schema.document_font.style IS 'REGULAR, BOLD, ITALIC or BOLD_ITALIC: which style of the family the file is.';
COMMENT ON COLUMN ember_schema.document_font.file_name IS 'The name the file was uploaded under.';
COMMENT ON COLUMN ember_schema.document_font.outline IS
    'TRUETYPE for a font with TrueType outlines (every .ttf and some .otf), CFF for an OpenType font with PostScript outlines. Letters print both; fields on uploaded PDFs print TrueType outlines only.';
COMMENT ON COLUMN ember_schema.document_font.internal_family IS
    'The family name the file itself carries, read at upload, which is the name the letter renderer asks for.';
COMMENT ON COLUMN ember_schema.document_font.size_bytes IS 'The size of the file in bytes.';
COMMENT ON COLUMN ember_schema.document_font.sha256 IS 'The SHA-256 of the file as lowercase hex.';
COMMENT ON COLUMN ember_schema.document_font.uploaded_at IS 'When the file was uploaded.';
COMMENT ON COLUMN ember_schema.document_font.uploaded_by IS
    'The account that uploaded the file, who confirmed that the owner may use the font. NULL once the account is gone.';
COMMENT ON COLUMN ember_schema.document_font.web_file_name IS
    'The name the web version of the style was uploaded under, which the template editor shows the style in instead of the file documents print with. NULL where the style has no web version; the web columns are all set or all NULL.';
COMMENT ON COLUMN ember_schema.document_font.web_format IS
    'WOFF2, WOFF, TRUETYPE or CFF: what the web version is, which decides the media type it is served as. NULL without a web version.';
COMMENT ON COLUMN ember_schema.document_font.web_size_bytes IS
    'The size of the web version in bytes, counted against the owner''s room like the file itself. NULL without a web version.';
COMMENT ON COLUMN ember_schema.document_font.web_sha256 IS 'The SHA-256 of the web version as lowercase hex. NULL without a web version.';
COMMENT ON COLUMN ember_schema.document_font.web_uploaded_at IS 'When the web version was uploaded. NULL without a web version.';

CREATE INDEX IF NOT EXISTS idx_document_generation_event
    ON ember_schema.document_generation (event_id, event_date) WHERE event_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS ember_schema.event_document_requirement
(
    id                SERIAL PRIMARY KEY,
    event_id          INTEGER NULL REFERENCES ember_schema.station_event (id) ON DELETE CASCADE,
    event_template_id INTEGER NULL REFERENCES ember_schema.event_template (id) ON DELETE CASCADE,
    template_id       INTEGER NOT NULL REFERENCES ember_schema.document_template (id) ON DELETE CASCADE,
    position          INTEGER NOT NULL,
    CHECK (num_nonnulls(event_id, event_template_id) = 1)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_event_document_requirement_event
    ON ember_schema.event_document_requirement (event_id, template_id) WHERE event_id IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_event_document_requirement_event_template
    ON ember_schema.event_document_requirement (event_template_id, template_id) WHERE event_template_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_event_document_requirement_template
    ON ember_schema.event_document_requirement (template_id);

COMMENT ON TABLE ember_schema.event_document_requirement IS
    'A document template an appointment or an appointment template names as a document to bring. Every registered participant gets a copy filled with their data. An appointment made from a template starts with the template''s list.';
COMMENT ON COLUMN ember_schema.event_document_requirement.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.event_document_requirement.event_id IS
    'The appointment that requires the document. Exactly one of event_id and event_template_id is set.';
COMMENT ON COLUMN ember_schema.event_document_requirement.event_template_id IS
    'The appointment template that hands the requirement to every appointment made from it.';
COMMENT ON COLUMN ember_schema.event_document_requirement.template_id IS
    'The document template required, one marked for appointments.';
COMMENT ON COLUMN ember_schema.event_document_requirement.position IS 'Where the document stands in the list, counted from 0.';

CREATE TABLE IF NOT EXISTS ember_schema.document_generation_job
(
    id             SERIAL PRIMARY KEY,
    station_id     INTEGER     NOT NULL REFERENCES ember_schema.station (id) ON DELETE CASCADE,
    template_id    INTEGER     NOT NULL REFERENCES ember_schema.document_template (id) ON DELETE CASCADE,
    started_by     INTEGER     NOT NULL REFERENCES ember_schema.station_member (id) ON DELETE CASCADE,
    accept_missing BOOLEAN     NOT NULL,
    issuer_id       INTEGER     NULL REFERENCES ember_schema.station_member (id) ON DELETE SET NULL,
    issuer_function TEXT        NULL,
    issuer_fixed    BOOLEAN     NOT NULL DEFAULT TRUE,
    started_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at    TIMESTAMPTZ NULL
);

CREATE INDEX IF NOT EXISTS idx_document_generation_job_station
    ON ember_schema.document_generation_job (station_id, started_at);
CREATE INDEX IF NOT EXISTS idx_document_generation_job_open
    ON ember_schema.document_generation_job (id) WHERE finished_at IS NULL;

COMMENT ON TABLE ember_schema.document_generation_job IS
    'A run that generates one template for many members in the background and files each document with its member. Kept in the database so the run carries on after a restart and its progress survives a reload of the page.';
COMMENT ON COLUMN ember_schema.document_generation_job.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.document_generation_job.station_id IS 'The station the documents are filed at.';
COMMENT ON COLUMN ember_schema.document_generation_job.template_id IS 'The template generated for every member of the run.';
COMMENT ON COLUMN ember_schema.document_generation_job.started_by IS
    'The manager who started the run, who is the uploader of every document it files.';
COMMENT ON COLUMN ember_schema.document_generation_job.accept_missing IS
    'Whether a member whose data the template needs is incomplete still gets a document, the gaps left to fill in by hand. Without it, such a member is listed as failed.';
COMMENT ON COLUMN ember_schema.document_generation_job.issuer_id IS
    'The member who issues every document of the run, taken when the run was started: the template''s issuer or another member the manager picked. NULL where there was none, and once that member was deleted, which every document still waiting then lacks.';
COMMENT ON COLUMN ember_schema.document_generation_job.issuer_function IS
    'What the issuer does at the station, printed in every document of the run. NULL where none is given.';
COMMENT ON COLUMN ember_schema.document_generation_job.issuer_fixed IS
    'Whether the issuer of the run is the one the template names for the station rather than one the manager picked.';
COMMENT ON COLUMN ember_schema.document_generation_job.started_at IS 'When the run was started.';
COMMENT ON COLUMN ember_schema.document_generation_job.finished_at IS 'When the last member of the run was done. NULL while it runs.';

CREATE TABLE IF NOT EXISTS ember_schema.document_generation_job_member
(
    job_id         INTEGER     NOT NULL REFERENCES ember_schema.document_generation_job (id) ON DELETE CASCADE,
    member_id      INTEGER     NOT NULL REFERENCES ember_schema.station_member (id) ON DELETE CASCADE,
    position       INTEGER     NOT NULL,
    status         TEXT        NOT NULL DEFAULT 'WAITING' CHECK (status IN ('WAITING', 'FILED', 'FAILED')),
    generation_id  INTEGER     NULL REFERENCES ember_schema.document_generation (id) ON DELETE SET NULL,
    refusal_code   TEXT        NULL,
    refusal_detail TEXT        NULL,
    done_at        TIMESTAMPTZ NULL,
    PRIMARY KEY (job_id, member_id)
);

COMMENT ON TABLE ember_schema.document_generation_job_member IS
    'One member of a generation run and how it went for them.';
COMMENT ON COLUMN ember_schema.document_generation_job_member.job_id IS 'References the run.';
COMMENT ON COLUMN ember_schema.document_generation_job_member.member_id IS 'The member a document is generated for.';
COMMENT ON COLUMN ember_schema.document_generation_job_member.position IS
    'Where the member stands in the run, counted from 0. Members are generated in this order.';
COMMENT ON COLUMN ember_schema.document_generation_job_member.status IS
    'WAITING until the member is done, then FILED where the document was filed or FAILED where it could not be.';
COMMENT ON COLUMN ember_schema.document_generation_job_member.generation_id IS
    'The entry in the generation log of the filed document. NULL while waiting, for a failure, and once the entry is gone.';
COMMENT ON COLUMN ember_schema.document_generation_job_member.refusal_code IS
    'The code of the refusal that kept the document from being filed, such as D-091 for missing data. NULL unless FAILED.';
COMMENT ON COLUMN ember_schema.document_generation_job_member.refusal_detail IS
    'What the refusal was about, such as the names of the missing values. NULL where it named nothing.';
COMMENT ON COLUMN ember_schema.document_generation_job_member.done_at IS 'When the member was done. NULL while waiting.';

ALTER TABLE ember_schema.user_tag
    ADD COLUMN IF NOT EXISTS visibility TEXT NOT NULL DEFAULT 'PLAIN'
        CHECK (visibility IN ('BADGE', 'PLAIN', 'PRIVATE'));
UPDATE ember_schema.user_tag SET visibility = 'BADGE' WHERE visible;
ALTER TABLE ember_schema.user_tag DROP COLUMN IF EXISTS visible;

COMMENT ON COLUMN ember_schema.user_tag.visibility IS
    'Who sees the tag. BADGE shows it as a badge behind member names, PLAIN shows it on the member only, PRIVATE shows it only to readers allowed to view members, never to the tagged member, and keeps it out of every audience and restriction.';

ALTER TABLE ember_schema.station
    ADD COLUMN IF NOT EXISTS instance_mail_granted_at  TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS instance_mail_daily_limit INT
        CHECK (instance_mail_daily_limit IS NULL OR instance_mail_daily_limit > 0),
    ADD COLUMN IF NOT EXISTS mail_reply_to             TEXT;

COMMENT ON COLUMN ember_schema.station.instance_mail_granted_at IS
    'When an instance administrator let the station send its own mail through the instance''s mail providers, after its own. NULL while it may not.';
COMMENT ON COLUMN ember_schema.station.instance_mail_daily_limit IS
    'How many mails a day the station may send through the instance''s mail providers. NULL for no limit of its own; the share all stations together may use of each provider still applies.';
COMMENT ON COLUMN ember_schema.station.mail_reply_to IS
    'The address replies to the station''s mail go to. NULL when the station named none.';

ALTER TABLE ember_schema.email_queue
    ADD COLUMN IF NOT EXISTS instance_position INT;

UPDATE ember_schema.email_queue
SET instance_position = provider_position
WHERE station_id IS NULL
  AND sent_at IS NOT NULL;

COMMENT ON COLUMN ember_schema.email_queue.instance_position IS
    'Which provider of the instance''s list carried the mail, counted from zero, whether the mail was the instance''s own or a station''s. NULL while unsent and for mail a station''s own provider carried. The daily allowance of an instance provider and the share stations may use of it are counted from this.';

CREATE INDEX IF NOT EXISTS idx_email_queue_instance_sent
    ON ember_schema.email_queue (sent_at, instance_position)
    WHERE instance_position IS NOT NULL;

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

ALTER TABLE ember_schema.account
    ADD COLUMN unconfirmed_since TIMESTAMPTZ NULL;

COMMENT ON COLUMN ember_schema.account.unconfirmed_since IS
    'When a station import created this account under its address. Until the owner sets a password or a passkey through a link sent to that address, no station may change the account''s address or how it signs in. NULL for every account whose owner confirmed it, and for every account made any other way.';

CREATE TABLE IF NOT EXISTS ember_schema.account_link_request
(
    id                SERIAL PRIMARY KEY,
    uid               UUID        NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    station_id        INTEGER     NOT NULL REFERENCES ember_schema.station (id) ON DELETE CASCADE,
    station_member_id INTEGER     NOT NULL REFERENCES ember_schema.station_member (id) ON DELETE CASCADE,
    account_id        INTEGER     NOT NULL REFERENCES ember_schema.account (id) ON DELETE CASCADE,
    origin            TEXT        NOT NULL CHECK (origin IN ('IMPORT', 'INVITE')),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by        INTEGER     NULL REFERENCES ember_schema.station_member (id) ON DELETE SET NULL,
    sent_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    token_hash        TEXT        NULL UNIQUE,
    expires_at        TIMESTAMPTZ NOT NULL,
    answered_at       TIMESTAMPTZ NULL,
    answer            TEXT        NULL CHECK (answer IN ('ACCEPTED', 'DECLINED', 'EXPIRED')),
    CONSTRAINT account_link_request_answered CHECK ((answer IS NULL) = (answered_at IS NULL))
);

CREATE UNIQUE INDEX IF NOT EXISTS account_link_request_open_idx
    ON ember_schema.account_link_request (station_member_id)
    WHERE answer IS NULL;
CREATE INDEX IF NOT EXISTS account_link_request_account_idx
    ON ember_schema.account_link_request (account_id)
    WHERE answer IS NULL;
CREATE INDEX IF NOT EXISTS account_link_request_station_idx ON ember_schema.account_link_request (station_id);
CREATE INDEX IF NOT EXISTS account_link_request_member_idx ON ember_schema.account_link_request (station_member_id);

COMMENT ON TABLE ember_schema.account_link_request IS
    'A station asking a person to link their existing account to one of its members. An import that found the account by its address and an invite naming that address both leave the member without an account and ask here instead; only the person signed in to that account can accept. The answered rows stay as the record of what was asked and answered.';
COMMENT ON COLUMN ember_schema.account_link_request.id IS 'Primary key.';
COMMENT ON COLUMN ember_schema.account_link_request.uid IS
    'The request as the person''s screens name it when they accept or decline.';
COMMENT ON COLUMN ember_schema.account_link_request.station_id IS 'The station that asks.';
COMMENT ON COLUMN ember_schema.account_link_request.station_member_id IS
    'The member the account would be linked to. The member exists without an account while the request waits.';
COMMENT ON COLUMN ember_schema.account_link_request.account_id IS
    'The existing account the station asks to link, found by the address it named.';
COMMENT ON COLUMN ember_schema.account_link_request.origin IS
    'How the station came to ask: IMPORT when a station import found the account by its address, INVITE when a member manager invited that address.';
COMMENT ON COLUMN ember_schema.account_link_request.created_at IS 'When the station first asked.';
COMMENT ON COLUMN ember_schema.account_link_request.created_by IS
    'The member who invited the address. NULL for an import, and once that member is gone.';
COMMENT ON COLUMN ember_schema.account_link_request.sent_at IS
    'When the request was last sent, first or again. Sending again is refused for a day after it.';
COMMENT ON COLUMN ember_schema.account_link_request.token_hash IS
    'HMAC-SHA-256 of the token in the link mailed to the account, keyed with the installation''s token pepper. The link only opens the request for a session of that account and never answers it. NULL where no mail could be sent, and once the request is answered.';
COMMENT ON COLUMN ember_schema.account_link_request.expires_at IS
    'Until when the person may answer: thirty days after the request was last sent. An hourly sweep marks it EXPIRED afterwards.';
COMMENT ON COLUMN ember_schema.account_link_request.answered_at IS
    'When the request was answered or expired. NULL while it waits.';
COMMENT ON COLUMN ember_schema.account_link_request.answer IS
    'ACCEPTED when the person linked their account to the member, DECLINED when they refused, EXPIRED when thirty days passed without an answer. NULL while it waits.';
COMMENT ON CONSTRAINT account_link_request_answered ON ember_schema.account_link_request IS
    'A request has an answering time exactly when it has an answer.';
COMMENT ON INDEX ember_schema.account_link_request_open_idx IS
    'A member waits for at most one link at a time.';

ALTER TYPE ember_schema.two_factor_event ADD VALUE IF NOT EXISTS 'ACCOUNT_LINK_ACCEPTED';

COMMENT ON COLUMN ember_schema.account_2fa_audit.station_id IS
    'The station whose administration acted, for an event a station administrator caused, and the station the account was linked to for ACCOUNT_LINK_ACCEPTED. NULL for everything else done by the account itself or by an instance administrator.';


ALTER TABLE ember_schema.station_storage_config
    ADD COLUMN shared_by_transfer BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN ember_schema.station_storage_config.shared_by_transfer IS
    'TRUE when the station took this storage over from the installation it moved here from. That installation can still keep files in the same place, which no row here names, so the storage check never deletes a file it finds no row for while this is set. Back to FALSE once the station is put on other storage.';

CREATE UNIQUE INDEX IF NOT EXISTS signing_request_live_idx
    ON ember_schema.signing_request (generation_id)
    WHERE state IN ('OPEN', 'COMPLETE');
COMMENT ON INDEX ember_schema.signing_request_live_idx IS
    'A generated document has at most one request that is open or complete. Two managers asking at the same moment, or a correction racing a request, leave one of them refused.';

ALTER TABLE ember_schema.signing_request
    ADD COLUMN seal_failed_at TIMESTAMPTZ NULL;

COMMENT ON COLUMN ember_schema.signing_request.seal_failed_at IS
    'When the sweep that seals the newest state of requests last failed to seal this one. The sweep takes the requests that never failed first and the others in the order they last failed, so a request that keeps failing holds back no other. NULL once a version was filed, and for a request whose sealing never failed in the sweep.';

ALTER TABLE ember_schema.member_document_version
    ADD COLUMN timestamps_failed_at TIMESTAMPTZ NULL;

COMMENT ON COLUMN ember_schema.member_document_version.timestamps_failed_at IS
    'When adding a later timestamp to this version, the first one or a renewal, last failed for a reason of the version itself, such as a file missing from the store. The runs that add them take the versions that never failed first and the others in the order they last failed, so a version that keeps failing holds back no other. NULL for a version where it never failed.';

ALTER TABLE ember_schema.issuer_signature
    DROP CONSTRAINT issuer_signature_seal_level_check,
    ADD CONSTRAINT issuer_signature_seal_level_check
        CHECK (seal_level IN ('BASELINE_B', 'BASELINE_T', 'BASELINE_LT', 'BASELINE_LTA'));

COMMENT ON COLUMN ember_schema.issuer_signature.seal_level IS
    'The PAdES baseline level the station''s seal on the letter reached: BASELINE_B without a timestamp, BASELINE_T with one, BASELINE_LT with the timestamp and the material to check both offline, BASELINE_LTA with that material covered by a later document timestamp that renews the earlier ones.';
COMMENT ON CONSTRAINT issuer_signature_seal_level_check ON ember_schema.issuer_signature IS
    'The PAdES baseline levels Ember seals at.';

ALTER TABLE ember_schema.event_document_submission
    ALTER COLUMN document_id DROP NOT NULL,
    DROP CONSTRAINT event_document_submission_document_id_fkey,
    ADD CONSTRAINT event_document_submission_document_id_fkey
        FOREIGN KEY (document_id) REFERENCES ember_schema.member_document (id) ON DELETE SET NULL;

DROP INDEX IF EXISTS ember_schema.uq_event_document_submission_standing;
CREATE UNIQUE INDEX IF NOT EXISTS uq_event_document_submission_standing
    ON ember_schema.event_document_submission (event_id, event_date, template_id, member_id)
    WHERE state <> 'REJECTED' AND document_id IS NOT NULL;

COMMENT ON COLUMN ember_schema.event_document_submission.document_id IS
    'The scan, filed in the participant''s documents. NULL once the scan was deleted: the submission stays on record with its state and reason, but no longer stands, which opens the requirement again.';
COMMENT ON INDEX ember_schema.uq_event_document_submission_standing IS
    'At most one scan per participant, document and date waits or is confirmed; turned-down ones and those whose scan was deleted are kept beside it.';
COMMENT ON CONSTRAINT event_document_submission_document_id_fkey ON ember_schema.event_document_submission IS
    'Deleting the scan keeps the submission on record without it.';

ALTER TABLE ember_schema.event_document_submission
    DROP CONSTRAINT event_document_submission_state_check,
    ADD CONSTRAINT event_document_submission_state_check
        CHECK (state IN ('SUBMITTED', 'CONFIRMED', 'REJECTED', 'WITHDRAWN'));

DROP INDEX IF EXISTS ember_schema.uq_event_document_submission_standing;
CREATE UNIQUE INDEX IF NOT EXISTS uq_event_document_submission_standing
    ON ember_schema.event_document_submission (event_id, event_date, template_id, member_id)
    WHERE state IN ('SUBMITTED', 'CONFIRMED') AND document_id IS NOT NULL;

COMMENT ON COLUMN ember_schema.event_document_submission.state IS
    'SUBMITTED while it waits for a manager, CONFIRMED once a manager confirmed it as the signed paper copy, REJECTED once a manager turned it down, WITHDRAWN once the agreement it was confirmed for was withdrawn.';
COMMENT ON CONSTRAINT event_document_submission_state_check ON ember_schema.event_document_submission IS
    'The states a scan handed in can be in.';
COMMENT ON INDEX ember_schema.uq_event_document_submission_standing IS
    'At most one scan per participant, document and date waits or is confirmed; turned-down and withdrawn ones and those whose scan was deleted are kept beside it.';

CREATE UNIQUE INDEX IF NOT EXISTS uq_station_member_station_account
    ON ember_schema.station_member (station_id, account_id)
    WHERE account_id IS NOT NULL;

COMMENT ON INDEX ember_schema.uq_station_member_station_account IS
    'An account is at most one member of a station, former members included, so two answers or joins arriving at once cannot give it a second one.';

ALTER TABLE ember_schema.document_template_field
    DROP CONSTRAINT document_template_field_kind_check,
    ADD CONSTRAINT document_template_field_kind_check CHECK (kind IN ('TEXT', 'CHECK', 'SIGNATURE', 'FILL_IN')),
    DROP CONSTRAINT document_template_field_check,
    ADD CONSTRAINT document_template_field_role_kind_check CHECK ((kind IN ('SIGNATURE', 'FILL_IN')) = (role IS NOT NULL)),
    ADD COLUMN required BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN max_length INTEGER NULL,
    ADD CONSTRAINT document_template_field_fill_in_check CHECK (
        kind = 'FILL_IN' OR (NOT required AND max_length IS NULL)),
    ADD CONSTRAINT document_template_field_max_length_check CHECK (max_length IS NULL OR max_length BETWEEN 1 AND 500);

COMMENT ON COLUMN ember_schema.document_template_field.kind IS
    'TEXT prints a text with placeholders, CHECK prints a cross where its text says yes, SIGNATURE becomes an empty PDF signature field named after its role, FILL_IN becomes an empty PDF text field the signer of its role types into when signing, named fill-<signature field>-<position>.';
COMMENT ON COLUMN ember_schema.document_template_field.text IS
    'The text with placeholders written as {{key}}: printed by TEXT, deciding the cross of CHECK, and for SIGNATURE the text under the line, printed only where print_text is set. NULL for a SIGNATURE without one. For FILL_IN the label the signer is shown, at most 100 characters and printed nowhere.';
COMMENT ON COLUMN ember_schema.document_template_field.role IS
    'Who signs in a SIGNATURE field, and for FILL_IN whose signer fills it in: PARTICIPANT, GUARDIAN_1, GUARDIAN_2 (left out for a member with fewer than two guardians), EACH_GUARDIAN (the box shared out into one field per guardian of the member), ANY_GUARDIAN (one field any guardian may sign) or ISSUER. NULL for every other kind.';
COMMENT ON COLUMN ember_schema.document_template_field.required IS
    'For FILL_IN: true where the signer has to fill the field in to sign. FALSE for every other kind.';
COMMENT ON COLUMN ember_schema.document_template_field.max_length IS
    'For FILL_IN: the most characters the typed value may have, 1 to 500. NULL for 500, and for every other kind.';
COMMENT ON CONSTRAINT document_template_field_role_kind_check ON ember_schema.document_template_field IS
    'A signature field and a field to fill in name a signer, no other field does.';
COMMENT ON CONSTRAINT document_template_field_fill_in_check ON ember_schema.document_template_field IS
    'Only a field to fill in can be required or limit its length.';

CREATE TABLE IF NOT EXISTS ember_schema.event_partner_agreement
(
    id               SERIAL PRIMARY KEY,
    station_id       INTEGER     NOT NULL REFERENCES ember_schema.station (id) ON DELETE CASCADE,
    event_id         INTEGER     NOT NULL REFERENCES ember_schema.station_event (id) ON DELETE CASCADE,
    event_date       DATE        NOT NULL,
    template_id      INTEGER     NOT NULL REFERENCES ember_schema.document_template (id) ON DELETE CASCADE,
    template_version INTEGER     NOT NULL,
    title            TEXT        NOT NULL,
    file_name        TEXT        NOT NULL,
    content          BYTEA       NOT NULL,
    sha256           TEXT        NOT NULL CHECK (sha256 ~ '^[0-9a-f]{64}$'),
    generated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT event_partner_agreement_once UNIQUE (event_id, event_date, template_id, template_version)
);

CREATE INDEX IF NOT EXISTS event_partner_agreement_station_idx ON ember_schema.event_partner_agreement (station_id);
CREATE INDEX IF NOT EXISTS event_partner_agreement_template_idx ON ember_schema.event_partner_agreement (template_id);

COMMENT ON TABLE ember_schema.event_partner_agreement IS
    'The one copy of a document an appointment asks for that the members of partner stations sign on one date: drawn once per date and version of the template, about nobody in particular, with the appointment''s values for that date and the station''s. Every partner''s signer signs these same bytes at their home installation. Goes with the appointment and the template.';
COMMENT ON COLUMN ember_schema.event_partner_agreement.id IS 'Primary key.';
COMMENT ON COLUMN ember_schema.event_partner_agreement.station_id IS 'The station that holds the appointment.';
COMMENT ON COLUMN ember_schema.event_partner_agreement.event_id IS 'The appointment that asks for the document.';
COMMENT ON COLUMN ember_schema.event_partner_agreement.event_date IS 'The date of the appointment the copy is for.';
COMMENT ON COLUMN ember_schema.event_partner_agreement.template_id IS 'The template the copy was drawn from.';
COMMENT ON COLUMN ember_schema.event_partner_agreement.template_version IS
    'The version of the template it was drawn from. A newer version is drawn anew, and the copy partners signed before stays.';
COMMENT ON COLUMN ember_schema.event_partner_agreement.title IS 'The title the copy carries.';
COMMENT ON COLUMN ember_schema.event_partner_agreement.file_name IS 'The file name the copy is handed out under, ending in .pdf.';
COMMENT ON COLUMN ember_schema.event_partner_agreement.content IS 'The PDF exactly as it is handed out, which every signature binds to.';
COMMENT ON COLUMN ember_schema.event_partner_agreement.sha256 IS
    'SHA-256 of the PDF, lower-case hexadecimal, which a signed copy coming back names.';
COMMENT ON COLUMN ember_schema.event_partner_agreement.generated_at IS 'When the copy was drawn.';
COMMENT ON CONSTRAINT event_partner_agreement_once ON ember_schema.event_partner_agreement IS
    'One copy per appointment, date and version of the template.';

CREATE TABLE IF NOT EXISTS ember_schema.partner_agreement
(
    id                  SERIAL PRIMARY KEY,
    station_id          INTEGER     NOT NULL,
    event_id            INTEGER     NULL REFERENCES ember_schema.station_event (id) ON DELETE SET NULL,
    event_date          DATE        NOT NULL,
    template_id         INTEGER     NULL REFERENCES ember_schema.document_template (id) ON DELETE SET NULL,
    template_name       TEXT        NOT NULL,
    partner_id          INTEGER     NULL REFERENCES ember_schema.federation_partner (id) ON DELETE SET NULL,
    partner_station_uid UUID        NOT NULL,
    partner_name        TEXT        NULL,
    remote_member_id    UUID        NOT NULL,
    state               TEXT        NOT NULL CHECK (state IN ('ASKED', 'SIGNED', 'PAPER_CONFIRMED', 'WITHDRAWN')),
    content_sha256      TEXT        NULL CHECK (content_sha256 ~ '^[0-9a-f]{64}$'),
    complete            BOOLEAN     NOT NULL DEFAULT FALSE,
    confirmed_by        INTEGER     NULL REFERENCES ember_schema.station_member (id) ON DELETE SET NULL,
    confirmed_by_name   TEXT        NULL,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    retention_months    INTEGER     NULL CHECK (retention_months >= 0),
    retain_until        TIMESTAMPTZ NOT NULL,
    CONSTRAINT partner_agreement_station FOREIGN KEY (station_id)
        REFERENCES ember_schema.station (id) ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT partner_agreement_once UNIQUE (event_id, event_date, template_id, partner_station_uid, remote_member_id),
    CONSTRAINT partner_agreement_paper CHECK (state <> 'PAPER_CONFIRMED' OR confirmed_by_name IS NOT NULL)
);

CREATE INDEX IF NOT EXISTS partner_agreement_station_idx ON ember_schema.partner_agreement (station_id);
CREATE INDEX IF NOT EXISTS partner_agreement_template_idx ON ember_schema.partner_agreement (template_id);
CREATE INDEX IF NOT EXISTS partner_agreement_partner_idx ON ember_schema.partner_agreement (partner_id);
CREATE INDEX IF NOT EXISTS partner_agreement_confirmed_by_idx ON ember_schema.partner_agreement (confirmed_by);
CREATE INDEX IF NOT EXISTS partner_agreement_retain_until_idx ON ember_schema.partner_agreement (retain_until);

COMMENT ON TABLE ember_schema.partner_agreement IS
    'Where a document an appointment asks partners to sign stands for one member of a partner station on one date, as the station holding the appointment sees it. No row means the partner never said it takes the document on: its installation cannot sign, or signing failed there, and the document counts as signature missing. The signed copies that came back are in partner_agreement_copy. Kept after the appointment and after the partnership end, until retain_until, then deleted by a daily sweep with its copies.';
COMMENT ON COLUMN ember_schema.partner_agreement.id IS 'Primary key.';
COMMENT ON COLUMN ember_schema.partner_agreement.station_id IS 'The station that holds the appointment.';
COMMENT ON COLUMN ember_schema.partner_agreement.event_id IS 'The appointment. NULL once it was deleted.';
COMMENT ON COLUMN ember_schema.partner_agreement.event_date IS 'The date of the appointment.';
COMMENT ON COLUMN ember_schema.partner_agreement.template_id IS 'The template the document was drawn from. NULL once it was deleted.';
COMMENT ON COLUMN ember_schema.partner_agreement.template_name IS
    'What the template was called when the row was written, so the row still says what was signed once it is gone.';
COMMENT ON COLUMN ember_schema.partner_agreement.partner_id IS
    'This station''s partnership with the member''s station. NULL once the partnership was deleted.';
COMMENT ON COLUMN ember_schema.partner_agreement.partner_station_uid IS 'The member''s station, as the federation knows it.';
COMMENT ON COLUMN ember_schema.partner_agreement.partner_name IS
    'The name the partnership knew the member''s station by when the row was last written. NULL where it knew none.';
COMMENT ON COLUMN ember_schema.partner_agreement.remote_member_id IS
    'The member at the partner station, by the id their registration names. Nothing else of the member is kept here; the signed copy carries the signers'' official names.';
COMMENT ON COLUMN ember_schema.partner_agreement.state IS
    'ASKED once the partner took the document on for the member and asks for it to be signed there, SIGNED once a sealed copy came back from the partner (whether every field is settled says complete), PAPER_CONFIRMED when a manager here confirmed a signed paper copy and no sealed copy came back, WITHDRAWN when the partner reported the signed agreement withdrawn there.';
COMMENT ON COLUMN ember_schema.partner_agreement.content_sha256 IS
    'SHA-256 of the copy the partner asked to be signed, the one in event_partner_agreement. NULL for a paper copy confirmed before the partner said anything.';
COMMENT ON COLUMN ember_schema.partner_agreement.complete IS
    'Whether the latest copy that came back has every signature field settled at the partner; false while one still waits.';
COMMENT ON COLUMN ember_schema.partner_agreement.confirmed_by IS
    'The member here who confirmed a signed paper copy. NULL where nobody did, and once that member is gone. Stays when a sealed copy comes back afterwards.';
COMMENT ON COLUMN ember_schema.partner_agreement.confirmed_by_name IS
    'The official name of the member who confirmed a paper copy, as it was then. NULL where nobody did.';
COMMENT ON COLUMN ember_schema.partner_agreement.updated_at IS 'When the row last changed.';
COMMENT ON COLUMN ember_schema.partner_agreement.retention_months IS
    'The months the template keeps signed documents, copied when the row was written. NULL where the template sets none.';
COMMENT ON COLUMN ember_schema.partner_agreement.retain_until IS
    'Until when the row and its copies are kept: the date of the appointment plus retention_months, or plus 12 months where the template sets none. The daily sweep deletes it afterwards. The partner deleting its member never changes it, since nothing of that travels.';
COMMENT ON CONSTRAINT partner_agreement_station ON ember_schema.partner_agreement IS
    'A row goes with its station. Checked at commit, because deleting a station also empties the appointment, template, partnership and member columns of a row it is about to delete.';
COMMENT ON CONSTRAINT partner_agreement_once ON ember_schema.partner_agreement IS
    'One row per appointment, date, document and member of a partner station.';
COMMENT ON CONSTRAINT partner_agreement_paper ON ember_schema.partner_agreement IS
    'A paper copy always names the member who confirmed it.';

CREATE TABLE IF NOT EXISTS ember_schema.partner_agreement_copy
(
    id            SERIAL PRIMARY KEY,
    agreement_id  INTEGER     NOT NULL,
    sealed_sha256 TEXT        NOT NULL CHECK (sealed_sha256 ~ '^[0-9a-f]{64}$'),
    content       BYTEA       NOT NULL,
    fields        JSONB       NOT NULL,
    complete      BOOLEAN     NOT NULL,
    received_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT partner_agreement_copy_agreement FOREIGN KEY (agreement_id)
        REFERENCES ember_schema.partner_agreement (id) ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT partner_agreement_copy_once UNIQUE (agreement_id, sealed_sha256)
);

COMMENT ON TABLE ember_schema.partner_agreement_copy IS
    'A sealed copy of a document an appointment asks partners to sign, as it came back from the member''s home installation: sealed there and checked here against the partner''s pinned signing authorities before it was taken. Each state the partner sealed is kept beside the earlier ones; the newest is the current one. Locked: a copy is never changed, and deleted only with its row in partner_agreement.';
COMMENT ON COLUMN ember_schema.partner_agreement_copy.id IS 'Primary key.';
COMMENT ON COLUMN ember_schema.partner_agreement_copy.agreement_id IS 'The document and member the copy is for.';
COMMENT ON COLUMN ember_schema.partner_agreement_copy.sealed_sha256 IS 'SHA-256 of the sealed PDF, lower-case hexadecimal.';
COMMENT ON COLUMN ember_schema.partner_agreement_copy.content IS
    'The sealed PDF exactly as it came back: the document, the signature of each signer drawn in, the record page and the evidence attached, under the partner station''s seal.';
COMMENT ON COLUMN ember_schema.partner_agreement_copy.fields IS
    'What the partner reported of each signature field with the copy: its name, whether it is signed, open, confirmed on paper, waived or withdrawn, when it was settled, and for a signed one the kind of proof the signer gave.';
COMMENT ON COLUMN ember_schema.partner_agreement_copy.complete IS 'Whether every field was settled at the partner in this copy.';
COMMENT ON COLUMN ember_schema.partner_agreement_copy.received_at IS 'When the copy came in.';
COMMENT ON CONSTRAINT partner_agreement_copy_agreement ON ember_schema.partner_agreement_copy IS
    'A copy goes with its row. Checked at commit, for the same reason as partner_agreement_station.';
COMMENT ON CONSTRAINT partner_agreement_copy_once ON ember_schema.partner_agreement_copy IS
    'A copy is kept once, whichever way it arrived again.';

CREATE OR REPLACE FUNCTION ember_schema.partner_agreement_keep_copies() RETURNS TRIGGER
    LANGUAGE plpgsql
AS
$$
BEGIN
    IF EXISTS (SELECT 1 FROM ember_schema.station s WHERE s.id = OLD.station_id)
        AND OLD.retain_until > now()
        AND EXISTS (SELECT 1 FROM ember_schema.partner_agreement_copy c WHERE c.agreement_id = OLD.id) THEN
        RAISE EXCEPTION 'Partner agreement % holds signed copies and is kept until %', OLD.id, OLD.retain_until
            USING ERRCODE = 'restrict_violation';
    END IF;
    RETURN OLD;
END;
$$;

COMMENT ON FUNCTION ember_schema.partner_agreement_keep_copies()
    IS 'Refuses deleting a row that holds signed copies while its station exists and its retention is not over. Deleting the station still takes it, since the cascade from the station runs after the station row is gone.';

CREATE TRIGGER partner_agreement_keep_copies
    BEFORE DELETE
    ON ember_schema.partner_agreement
    FOR EACH ROW
EXECUTE FUNCTION ember_schema.partner_agreement_keep_copies();

CREATE OR REPLACE FUNCTION ember_schema.partner_agreement_copy_locked() RETURNS TRIGGER
    LANGUAGE plpgsql
AS
$$
BEGIN
    IF TG_OP = 'UPDATE' THEN
        RAISE EXCEPTION 'Signed copy % of a partner agreement cannot be changed', OLD.id
            USING ERRCODE = 'restrict_violation';
    END IF;
    IF EXISTS (SELECT 1 FROM ember_schema.partner_agreement a WHERE a.id = OLD.agreement_id) THEN
        RAISE EXCEPTION 'Signed copy % of a partner agreement is only deleted with its agreement', OLD.id
            USING ERRCODE = 'restrict_violation';
    END IF;
    RETURN OLD;
END;
$$;

COMMENT ON FUNCTION ember_schema.partner_agreement_copy_locked()
    IS 'Refuses changing a signed copy of a partner agreement, and deleting one while its row in partner_agreement exists. Deleting that row takes its copies, since the cascade runs after the row is gone.';

CREATE TRIGGER partner_agreement_copy_locked
    BEFORE UPDATE OR DELETE
    ON ember_schema.partner_agreement_copy
    FOR EACH ROW
EXECUTE FUNCTION ember_schema.partner_agreement_copy_locked();

CREATE TABLE IF NOT EXISTS ember_schema.partner_signing_request
(
    id                 SERIAL PRIMARY KEY,
    station_id         INTEGER     NOT NULL,
    request_id         INTEGER     NOT NULL UNIQUE,
    partner_id         INTEGER     NULL REFERENCES ember_schema.federation_partner (id) ON DELETE SET NULL,
    partner_station_uid UUID       NOT NULL,
    remote_event_id    INTEGER     NOT NULL,
    event_date         DATE        NOT NULL,
    remote_template_id INTEGER     NOT NULL,
    template_version   INTEGER     NOT NULL,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    announced_at       TIMESTAMPTZ NULL,
    delivered_sha256   TEXT        NULL CHECK (delivered_sha256 ~ '^[0-9a-f]{64}$'),
    delivery_attempts  INTEGER     NOT NULL DEFAULT 0 CHECK (delivery_attempts >= 0),
    next_delivery_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT partner_signing_request_station FOREIGN KEY (station_id)
        REFERENCES ember_schema.station (id) ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT partner_signing_request_request FOREIGN KEY (request_id)
        REFERENCES ember_schema.signing_request (id) ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED
);

CREATE INDEX IF NOT EXISTS partner_signing_request_station_idx ON ember_schema.partner_signing_request (station_id);
CREATE INDEX IF NOT EXISTS partner_signing_request_partner_idx ON ember_schema.partner_signing_request (partner_id);
CREATE INDEX IF NOT EXISTS partner_signing_request_due_idx ON ember_schema.partner_signing_request (next_delivery_at);

COMMENT ON TABLE ember_schema.partner_signing_request IS
    'A signing request at this installation for a document a partner station''s appointment asks one of this station''s members to sign: which partner, appointment, date and document it answers, and how far its signed copies have travelled back to that partner. The member signs here, and every sealed state is sent back to the partner, signed with this station''s federation key, and sent again later while the partner is unreachable. Goes with its request.';
COMMENT ON COLUMN ember_schema.partner_signing_request.id IS 'Primary key.';
COMMENT ON COLUMN ember_schema.partner_signing_request.station_id IS 'The station of the member who signs.';
COMMENT ON COLUMN ember_schema.partner_signing_request.request_id IS
    'The signing request here, on the copy filed in the member''s documents.';
COMMENT ON COLUMN ember_schema.partner_signing_request.partner_id IS
    'This station''s partnership with the station that holds the appointment. NULL once the partnership was deleted; nothing travels then.';
COMMENT ON COLUMN ember_schema.partner_signing_request.partner_station_uid IS
    'The station that holds the appointment, as the federation knows it.';
COMMENT ON COLUMN ember_schema.partner_signing_request.remote_event_id IS 'The appointment, by its id at the partner.';
COMMENT ON COLUMN ember_schema.partner_signing_request.event_date IS 'The date of the appointment.';
COMMENT ON COLUMN ember_schema.partner_signing_request.remote_template_id IS
    'The document the appointment asks for, by the id of its template at the partner.';
COMMENT ON COLUMN ember_schema.partner_signing_request.template_version IS 'The version of that template the copy was drawn from.';
COMMENT ON COLUMN ember_schema.partner_signing_request.created_at IS 'When the document was taken on here.';
COMMENT ON COLUMN ember_schema.partner_signing_request.announced_at IS
    'When the partner was told the document is asked for here. NULL until that reached it.';
COMMENT ON COLUMN ember_schema.partner_signing_request.delivered_sha256 IS
    'SHA-256 of the newest sealed copy the partner took. NULL until one reached it.';
COMMENT ON COLUMN ember_schema.partner_signing_request.delivery_attempts IS
    'How many times in a row sending to the partner failed; back to 0 once something reached it. Sending is given up after 32 failures in a row, about a week of tries, until the next sealed state starts it again.';
COMMENT ON COLUMN ember_schema.partner_signing_request.next_delivery_at IS
    'When to try again what has not reached the partner yet, later after every failure.';
COMMENT ON CONSTRAINT partner_signing_request_station ON ember_schema.partner_signing_request IS
    'A row goes with its station. Checked at commit, because deleting a station also empties the partnership column of a row it is about to delete.';
COMMENT ON CONSTRAINT partner_signing_request_request ON ember_schema.partner_signing_request IS
    'A row goes with its signing request, which the retention sweep deletes. Checked at commit for the same reason as partner_signing_request_station.';

ALTER TABLE ember_schema.account_link_request
    ALTER COLUMN station_id DROP NOT NULL,
    ALTER COLUMN station_member_id DROP NOT NULL,
    ADD COLUMN cluster_id INTEGER NULL REFERENCES ember_schema.cluster (id) ON DELETE CASCADE,
    ADD COLUMN user_type  TEXT    NULL CHECK (user_type IN ('CLUSTER_USER', 'CLUSTER_ADMIN')),
    ADD COLUMN address    TEXT    NULL,
    DROP CONSTRAINT IF EXISTS account_link_request_origin_check,
    ADD CONSTRAINT account_link_request_origin_check
        CHECK (origin IN ('IMPORT', 'INVITE', 'ASSOCIATION_INVITE')),
    ADD CONSTRAINT account_link_request_owner CHECK (
        (station_id IS NOT NULL AND station_member_id IS NOT NULL
            AND cluster_id IS NULL AND user_type IS NULL AND address IS NULL
            AND origin IN ('IMPORT', 'INVITE'))
        OR
        (station_id IS NULL AND station_member_id IS NULL AND created_by IS NULL
            AND cluster_id IS NOT NULL AND user_type IS NOT NULL AND address IS NOT NULL
            AND origin = 'ASSOCIATION_INVITE'));

CREATE UNIQUE INDEX IF NOT EXISTS account_link_request_cluster_open_idx
    ON ember_schema.account_link_request (cluster_id, account_id)
    WHERE answer IS NULL AND cluster_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS account_link_request_cluster_idx
    ON ember_schema.account_link_request (cluster_id)
    WHERE cluster_id IS NOT NULL;

COMMENT ON TABLE ember_schema.account_link_request IS
    'A station or an association asking a person to take an existing account in. A station asks to link the account to one of its members: an import that found the account by its address and an invite naming that address both leave the member without an account and ask here instead. An association asks the account to take a role there: adding an address that already has an account asks here instead of making the membership. Only the person signed in to that account can accept. The answered rows stay as the record of what was asked and answered.';
COMMENT ON COLUMN ember_schema.account_link_request.station_id IS
    'The station that asks. NULL for a request of an association.';
COMMENT ON COLUMN ember_schema.account_link_request.station_member_id IS
    'The member the account would be linked to. The member exists without an account while the request waits. NULL for a request of an association.';
COMMENT ON COLUMN ember_schema.account_link_request.cluster_id IS
    'The association that asks the account to take a role. NULL for a request of a station.';
COMMENT ON COLUMN ember_schema.account_link_request.user_type IS
    'The role the association offers: CLUSTER_USER or CLUSTER_ADMIN. Accepting makes the account a member of the association with it. NULL for a request of a station.';
COMMENT ON COLUMN ember_schema.account_link_request.address IS
    'The address the association typed, which is how its member page names the request. Kept as typed so a later change of the account''s own address never reaches the association. NULL for a request of a station.';
COMMENT ON COLUMN ember_schema.account_link_request.origin IS
    'How the request came about: IMPORT when a station import found the account by its address, INVITE when a member manager of a station invited that address, ASSOCIATION_INVITE when an association administrator added that address to the association.';
COMMENT ON CONSTRAINT account_link_request_owner ON ember_schema.account_link_request IS
    'A request belongs to exactly one asker: a station member (station and member set, origin IMPORT or INVITE) or an association role (association, role and address set, origin ASSOCIATION_INVITE).';
COMMENT ON INDEX ember_schema.account_link_request_cluster_open_idx IS
    'An association waits for at most one answer per account at a time.';

ALTER TYPE ember_schema.two_factor_event ADD VALUE IF NOT EXISTS 'ASSOCIATION_LINK_ACCEPTED';

ALTER TABLE ember_schema.signing_request
    DROP CONSTRAINT IF EXISTS signing_request_state_check,
    ADD CONSTRAINT signing_request_state_check
        CHECK (state IN ('OPEN', 'COMPLETE', 'WITHDRAWN', 'SUPERSEDED', 'REVOKED'));

COMMENT ON COLUMN ember_schema.signing_request.state IS
    'OPEN while a field waits for a signature, COMPLETE once every field is signed, confirmed on paper, waived or withdrawn and at least one was signed or confirmed, WITHDRAWN when nothing was signed or confirmed, SUPERSEDED when a corrected document replaced it, REVOKED when a signer withdrew the signed agreement (signing_withdrawal); what was signed on a revoked request stays as evidence.';
COMMENT ON CONSTRAINT signing_request_state_check ON ember_schema.signing_request IS
    'The states a request can be in.';

CREATE TABLE IF NOT EXISTS ember_schema.signing_withdrawal
(
    id                SERIAL PRIMARY KEY,
    request_id        INTEGER     NOT NULL UNIQUE,
    member_id         INTEGER     NULL REFERENCES ember_schema.station_member (id) ON DELETE SET NULL,
    withdrawn_by      INTEGER     NULL REFERENCES ember_schema.station_member (id) ON DELETE SET NULL,
    withdrawn_by_name TEXT        NOT NULL,
    capacity          TEXT        NOT NULL CHECK (capacity IN ('ACCOUNT_HOLDER', 'GUARDIAN')),
    reason            TEXT        NULL CHECK (reason IS NULL OR length(reason) BETWEEN 1 AND 500),
    withdrawn_at      TIMESTAMPTZ NOT NULL,
    truncated_ip      TEXT        NULL,
    user_agent        TEXT        NULL,
    sealed_sha256     TEXT        NULL CHECK (sealed_sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT signing_withdrawal_request FOREIGN KEY (request_id)
        REFERENCES ember_schema.signing_request (id) ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED
);

CREATE INDEX IF NOT EXISTS signing_withdrawal_member_idx ON ember_schema.signing_withdrawal (member_id);
CREATE INDEX IF NOT EXISTS signing_withdrawal_withdrawn_by_idx ON ember_schema.signing_withdrawal (withdrawn_by);
CREATE INDEX IF NOT EXISTS signing_withdrawal_unsealed_idx
    ON ember_schema.signing_withdrawal (withdrawn_at)
    WHERE sealed_sha256 IS NULL;

COMMENT ON TABLE ember_schema.signing_withdrawal IS
    'A signed agreement its signer withdrew: who withdrew it, when, from where and why. The request it withdraws is REVOKED, its signed fields and their evidence stay, and the withdrawal is sealed into a new version of the document, after the versions that carry the signatures. Kept and deleted with its request.';
COMMENT ON COLUMN ember_schema.signing_withdrawal.id IS 'Primary key.';
COMMENT ON COLUMN ember_schema.signing_withdrawal.request_id IS
    'The request whose agreement was withdrawn. A request is withdrawn once.';
COMMENT ON COLUMN ember_schema.signing_withdrawal.member_id IS
    'The member the document is about, copied from the request so the withdrawal is part of that member''s data. NULL once that member was deleted.';
COMMENT ON COLUMN ember_schema.signing_withdrawal.withdrawn_by IS
    'The member who withdrew the agreement: the member themselves, a guardian acting for them, or somebody who signed a field of it. NULL once they are gone.';
COMMENT ON COLUMN ember_schema.signing_withdrawal.withdrawn_by_name IS
    'The official name of whoever withdrew the agreement, as it was then, kept once they are gone.';
COMMENT ON COLUMN ember_schema.signing_withdrawal.capacity IS
    'In what capacity the agreement was withdrawn: ACCOUNT_HOLDER by the member themselves or by a signer for their own signature, GUARDIAN by a guardian on behalf of the member.';
COMMENT ON COLUMN ember_schema.signing_withdrawal.reason IS
    'Why it was withdrawn, as the person wrote it, at most 500 characters. NULL where no reason was given.';
COMMENT ON COLUMN ember_schema.signing_withdrawal.withdrawn_at IS 'When the agreement was withdrawn.';
COMMENT ON COLUMN ember_schema.signing_withdrawal.truncated_ip IS
    'The network address the withdrawal came from, shortened like the address of a signing act. NULL where none was known.';
COMMENT ON COLUMN ember_schema.signing_withdrawal.user_agent IS
    'The browser the withdrawal came from, as it named itself. NULL where none was known.';
COMMENT ON COLUMN ember_schema.signing_withdrawal.sealed_sha256 IS
    'SHA-256 of the first sealed version of the document that carries the withdrawal, lower-case hexadecimal. NULL until it is sealed; a sweep seals a withdrawal whose sealing failed.';
COMMENT ON CONSTRAINT signing_withdrawal_request ON ember_schema.signing_withdrawal IS
    'A withdrawal goes with its request. Checked at commit, for the same reason as signing_request_field_request.';
COMMENT ON INDEX ember_schema.signing_withdrawal_unsealed_idx IS
    'Finds the withdrawals no sealed version carries yet, which the sweep seals.';

ALTER TABLE ember_schema.event_registration
    ADD COLUMN agreement_withdrawn_at TIMESTAMPTZ NULL;

COMMENT ON COLUMN ember_schema.event_registration.agreement_withdrawn_at IS
    'When a signed agreement for this date that one of the appointment''s documents to bring asked for was withdrawn while the registration stood, which flags the registration for whoever runs the appointment. NULL where none was, and again once the agreement is signed anew.';

ALTER TABLE ember_schema.signing_evidence
    ADD COLUMN batch_uid          UUID    NULL,
    ADD COLUMN batch_position     INTEGER NULL,
    ADD COLUMN batch_request_uids TEXT[]  NULL,
    ADD COLUMN batch_field_names  TEXT[]  NULL,
    ADD COLUMN batch_digests      BYTEA   NULL,
    ADD CONSTRAINT signing_evidence_batch CHECK (
        num_nonnulls(batch_uid, batch_position, batch_request_uids, batch_field_names, batch_digests) = 0
        OR (num_nonnulls(batch_uid, batch_position, batch_request_uids, batch_field_names, batch_digests) = 5
            AND cardinality(batch_request_uids) >= 2
            AND cardinality(batch_field_names) = cardinality(batch_request_uids)
            AND length(batch_digests) = 32 * cardinality(batch_request_uids)
            AND batch_position >= 0
            AND batch_position < cardinality(batch_request_uids)));

COMMENT ON COLUMN ember_schema.signing_evidence.batch_uid IS
    'The batch the act was confirmed in together with other fields, by one passkey or security key answer, code or password. NULL for an act confirmed on its own, whose challenge has the single layout.';
COMMENT ON COLUMN ember_schema.signing_evidence.batch_position IS
    'Where this act stands in its batch, counted from 0, in the order the fields were signed. NULL outside a batch.';
COMMENT ON COLUMN ember_schema.signing_evidence.batch_request_uids IS
    'The request of every field of the batch in its order, this act''s included, so the evidence names the other fields the confirmation covered. NULL outside a batch.';
COMMENT ON COLUMN ember_schema.signing_evidence.batch_field_names IS
    'The name of every field of the batch in its order, beside batch_request_uids. NULL outside a batch.';
COMMENT ON COLUMN ember_schema.signing_evidence.batch_digests IS
    'The item digest of every field of the batch in its order, 32 bytes each one after the other, from which with the nonce the batch challenge is recomputed. The other fields are known by their digest only, never by what they bound; this act''s own digest is recomputed from the act. NULL outside a batch.';
COMMENT ON CONSTRAINT signing_evidence_batch ON ember_schema.signing_evidence IS
    'An act is either outside any batch, or names a batch of two fields or more with its position in it, the request, the name and the 32-byte digest of each.';
COMMENT ON COLUMN ember_schema.signing_evidence.mark_image IS
    'The signature picture drawn into the signed field, a transparent PNG as the signer gave it at the act: their saved picture or one drawn on the spot. Every sealed version of the document draws it from here and shows nothing beside it, so a later change of the saved picture never changes an earlier signature. NULL for acts recorded before signature pictures, whose field is left empty.';
COMMENT ON COLUMN ember_schema.signing_evidence.mark_source IS
    'How mark_image came to the act: DRAWN, TYPED or UPLOADED when it was made for the act, SAVED when it was the picture the signer''s account kept. The evidence attached to each sealed version, and the record built from it on request, name it beside the picture''s SHA-256. NULL exactly when mark_image is.';
