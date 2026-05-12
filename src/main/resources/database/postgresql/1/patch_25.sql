ALTER TABLE ember_schema.inventory_check_item
    ALTER COLUMN item_id DROP NOT NULL;
ALTER TABLE ember_schema.inventory_check_item
    ADD COLUMN inventory_id INTEGER REFERENCES ember_schema.inventory (id) ON DELETE CASCADE;
ALTER TABLE ember_schema.inventory_check_item
    DROP CONSTRAINT inventory_check_item_check_id_item_id_key;
ALTER TABLE ember_schema.inventory_check_item
    ADD CONSTRAINT inventory_check_item_check_id_item_id_key UNIQUE NULLS NOT DISTINCT (check_id, item_id, inventory_id);
