ALTER TABLE ember_schema.waiting_list_entry
    ADD COLUMN invited_message TEXT NOT NULL DEFAULT '';

COMMENT ON COLUMN ember_schema.waiting_list_entry.invited_message IS
    'A personal note the station wrote into the current invitation, empty when it wrote none. Shown in the invitation mail and on the page behind the entry link. Cleared together with the invitation it belongs to.';

ALTER TABLE ember_schema.waiting_list_entry_guardian
    ADD COLUMN member_id INTEGER NULL REFERENCES ember_schema.station_member (id) ON DELETE SET NULL;

CREATE INDEX idx_wle_guardian_member
    ON ember_schema.waiting_list_entry_guardian (member_id)
    WHERE member_id IS NOT NULL;

COMMENT ON COLUMN ember_schema.waiting_list_entry_guardian.member_id IS
    'The station member this guardian became when the trial began, either a new one or one already at the station they were linked to. NULL until then. Keeps a guardian from being taken on twice when the entry joins, and names which memberships ending the trial may take back.';

CREATE TABLE IF NOT EXISTS ember_schema.document_template_question
(
    id          SERIAL PRIMARY KEY,
    template_id INTEGER NOT NULL REFERENCES ember_schema.document_template (id) ON DELETE CASCADE,
    position    INTEGER NOT NULL,
    key         TEXT    NOT NULL CHECK (key ~ '^[A-Za-z0-9_]{1,40}$'),
    label       TEXT    NOT NULL,
    kind        TEXT    NOT NULL CHECK (kind IN ('TEXT', 'DATE', 'CHECK')),
    suggestion  TEXT    NOT NULL DEFAULT '',
    required    BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE (template_id, key)
);

CREATE INDEX IF NOT EXISTS idx_document_template_question_template
    ON ember_schema.document_template_question (template_id, position);

COMMENT ON TABLE ember_schema.document_template_question IS
    'What the person generating a document from a template is asked. The template names each answer as {{ask.<key>}}.';
COMMENT ON COLUMN ember_schema.document_template_question.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.document_template_question.template_id IS 'References the template that asks it.';
COMMENT ON COLUMN ember_schema.document_template_question.position IS 'The order the questions are asked in, counted from 0.';
COMMENT ON COLUMN ember_schema.document_template_question.key IS
    'The name the template names the answer by, as {{ask.<key>}}. Unique within the template.';
COMMENT ON COLUMN ember_schema.document_template_question.label IS 'What the person generating is asked.';
COMMENT ON COLUMN ember_schema.document_template_question.kind IS
    'TEXT takes a text, DATE a day and CHECK a tick, which prints as a ticked or an empty box.';
COMMENT ON COLUMN ember_schema.document_template_question.suggestion IS
    'A text with placeholders, filled in for the member to suggest the answer. For a CHECK it ticks the box where it says yes. Where nobody answers, as in self service, the suggestion is the answer.';
COMMENT ON COLUMN ember_schema.document_template_question.required IS
    'Whether an empty answer counts as a missing value of the document.';

ALTER TABLE ember_schema.document_generation
    ADD COLUMN IF NOT EXISTS answers JSONB NULL;

COMMENT ON COLUMN ember_schema.document_generation.answers IS
    'What the template''s questions were answered with, by the key of the question, or NULL where it asks none.';

WITH checks AS (SELECT f.id,
                       f.template_id,
                       f.text,
                       row_number() OVER (PARTITION BY f.template_id ORDER BY f.position, f.id) AS number
                FROM ember_schema.document_template_field f
                WHERE f.kind = 'CHECK'
                  AND coalesce(f.text, '') NOT LIKE '{{ask.%'),
     asked AS (
         INSERT INTO ember_schema.document_template_question (template_id, position, key, label, kind, suggestion)
             SELECT template_id, number - 1, 'kreuz_' || number, 'Kreuz ' || number, 'CHECK', coalesce(text, '')
             FROM checks
             ON CONFLICT (template_id, key) DO NOTHING
             RETURNING template_id, key)
UPDATE ember_schema.document_template_field f
SET text = '{{ask.kreuz_' || c.number || '}}'
FROM checks c
WHERE f.id = c.id;

WITH boxes AS (SELECT b.template_id,
                      b.field_name,
                      b.text,
                      coalesce(nullif(trim(form_field ->> 'tooltip'), ''), b.field_name) AS label,
                      row_number() OVER (PARTITION BY b.template_id ORDER BY b.field_name) AS number
               FROM ember_schema.document_template_form_binding b
                        JOIN ember_schema.document_template_pdf p ON p.template_id = b.template_id
                        JOIN ember_schema.document_template_pdf_original o ON o.id = p.original_id
                        CROSS JOIN LATERAL jsonb_array_elements(o.inspection -> 'formFields') AS form_field
               WHERE form_field ->> 'name' = b.field_name
                 AND form_field ->> 'kind' = 'CHECK'
                 AND b.text NOT LIKE '{{ask.%'),
     offset_by AS (SELECT template_id, count(*) AS taken
                   FROM ember_schema.document_template_question
                   GROUP BY template_id),
     asked AS (
         INSERT INTO ember_schema.document_template_question (template_id, position, key, label, kind, suggestion)
             SELECT bx.template_id,
                    coalesce(o.taken, 0) + bx.number - 1,
                    'feld_' || bx.number,
                    left(bx.label, 120),
                    'CHECK',
                    bx.text
             FROM boxes bx
                      LEFT JOIN offset_by o ON o.template_id = bx.template_id
             ON CONFLICT (template_id, key) DO NOTHING
             RETURNING template_id, key)
UPDATE ember_schema.document_template_form_binding b
SET text = '{{ask.feld_' || bx.number || '}}'
FROM boxes bx
WHERE b.template_id = bx.template_id
  AND b.field_name = bx.field_name;
