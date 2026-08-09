ALTER TABLE ember_schema.federation_partner
    DROP COLUMN federation_version;

ALTER TABLE ember_schema.federation_partner
    ADD COLUMN federation_contract JSONB;

COMMENT ON COLUMN ember_schema.federation_partner.federation_contract IS 'Last contract vector the partner presented: core hash plus one hash per feature surface. NULL until the first version exchange; treated as incompatible while unknown.';
