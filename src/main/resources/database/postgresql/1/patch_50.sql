-- An instance that answers for others.
--
-- A beacon is an instance other instances report to: the faults they ran into, and once a day a
-- bucketed account of how much they hold. This patch carries both ends. The reporting end needs a
-- name for a station that is not the station's own, and the receiving end needs somewhere to put
-- what arrives.

-- The name a station goes by in the numbers, and nowhere else.
--
-- Metrics must not say whose they are, so nothing that already identifies a station can travel with
-- them: not the name, not the address, not the uid federation knows it by. This is a second identity
-- used for one purpose, so that two reports can be recognised as the same station and tied to
-- nothing further. It also decides the minute of the day the instance reports at, which is why it
-- has to stay secret: an identifier anybody could derive would put the sender's name on every
-- supposedly anonymous row by arrival time alone.
--
-- A restored dump carries these with it, so a copy reports as the instance it was copied from. That
-- is a documented consequence rather than a defect, and the operator documentation says so.
ALTER TABLE ember_schema.station
    ADD COLUMN metrics_uid UUID NOT NULL DEFAULT gen_random_uuid();

COMMENT ON COLUMN ember_schema.station.metrics_uid IS
    'The name this station goes by in beacon metrics and nowhere else, so counts cannot be tied back to it. Never sent beside the name, the address or the federation uid.';

CREATE UNIQUE INDEX idx_station_metrics_uid ON ember_schema.station (metrics_uid);

-- The instance's own, kept where the other small marks live.
INSERT INTO ember_schema.application_setting (key, value)
VALUES ('beacon_metrics_uid', gen_random_uuid()::TEXT)
ON CONFLICT (key) DO NOTHING;

-- ============================================================
-- The receiving end
-- ============================================================

-- Every instance this beacon has heard from.
--
-- The identifier is computed from the public key that signed the report and never taken from the
-- body, so it cannot be claimed by somebody else. The contact is whatever the operator chose to be
-- reachable by, rewritten by every report so that clearing it at the source clears it here.
CREATE TABLE ember_schema.beacon_instance
(
    instance_id  TEXT PRIMARY KEY,
    public_key   TEXT      NOT NULL,
    contact_name TEXT,
    contact_mail TEXT,
    last_version TEXT,
    first_seen   TIMESTAMP NOT NULL DEFAULT now(),
    last_seen    TIMESTAMP NOT NULL DEFAULT now()
);

COMMENT ON TABLE ember_schema.beacon_instance IS 'An instance that has reported to this beacon, identified by the fingerprint of the key it signs with.';
COMMENT ON COLUMN ember_schema.beacon_instance.instance_id IS 'Computed from the presented public key, never read from the payload.';
COMMENT ON COLUMN ember_schema.beacon_instance.contact_mail IS 'Where to write about this instance''s reports. The operator''s own address, never a member''s.';

CREATE INDEX idx_beacon_instance_last_seen ON ember_schema.beacon_instance (last_seen);

-- One row per fault, however many instances have hit it.
--
-- The fingerprint is computed by the sender from the exception chain and the frame class and method
-- names, with no line numbers: a line moves on almost every release, and a fault that splits in two
-- at a version boundary cannot answer the one question this table exists for.
CREATE TABLE ember_schema.beacon_problem
(
    id              SERIAL PRIMARY KEY,
    fingerprint     TEXT      NOT NULL UNIQUE,
    level           TEXT      NOT NULL,
    logger          TEXT,
    exception_class TEXT,
    frames          TEXT,
    first_seen      TIMESTAMP NOT NULL DEFAULT now(),
    last_seen       TIMESTAMP NOT NULL DEFAULT now(),
    acknowledged    BOOLEAN   NOT NULL DEFAULT FALSE,
    resolved_in     TEXT
);

COMMENT ON TABLE ember_schema.beacon_problem IS 'A fault as the beacon knows it, gathered from every instance that reported it.';
COMMENT ON COLUMN ember_schema.beacon_problem.fingerprint IS 'Exception chain plus frame class and method names, without line numbers, so the same fault stays one row across releases.';
COMMENT ON COLUMN ember_schema.beacon_problem.resolved_in IS 'The version this was fixed in, where somebody has said so. NULL while it still stands.';

