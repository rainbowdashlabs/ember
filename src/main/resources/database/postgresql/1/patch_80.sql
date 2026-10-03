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
    station_id                 INTEGER     NOT NULL REFERENCES ember_schema.station (id) ON DELETE CASCADE,
    kind                       TEXT        NOT NULL DEFAULT 'LETTER' CHECK (kind IN ('LETTER', 'PDF')),
    name                       TEXT        NOT NULL,
    title_pattern              TEXT        NOT NULL,
    file_name_pattern          TEXT        NOT NULL,
    tags                       TEXT[]      NOT NULL DEFAULT '{}',
    hidden                     BOOLEAN     NOT NULL DEFAULT FALSE,
    keep_on_archive            BOOLEAN     NOT NULL DEFAULT FALSE,
    legal                      BOOLEAN     NOT NULL DEFAULT FALSE,
    self_service               BOOLEAN     NOT NULL DEFAULT FALSE,
    self_service_cooldown_days INTEGER     NOT NULL DEFAULT 30 CHECK (self_service_cooldown_days >= 0),
    restriction_mode           TEXT        NOT NULL DEFAULT 'AND' CHECK (restriction_mode IN ('AND', 'OR')),
    language                   TEXT        NOT NULL DEFAULT 'DE' CHECK (language IN ('DE', 'EN')),
    version                   INTEGER     NOT NULL DEFAULT 1,
    created_at                 TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by                 INTEGER     NULL REFERENCES ember_schema.station_member (id) ON DELETE SET NULL,
    updated_at                 TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by                 INTEGER     NULL REFERENCES ember_schema.station_member (id) ON DELETE SET NULL,
    archived_at                TIMESTAMPTZ NULL
);

CREATE INDEX IF NOT EXISTS idx_document_template_station ON ember_schema.document_template (station_id);

COMMENT ON TABLE ember_schema.document_template IS
    'A template a station turns into a PDF for one member at a time. Never deleted, only archived, so every generated document keeps pointing at the template it came from.';
COMMENT ON COLUMN ember_schema.document_template.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.document_template.station_id IS 'The station that owns the template.';
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
COMMENT ON COLUMN ember_schema.document_template.self_service IS
    'Whether members of the audience may generate the document for themselves, and guardians for the members in their care.';
COMMENT ON COLUMN ember_schema.document_template.self_service_cooldown_days IS
    'How many days must pass before a member may generate the document again through self service. 0 means no wait.';
COMMENT ON COLUMN ember_schema.document_template.restriction_mode IS
    'AND or OR, how the parts of document_template_restriction combine.';
COMMENT ON COLUMN ember_schema.document_template.language IS
    'DE or EN, the language the documents are written in: it picks the pronouns of the gender profile field, how dates are written, and the words the letter prints itself.';
COMMENT ON COLUMN ember_schema.document_template.version IS
    'Counts up with every change, so the generation log says which state of the template a document came from.';
COMMENT ON COLUMN ember_schema.document_template.created_at IS 'When the template was created.';
COMMENT ON COLUMN ember_schema.document_template.created_by IS 'The member who created the template. NULL once they are gone.';
COMMENT ON COLUMN ember_schema.document_template.updated_at IS 'When the template was last changed.';
COMMENT ON COLUMN ember_schema.document_template.updated_by IS 'The member who last changed the template. NULL once they are gone.';
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
    'Who may generate a self service template for themselves. Empty means every member. Shaped like event_restriction.';
COMMENT ON COLUMN ember_schema.document_template_restriction.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.document_template_restriction.template_id IS 'References the document template.';
COMMENT ON COLUMN ember_schema.document_template_restriction.user_type IS
    'Required user type. Exactly one of user_type/group_id/tag_id/member_id must be set.';
COMMENT ON COLUMN ember_schema.document_template_restriction.group_id IS 'Required group membership.';
COMMENT ON COLUMN ember_schema.document_template_restriction.tag_id IS 'Required tag.';
COMMENT ON COLUMN ember_schema.document_template_restriction.member_id IS
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
    'The rows drawn at the top of every page, shaped like the rows of a page: each row up to three columns with their width, each holding a text with placeholders written as {{key}}, a picture from the media library by its content hash or the station logo, or blocks stacked in it. A block may carry a restriction saying which members it is printed for.';
