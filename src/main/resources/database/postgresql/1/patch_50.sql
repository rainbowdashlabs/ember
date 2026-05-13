-- Normalize inventory_type to uppercase enum names
UPDATE ember_schema.inventory SET inventory_type = UPPER(inventory_type);
