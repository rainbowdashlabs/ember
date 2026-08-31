-- What kind of thing an inventory holds.
--
-- An inventory says who owns its items and whether they come in sizes, and neither answers the
-- question three features have been assuming the answer to: does it hold one thing in many copies,
-- or a drawer of different things. A requirement, an order for three more and a swap of one size for
-- another all only mean something for the first. Offered on a drawer of odds and ends they mean
-- nothing, and a station that used them there got a shelf of nonsense rather than a refusal.
--
-- Every inventory that exists becomes the first kind, because that is the permissive one: nothing a
-- station is doing today stops working on the day this arrives, and marking a drawer as a drawer is
-- something it opts into rather than inherits. Deriving the value from the sizes or from whether a
-- requirement happens to exist was considered and dropped: it would have been right most of the time,
-- and the times it was wrong are an inventory quietly losing something it was using.

ALTER TABLE ember_schema.inventory
    ADD COLUMN homogeneous BOOLEAN NOT NULL DEFAULT TRUE;

COMMENT ON COLUMN ember_schema.inventory.homogeneous IS
    'True where the inventory holds one thing in many copies, which is the only kind requirements, procurements and exchanges are offered for. False where it holds a drawer of different things. Every row existing before this column was added is true, deliberately: that is the permissive state.';

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

-- The evening a procedure was prepared for.
--
-- A preparation list made out of who is coming is about one appointment on one date, and until now
-- nothing recorded that. The list merely carried the appointment's name in its title, which reads
-- like a connection and is not one: nothing could lead back to the appointment, and nothing could
-- tell that a second press of the same button would make a second list for the very same evening.
--
-- Both columns are set together or not at all. A procedure written by hand keeps them empty and
-- behaves exactly as it did before. Losing the appointment leaves the procedure standing with its
-- steps and its progress intact, so the reference clears itself rather than taking the list with it.

ALTER TABLE ember_schema.procedure
    ADD COLUMN event_id   INTEGER REFERENCES ember_schema.station_event (id) ON DELETE SET NULL,
    ADD COLUMN event_date DATE;

CREATE INDEX procedure_event_idx ON ember_schema.procedure (event_id, event_date);

COMMENT ON COLUMN ember_schema.procedure.event_id IS
    'The appointment this procedure was prepared for, or NULL when it was written by hand. Cleared rather than cascaded when the appointment is deleted, so the procedure and its recorded progress survive it.';

COMMENT ON COLUMN ember_schema.procedure.event_date IS
    'The single occurrence of that appointment the procedure belongs to, since a recurring appointment has one per date. NULL exactly when event_id is NULL.';

ALTER TABLE ember_schema.checklist
    ADD COLUMN source_event_id   INTEGER NULL REFERENCES ember_schema.station_event (id) ON DELETE SET NULL,
    ADD COLUMN source_event_date DATE    NULL,
    ADD CONSTRAINT checklist_source_event_needs_date CHECK (source_event_id IS NULL OR source_event_date IS NOT NULL);

CREATE INDEX idx_checklist_source_event ON ember_schema.checklist (source_event_id);

COMMENT ON COLUMN ember_schema.checklist.source_event_id IS
    'The appointment this checklist follows instead of a filter, or NULL when it follows the rows in checklist_member_filter. Set means the two are exclusive: the filter table is empty and refresh resolves the accepted sign-ups of the occurrence instead. Deleting the appointment sets this back to NULL, which keeps every row already on the list and stops the list following anything.';
COMMENT ON COLUMN ember_schema.checklist.source_event_date IS
    'The one date of that appointment whose sign-ups are followed. Registrations are kept per appointment and date, so an appointment without a date would resolve to the union of every occurrence there has ever been. A leftover date with no appointment is what a deleted appointment leaves behind and carries no meaning.';

-- Where borrowed gear lives at the station that borrowed it.
--
-- Until now a borrowed piece had no row at the borrower at all. Lending wrote on the owner's row
-- and set it to WITH_PARTNER, and that was the whole of it, so the borrower could not put the piece
-- in a container, hand it to a member, walk it in a check or count it towards anything: there was
-- nothing to point at. The borrower's only view was a lending request in status LENT, which is a
-- process rather than a thing.
--
-- Three changes, and they only mean something together:
--
--   1. A third owner kind, so a borrowed piece can be an ordinary row that says whose it is.
--   2. A loan reference on that row, which is what pairs it with the owner's row and what makes it
--      disappear again when the gear goes home.
--   3. A borrowed inventory per station, and a partner named on the owner's own row.