COMMENT ON COLUMN ember_schema.document_template_letter.footer IS
    'The rows drawn at the bottom of every page, shaped like the header.';
COMMENT ON COLUMN ember_schema.document_template_letter.body IS
    'The rows of the letter itself, shaped like the header. A block whose restriction does not match the member is left out, and a row left with nothing is dropped.';
COMMENT ON COLUMN ember_schema.document_template_letter.page IS
    'The page margins in millimetres and the body font size in points. The paper is always A4.';

CREATE TABLE IF NOT EXISTS ember_schema.document_template_pdf_original
(
    id          SERIAL PRIMARY KEY,
    template_id INTEGER     NOT NULL REFERENCES ember_schema.document_template (id) ON DELETE CASCADE,
    file_name   TEXT        NOT NULL,
    size_bytes  BIGINT      NOT NULL,
    sha256      TEXT        NOT NULL,
    inspection  JSONB       NOT NULL,
    uploaded_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    uploaded_by INTEGER     NULL REFERENCES ember_schema.station_member (id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_document_template_pdf_original_template
    ON ember_schema.document_template_pdf_original (template_id);

COMMENT ON TABLE ember_schema.document_template_pdf_original IS
    'Every uploaded version of the PDF a PDF template fills in. The file is kept in the station storage under document-templates/<id>/original, as it arrived, and never changed; a new upload is a new row.';
COMMENT ON COLUMN ember_schema.document_template_pdf_original.id IS 'Auto-generated primary key, also the storage key of the file.';
COMMENT ON COLUMN ember_schema.document_template_pdf_original.template_id IS 'The PDF template it was uploaded for.';
COMMENT ON COLUMN ember_schema.document_template_pdf_original.file_name IS 'The name the file was uploaded under.';
COMMENT ON COLUMN ember_schema.document_template_pdf_original.size_bytes IS 'The size of the file in bytes.';
COMMENT ON COLUMN ember_schema.document_template_pdf_original.sha256 IS 'The SHA-256 of the file as lowercase hex.';
COMMENT ON COLUMN ember_schema.document_template_pdf_original.inspection IS
    'What the PDF holds that fields care about, read at upload: each page with its crop box in points and its rotation, and the form fields it brings with their kind and where their first widget sits.';
COMMENT ON COLUMN ember_schema.document_template_pdf_original.uploaded_at IS 'When the file was uploaded.';
COMMENT ON COLUMN ember_schema.document_template_pdf_original.uploaded_by IS 'The member who uploaded the file. NULL once they are gone.';

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
    align       TEXT             NOT NULL CHECK (align IN ('LEFT', 'CENTER', 'RIGHT')),
    wrap        BOOLEAN          NOT NULL DEFAULT FALSE,
    role        TEXT             NULL CHECK (role IN ('PARTICIPANT', 'GUARDIAN_1', 'GUARDIAN_2', 'ISSUER')),
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
COMMENT ON COLUMN ember_schema.document_template_field.align IS 'LEFT, CENTER or RIGHT: where the text sits across the field.';
COMMENT ON COLUMN ember_schema.document_template_field.wrap IS
    'Whether a long text runs onto further lines. Otherwise it stays on one line and shrinks to fit.';
COMMENT ON COLUMN ember_schema.document_template_field.role IS
    'Who signs in a SIGNATURE field: PARTICIPANT, GUARDIAN_1, GUARDIAN_2 or ISSUER. NULL for every other kind.';

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
    pdf_original_id  INTEGER     NULL REFERENCES ember_schema.document_template_pdf_original (id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_document_generation_template_member
    ON ember_schema.document_generation (template_id, member_id, generated_at);
CREATE INDEX IF NOT EXISTS idx_document_generation_station ON ember_schema.document_generation (station_id);

COMMENT ON TABLE ember_schema.document_generation IS
    'Every document generated from a template: which template and which state of it, about whom, by whom, when, and the file that came out.';
COMMENT ON COLUMN ember_schema.document_generation.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.document_generation.station_id IS 'The station the document was filed at.';
COMMENT ON COLUMN ember_schema.document_generation.template_id IS 'The template the document was generated from.';
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
