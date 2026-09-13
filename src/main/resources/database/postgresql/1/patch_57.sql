-- When a movement last moved, beside when it started.
--
-- A queue is read by what has gone quiet: a swap raised this morning and one raised in March both say
-- only their starting date, and the one that has been waiting three weeks for somebody to press a step
-- looks exactly like the one that moved an hour ago. The starting date cannot say that.
--
-- Existing rows take the time of the last thing that happened to them, which the log already records:
-- a step walked, forced or corrected, a refusal, a call-off. A movement nothing has ever happened to
-- takes the time it was started.

ALTER TABLE ember_schema.item_movement
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

COMMENT ON COLUMN ember_schema.item_movement.updated_at IS
    'When this movement last moved: a step acknowledged, forced or corrected, a refusal, a call-off or a '
    'move onto another chain. Set to the starting time for one nothing has happened to yet.';

UPDATE ember_schema.item_movement m
   SET updated_at = COALESCE(
           (SELECT MAX(l.changed_at) FROM ember_schema.item_movement_log l WHERE l.movement_id = m.id),
           m.created_at);
