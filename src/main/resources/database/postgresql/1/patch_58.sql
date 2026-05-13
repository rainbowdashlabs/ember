-- Decouple former members from accounts
ALTER TABLE ember_schema.station_member ALTER COLUMN account_id DROP NOT NULL;
ALTER TABLE ember_schema.station_member ADD COLUMN IF NOT EXISTS display_name TEXT NOT NULL DEFAULT '';

-- Mark profile fields as archived (kept on archive)
ALTER TABLE ember_schema.profile_field ADD COLUMN IF NOT EXISTS keep_on_archive BOOLEAN NOT NULL DEFAULT FALSE;
