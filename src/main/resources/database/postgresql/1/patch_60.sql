-- Normalized notification settings: one row per (member, type) instead of wide columns.
-- user_settings keeps only email_enabled as master toggle.
-- New table user_notification_settings stores per-type app/email preferences.

CREATE TABLE IF NOT EXISTS ember_schema.user_notification_settings (
    member_id   INTEGER NOT NULL REFERENCES ember_schema.station_member(id) ON DELETE CASCADE,
    notification_type TEXT NOT NULL,
    app_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    email_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (member_id, notification_type)
);

-- Migrate old per-type columns if they exist (from notify_news, notify_new_events, notify_event_status).
-- These columns may still exist on user_settings from before this patch.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema = 'ember_schema' AND table_name = 'user_settings' AND column_name = 'notify_news') THEN
        INSERT INTO ember_schema.user_notification_settings (member_id, notification_type, app_enabled, email_enabled)
        SELECT member_id, 'NEW_NEWS', TRUE, notify_news FROM ember_schema.user_settings WHERE notify_news = TRUE
        ON CONFLICT DO NOTHING;

        INSERT INTO ember_schema.user_notification_settings (member_id, notification_type, app_enabled, email_enabled)
        SELECT member_id, 'NEW_EVENT', TRUE, notify_new_events FROM ember_schema.user_settings WHERE notify_new_events = TRUE
        ON CONFLICT DO NOTHING;

        INSERT INTO ember_schema.user_notification_settings (member_id, notification_type, app_enabled, email_enabled)
        SELECT member_id, 'EVENT_REGISTRATION_STATUS', TRUE, notify_event_status FROM ember_schema.user_settings WHERE notify_event_status = TRUE
        ON CONFLICT DO NOTHING;

        ALTER TABLE ember_schema.user_settings
            DROP COLUMN IF EXISTS notify_news,
            DROP COLUMN IF EXISTS notify_new_events,
            DROP COLUMN IF EXISTS notify_event_status;
    END IF;

    -- Also drop wide columns added by a previous version of this patch if they exist
    ALTER TABLE ember_schema.user_settings
        DROP COLUMN IF EXISTS notify_news_app,
        DROP COLUMN IF EXISTS notify_news_email,
        DROP COLUMN IF EXISTS notify_comments_app,
        DROP COLUMN IF EXISTS notify_comments_email,
        DROP COLUMN IF EXISTS notify_events_app,
        DROP COLUMN IF EXISTS notify_events_email,
        DROP COLUMN IF EXISTS notify_event_status_app,
        DROP COLUMN IF EXISTS notify_event_status_email,
        DROP COLUMN IF EXISTS notify_exchanges_app,
        DROP COLUMN IF EXISTS notify_exchanges_email,
        DROP COLUMN IF EXISTS notify_groups_app,
        DROP COLUMN IF EXISTS notify_groups_email,
        DROP COLUMN IF EXISTS notify_profile_app,
        DROP COLUMN IF EXISTS notify_profile_email,
        DROP COLUMN IF EXISTS notify_procurement_app,
        DROP COLUMN IF EXISTS notify_procurement_email;
END $$;
