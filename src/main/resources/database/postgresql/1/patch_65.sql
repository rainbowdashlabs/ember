-- Profile pictures stored on account level
ALTER TABLE ember_schema.account ADD COLUMN avatar BYTEA;
ALTER TABLE ember_schema.account ADD COLUMN avatar_content_type TEXT;