-- A partner station is a third owner kind.
--
-- owner_station_id names the owning station, symmetric to owner_cluster_id naming the owning body.
-- The reason for putting it on the ownership axis is what it inherits: a station may not edit, lend
-- or delete gear it does not own, and both of those rules already read owner_kind, so they cover
-- borrowed gear from the first day without a second case written anywhere.
--
-- Both references delete the row rather than emptying it. A borrowed row is a copy of somebody
-- else's gear taken for the length of one loan; without the loan or without the owner it is not a
-- row waiting to be re-homed, it is a row about nothing.

ALTER TABLE ember_schema.inventory_item
    ADD COLUMN IF NOT EXISTS owner_station_id     INTEGER
        REFERENCES ember_schema.station (id) ON DELETE CASCADE,
    ADD COLUMN IF NOT EXISTS loan_request_item_id INTEGER
        REFERENCES ember_schema.federation_lending_request_item (id) ON DELETE CASCADE;

ALTER TABLE ember_schema.inventory_item
    DROP CONSTRAINT IF EXISTS chk_inventory_item_owner;

-- Each owner kind forbids the pointers that do not belong to it, and PARTNER_STATION requires the
-- two that do: a borrowed row always knows whose the gear is and which loan it came on.
ALTER TABLE ember_schema.inventory_item
    ADD CONSTRAINT chk_inventory_item_owner CHECK (
        CASE owner_kind
            WHEN 'STATION' THEN owner_cluster_id IS NULL
                AND owner_station_id IS NULL AND loan_request_item_id IS NULL
            WHEN 'CLUSTER' THEN owner_station_id IS NULL AND loan_request_item_id IS NULL
            WHEN 'PARTNER_STATION' THEN owner_cluster_id IS NULL
                AND owner_station_id IS NOT NULL AND loan_request_item_id IS NOT NULL
            ELSE FALSE
        END
    );

CREATE INDEX IF NOT EXISTS idx_inventory_item_loan_request_item
    ON ember_schema.inventory_item (loan_request_item_id)
    WHERE loan_request_item_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_inventory_item_owner_station
    ON ember_schema.inventory_item (owner_station_id)
    WHERE owner_station_id IS NOT NULL;

COMMENT ON COLUMN ember_schema.inventory_item.owner_station_id
    IS 'The station that owns the item when a partner station does, null for every other owner. Only ever set for PARTNER_STATION.';
COMMENT ON COLUMN ember_schema.inventory_item.loan_request_item_id
    IS 'The line of the lending request this borrowed copy came in on, which is what pairs it with the owner''s row and what ends it when the gear goes home. Only ever set for PARTNER_STATION.';


-- Which partner has it, on the owner's own row.
--
-- The custody columns said an item was with a partner but not which one, and that fact lived in the
-- lending request, one join away from anything asking where a radio is. custody_station_id keeps
-- naming the lender, because that is what puts the piece in the lender's own lists; the partner
-- holding it gets a column of its own.
--
-- This one empties rather than deletes. A partner station going away leaves gear recorded as being
-- somewhere nobody can name any more, which is a row waiting to be dealt with rather than a row
-- about nothing.

ALTER TABLE ember_schema.inventory_item
    ADD COLUMN IF NOT EXISTS custody_partner_station_id INTEGER
        REFERENCES ember_schema.station (id) ON DELETE SET NULL;

UPDATE ember_schema.inventory_item ii
SET custody_partner_station_id = s.id
FROM ember_schema.federation_lending_request_item ri
         JOIN ember_schema.federation_lending_request r ON r.id = ri.request_id
         JOIN ember_schema.station s ON s.uid = r.requesting_station_uid
WHERE ri.assigned_item_id = ii.id
  AND ii.custody = 'WITH_PARTNER';

ALTER TABLE ember_schema.inventory_item
    DROP CONSTRAINT IF EXISTS chk_inventory_item_custody_partner;

ALTER TABLE ember_schema.inventory_item
    ADD CONSTRAINT chk_inventory_item_custody_partner
        CHECK (custody = 'WITH_PARTNER' OR custody_partner_station_id IS NULL);

COMMENT ON COLUMN ember_schema.inventory_item.custody_partner_station_id
    IS 'The federation partner holding the item while it is WITH_PARTNER, null for every other custody. Null while it stands only when that station has since been removed.';


