-- How a standing station joins a cluster.
--
-- A cluster never absorbs a station. Either the cluster created the station itself, in which case it belongs
-- from the first moment, or the station's owner asked to join and somebody at the cluster said yes. This
-- table is that second path, and only the station's owner may open one.
--
-- It is deliberately not the existing station_application table. That one is a stranger asking the instance
-- to found a station for them, and the two share neither their states nor their parties: this one can be
-- withdrawn by the applicant, that one can be waiting on an email nobody has clicked.

CREATE TABLE IF NOT EXISTS ember_schema.cluster_application
(
    id           SERIAL PRIMARY KEY,
    cluster_id   INTEGER                  NOT NULL REFERENCES ember_schema.cluster (id) ON DELETE CASCADE,
    station_id   INTEGER                  NOT NULL REFERENCES ember_schema.station (id) ON DELETE CASCADE,
    requested_by INTEGER REFERENCES ember_schema.station_member (id) ON DELETE SET NULL,
    requested_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    status       TEXT                     NOT NULL DEFAULT 'PENDING',
    deny_reason  TEXT,
    resolved_at  TIMESTAMP WITH TIME ZONE,
    resolved_by  INTEGER REFERENCES ember_schema.cluster_member (id) ON DELETE SET NULL,
    UNIQUE (cluster_id, station_id)
);

COMMENT ON TABLE ember_schema.cluster_application
    IS 'A station owner asking to join a cluster. One row per station and cluster, whatever became of it.';
COMMENT ON COLUMN ember_schema.cluster_application.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.cluster_application.cluster_id IS 'The cluster being asked.';
COMMENT ON COLUMN ember_schema.cluster_application.station_id IS 'The station asking to join.';
COMMENT ON COLUMN ember_schema.cluster_application.requested_by
    IS 'The station member who asked, always the station owner at the time. Survives them leaving.';
COMMENT ON COLUMN ember_schema.cluster_application.requested_at IS 'When the application was opened.';
COMMENT ON COLUMN ember_schema.cluster_application.status
    IS 'PENDING while it waits, APPROVED, DENIED, or WITHDRAWN when the station took it back.';
COMMENT ON COLUMN ember_schema.cluster_application.deny_reason
    IS 'What the cluster said when it refused, shown to the station owner.';
COMMENT ON COLUMN ember_schema.cluster_application.resolved_at IS 'When it stopped being pending.';
COMMENT ON COLUMN ember_schema.cluster_application.resolved_by
    IS 'The cluster member who decided. Survives them leaving the cluster.';

CREATE INDEX IF NOT EXISTS idx_cluster_application_cluster ON ember_schema.cluster_application (cluster_id, status);
CREATE INDEX IF NOT EXISTS idx_cluster_application_station ON ember_schema.cluster_application (station_id);

-- A notification can now be addressed to a cluster member.
--
-- Until now every notification named a station member, because every recipient was one. A cluster member is
-- not: the people who run a cluster hold no membership in any of its stations, and inventing one for them
-- would put them in member lists where they do not belong. So the row names one of the two, never both and
-- never neither, and the queries that read a station member's feed keep working untouched because a cluster
-- row can never match them.

ALTER TABLE ember_schema.notification
    ALTER COLUMN member_id DROP NOT NULL;

ALTER TABLE ember_schema.notification
    ADD COLUMN IF NOT EXISTS cluster_member_id INTEGER REFERENCES ember_schema.cluster_member (id) ON DELETE CASCADE;

COMMENT ON COLUMN ember_schema.notification.member_id
    IS 'The station member this is for, or null when it is addressed to a cluster member instead.';
COMMENT ON COLUMN ember_schema.notification.cluster_member_id
    IS 'The cluster member this is for, or null when it is addressed to a station member instead.';

ALTER TABLE ember_schema.notification
    DROP CONSTRAINT IF EXISTS chk_notification_recipient;
ALTER TABLE ember_schema.notification
    ADD CONSTRAINT chk_notification_recipient CHECK (num_nonnulls(member_id, cluster_member_id) = 1);

CREATE INDEX IF NOT EXISTS idx_notification_cluster_member
    ON ember_schema.notification (cluster_member_id) WHERE cluster_member_id IS NOT NULL;
