-- Whether a cluster keeps its gear in Ember at all.
--
-- A cluster that runs here but does not use the inventory has nobody who can acknowledge anything about an
-- item: no store to post from, no queue to answer, no person whose job it is to confirm that a jacket came
-- back. Its stations then behave exactly as if there were no cluster above them, and their gear walks the
-- station's own flows, which carry no owner steps for precisely that reason.
--
-- The alternative would be a chain that stops on a step nobody will ever press, and a station left staring
-- at a movement waiting on a party that does not exist. Better to ask once.

ALTER TABLE ember_schema.cluster
    ADD COLUMN IF NOT EXISTS uses_inventory BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN ember_schema.cluster.uses_inventory
    IS 'TRUE when the cluster keeps its gear here, which is what lets its own steps appear in a movement.';
