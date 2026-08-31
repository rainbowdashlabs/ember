-- Nothing is offered to a partner until somebody says so.
--
-- The table for it was created and never wired up: every partner saw every inventory a station had,
-- and the only filter that ever ran was a date block. A row says a whole inventory, a kind of thing
-- in it, or a single piece is on offer, to all partners or to named ones, and the narrowest row that
-- exists decides.
--
-- Taking one piece back out of a shared drawer is the case that comes up, so a row can withhold as
-- well as grant. Working that as "share the other things instead" would mean redoing the choice
-- every time something is added, and the one that was forgotten would silently be on offer.
--
-- One row per inventory, one per kind and one per piece, because two rows saying different things
-- about the same gear have no answer.

ALTER TABLE ember_schema.federation_inventory_share
    ADD COLUMN share_grant TEXT NOT NULL DEFAULT 'GRANT',
    ADD COLUMN art_id      INT REFERENCES ember_schema.inventory_art (id) ON DELETE CASCADE;

COMMENT ON COLUMN ember_schema.federation_inventory_share.share_grant IS
    'GRANT puts the gear on offer, WITHHOLD takes it back out of a wider offer. The narrowest row that exists decides.';
COMMENT ON COLUMN ember_schema.federation_inventory_share.art_id IS
    'References the kind of thing. Exactly one of inventory_id/art_id/item_id must be set.';

ALTER TABLE ember_schema.federation_inventory_share
    DROP CONSTRAINT federation_inventory_share_check;

ALTER TABLE ember_schema.federation_inventory_share
    ADD CONSTRAINT federation_inventory_share_one_level
        CHECK (num_nonnulls(inventory_id, art_id, item_id) = 1);

CREATE UNIQUE INDEX uq_federation_inventory_share_inventory
    ON ember_schema.federation_inventory_share (station_id, inventory_id)
    WHERE inventory_id IS NOT NULL;

CREATE UNIQUE INDEX uq_federation_inventory_share_art
    ON ember_schema.federation_inventory_share (station_id, art_id)
    WHERE art_id IS NOT NULL;

CREATE UNIQUE INDEX uq_federation_inventory_share_item
    ON ember_schema.federation_inventory_share (station_id, item_id)
    WHERE item_id IS NOT NULL;
-- Tags on items.
--
-- An inventory groups things that are stored together and a size tells one piece from another, but
-- neither can say that the radios, the charging station and the antenna belong together. A tag says
-- exactly that: a standing property of a piece, true whatever the occasion, carrying no quantity.
--
-- The tag is an entity of the station and is picked from what exists rather than typed on each
-- piece, because free text on a piece is how "orange" and "organge" end up being two things.
--
-- canonical_name is the trimmed lowercase form, computed by the database. Tags of the same name are
-- one tag across stations, and every query that merges them compares this column rather than
-- repeating the normal form and getting it wrong once. Postgres maintains it, so a rename has
-- nothing to cascade.

CREATE TABLE ember_schema.inventory_tag
(
    id             SERIAL PRIMARY KEY,
    station_id     INT  NOT NULL REFERENCES ember_schema.station (id) ON DELETE CASCADE,
    name           TEXT NOT NULL,
    canonical_name TEXT GENERATED ALWAYS AS (lower(btrim(name))) STORED,
    color          TEXT,
    position       INT  NOT NULL DEFAULT 0,
    UNIQUE (station_id, canonical_name)
);

CREATE INDEX idx_inventory_tag_canonical ON ember_schema.inventory_tag (canonical_name);

COMMENT ON TABLE ember_schema.inventory_tag IS
    'A tag a station puts on its items, picked from what exists rather than typed per item.';
COMMENT ON COLUMN ember_schema.inventory_tag.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.inventory_tag.station_id IS 'The station the tag belongs to.';
COMMENT ON COLUMN ember_schema.inventory_tag.name IS 'The tag as the station spelled it, which is what every list shows.';
COMMENT ON COLUMN ember_schema.inventory_tag.canonical_name IS
    'The trimmed lowercase name, maintained by the database. Two stations spelling one tag differently match on this.';
COMMENT ON COLUMN ember_schema.inventory_tag.color IS 'Optional hex colour for the badge. Null means the badge takes the neutral colour.';
COMMENT ON COLUMN ember_schema.inventory_tag.position IS 'Where the tag sits in the station''s own list.';

CREATE TABLE ember_schema.inventory_item_tag
(
    item_id INT NOT NULL REFERENCES ember_schema.inventory_item (id) ON DELETE CASCADE,
    tag_id  INT NOT NULL REFERENCES ember_schema.inventory_tag (id) ON DELETE CASCADE,
    PRIMARY KEY (item_id, tag_id)
);

CREATE INDEX idx_inventory_item_tag_tag ON ember_schema.inventory_item_tag (tag_id);

COMMENT ON TABLE ember_schema.inventory_item_tag IS
    'Which tags one item carries. The tag sits on the item, so a piece filed anywhere can carry it.';
COMMENT ON COLUMN ember_schema.inventory_item_tag.item_id IS 'The item wearing the tag.';
COMMENT ON COLUMN ember_schema.inventory_item_tag.tag_id IS 'The tag.';

-- What an association recommends its stations call things.
--
-- Shaped like cluster_profile_field, including the group of stations it is meant for, but without
-- that table's readonly flag. An association tag stands beside a station's own and never replaces
-- it: both rows are already one concept through the canonical name, and every station keeps showing
-- the spelling it gave.

CREATE TABLE ember_schema.cluster_inventory_tag
(
    id               SERIAL PRIMARY KEY,
    cluster_id       INT  NOT NULL REFERENCES ember_schema.cluster (id) ON DELETE CASCADE,
    name             TEXT NOT NULL,
    canonical_name   TEXT GENERATED ALWAYS AS (lower(btrim(name))) STORED,
    color            TEXT,
    position         INT  NOT NULL DEFAULT 0,
    station_group_id INT REFERENCES ember_schema.cluster_station_group (id) ON DELETE RESTRICT,
    UNIQUE NULLS NOT DISTINCT (cluster_id, station_group_id, canonical_name)
);

CREATE INDEX idx_cluster_inventory_tag_canonical ON ember_schema.cluster_inventory_tag (canonical_name);

COMMENT ON TABLE ember_schema.cluster_inventory_tag IS
    'A tag an association recommends to its stations. It never displaces a station''s own tag of the same name.';
COMMENT ON COLUMN ember_schema.cluster_inventory_tag.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.cluster_inventory_tag.cluster_id IS 'The association recommending it.';
COMMENT ON COLUMN ember_schema.cluster_inventory_tag.name IS 'The tag as the association spelled it.';
COMMENT ON COLUMN ember_schema.cluster_inventory_tag.canonical_name IS
    'The trimmed lowercase name, maintained by the database, which is what a station''s own tag matches on.';
COMMENT ON COLUMN ember_schema.cluster_inventory_tag.color IS 'Optional hex colour for the badge.';
COMMENT ON COLUMN ember_schema.cluster_inventory_tag.position IS 'Where it sits among the association''s own tags.';
COMMENT ON COLUMN ember_schema.cluster_inventory_tag.station_group_id IS
    'The group of stations the tag is recommended to. NULL recommends it to every station under the association.';