-- One borrowed inventory per station, created when it is first needed.
--
-- Everything belonging to somebody else lands in it, whichever partner it came from, because that
-- is the question a station actually asks: what have we got here at the moment that is not ours.
-- Split by partner, that question needs several screens read together and every one-off loan leaves
-- an empty shell behind for good.
--
-- It is heterogeneous by construction, so it can never be used for a requirement or a procurement.
-- The station may rename it, and the partial unique index is what keeps there being only one.

ALTER TABLE ember_schema.inventory
    ADD COLUMN IF NOT EXISTS borrowed BOOLEAN NOT NULL DEFAULT FALSE;

CREATE UNIQUE INDEX IF NOT EXISTS idx_inventory_borrowed_per_station
    ON ember_schema.inventory (station_id)
    WHERE borrowed;

COMMENT ON COLUMN ember_schema.inventory.borrowed
    IS 'Whether this is the station''s one shelf for gear belonging to somebody else. Created on the first handover, renameable, and refused deletion while anything is still on it.';

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

-- A named set of things that belong together.
--
-- The everyday case is a box: the three games and the laminator somebody fetches for every youth
-- evening, spread over whichever inventories the pieces happened to be filed in. Nothing about those
-- rows says they belong together, and this is where that knowledge goes.
--
-- A collection is a template and carries no promise. Its lines are copied wherever they are used, so
-- editing it next month changes nothing that was already asked for, and it neither reserves nor holds
-- stock.
--
-- The line shape is deliberately the one inventory_requirement and federation_lending_request_item
-- already have, so the next thing that needs a line can share it instead of making a fourth copy. The
-- lending line allows both targets null and both set; this one does not.

