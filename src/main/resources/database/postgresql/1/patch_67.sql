-- Add privacy policy and terms of service version tracking to consent records
ALTER TABLE ember_schema.gdpr_consent
    ADD COLUMN privacy_version TEXT,
    ADD COLUMN tos_version TEXT;
