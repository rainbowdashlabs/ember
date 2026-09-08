-- What an attendance sheet is worth, apart from how long it ran.
--
-- A sheet stands in for paid hours in a station that pays them, and the clock is not always the
-- number that is owed: a weekend that runs from Friday evening to Sunday afternoon is not 46 hours
-- of work, and an evening that overruns is not paid to the minute. The times stay what they were,
-- because they are what happened, and this column says what a whole presence at the sheet counts as
-- when the report adds it up. Somebody who was there for part of it counts their share of it.

ALTER TABLE ember_schema.attendance_session
    ADD COLUMN counted_minutes INTEGER;

COMMENT ON COLUMN ember_schema.attendance_session.counted_minutes IS
    'What a whole presence at this sheet is worth in minutes when hours are added up, whatever the sheet''s own times say. NULL means the times decide, which is the ordinary case. Somebody present for part of the sheet counts their share of this number.';
