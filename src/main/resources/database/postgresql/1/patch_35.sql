-- Add timezone to station
ALTER TABLE ember_schema.station
    ADD COLUMN timezone TEXT NOT NULL DEFAULT 'Europe/Berlin';
