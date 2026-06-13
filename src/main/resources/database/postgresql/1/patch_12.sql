-- Rename ExchangeStatus.EXCHANGED -> DONE.
-- The status is stored as TEXT in three columns; rewrite every occurrence
-- so existing rows match the new enum constant name.

UPDATE ember_schema.equipment_exchange_request
SET status = 'DONE'
WHERE status = 'EXCHANGED';

UPDATE ember_schema.equipment_exchange_log
SET old_status = 'DONE'
WHERE old_status = 'EXCHANGED';

UPDATE ember_schema.equipment_exchange_log
SET new_status = 'DONE'
WHERE new_status = 'EXCHANGED';
