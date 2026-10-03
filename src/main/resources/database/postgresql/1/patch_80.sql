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
    'The text with placeholders written as {{key}}, for TEXT and CHECK. NULL for SIGNATURE.';
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
    event_date       DATE        NULL
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
    CHECK (num_nonnulls(station_id, cluster_id) <= 1)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_document_font_station
    ON ember_schema.document_font (station_id, lower(family), style) WHERE station_id IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_document_font_cluster
    ON ember_schema.document_font (cluster_id, lower(family), style) WHERE cluster_id IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_document_font_instance
    ON ember_schema.document_font (lower(family), style) WHERE station_id IS NULL AND cluster_id IS NULL;

COMMENT ON TABLE ember_schema.document_font IS
    'A font uploaded for documents, one file per family and style. Owned by the instance (station_id and cluster_id both NULL), an association (cluster_id) or a station (station_id). A station reaches its own fonts, its association''s and the instance''s; on a family name found at several, the nearest owner wins. The file is kept in the owner''s storage under fonts/<id> and never sent to a browser.';
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
