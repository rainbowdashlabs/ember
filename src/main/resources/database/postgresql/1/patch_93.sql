-- An account can let member managers of other stations find it by name.

ALTER TABLE ember_schema.account_preference
    ADD COLUMN findable_by_name BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN ember_schema.account_preference.findable_by_name
    IS 'Whether member managers of any station of this instance may find the account by its name to invite it. Without it, only managers who share a station or an association with the account find it.';

COMMENT ON TABLE ember_schema.account_preference
    IS 'An account''s own choices that follow it to every device: how the app is laid out and whether it can be found by name. A row exists once the account changed something; an account without one has the defaults.';

-- A member manager can ask an account to link that they found by its name rather than its address.

ALTER TABLE ember_schema.account_link_request
    DROP CONSTRAINT IF EXISTS account_link_request_origin_check,
    ADD CONSTRAINT account_link_request_origin_check
        CHECK (origin IN ('IMPORT', 'INVITE', 'NAME_INVITE', 'ASSOCIATION_INVITE')),
    DROP CONSTRAINT IF EXISTS account_link_request_owner,
    ADD CONSTRAINT account_link_request_owner CHECK (
        (station_id IS NOT NULL AND station_member_id IS NOT NULL
            AND cluster_id IS NULL AND user_type IS NULL AND address IS NULL
            AND origin IN ('IMPORT', 'INVITE', 'NAME_INVITE'))
        OR
        (station_id IS NULL AND station_member_id IS NULL AND created_by IS NULL
            AND cluster_id IS NOT NULL AND user_type IS NOT NULL AND address IS NOT NULL
            AND origin = 'ASSOCIATION_INVITE'));

COMMENT ON COLUMN ember_schema.account_link_request.origin IS
    'How the request came about: IMPORT when a station import found the account by its address, INVITE when a member manager of a station invited that address, NAME_INVITE when a member manager of a station found the account by its name, ASSOCIATION_INVITE when an association administrator added that address to the association.';
COMMENT ON CONSTRAINT account_link_request_owner ON ember_schema.account_link_request IS
    'A request belongs to exactly one asker: a station member (station and member set, origin IMPORT, INVITE or NAME_INVITE) or an association role (association, role and address set, origin ASSOCIATION_INVITE).';

-- An inventory and a kind can carry a picture; a piece shows its own, otherwise its kind's, otherwise its inventory's.

ALTER TABLE ember_schema.inventory
    ADD COLUMN picture_key TEXT;

ALTER TABLE ember_schema.inventory_art
    ADD COLUMN picture_key TEXT;

COMMENT ON COLUMN ember_schema.inventory.picture_key
    IS 'Storage key of the inventory''s picture in the inventory pictures category, or NULL for none. Every upload gets a new key, so a cached old picture is never served for a new one.';

COMMENT ON COLUMN ember_schema.inventory_art.picture_key
    IS 'Storage key of the kind''s picture in the inventory pictures category, or NULL for none. A piece of the kind shows this picture before its inventory''s. Every upload gets a new key.';

-- A procurement in a mixed inventory records whose piece it brings, the station's or the association's.

ALTER TABLE ember_schema.equipment_procurement
    ADD COLUMN owner_kind TEXT CHECK (owner_kind IN ('STATION', 'CLUSTER'));

COMMENT ON COLUMN ember_schema.equipment_procurement.owner_kind
    IS 'Who owns the piece the procurement brings, for a mixed inventory only: STATION for the station, CLUSTER for the association above it. Null for an inventory whose type already settles the owner, and for orders placed before the choice existed.';

-- A station can hold calendar subscriptions of its own for shared calendars, each showing the
-- appointments up to a chosen visibility, and any of them, like a member's personal feed, can leave
-- out chosen categories.

