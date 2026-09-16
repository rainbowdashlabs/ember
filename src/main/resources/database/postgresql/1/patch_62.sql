ALTER TABLE ember_schema.problem_report
    ADD COLUMN screenshot_file_id INTEGER     NULL REFERENCES ember_schema.station_file (id) ON DELETE SET NULL,
    ADD COLUMN acknowledged_at    TIMESTAMPTZ NULL,
    ADD COLUMN forwarded_at       TIMESTAMPTZ NULL;

COMMENT ON COLUMN ember_schema.problem_report.screenshot_file_id IS
    'The picture of the page the report was written about, or NULL where none was given. A picture of a page can hold the data of people other than the reporter, which is why it is theirs to attach and never taken without being asked for.';

COMMENT ON COLUMN ember_schema.problem_report.acknowledged_at IS
    'When the report was marked dealt with, which is when its thirty days begin. NULL while nobody has.';

COMMENT ON COLUMN ember_schema.problem_report.forwarded_at IS
    'When the report was passed on to a beacon, or NULL where it has not been. A report waiting for somebody to look at its picture first is one with a picture and no such date.';

-- Reports already marked dealt with carry no date to count from. Stamping them now gives them their
-- thirty days from the update rather than deleting them the moment it lands, which is a deletion
-- nobody asked for arriving as a surprise.
UPDATE ember_schema.problem_report
SET acknowledged_at = now()
WHERE acknowledged = TRUE
  AND acknowledged_at IS NULL;
