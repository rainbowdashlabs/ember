ALTER TABLE ember_schema.kb_access_restriction
    ADD COLUMN level TEXT;

ALTER TABLE ember_schema.kb_access_restriction
    ADD CONSTRAINT kb_access_restriction_level_check
        CHECK (level IS NULL OR level IN ('NONE', 'READ', 'WRITE', 'MANAGE'));

ALTER TABLE ember_schema.kb_access_restriction
    RENAME TO kb_access_grant;

ALTER TABLE ember_schema.kb_access_grant
    RENAME CONSTRAINT kb_access_restriction_level_check TO kb_access_grant_level_check;

COMMENT ON TABLE ember_schema.kb_access_grant IS 'Who may reach a knowledge-base folder or file, and what they may do with it. A row names an audience; its level, when set, also decides whether that audience may only read, may write, or may manage.';
COMMENT ON COLUMN ember_schema.kb_access_grant.level IS 'NONE, READ, WRITE or MANAGE, or NULL for an audience row that says nothing about the level and leaves it to the station permission the member holds. NULL is what every row migrated from the previous restriction table carries, so the upgrade changes nothing.';
