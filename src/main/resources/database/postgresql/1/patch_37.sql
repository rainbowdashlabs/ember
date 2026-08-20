-- Who owns an inventory item.
--
-- item_source said INTERNAL or EXTERNAL and was documented as "owned by the organization" against
-- "owned by the member". The second half was never true: members do not own tracked gear. What
-- EXTERNAL has always meant in practice is "owned by the body above this station", the municipality,
-- the district association or the umbrella organisation. owner_kind says that outright, and
-- owner_cluster_id names the body when it runs on this instance and is null when it does not.
--
-- This is a reinterpretation, not a data move. Every INTERNAL row becomes STATION, every EXTERNAL
-- row becomes CLUSTER with no cluster named, which is exactly what those rows have always meant.
--
-- owner_cluster_id carries no foreign key yet because the cluster table does not exist. The
-- reference is added by the patch that creates it.

ALTER TABLE ember_schema.inventory_item
    ADD COLUMN IF NOT EXISTS owner_kind       TEXT NOT NULL DEFAULT 'STATION',
    ADD COLUMN IF NOT EXISTS owner_cluster_id INTEGER;

UPDATE ember_schema.inventory_item
SET owner_kind = CASE WHEN item_source = 'EXTERNAL' THEN 'CLUSTER' ELSE 'STATION' END;

ALTER TABLE ember_schema.inventory_item
    DROP CONSTRAINT IF EXISTS chk_inventory_item_owner;

ALTER TABLE ember_schema.inventory_item
    ADD CONSTRAINT chk_inventory_item_owner
        CHECK (owner_kind = 'CLUSTER' OR owner_cluster_id IS NULL);

COMMENT ON COLUMN ember_schema.inventory_item.owner_kind
    IS 'Who owns the item: STATION for the station running its inventory, CLUSTER for the one body above that station.';
COMMENT ON COLUMN ember_schema.inventory_item.owner_cluster_id
    IS 'The owning body when it runs on this instance, null when it owns the item without using Ember. Only ever set for CLUSTER.';

ALTER TABLE ember_schema.inventory_item
    DROP COLUMN IF EXISTS item_source;

-- The second ownership flag. It lived in the metadata object, was never set by a user, was never
-- read by the frontend, and contradicted item_source wherever both had an opinion.
UPDATE ember_schema.inventory_item
SET metadata = metadata - 'owned'
WHERE jsonb_exists(metadata, 'owned');
