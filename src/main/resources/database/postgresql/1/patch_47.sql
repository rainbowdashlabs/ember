DROP TABLE IF EXISTS news_acknowledgement;

CREATE TABLE notification (
    id SERIAL PRIMARY KEY,
    member_id INTEGER NOT NULL REFERENCES station_member(id) ON DELETE CASCADE,
    type TEXT NOT NULL,
    reference_id INTEGER,
    message TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    acknowledged_at TIMESTAMP
);

CREATE INDEX idx_notification_member ON notification(member_id);
CREATE INDEX idx_notification_unacknowledged ON notification(member_id) WHERE acknowledged_at IS NULL;
