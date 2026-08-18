-- The application log, so it can be read and searched from inside the application.
--
-- Until now the log went to a file that grew without bound and to the console, and neither could be
-- looked at without reaching the machine. In the database the search is an index rather than a walk
-- across several files, and the retention is a delete rather than a hope.
--
-- What goes here is chosen by the operator: the console keeps everything regardless, because the
-- failure this table cannot cover is the database being the thing that broke.
CREATE TABLE ember_schema.application_log
(
    id        BIGSERIAL PRIMARY KEY,
    logged_at TIMESTAMP NOT NULL,
    level     TEXT      NOT NULL,
    logger    TEXT      NOT NULL,
    thread    TEXT      NOT NULL DEFAULT '',
    message   TEXT      NOT NULL,
    throwable TEXT
);

COMMENT ON TABLE ember_schema.application_log
    IS 'Application log lines kept for reading and searching from the administration area.';
COMMENT ON COLUMN ember_schema.application_log.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.application_log.logged_at IS 'When the line was logged.';
COMMENT ON COLUMN ember_schema.application_log.level IS 'Severity: TRACE, DEBUG, INFO, WARN or ERROR.';
COMMENT ON COLUMN ember_schema.application_log.logger IS 'Which logger emitted the line.';
COMMENT ON COLUMN ember_schema.application_log.thread IS 'The thread the line was logged from.';
COMMENT ON COLUMN ember_schema.application_log.message IS 'The line itself, already formatted.';
COMMENT ON COLUMN ember_schema.application_log.throwable IS 'The stack trace, when the line carried one.';

-- Reading is newest first and pages by id, so the level index has to be ordered by id as well.
-- Ordering it by logged_at instead looks right and is not: the planner cannot use it for the sort,
-- so it collects every matching row and sorts them. On a table where errors are rare, which is the
-- case worth optimising for, that is the difference between reading forty rows and forty thousand.
CREATE INDEX idx_application_log_level_id ON ember_schema.application_log (level, id DESC);

-- Retention deletes by age, which is the one thing that does ask about the timestamp.
CREATE INDEX idx_application_log_time ON ember_schema.application_log (logged_at);
