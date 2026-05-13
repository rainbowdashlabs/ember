-- Add former member flag
ALTER TABLE ember_schema.station_member ADD COLUMN IF NOT EXISTS former BOOLEAN NOT NULL DEFAULT FALSE;
CREATE INDEX idx_station_member_former ON ember_schema.station_member (station_id, former);
