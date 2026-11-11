-- The problem log kept in the database, so a fault outlives a restart. One row per problem, found
-- again by its fingerprint, with the last few occurrences beside it: when and on which thread, which
-- is what the log lines around a problem are looked up by. On a beacon, the log lines an instance
-- sent with a problem are kept beside that problem.

CREATE TABLE ember_schema.problem_log
(
    id                BIGSERIAL PRIMARY KEY,
    fingerprint       TEXT      NOT NULL UNIQUE,
    level             TEXT      NOT NULL CHECK (level IN ('WARN', 'ERROR', 'REFUSAL')),
    logger            TEXT      NOT NULL,
    message           TEXT      NOT NULL,
    exception_class   TEXT      NULL,
    exception_message TEXT      NULL,
    stacktrace        TEXT      NULL,
    distinct_messages TEXT[]    NOT NULL DEFAULT '{}',
    count             BIGINT    NOT NULL DEFAULT 1,
    first_seen        TIMESTAMP NOT NULL,
    last_seen         TIMESTAMP NOT NULL,
    state             TEXT      NOT NULL DEFAULT 'OPEN' CHECK (state IN ('OPEN', 'RESOLVED', 'MUTED')),
    state_changed_at  TIMESTAMP NULL,
    state_changed_by  INT       NULL REFERENCES ember_schema.account (id) ON DELETE SET NULL,
    reopened_count    INT       NOT NULL DEFAULT 0
);

CREATE INDEX idx_problem_log_last_seen ON ember_schema.problem_log (last_seen);
CREATE INDEX idx_problem_log_state ON ember_schema.problem_log (state, level);
CREATE INDEX idx_problem_log_state_changed_by
    ON ember_schema.problem_log (state_changed_by)
    WHERE state_changed_by IS NOT NULL;

COMMENT ON TABLE ember_schema.problem_log IS
    'Warnings, errors and refusals the application logged, one row per problem, kept across restarts until the retention removes them.';
COMMENT ON COLUMN ember_schema.problem_log.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.problem_log.fingerprint IS
    'What makes two log events the same problem: logger, exception class and first frame, or logger, level and message without an exception, or route and refusal for a refusal.';
COMMENT ON COLUMN ember_schema.problem_log.level IS 'WARN, ERROR, or REFUSAL for a request the application refused.';
COMMENT ON COLUMN ember_schema.problem_log.logger IS 'Which logger reported the problem.';
COMMENT ON COLUMN ember_schema.problem_log.message IS 'The message of the first occurrence. May contain request paths and identifiers.';
COMMENT ON COLUMN ember_schema.problem_log.exception_class IS 'The class of the exception, NULL for a problem logged without one.';
COMMENT ON COLUMN ember_schema.problem_log.exception_message IS 'The message of the latest exception, NULL without one.';
COMMENT ON COLUMN ember_schema.problem_log.stacktrace IS 'The stacktrace of the latest occurrence, causes included. NULL without an exception.';
COMMENT ON COLUMN ember_schema.problem_log.distinct_messages IS
    'Up to 20 different messages the problem was logged with, oldest first. May contain request paths and identifiers.';
COMMENT ON COLUMN ember_schema.problem_log.count IS 'How often the problem happened.';
COMMENT ON COLUMN ember_schema.problem_log.first_seen IS 'When it happened first.';
COMMENT ON COLUMN ember_schema.problem_log.last_seen IS 'When it happened last; the retention counts from here.';
COMMENT ON COLUMN ember_schema.problem_log.state IS
    'OPEN: waiting for an administrator. RESOLVED: taken care of; happening again opens it. MUTED: known noise; happening again only counts.';
COMMENT ON COLUMN ember_schema.problem_log.state_changed_at IS 'When the state was last changed by hand or by happening again.';
COMMENT ON COLUMN ember_schema.problem_log.state_changed_by IS
    'The administrator who last changed the state. NULL when it changed by happening again, and once that account is deleted.';
COMMENT ON COLUMN ember_schema.problem_log.reopened_count IS 'How often the problem came back after it was resolved.';

CREATE TABLE ember_schema.problem_occurrence
(
    id          BIGSERIAL PRIMARY KEY,
    problem_id  BIGINT    NOT NULL REFERENCES ember_schema.problem_log (id) ON DELETE CASCADE,
    occurred_at TIMESTAMP NOT NULL,
    thread      TEXT      NOT NULL
);

CREATE INDEX idx_problem_occurrence_problem ON ember_schema.problem_occurrence (problem_id, occurred_at DESC);

COMMENT ON TABLE ember_schema.problem_occurrence IS
    'The last five occurrences of each problem: when and on which thread. Holds no message text; the log lines around an occurrence are looked up by it.';
COMMENT ON COLUMN ember_schema.problem_occurrence.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.problem_occurrence.problem_id IS 'The problem. Deleting it deletes its occurrences.';
COMMENT ON COLUMN ember_schema.problem_occurrence.occurred_at IS 'When it happened.';
COMMENT ON COLUMN ember_schema.problem_occurrence.thread IS 'The thread that logged it.';

CREATE INDEX idx_application_log_thread ON ember_schema.application_log (thread, logged_at);

CREATE TABLE ember_schema.beacon_problem_context
(
    id          BIGSERIAL PRIMARY KEY,
    problem_id  INTEGER   NOT NULL REFERENCES ember_schema.beacon_problem (id) ON DELETE CASCADE,
    instance_id TEXT      NOT NULL REFERENCES ember_schema.beacon_instance (instance_id) ON DELETE CASCADE,
    occurred_at TIMESTAMP NOT NULL,
    received_at TIMESTAMP NOT NULL DEFAULT now(),
    lines       JSONB     NOT NULL
);

CREATE INDEX idx_beacon_problem_context_problem
    ON ember_schema.beacon_problem_context (problem_id, instance_id, received_at DESC);
CREATE INDEX idx_beacon_problem_context_instance ON ember_schema.beacon_problem_context (instance_id);

COMMENT ON TABLE ember_schema.beacon_problem_context IS
    'On a beacon: the log lines an instance sent with a problem, the last five per problem and instance. The sender replaced mail addresses, identifiers and names before sending.';
COMMENT ON COLUMN ember_schema.beacon_problem_context.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.beacon_problem_context.problem_id IS 'The problem the lines belong to. Deleting it deletes them.';
COMMENT ON COLUMN ember_schema.beacon_problem_context.instance_id IS 'The instance that sent them. Forgetting it deletes them.';
COMMENT ON COLUMN ember_schema.beacon_problem_context.occurred_at IS 'When the occurrence the lines surround happened, on the sending instance.';
COMMENT ON COLUMN ember_schema.beacon_problem_context.received_at IS 'When the beacon received them.';
COMMENT ON COLUMN ember_schema.beacon_problem_context.lines IS
    'The lines, oldest first: time, level, logger, thread and the redacted message of each.';