CREATE TABLE ember_schema.inventory_collection
(
    id         SERIAL PRIMARY KEY,
    station_id INT         NOT NULL REFERENCES ember_schema.station (id) ON DELETE CASCADE,
    name       TEXT        NOT NULL,
    note       TEXT        NOT NULL DEFAULT '',
    created_by INT REFERENCES ember_schema.station_member (id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (station_id, name)
);

COMMENT ON TABLE ember_schema.inventory_collection IS
    'A named, reusable set of inventory lines. A template that is copied where it is used, never a reservation.';
COMMENT ON COLUMN ember_schema.inventory_collection.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.inventory_collection.station_id IS
    'The station the collection belongs to. An association reads the collections of its home station, so there is no second scope.';
COMMENT ON COLUMN ember_schema.inventory_collection.name IS 'What the station calls it, unique within the station.';
COMMENT ON COLUMN ember_schema.inventory_collection.note IS 'Free text about the purpose, may be empty.';
COMMENT ON COLUMN ember_schema.inventory_collection.created_by IS
    'The member who created it, or null once that member is gone.';
COMMENT ON COLUMN ember_schema.inventory_collection.created_at IS 'When it was created.';

CREATE TABLE ember_schema.inventory_collection_line
(
    id            SERIAL PRIMARY KEY,
    collection_id INT NOT NULL REFERENCES ember_schema.inventory_collection (id) ON DELETE CASCADE,
    item_id       INT REFERENCES ember_schema.inventory_item (id) ON DELETE CASCADE,
    art_id        INT REFERENCES ember_schema.inventory_art (id) ON DELETE CASCADE,
    inventory_id  INT REFERENCES ember_schema.inventory (id) ON DELETE CASCADE,
    quantity      INT NOT NULL DEFAULT 1,
    position      INT NOT NULL DEFAULT 0,
    CONSTRAINT chk_collection_line_one_target CHECK (num_nonnulls(item_id, art_id, inventory_id) = 1),
    CONSTRAINT chk_collection_line_quantity CHECK (quantity >= 1),
    CONSTRAINT chk_collection_line_named_item_single CHECK (item_id IS NULL OR quantity = 1)
);

CREATE INDEX idx_inventory_collection_line_collection
    ON ember_schema.inventory_collection_line (collection_id);
CREATE INDEX idx_inventory_collection_line_item
    ON ember_schema.inventory_collection_line (item_id) WHERE item_id IS NOT NULL;
CREATE INDEX idx_inventory_collection_line_art
    ON ember_schema.inventory_collection_line (art_id) WHERE art_id IS NOT NULL;
CREATE INDEX idx_inventory_collection_line_inventory
    ON ember_schema.inventory_collection_line (inventory_id) WHERE inventory_id IS NOT NULL;

COMMENT ON TABLE ember_schema.inventory_collection_line IS
    'One line of a collection: a named piece, a count of one kind of thing, or a count out of a whole inventory. Exactly one of the three, never two and never none.';
COMMENT ON COLUMN ember_schema.inventory_collection_line.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.inventory_collection_line.collection_id IS 'References the collection.';
COMMENT ON COLUMN ember_schema.inventory_collection_line.item_id IS
    'The named piece this line asks for. Exactly one of item_id/art_id/inventory_id is set. The line goes when the piece goes.';
COMMENT ON COLUMN ember_schema.inventory_collection_line.art_id IS
    'The kind of thing a counted line asks for, which is how a line says four blue radios rather than four of whatever is in the drawer. Exactly one of item_id/art_id/inventory_id is set. The line goes when the kind goes.';
COMMENT ON COLUMN ember_schema.inventory_collection_line.inventory_id IS
    'The inventory a counted line draws from, for the inventories that hold one thing in many copies and therefore carry no kinds. Exactly one of item_id/art_id/inventory_id is set.';
COMMENT ON COLUMN ember_schema.inventory_collection_line.quantity IS
    'How many pieces the line asks for. Always 1 on a named-item line, because a named piece is one piece.';
COMMENT ON COLUMN ember_schema.inventory_collection_line.position IS 'Display order within the collection.';

ALTER TABLE ember_schema.waiting_list_entry
    ADD COLUMN invited_event_id     INTEGER NULL REFERENCES ember_schema.station_event (id) ON DELETE SET NULL,
    ADD COLUMN invited_event_date   DATE    NULL,
    ADD COLUMN invited_arrival_time TIME    NULL,
    ADD CONSTRAINT waiting_list_entry_invited_event_needs_date
        CHECK (invited_event_id IS NULL OR invited_event_date IS NOT NULL);

CREATE INDEX idx_waiting_list_entry_invited_event
    ON ember_schema.waiting_list_entry (invited_event_id, invited_event_date)
    WHERE invited_event_id IS NOT NULL;

COMMENT ON COLUMN ember_schema.waiting_list_entry.invited_event_id IS
    'The appointment the current invitation asks them to come to, or NULL when nobody has been invited yet. No sign-up is created from it: they have not joined anything, so they are not on the attendee list and count towards nothing the station plans from. Deleting the appointment empties this, which leaves the invitation without an occasion rather than pointing at nothing.';
COMMENT ON COLUMN ember_schema.waiting_list_entry.invited_event_date IS
    'The one date of that appointment they were invited to. An appointment repeats, so without a date the invitation would name every Tuesday there has ever been, and the answer to it would mean nothing.';
COMMENT ON COLUMN ember_schema.waiting_list_entry.invited_arrival_time IS
    'When they were asked to be there, which is usually earlier than everybody else so somebody can meet them. NULL when the invitation named no time of its own. A time rather than an offset, because that is what the mail has to say and what the reader has to read.';

ALTER TABLE ember_schema.waiting_list_entry
    ADD COLUMN invitation_answer      TEXT      NULL,
    ADD COLUMN invitation_answered_at TIMESTAMP NULL,
    ADD COLUMN invitation_answer_note TEXT      NOT NULL DEFAULT '',
    ADD CONSTRAINT waiting_list_entry_answer_needs_time
        CHECK (invitation_answer IS NULL OR invitation_answered_at IS NOT NULL);

COMMENT ON COLUMN ember_schema.waiting_list_entry.invitation_answer IS
    'What they answered to the invitation they currently hold: COMING, NOT_INTERESTED or DATE_DOES_NOT_SUIT. NULL while the invitation is unanswered. A refusal is kept here rather than moving the entry out of the open section, because an answer that vanishes on arrival is the same failure as no answer at all. Cleared whenever a new invitation replaces the one it answered.';
COMMENT ON COLUMN ember_schema.waiting_list_entry.invitation_answered_at IS
    'When that answer was given, so a station can see how long an invitation has been sitting unanswered. NULL exactly when invitation_answer is NULL.';
COMMENT ON COLUMN ember_schema.waiting_list_entry.invitation_answer_note IS
    'What they wrote alongside the answer, empty when they wrote nothing. Given by whoever holds the entry link rather than by a member, which is why it is kept apart from the station''s own notes on the entry.';

