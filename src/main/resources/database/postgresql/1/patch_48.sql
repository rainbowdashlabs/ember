-- The Art: a level between the inventory and the piece.
--
-- A drawer of different things has no single answer to "what is in here". Six radios called blau,
-- five called gruen, four called orange, then a charging station and a cable: eighteen rows, six
-- names, and nothing in the model that knows the six are six groups. The Art is that row, so that
-- other rows can point at it and a count, a share and a request can finally mean "four blue ones".
--
-- It is a new field beside the piece's name, never that field renamed. The name is what carries
-- "Pager 01" and it stays exactly where it is. A piece may have an Art and no name worth reading, a
-- name and no Art, or both, and having no Art is the steady state rather than a migration window:
-- five of the seven ways a piece comes into being have nobody present to say what it is.
--
-- Nothing is grouped here and nothing is seeded, deliberately. Creating one Art per distinct name
-- would carve every typo somebody ever made into the model beside the word they meant, which is the
-- exact thing this level exists to let a station clear up.

CREATE TABLE ember_schema.inventory_art
(
    id           SERIAL PRIMARY KEY,
    inventory_id INTEGER NOT NULL REFERENCES ember_schema.inventory (id) ON DELETE CASCADE,
    name         TEXT    NOT NULL,
    note         TEXT    NOT NULL DEFAULT '',
    position     INTEGER NOT NULL DEFAULT 0,
    merge_key    TEXT GENERATED ALWAYS AS (lower(trim(name))) STORED,
    UNIQUE (inventory_id, name)
);

CREATE INDEX idx_inventory_art_inventory ON ember_schema.inventory_art (inventory_id);
CREATE INDEX idx_inventory_art_merge_key ON ember_schema.inventory_art (merge_key);

COMMENT ON TABLE ember_schema.inventory_art IS
    'A kind of thing inside one inventory, sitting between the inventory and the individual piece. Only inventories that hold a drawer of different things carry these: an inventory of one thing in many copies is structured by its sizes instead, and creating an Art in one is refused.';
COMMENT ON COLUMN ember_schema.inventory_art.id IS
    'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.inventory_art.inventory_id IS
    'The inventory this kind belongs to. An Art belongs to exactly one inventory, which is what makes the inventory reference on a field definition enough to scope it.';
COMMENT ON COLUMN ember_schema.inventory_art.name IS
    'What the station calls this kind, in its own spelling. Unique within the inventory.';
COMMENT ON COLUMN ember_schema.inventory_art.note IS
    'A free note about the kind, empty when nobody wrote one.';
COMMENT ON COLUMN ember_schema.inventory_art.position IS
    'Sort position among the kinds of the same inventory.';
COMMENT ON COLUMN ember_schema.inventory_art.merge_key IS
    'The name trimmed and lowered, maintained by the database. Two stations that use the same word are talking about the same kind, so availability and shares can be computed across a partnership without anybody maintaining a shared list. Generated rather than written, so renaming a kind cascades nowhere.';

ALTER TABLE ember_schema.inventory_item
    ADD COLUMN art_id INTEGER REFERENCES ember_schema.inventory_art (id) ON DELETE SET NULL;

CREATE INDEX idx_inventory_item_art
    ON ember_schema.inventory_item (art_id)
    WHERE art_id IS NOT NULL;

COMMENT ON COLUMN ember_schema.inventory_item.art_id IS
    'The kind of thing this piece is, or null when nobody has said. Null is the ordinary case and not a gap to be filled: every read tolerates it, a share resolves past it to the inventory, and a count per kind leaves the piece out rather than inventing a group for it. ON DELETE SET NULL, as size_id is, so removing a kind never takes pieces with it.';

-- Field definitions gain a scope below the inventory.
--
-- The mechanism has zero rows in production and the reason is the scope, not neglect: a definition
-- hangs on the whole inventory, and the inventories where a field would be interesting are exactly
-- the mixed drawers. A frequency band is nonsense on a charging station. So a definition may now
-- hang on one Art, or on one single piece for the thing that has a plate number nothing else has,
-- and null in both still means the whole inventory as it always did.
--
-- Definitions move down a level; values do not. Six radios share the field, never the value, so
-- nothing is inherited and there is no way for six radios to claim one inspection date between them.

ALTER TABLE ember_schema.inventory_field_definition
    ADD COLUMN art_id INTEGER REFERENCES ember_schema.inventory_art (id) ON DELETE CASCADE,
    ADD COLUMN item_id INTEGER REFERENCES ember_schema.inventory_item (id) ON DELETE CASCADE;

ALTER TABLE ember_schema.inventory_field_definition
    ADD CONSTRAINT inventory_field_definition_one_level CHECK (art_id IS NULL OR item_id IS NULL);

ALTER TABLE ember_schema.inventory_field_definition
    DROP CONSTRAINT inventory_field_definition_inventory_id_key_key;

ALTER TABLE ember_schema.inventory_field_definition
    ADD CONSTRAINT inventory_field_definition_scope_key
        UNIQUE NULLS NOT DISTINCT (inventory_id, art_id, item_id, key);

CREATE INDEX idx_inventory_field_definition_art
    ON ember_schema.inventory_field_definition (art_id)
    WHERE art_id IS NOT NULL;

CREATE INDEX idx_inventory_field_definition_item
    ON ember_schema.inventory_field_definition (item_id)
    WHERE item_id IS NOT NULL;

COMMENT ON COLUMN ember_schema.inventory_field_definition.art_id IS
    'The kind this field is defined for, or null when it is defined for the whole inventory. At most one of art_id and item_id is set.';
COMMENT ON COLUMN ember_schema.inventory_field_definition.item_id IS
    'The single piece this field is defined for, or null when it is not defined for one piece alone. At most one of art_id and item_id is set.';
COMMENT ON CONSTRAINT inventory_field_definition_one_level ON ember_schema.inventory_field_definition IS
    'A definition sits at exactly one level: the whole inventory, one kind, or one piece. Both references set at once would describe no level at all.';
COMMENT ON CONSTRAINT inventory_field_definition_scope_key ON ember_schema.inventory_field_definition IS
    'One key per level. NULLS NOT DISTINCT makes the absence of a kind and the absence of a piece values like any other, so the inventory level is one scope rather than an unlimited set of them. The same key may still be defined at two levels; the narrower definition is the one that describes the value.';
