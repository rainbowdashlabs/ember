UPDATE inventory_check_item SET result = 'CONFIRMED' WHERE result = 'confirmed';
UPDATE inventory_check_item SET result = 'NOT_IN_POSSESSION' WHERE result = 'not_in_possession';
UPDATE inventory_check_item SET result = 'LOST' WHERE result = 'lost';
