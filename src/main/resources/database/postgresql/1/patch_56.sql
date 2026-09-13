-- Every movement of gear is a movement now, and only one of the four kinds was ever an exchange.
--
-- The queue holds issues, returns, swaps and requests, and it always did: the exchange list showed every
-- one of them and called them all exchanges. The permission that opens it, and the two notification types
-- it sends, said the same thing and were as wrong.
--
-- The permission rows are the lookup table, so a grant keeps its row and nobody loses anything. The
-- notification rows are read back through an enum, so a row still carrying the old word would refuse to
-- load at all: those are rewritten, and so is the page a notification links to.

UPDATE ember_schema.station_permission
   SET name = 'INVENTORY_MOVEMENTS'
 WHERE name = 'INVENTORY_EXCHANGE';

UPDATE ember_schema.cluster_permission
   SET name = 'CLUSTER_INVENTORY_MOVEMENTS'
 WHERE name = 'CLUSTER_INVENTORY_EXCHANGE';

UPDATE ember_schema.user_notification_settings
   SET notification_type = 'MOVEMENT_RAISED'
 WHERE notification_type = 'EXCHANGE_NEW_REQUEST';

UPDATE ember_schema.user_notification_settings
   SET notification_type = 'MOVEMENT_ADVANCED'
 WHERE notification_type = 'EXCHANGE_STATUS_CHANGE';

UPDATE ember_schema.notification
   SET type = 'MOVEMENT_RAISED'
 WHERE type = 'EXCHANGE_NEW_REQUEST';

UPDATE ember_schema.notification
   SET type = 'MOVEMENT_ADVANCED'
 WHERE type = 'EXCHANGE_STATUS_CHANGE';

-- The link a notification carries names a page rather than a path, and that page has moved.
UPDATE ember_schema.notification
   SET data = jsonb_set(data, '{route}', '"inventory-movements"')
 WHERE data ->> 'route' = 'inventory-exchanges';
