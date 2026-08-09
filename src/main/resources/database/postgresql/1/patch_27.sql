UPDATE ember_schema.inventory_field_definition
SET config = jsonb_set(config, '{kind}', to_jsonb(field_type))
WHERE config -> 'kind' IS NULL;
