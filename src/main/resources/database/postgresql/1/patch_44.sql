-- When a repeating appointment stops repeating.
--
-- A repeating appointment ran for ever. A course that meets eight times, a summer of Saturday duties,
-- a working group that disbands in spring: all of them had to be deleted by hand on the day they
-- ended, and until somebody remembered, they kept turning up in the calendar, in the reminders and in
-- the feed of every member.
--
-- Two ways of saying the same thing, and only ever one of them at a time: a last day, or a number of
-- times. The number is kept as it was said rather than turned into a date, so that moving the series
-- still means what it said: eight times stays eight times.

ALTER TABLE ember_schema.station_event
    ADD COLUMN repeat_until DATE,
    ADD COLUMN repeat_count INTEGER,
    ADD CONSTRAINT station_event_repeat_end_is_one_thing
        CHECK (repeat_until IS NULL OR repeat_count IS NULL),
    ADD CONSTRAINT station_event_repeat_count_is_positive
        CHECK (repeat_count IS NULL OR repeat_count > 0);

COMMENT ON COLUMN ember_schema.station_event.repeat_until IS
    'The last day a repeating appointment may take place on. Null where it repeats without an end.';

COMMENT ON COLUMN ember_schema.station_event.repeat_count IS
    'How many times a repeating appointment takes place in total, counted from its first date. Null where it repeats without an end.';
