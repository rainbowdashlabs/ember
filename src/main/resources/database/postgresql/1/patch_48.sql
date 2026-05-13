-- Replace plain-text message with structured JSONB data for localized notifications
ALTER TABLE ember_schema.notification ADD COLUMN data JSONB;

-- Migrate existing messages to new format
UPDATE ember_schema.notification
SET data = jsonb_build_object(
    'localeKey', 'notification.' || lower(type),
    'params', '{}'::jsonb,
    'link', CASE
        WHEN reference_id IS NOT NULL AND type = 'NEW_NEWS' THEN jsonb_build_object('route', 'news-list')
        WHEN reference_id IS NOT NULL AND type IN ('EXCHANGE_STATUS_CHANGE', 'EXCHANGE_NEW_REQUEST') THEN jsonb_build_object('route', 'inventory-exchanges')
        WHEN reference_id IS NOT NULL AND type IN ('EVENT_REGISTRATION_STATUS', 'NEW_EVENT') THEN jsonb_build_object('route', 'events')
        WHEN reference_id IS NOT NULL AND type = 'MEMBER_ADDED_TO_GROUP' THEN jsonb_build_object('route', 'members-groups')
        WHEN reference_id IS NOT NULL AND type = 'PROFILE_FIELD_CHANGED' THEN jsonb_build_object('route', 'members-changes')
        ELSE '{}'::jsonb
    END
);

ALTER TABLE ember_schema.notification ALTER COLUMN data SET NOT NULL;
ALTER TABLE ember_schema.notification DROP COLUMN message;
ALTER TABLE ember_schema.notification DROP COLUMN reference_id;
