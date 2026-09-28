-- Options of a question are named by a key of their own instead of by where they stand.
--
-- Choice and ranking options and Likert statements were plain lists of text, and an answer named
-- them by position: a choice by the indexes picked, a ranking by the indexes in order, a Likert grid
-- by the index of each statement written as text. Reordering, removing or renaming options on a
-- form with answers silently changed what those answers said.
--
-- Every option becomes an object holding a key and its label. An option that already exists gets
-- the key of its position, o0, o1 and so on, and every stored answer is rewritten from positions to
-- those keys. A position that names no option of its question is dropped from the answer, as the
-- results never counted it. Only data in the old shape is touched, so running this again changes
-- nothing.

UPDATE ember_schema.form_question
SET config = jsonb_set(
        config,
        '{options}',
        (SELECT coalesce(jsonb_agg(jsonb_build_object('key', 'o' || (o.pos - 1), 'label', coalesce(o.label, ''))
                                   ORDER BY o.pos), '[]'::JSONB)
         FROM jsonb_array_elements_text(config -> 'options') WITH ORDINALITY AS o(label, pos)))
WHERE question_type IN ('CHOICE', 'RANKING')
  AND jsonb_typeof(config -> 'options') = 'array'
  AND jsonb_typeof(config -> 'options' -> 0) = 'string';

UPDATE ember_schema.form_question
SET config = jsonb_set(
        config,
        '{statements}',
        (SELECT coalesce(jsonb_agg(jsonb_build_object('key', 'o' || (s.pos - 1), 'label', coalesce(s.label, ''))
                                   ORDER BY s.pos), '[]'::JSONB)
         FROM jsonb_array_elements_text(config -> 'statements') WITH ORDINALITY AS s(label, pos)))
WHERE question_type = 'LIKERT'
  AND jsonb_typeof(config -> 'statements') = 'array'
  AND jsonb_typeof(config -> 'statements' -> 0) = 'string';

UPDATE ember_schema.form_answer a
SET value = jsonb_set(
        a.value,
        '{selected}',
        (SELECT coalesce(jsonb_agg(to_jsonb('o' || s.idx) ORDER BY s.pos), '[]'::JSONB)
         FROM jsonb_array_elements_text(a.value -> 'selected') WITH ORDINALITY AS s(idx, pos)
         WHERE s.idx ~ '^[0-9]+$'
           AND jsonb_typeof(q.config -> 'options') = 'array'
           AND s.idx::INT < jsonb_array_length(q.config -> 'options')))
FROM ember_schema.form_question q
WHERE q.id = a.question_id
  AND q.question_type = 'CHOICE'
  AND jsonb_typeof(a.value -> 'selected') = 'array'
  AND jsonb_typeof(a.value -> 'selected' -> 0) = 'number';

UPDATE ember_schema.form_answer a
SET value = jsonb_set(
        a.value,
        '{order}',
        (SELECT coalesce(jsonb_agg(to_jsonb('o' || r.idx) ORDER BY r.pos), '[]'::JSONB)
         FROM jsonb_array_elements_text(a.value -> 'order') WITH ORDINALITY AS r(idx, pos)
         WHERE r.idx ~ '^[0-9]+$'
           AND jsonb_typeof(q.config -> 'options') = 'array'
           AND r.idx::INT < jsonb_array_length(q.config -> 'options')))
FROM ember_schema.form_question q
WHERE q.id = a.question_id
  AND q.question_type = 'RANKING'
  AND jsonb_typeof(a.value -> 'order') = 'array'
  AND jsonb_typeof(a.value -> 'order' -> 0) = 'number';

UPDATE ember_schema.form_answer a
SET value = jsonb_set(
        a.value,
        '{ratings}',
        (SELECT coalesce(jsonb_object_agg('o' || r.key, r.value), '{}'::JSONB)
         FROM jsonb_each(a.value -> 'ratings') AS r(key, value)
         WHERE r.key ~ '^[0-9]+$'
           AND jsonb_typeof(q.config -> 'statements') = 'array'
           AND r.key::INT < jsonb_array_length(q.config -> 'statements')))
