-- A saved report filter holds what the filter on screen offers: several user types and several groups.
--
-- Until now a preset held one of each, so what came back was never what was saved. Both lists are read
-- whole, written whole and mean nothing in pieces, the same reasoning the member table preset keeps its
-- columns under, and arrays keep the values typed.
--
-- A group no longer carries a foreign key. Deleting a group used to delete every preset that mentioned
-- it; losing one group out of three is not a reason to lose the preset, so the identifier stays and
-- whoever reads it ignores what it cannot name.

ALTER TABLE ember_schema.attendance_report_preset
    ADD COLUMN user_types TEXT[]    NOT NULL DEFAULT '{}',
    ADD COLUMN group_ids  INTEGER[] NOT NULL DEFAULT '{}';

UPDATE ember_schema.attendance_report_preset
SET user_types = ARRAY(SELECT DISTINCT trim(user_type)
                       FROM unnest(string_to_array(role_name, ',')) AS user_type
                       WHERE trim(user_type) IN ('TRIAL', 'MEMBER', 'GUARDIAN', 'TEAM', 'MANAGER')),
    group_ids  = CASE WHEN group_id IS NULL THEN '{}' ELSE ARRAY [group_id] END;

ALTER TABLE ember_schema.attendance_report_preset
    DROP COLUMN role_name,
    DROP COLUMN group_id;

COMMENT ON COLUMN ember_schema.attendance_report_preset.user_types IS
    'Every user type the saved filter selects, by name. Empty when the filter selects none.';
COMMENT ON COLUMN ember_schema.attendance_report_preset.group_ids IS
    'Every group the saved filter selects. Not a foreign key, so deleting a group keeps the preset; an identifier that no longer resolves is ignored when the preset is shown or applied.';
