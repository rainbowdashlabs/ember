-- What a cluster decides on behalf of its stations.
--
-- Three separate things, and the difference between them is the point. A denied module is a hard no: the
-- station cannot turn it on and does not get to argue. A look-and-feel setting is a starting point unless the
-- cluster marks it locked, so the usual case is that a station is handed something sensible and may still
-- change it. Storage is neither: the cluster is given a pool by the instance and hands portions of it out,
-- which is arithmetic rather than a rule.
--
-- Denying a module never deletes what the station already put in it. The module simply stops being
-- reachable, and everything reappears intact if the denial is lifted or the station released.

CREATE TABLE IF NOT EXISTS ember_schema.cluster_denied_module
(
    cluster_id INTEGER NOT NULL REFERENCES ember_schema.cluster (id) ON DELETE CASCADE,
    module     TEXT    NOT NULL,
    PRIMARY KEY (cluster_id, module)
);

COMMENT ON TABLE ember_schema.cluster_denied_module
    IS 'Modules a cluster switches off for all of its stations. A station cannot turn a denied module back on.';
COMMENT ON COLUMN ember_schema.cluster_denied_module.cluster_id IS 'The cluster doing the denying.';
COMMENT ON COLUMN ember_schema.cluster_denied_module.module
    IS 'The module name, matching the values a station uses for its own switched-off modules.';

-- The look a cluster hands its stations. The four locked flags already sit on the cluster row.

ALTER TABLE ember_schema.cluster
    ADD COLUMN IF NOT EXISTS default_theme       TEXT,
    ADD COLUMN IF NOT EXISTS custom_theme_colors TEXT,
    ADD COLUMN IF NOT EXISTS default_feel        TEXT;

COMMENT ON COLUMN ember_schema.cluster.default_theme
    IS 'The colour theme the cluster hands its stations, or null when it does not care.';
COMMENT ON COLUMN ember_schema.cluster.custom_theme_colors
    IS 'The cluster''s own colour set, in the same shape a station keeps its own.';
COMMENT ON COLUMN ember_schema.cluster.default_feel
    IS 'The interface feel the cluster hands its stations, or null when it does not care.';

-- A storage backend of the cluster's own, sitting between the station's override and the instance default.

CREATE TABLE IF NOT EXISTS ember_schema.cluster_storage_config
(
    cluster_id   INTEGER     NOT NULL PRIMARY KEY REFERENCES ember_schema.cluster (id) ON DELETE CASCADE,
    backend_type TEXT        NOT NULL,
    config       JSONB       NOT NULL DEFAULT '{}',
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE ember_schema.cluster_storage_config
    IS 'A cluster''s own storage backend, used by its stations unless a station has an override of its own.';
COMMENT ON COLUMN ember_schema.cluster_storage_config.cluster_id IS 'The cluster this backend belongs to.';
COMMENT ON COLUMN ember_schema.cluster_storage_config.backend_type
    IS 'Discriminator for the typed backend configuration carried in config.';
COMMENT ON COLUMN ember_schema.cluster_storage_config.config
    IS 'The typed backend configuration, encrypted where it carries credentials.';
COMMENT ON COLUMN ember_schema.cluster_storage_config.created_at IS 'When the override was first set.';
COMMENT ON COLUMN ember_schema.cluster_storage_config.updated_at IS 'When it was last changed.';
