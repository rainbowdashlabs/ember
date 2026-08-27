-- Who an event template says its appointments are for.
--
-- The table has held one kind of audience since it was written: the type a member is. An
-- appointment can be addressed to a group, to everybody carrying a tag or to named members as well,
-- and a template that fixes the title, the questions and the attendance sheet but can only say
-- "everybody of this type" makes whoever applies it pick the group again on every date of the year.
--
-- Afterwards the table is shaped exactly like event_restriction and the rest of them, which is what
-- lets the shared restriction machinery read it: one row per named audience, exactly one of the four
-- columns set. The primary key over (template_id, user_type) has to go with that, because three of
-- the four columns are now allowed to be the one that is filled in.
ALTER TABLE ember_schema.event_template_restriction
    DROP CONSTRAINT IF EXISTS event_template_restriction_pkey;

ALTER TABLE ember_schema.event_template_restriction
    ADD COLUMN id SERIAL PRIMARY KEY;

ALTER TABLE ember_schema.event_template_restriction
    ALTER COLUMN user_type DROP NOT NULL;

ALTER TABLE ember_schema.event_template_restriction
    ADD COLUMN group_id INT REFERENCES ember_schema.member_group (id) ON DELETE CASCADE,
    ADD COLUMN tag_id INT REFERENCES ember_schema.user_tag (id) ON DELETE CASCADE,
    ADD COLUMN member_id INT REFERENCES ember_schema.station_member (id) ON DELETE CASCADE;

ALTER TABLE ember_schema.event_template_restriction
    ADD CONSTRAINT event_template_restriction_check
        CHECK (num_nonnulls(user_type, group_id, tag_id, member_id) = 1);

CREATE INDEX IF NOT EXISTS idx_event_template_restriction_template
    ON ember_schema.event_template_restriction (template_id);