CREATE TABLE ember_schema.station_calendar_feed
(
    id         SERIAL PRIMARY KEY,
    station_id INTEGER     NOT NULL REFERENCES ember_schema.station (id) ON DELETE CASCADE,
    name       TEXT        NOT NULL,
    token      TEXT        NOT NULL UNIQUE,
    level      TEXT        NOT NULL CHECK (level IN ('PUBLIC', 'MEMBERS', 'ALL')),
    created_by INTEGER     REFERENCES ember_schema.station_member (id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    polled_at  TIMESTAMPTZ
);

CREATE INDEX idx_station_calendar_feed_station ON ember_schema.station_calendar_feed (station_id);

COMMENT ON TABLE ember_schema.station_calendar_feed IS
    'A calendar subscription of the station for a shared calendar, such as a screen in the station or the calendar of the command staff. It belongs to no member, carries no sign-up state and shows every appointment up to its level.';
COMMENT ON COLUMN ember_schema.station_calendar_feed.id IS 'Identifies the feed.';
COMMENT ON COLUMN ember_schema.station_calendar_feed.station_id IS 'References the station whose appointments the feed shows.';
COMMENT ON COLUMN ember_schema.station_calendar_feed.name IS 'The feed''s name, shown in the station''s list and as the calendar name.';
COMMENT ON COLUMN ember_schema.station_calendar_feed.token IS 'Unique access token in the feed address (32-byte SecureRandom, URL-safe Base64). Renewing it ends the old address.';
COMMENT ON COLUMN ember_schema.station_calendar_feed.level IS 'Up to which visibility the feed shows appointments: PUBLIC as the public calendar, MEMBERS every unrestricted appointment, ALL also the restricted ones.';
COMMENT ON COLUMN ember_schema.station_calendar_feed.created_by IS 'References the member who created the feed, NULL once that member is gone.';
COMMENT ON COLUMN ember_schema.station_calendar_feed.created_at IS 'When the feed was created.';
COMMENT ON COLUMN ember_schema.station_calendar_feed.polled_at IS 'When a calendar last fetched the feed, NULL while nothing has.';

CREATE TABLE ember_schema.station_calendar_feed_hidden_category
(
    feed_id     INTEGER NOT NULL REFERENCES ember_schema.station_calendar_feed (id) ON DELETE CASCADE,
    category_id INTEGER NOT NULL REFERENCES ember_schema.event_category (id) ON DELETE CASCADE,
    PRIMARY KEY (feed_id, category_id)
);

COMMENT ON TABLE ember_schema.station_calendar_feed_hidden_category IS
    'The categories a station calendar feed leaves out. Appointments of every other category and those without a category stay in the feed.';
COMMENT ON COLUMN ember_schema.station_calendar_feed_hidden_category.feed_id IS 'References the station calendar feed.';
COMMENT ON COLUMN ember_schema.station_calendar_feed_hidden_category.category_id IS 'References the event category the feed leaves out.';

CREATE TABLE ember_schema.user_feed_hidden_category
(
    member_id   INTEGER NOT NULL REFERENCES ember_schema.station_member (id) ON DELETE CASCADE,
    category_id INTEGER NOT NULL REFERENCES ember_schema.event_category (id) ON DELETE CASCADE,
    PRIMARY KEY (member_id, category_id)
);

COMMENT ON TABLE ember_schema.user_feed_hidden_category IS
    'The categories a member''s personal calendar feed leaves out. Keyed by member rather than by token, so renewing the address keeps the choice. Appointments the member or a member in their care signed up for stay in the feed whatever their category.';
COMMENT ON COLUMN ember_schema.user_feed_hidden_category.member_id IS 'References the station member whose personal feed leaves the category out.';
COMMENT ON COLUMN ember_schema.user_feed_hidden_category.category_id IS 'References the event category the feed leaves out.';

INSERT INTO ember_schema.station_permission (name)
VALUES ('EVENT_CALENDAR_FEEDS')
ON CONFLICT (name) DO NOTHING;

-- Partner stations read a description of what they borrow: of an inventory, of each kind, and of a
-- single piece where every piece is a different thing. A piece can also carry a photo of its own.

ALTER TABLE ember_schema.inventory
    ADD COLUMN description TEXT NOT NULL DEFAULT '';

ALTER TABLE ember_schema.inventory_art
    ADD COLUMN description TEXT NOT NULL DEFAULT '';

ALTER TABLE ember_schema.inventory_item
    ADD COLUMN description TEXT NOT NULL DEFAULT '',
    ADD COLUMN picture_key TEXT NULL;

COMMENT ON COLUMN ember_schema.inventory.description IS 'What partner stations read about the inventory before they borrow from it, in Markdown; empty when nobody wrote one.';
COMMENT ON COLUMN ember_schema.inventory_art.description IS 'What partner stations read about the kind, in Markdown; empty when nobody wrote one. The note stays internal.';
COMMENT ON COLUMN ember_schema.inventory_item.description IS 'What partner stations read about this one piece, in Markdown; empty when nobody wrote one.';
COMMENT ON COLUMN ember_schema.inventory_item.picture_key IS 'Storage key of the piece''s own photo, shown before its kind''s and its inventory''s; null for none.';

-- A station collects what it wants to borrow from its partners in one cart, shared by everybody who may
-- ask for gear there, and sends it as one request per lending station.

CREATE TABLE ember_schema.lending_cart
(
    station_id INTEGER     PRIMARY KEY REFERENCES ember_schema.station (id) ON DELETE CASCADE,
    date_from  DATE,
    date_to    DATE,
    occasion   TEXT        NOT NULL DEFAULT '',
    note       TEXT        NOT NULL DEFAULT '',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    sending_at TIMESTAMPTZ,
    CONSTRAINT lending_cart_days CHECK (date_to IS NULL OR (date_from IS NOT NULL AND date_to >= date_from))
);

COMMENT ON TABLE ember_schema.lending_cart IS
    'The one cart of a station for gear it wants to borrow from partner stations. Every member who may ask for gear works on the same cart; sending it opens one request per lending station and empties it.';
COMMENT ON COLUMN ember_schema.lending_cart.station_id IS 'References the borrowing station the cart belongs to.';
COMMENT ON COLUMN ember_schema.lending_cart.date_from IS 'The first day the gear is wanted, NULL while none is chosen.';
COMMENT ON COLUMN ember_schema.lending_cart.date_to IS 'The last day the gear is wanted, NULL for a single day or while none is chosen.';
COMMENT ON COLUMN ember_schema.lending_cart.occasion IS 'What the gear is wanted for, shown to the lending stations with each request; empty when nobody wrote it.';
COMMENT ON COLUMN ember_schema.lending_cart.note IS 'A note sent to every lending station as the first message of its request; empty when nobody wrote one.';
COMMENT ON COLUMN ember_schema.lending_cart.updated_at IS 'When the cart or one of its lines last changed.';
COMMENT ON COLUMN ember_schema.lending_cart.sending_at IS 'When somebody started sending the cart, NULL while nobody is. Another sending waits until that one is done, or until it is so old it can only have been cut off.';

CREATE TABLE ember_schema.lending_cart_line
(
    id                  SERIAL PRIMARY KEY,
    station_id          INTEGER     NOT NULL REFERENCES ember_schema.lending_cart (station_id) ON DELETE CASCADE,
    lending_station_uid UUID        NOT NULL,
    inventory_id        INTEGER     NOT NULL,
    art_id              INTEGER,
    item_id             INTEGER,
    quantity            INTEGER     NOT NULL CHECK (quantity > 0),
    inventory_name      TEXT        NOT NULL DEFAULT '',
    line_name           TEXT        NOT NULL DEFAULT '',
    picture_key         TEXT,
    added_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT lending_cart_line_one_target CHECK (art_id IS NULL OR item_id IS NULL),
    CONSTRAINT lending_cart_line_piece_once CHECK (item_id IS NULL OR quantity = 1),
    CONSTRAINT lending_cart_line_unique UNIQUE NULLS NOT DISTINCT (station_id, lending_station_uid, inventory_id, art_id, item_id)
);

COMMENT ON TABLE ember_schema.lending_cart_line IS
    'One thing in a station''s cart: a count of a partner''s inventory or of one of its kinds, or one single piece. The ids are the lending station''s own and are not references here, since that station may run on another instance.';
COMMENT ON COLUMN ember_schema.lending_cart_line.id IS 'Identifies the line.';
COMMENT ON COLUMN ember_schema.lending_cart_line.station_id IS 'References the cart, and so the borrowing station, the line belongs to.';
COMMENT ON COLUMN ember_schema.lending_cart_line.lending_station_uid IS 'The identity of the partner station the gear is borrowed from.';
COMMENT ON COLUMN ember_schema.lending_cart_line.inventory_id IS 'The inventory at the lending station the line draws from, by that station''s id.';
COMMENT ON COLUMN ember_schema.lending_cart_line.art_id IS 'The kind at the lending station the line counts, by that station''s id; NULL for a count of the whole inventory or a single piece.';
COMMENT ON COLUMN ember_schema.lending_cart_line.item_id IS 'The single piece at the lending station the line names, by that station''s id; NULL for a count.';
COMMENT ON COLUMN ember_schema.lending_cart_line.quantity IS 'How many are wanted; always 1 for a single piece.';
COMMENT ON COLUMN ember_schema.lending_cart_line.inventory_name IS 'The inventory''s name as last shown by the lending station, for showing the cart without asking it.';
COMMENT ON COLUMN ember_schema.lending_cart_line.line_name IS 'The kind''s or the piece''s name as last shown by the lending station; empty for a count of the whole inventory.';
COMMENT ON COLUMN ember_schema.lending_cart_line.picture_key IS 'The key of the photo the lending station last showed for the line, to ask for through this instance; NULL for none.';
COMMENT ON COLUMN ember_schema.lending_cart_line.added_at IS 'When the line was first put in the cart.';

-- Every lending request has a last day, never before its first; a request written without one, or with one
-- before its first, was asked for its first day only.

UPDATE ember_schema.federation_lending_request
SET requested_date_to = requested_date_from
WHERE requested_date_to IS NULL
   OR requested_date_to < requested_date_from;

ALTER TABLE ember_schema.federation_lending_request
    ALTER COLUMN requested_date_to SET NOT NULL,
    ADD CONSTRAINT federation_lending_request_days CHECK (requested_date_to >= requested_date_from);

COMMENT ON COLUMN ember_schema.federation_lending_request.requested_date_to IS 'The last day the gear is wanted, the first day itself for a request of one day.';

-- A lending station reminds a borrowing station of gear past its last day once on its own.

ALTER TABLE ember_schema.federation_lending_request
    ADD COLUMN reminded_at TIMESTAMPTZ;

UPDATE ember_schema.federation_lending_request
SET reminded_at = now()
WHERE status = 'LENT'
  AND requested_date_to < current_date;

COMMENT ON COLUMN ember_schema.federation_lending_request.reminded_at IS 'When the borrowing station was last reminded of gear past its last day, NULL while it was not. The reminder sent on its own the day after the last day goes once; a manager can remind again by hand.';

-- A borrowing station keeps what it asked for in the lending station's own ids, so a request to a station
-- on another instance can be borrowed again.

ALTER TABLE ember_schema.federation_lending_request_item
    ADD COLUMN offered_inventory_id INTEGER,
    ADD COLUMN offered_art_id       INTEGER,
    ADD COLUMN offered_item_id      INTEGER;

COMMENT ON COLUMN ember_schema.federation_lending_request_item.offered_inventory_id IS 'The inventory the borrowing station asked for, by the lending station''s id; not a reference, since that station may run on another instance. NULL on lines written before it was kept.';
COMMENT ON COLUMN ember_schema.federation_lending_request_item.offered_art_id IS 'The kind the borrowing station asked for, by the lending station''s id; NULL for none or on lines written before it was kept.';
COMMENT ON COLUMN ember_schema.federation_lending_request_item.offered_item_id IS 'The single piece the borrowing station asked for, by the lending station''s id; NULL for none or on lines written before it was kept.';

-- Reading and editing members no longer bring member documents along. Whoever held a member permission
-- that reached them is given the document permission of their own, so nothing changes until a station
-- gives the two apart.

INSERT INTO ember_schema.station_user_type_permission (station_id, user_type, permission_id)
SELECT held.station_id, held.user_type, child.id
FROM ember_schema.station_user_type_permission held
         JOIN ember_schema.station_permission parent ON parent.id = held.permission_id
         JOIN (VALUES ('MEMBER_READ', 'DOCUMENT_READ_MEMBER'),
                      ('MEMBER_NOTES', 'DOCUMENT_READ_MEMBER'),
                      ('MEMBER_CHANGES', 'DOCUMENT_READ_MEMBER'),
                      ('MEMBER_EXPORT', 'DOCUMENT_READ_MEMBER'),
                      ('MEMBER_EDIT', 'DOCUMENT_EDIT_MEMBER'),
                      ('MEMBER_MANAGER', 'DOCUMENT_EDIT_MEMBER')) AS inclusion(parent, child)
              ON inclusion.parent = parent.name
         JOIN ember_schema.station_permission child ON child.name = inclusion.child
ON CONFLICT DO NOTHING;

INSERT INTO ember_schema.member_group_permission (group_id, permission_id)
SELECT held.group_id, child.id
FROM ember_schema.member_group_permission held
         JOIN ember_schema.station_permission parent ON parent.id = held.permission_id
         JOIN (VALUES ('MEMBER_READ', 'DOCUMENT_READ_MEMBER'),
                      ('MEMBER_NOTES', 'DOCUMENT_READ_MEMBER'),
                      ('MEMBER_CHANGES', 'DOCUMENT_READ_MEMBER'),
                      ('MEMBER_EXPORT', 'DOCUMENT_READ_MEMBER'),
                      ('MEMBER_EDIT', 'DOCUMENT_EDIT_MEMBER'),
                      ('MEMBER_MANAGER', 'DOCUMENT_EDIT_MEMBER')) AS inclusion(parent, child)
              ON inclusion.parent = parent.name
         JOIN ember_schema.station_permission child ON child.name = inclusion.child
ON CONFLICT DO NOTHING;

INSERT INTO ember_schema.station_member_permission (member_id, permission_id)
SELECT held.member_id, child.id
FROM ember_schema.station_member_permission held
         JOIN ember_schema.station_permission parent ON parent.id = held.permission_id
         JOIN (VALUES ('MEMBER_READ', 'DOCUMENT_READ_MEMBER'),
                      ('MEMBER_NOTES', 'DOCUMENT_READ_MEMBER'),
                      ('MEMBER_CHANGES', 'DOCUMENT_READ_MEMBER'),
                      ('MEMBER_EXPORT', 'DOCUMENT_READ_MEMBER'),
                      ('MEMBER_EDIT', 'DOCUMENT_EDIT_MEMBER'),
                      ('MEMBER_MANAGER', 'DOCUMENT_EDIT_MEMBER')) AS inclusion(parent, child)
              ON inclusion.parent = parent.name
         JOIN ember_schema.station_permission child ON child.name = inclusion.child
ON CONFLICT DO NOTHING;

-- A place question can be the location the calendar shows for its appointment. Where a station kept the
-- meeting point the templates offer, that place question becomes the calendar's location, unless another
-- place of the same form already is. Only the first such question of a form is marked.

UPDATE ember_schema.event_field f
SET config = jsonb_set(COALESCE(f.config, '{}'::JSONB), '{calendarLocation}', 'true'::JSONB)
WHERE f.id IN (SELECT DISTINCT ON (m.event_id) m.id
               FROM ember_schema.event_field m
               WHERE m.field_type = 'LOCATION'
                 AND lower(trim(m.name)) IN ('treffpunkt', 'meeting point')
               ORDER BY m.event_id, m.position, m.id)
  AND NOT EXISTS (SELECT 1
                  FROM ember_schema.event_field o
                  WHERE o.event_id = f.event_id
                    AND (o.config ->> 'calendarLocation')::BOOLEAN IS TRUE);

UPDATE ember_schema.event_template_field f
SET config = jsonb_set(COALESCE(f.config, '{}'::JSONB), '{calendarLocation}', 'true'::JSONB)
WHERE f.id IN (SELECT DISTINCT ON (m.template_id) m.id
               FROM ember_schema.event_template_field m
               WHERE m.field_type = 'LOCATION'
                 AND lower(trim(m.name)) IN ('treffpunkt', 'meeting point')
               ORDER BY m.template_id, m.position, m.id)
  AND NOT EXISTS (SELECT 1
                  FROM ember_schema.event_template_field o
                  WHERE o.template_id = f.template_id
                    AND (o.config ->> 'calendarLocation')::BOOLEAN IS TRUE);

UPDATE ember_schema.event_group_field f
SET config = jsonb_set(COALESCE(f.config, '{}'::JSONB), '{calendarLocation}', 'true'::JSONB)
WHERE f.id IN (SELECT DISTINCT ON (m.event_group_id) m.id
               FROM ember_schema.event_group_field m
               WHERE m.field_type = 'LOCATION'
                 AND lower(trim(m.name)) IN ('treffpunkt', 'meeting point')
               ORDER BY m.event_group_id, m.position, m.id)
  AND NOT EXISTS (SELECT 1
                  FROM ember_schema.event_group_field o
                  WHERE o.event_group_id = f.event_group_id
                    AND (o.config ->> 'calendarLocation')::BOOLEAN IS TRUE);
