-- Add station_id to email queue for per-station provider routing
ALTER TABLE ember_schema.email_queue ADD COLUMN IF NOT EXISTS station_id INTEGER REFERENCES ember_schema.station(id) ON DELETE SET NULL;
