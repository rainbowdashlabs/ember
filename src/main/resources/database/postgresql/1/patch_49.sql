-- Rename size_id to old_size_id and add new_size_id for exchange requests
ALTER TABLE ember_schema.equipment_exchange_request RENAME COLUMN size_id TO old_size_id;
ALTER TABLE ember_schema.equipment_exchange_request ADD COLUMN new_size_id INTEGER REFERENCES ember_schema.inventory_size(id) ON DELETE SET NULL;

-- Copy old_size_id to new_size_id for existing requests (assume same size exchange by default)
UPDATE ember_schema.equipment_exchange_request SET new_size_id = old_size_id;

-- Add exchanged_item_id to track the new item given during exchange completion
ALTER TABLE ember_schema.equipment_exchange_request ADD COLUMN exchanged_item_id INTEGER REFERENCES ember_schema.inventory_item(id) ON DELETE SET NULL;
