-- Add provider display name and URL to station mail config
ALTER TABLE ember_schema.station_mail_config ADD COLUMN IF NOT EXISTS provider_name TEXT NOT NULL DEFAULT '';
ALTER TABLE ember_schema.station_mail_config ADD COLUMN IF NOT EXISTS provider_url TEXT NOT NULL DEFAULT '';

-- Add email_enabled master toggle to user settings (default OFF)
ALTER TABLE ember_schema.user_settings ADD COLUMN IF NOT EXISTS email_enabled BOOLEAN NOT NULL DEFAULT FALSE;

-- Disable email for all existing users (explicit opt-in required)
UPDATE ember_schema.user_settings SET email_enabled = FALSE;

-- Change defaults for notification toggles to FALSE
ALTER TABLE ember_schema.user_settings ALTER COLUMN notify_news SET DEFAULT FALSE;
ALTER TABLE ember_schema.user_settings ALTER COLUMN notify_new_events SET DEFAULT FALSE;
ALTER TABLE ember_schema.user_settings ALTER COLUMN notify_event_status SET DEFAULT FALSE;
