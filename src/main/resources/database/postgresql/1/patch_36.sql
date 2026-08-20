-- Ages a waiting list keeps, and the field it reads them from.
--
-- The field itself needs no column: waiting_list_field.field_type is text, so a date field becomes
-- the birth date by being called BIRTH_DATE, and the answers already given stay exactly where they
-- are. Turning an ordinary date field into the birth date therefore loses nothing.

ALTER TABLE ember_schema.waiting_list
    ADD COLUMN IF NOT EXISTS min_age_register INTEGER,
    ADD COLUMN IF NOT EXISTS min_age_join     INTEGER;

COMMENT ON COLUMN ember_schema.waiting_list.min_age_register
    IS 'How old somebody must be to put themselves on the list at all; null for no limit.';
COMMENT ON COLUMN ember_schema.waiting_list.min_age_join
    IS 'How old somebody must be to join the station from the list. Entries below it are marked as waiting for their age rather than for their turn; null for no limit.';
