-- Knowledge Base: Presentation support
-- Adds conversion_status column for tracking async PDF conversion of presentation files
ALTER TABLE ember_schema.kb_file
    ADD COLUMN conversion_status TEXT DEFAULT NULL;
