-- Federation pairs a cluster owns.
--
-- A cluster's content lives on its home station and reaches its member stations over the federation that
-- already exists. Nothing new carries it: same instance, same tables, same shares. What is new is that some
-- pairs are not the stations' own doing, so neither side may delete them, and one kind may not even be
-- paused.
--
-- Two kinds. The home pair runs between the home station and a member station in both directions, and it is
-- how cluster content arrives; without it the station would simply not see what its cluster publishes, so it
-- can be neither paused nor deleted. The mesh pairs run between member stations, and they exist because
-- stations under one roof usually want to see each other; either side may pause one, because that is a
-- matter between them.

ALTER TABLE ember_schema.federation_partner
    ADD COLUMN IF NOT EXISTS cluster_managed BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS cluster_home    BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN ember_schema.federation_partner.cluster_managed
    IS 'TRUE when a cluster made this pair rather than the two stations. Neither side may delete it.';
COMMENT ON COLUMN ember_schema.federation_partner.cluster_home
    IS 'TRUE when this pair runs to or from a cluster home station, which carries the cluster content. It can never be paused.';

CREATE INDEX IF NOT EXISTS idx_federation_partner_cluster_managed
    ON ember_schema.federation_partner (station_id) WHERE cluster_managed;
