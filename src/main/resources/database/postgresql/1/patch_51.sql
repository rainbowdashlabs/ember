-- Add item_source column to inventory_item for MIXED inventories
ALTER TABLE ember_schema.inventory_item ADD COLUMN IF NOT EXISTS item_source TEXT;

-- Default existing items based on their inventory type
UPDATE ember_schema.inventory_item ii
SET item_source = i.inventory_type
FROM ember_schema.inventory i
WHERE ii.inventory_id = i.id
  AND i.inventory_type IN ('INTERNAL', 'EXTERNAL');
