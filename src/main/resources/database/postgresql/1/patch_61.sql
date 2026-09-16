-- What a beacon was told about a fault, and about a report, was not enough to act on.
--
-- A fault arrived as a logger name, a level and a count. For a warning logged without an exception
-- there is no class and no stacktrace either, so several different failures logged by one class
-- arrived as one row that named nothing. A report arrived as somebody's sentence with no sight of
-- the screen it was written about.
--
-- Nothing here names a person: who wrote a report stays at the station it was written in, as it
-- always has.

ALTER TABLE ember_schema.beacon_problem
    ADD COLUMN IF NOT EXISTS message TEXT;

COMMENT ON COLUMN ember_schema.beacon_problem.message
    IS 'What the fault was logged with, mail addresses removed. The wordings of one fault are separated by newlines.';

ALTER TABLE ember_schema.beacon_report
    ADD COLUMN IF NOT EXISTS browser TEXT,
    ADD COLUMN IF NOT EXISTS screen_size TEXT,
    ADD COLUMN IF NOT EXISTS roles TEXT,
    ADD COLUMN IF NOT EXISTS recent_requests TEXT;

COMMENT ON COLUMN ember_schema.beacon_report.browser
    IS 'What the reporter was reading the screen in.';
COMMENT ON COLUMN ember_schema.beacon_report.screen_size
    IS 'How large their window was, which is half of what a layout fault needs.';
COMMENT ON COLUMN ember_schema.beacon_report.roles
    IS 'What the reporter was allowed to do, so a fault only some people meet is told from one everybody meets. Rights, never a name.';
COMMENT ON COLUMN ember_schema.beacon_report.recent_requests
    IS 'The calls the screen made before the report was written, each with its query string removed.';
