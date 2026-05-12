-- Persistent email queue
CREATE TABLE ember_schema.email_queue
(
    id         SERIAL PRIMARY KEY,
    recipient  TEXT      NOT NULL,
    subject    TEXT      NOT NULL,
    body       TEXT      NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    status     TEXT      NOT NULL DEFAULT 'PENDING'
);

CREATE INDEX idx_email_queue_status ON ember_schema.email_queue (status);

-- Daily send counter
CREATE TABLE ember_schema.email_daily_count
(
    day   DATE    PRIMARY KEY,
    count INTEGER NOT NULL DEFAULT 0
);
