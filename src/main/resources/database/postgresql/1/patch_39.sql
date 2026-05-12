-- Add locale to station for language-specific formatting
ALTER TABLE ember_schema.station
    ADD COLUMN locale TEXT NOT NULL DEFAULT 'de-DE';