CREATE INDEX idx_beacon_problem_last_seen ON ember_schema.beacon_problem (last_seen);

-- One row per fault per instance.
--
-- Holds what the fault row cannot: which versions have hit it and how many installations, both at
-- once. The count is the sender's own, so a report that arrives twice corrects this row rather than
-- adding to it.
CREATE TABLE ember_schema.beacon_problem_instance
(
    problem_id  INTEGER   NOT NULL REFERENCES ember_schema.beacon_problem (id) ON DELETE CASCADE,
    instance_id TEXT      NOT NULL REFERENCES ember_schema.beacon_instance (instance_id) ON DELETE CASCADE,
    version     TEXT,
    occurrences INTEGER   NOT NULL DEFAULT 1,
    first_seen  TIMESTAMP NOT NULL DEFAULT now(),
    last_seen   TIMESTAMP NOT NULL DEFAULT now(),
    PRIMARY KEY (problem_id, instance_id)
);

COMMENT ON TABLE ember_schema.beacon_problem_instance IS 'How one instance has met one fault: the version it ran, how often, and when.';
COMMENT ON COLUMN ember_schema.beacon_problem_instance.occurrences IS 'What the sender last said its own count was, not a running total kept here.';

-- What somebody wrote, forwarded.
--
-- These do not group: each is a sentence in somebody's own words. The member who wrote it is not
-- named, and the page keeps no query string.
CREATE TABLE ember_schema.beacon_report
(
    id          SERIAL PRIMARY KEY,
    instance_id TEXT      NOT NULL REFERENCES ember_schema.beacon_instance (instance_id) ON DELETE CASCADE,
    message     TEXT      NOT NULL,
    page        TEXT,
    version     TEXT,
    reported_at TIMESTAMP NOT NULL,
    received_at TIMESTAMP NOT NULL DEFAULT now(),
    acknowledged BOOLEAN  NOT NULL DEFAULT FALSE
);

COMMENT ON TABLE ember_schema.beacon_report IS 'A problem report written by a person, forwarded without naming them.';

CREATE INDEX idx_beacon_report_received ON ember_schema.beacon_report (received_at DESC);

-- A day of numbers from one subject.
--
-- Deliberately unreachable from beacon_instance: no key here leads there, because the whole point of
-- the metrics uid is that these rows say how much without saying whose. Counts arrive bucketed and
-- are stored as they arrive.
CREATE TABLE ember_schema.beacon_metrics
(
    metrics_uid  UUID    NOT NULL,
    subject      TEXT    NOT NULL,
    day          DATE    NOT NULL,
    members      TEXT,
    accounts     TEXT,
    stations     TEXT,
    inventory    TEXT,
    version      TEXT,
    PRIMARY KEY (metrics_uid, day)
);

COMMENT ON TABLE ember_schema.beacon_metrics IS 'One subject''s bucketed counts for one day. Carries no key to the instance that sent it, by design.';
COMMENT ON COLUMN ember_schema.beacon_metrics.subject IS 'STATION or INSTANCE, which decides which of the counts are filled.';
COMMENT ON COLUMN ember_schema.beacon_metrics.members IS 'A bucket such as <10 or 10-50, never an exact number: an exact trajectory identifies a station within weeks.';

CREATE INDEX idx_beacon_metrics_day ON ember_schema.beacon_metrics (day);

-- Deliveries already seen.
--
-- A signature over the body alone leaves a captured report replayable, and replayable straight into
-- the number this whole feature exists to show: how many installations hit a fault. The nonce is
-- what makes one delivery arrive once. Rows older than the drift window cannot be replayed anyway
-- and are swept.
CREATE TABLE ember_schema.beacon_nonce
(
    instance_id TEXT      NOT NULL,
    nonce       TEXT      NOT NULL,
    issued_at   TIMESTAMP NOT NULL,
    PRIMARY KEY (instance_id, nonce)
);

COMMENT ON TABLE ember_schema.beacon_nonce IS 'One row per accepted delivery, so the same signed payload cannot be replayed.';

CREATE INDEX idx_beacon_nonce_issued ON ember_schema.beacon_nonce (issued_at);
