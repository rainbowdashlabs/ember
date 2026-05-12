CREATE TABLE ember_schema.member_absence
(
    id           SERIAL    PRIMARY KEY,
    member_id    INTEGER   NOT NULL REFERENCES ember_schema.station_member (id) ON DELETE CASCADE,
    absent_until DATE      NOT NULL,
    reason       TEXT,
    created_at   TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_member_absence_member ON ember_schema.member_absence (member_id);
CREATE INDEX idx_member_absence_until ON ember_schema.member_absence (absent_until);

ALTER TABLE ember_schema.attendance_entry ADD COLUMN status TEXT NOT NULL DEFAULT 'present';