FROM ember_schema.form_question q
WHERE q.id = a.question_id
  AND q.question_type = 'LIKERT'
  AND jsonb_typeof(a.value -> 'ratings') = 'object'
  AND EXISTS (SELECT 1 FROM jsonb_object_keys(a.value -> 'ratings') AS k(key) WHERE k.key ~ '^[0-9]+$');

COMMENT ON COLUMN ember_schema.form_question.config IS
    'Type-specific configuration as JSONB (e.g. options, scale range). Choice and ranking options and Likert statements are objects holding a stable key and a label.';

COMMENT ON COLUMN ember_schema.form_answer.value IS
    'Answer value as JSONB. Options and Likert statements are named by their key, never by position.';

-- A form is made of pages, and every question stands on one of them.
--
-- Each page says at its end which page follows: the next one, a chosen page further down, or none,
-- in which case the form is sent. Pages are named by a key of their own, like options are, so the
-- path a reader took stays readable when pages are renamed or reordered. Every existing form gets
-- one page, keyed p0, holding all of its questions, and fills exactly as before.

CREATE TABLE IF NOT EXISTS ember_schema.form_page
(
    id          SERIAL PRIMARY KEY,
    form_id     INTEGER NOT NULL REFERENCES ember_schema.form (id) ON DELETE CASCADE,
    page_key    TEXT    NOT NULL,
    position    INTEGER NOT NULL,
    title       TEXT    NOT NULL DEFAULT '',
    description TEXT    NOT NULL DEFAULT '',
    after_kind  TEXT    NOT NULL DEFAULT 'NEXT',
    after_page  TEXT,
    UNIQUE (form_id, page_key)
);

CREATE INDEX IF NOT EXISTS idx_form_page_form ON ember_schema.form_page (form_id);

COMMENT ON TABLE ember_schema.form_page IS
    'One page of a form. A form has at least one; its questions stand on its pages.';
COMMENT ON COLUMN ember_schema.form_page.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.form_page.form_id IS 'References the form.';
COMMENT ON COLUMN ember_schema.form_page.page_key IS
    'Stable key of the page within its form, named by the page that follows and by the path a response took.';
COMMENT ON COLUMN ember_schema.form_page.position IS 'Display order position of the page within its form.';
COMMENT ON COLUMN ember_schema.form_page.title IS 'Optional title shown above the page.';
COMMENT ON COLUMN ember_schema.form_page.description IS 'Optional text shown under the page title.';
COMMENT ON COLUMN ember_schema.form_page.after_kind IS
    'What follows the page: NEXT for the page below, PAGE for the page named in after_page, SUBMIT to send the form.';
COMMENT ON COLUMN ember_schema.form_page.after_page IS
    'Key of the page that follows when after_kind is PAGE, always a page further down.';

INSERT INTO ember_schema.form_page (form_id, page_key, position)
SELECT f.id, 'p0', 0
FROM ember_schema.form f
WHERE NOT EXISTS (SELECT 1 FROM ember_schema.form_page p WHERE p.form_id = f.id);

ALTER TABLE ember_schema.form_question
    ADD COLUMN IF NOT EXISTS page_id INTEGER REFERENCES ember_schema.form_page (id) ON DELETE CASCADE;

UPDATE ember_schema.form_question q
SET page_id = p.id
FROM ember_schema.form_page p
WHERE p.form_id = q.form_id
  AND p.page_key = 'p0'
  AND q.page_id IS NULL;

ALTER TABLE ember_schema.form_question
    ALTER COLUMN page_id SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_form_question_page ON ember_schema.form_question (page_id);

COMMENT ON COLUMN ember_schema.form_question.page_id IS 'References the page of its form the question stands on.';
