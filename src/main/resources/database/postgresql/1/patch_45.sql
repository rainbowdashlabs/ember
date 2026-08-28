-- When a spell ends because the record was wrong.
--
-- A check finds a member holding something other than what is written down. Putting that right ends
-- the spell the same way handing gear back does, and afterwards the history says only "returned",
-- which is the one thing that did not happen. Six months on nobody can tell a correction from a
-- return, and the return is the story everybody believes.

ALTER TABLE ember_schema.inventory_item_history
    ADD COLUMN corrected BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN ember_schema.inventory_item_history.corrected IS
    'True where the spell ended because a check corrected the record rather than because the member handed the item back.';
