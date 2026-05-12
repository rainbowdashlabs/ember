UPDATE ember_schema.inventory_check_item SET result = 'CONFIRMED' WHERE result = 'confirmed';
UPDATE ember_schema.inventory_check_item SET result = 'NOT_IN_POSSESSION' WHERE result = 'not_in_possession';
UPDATE ember_schema.inventory_check_item SET result = 'LOST' WHERE result = 'lost';
