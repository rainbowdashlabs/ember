ALTER TABLE ember_schema.notification
    ADD COLUMN IF NOT EXISTS emailed_at TIMESTAMPTZ;
