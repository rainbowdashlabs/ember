DROP TABLE IF EXISTS ember_schema.news_acknowledgement;

CREATE TABLE ember_schema.notification (
    id SERIAL PRIMARY KEY,
    member_id INTEGER NOT NULL REFERENCES ember_schema.station_member(id) ON DELETE CASCADE,
    type TEXT NOT NULL,
    reference_id INTEGER,
    message TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    acknowledged_at TIMESTAMP
);

CREATE INDEX idx_notification_member ON ember_schema.notification(member_id);
CREATE INDEX idx_notification_unacknowledged ON ember_schema.notification(member_id) WHERE acknowledged_at IS NULL;
