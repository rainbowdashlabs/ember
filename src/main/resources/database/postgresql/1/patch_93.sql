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

-- An inventory and a kind can carry a picture; a piece shows its kind's picture, otherwise its inventory's.

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

-- A member's waiting list count shows the waiting entries they have not been shown yet.

CREATE TABLE ember_schema.waiting_list_seen
(
    list_id    INTEGER NOT NULL REFERENCES ember_schema.waiting_list (id) ON DELETE CASCADE,
    member_id  INTEGER NOT NULL REFERENCES ember_schema.station_member (id) ON DELETE CASCADE,
    seen_up_to INTEGER NOT NULL,
    PRIMARY KEY (list_id, member_id)
);

COMMENT ON TABLE ember_schema.waiting_list_seen IS
    'How far a member has looked at a waiting list. The sidebar counts the waiting entries the member has not been shown yet.';
COMMENT ON COLUMN ember_schema.waiting_list_seen.list_id IS 'References the waiting list.';
COMMENT ON COLUMN ember_schema.waiting_list_seen.member_id IS 'References the station member who looked at the list.';
COMMENT ON COLUMN ember_schema.waiting_list_seen.seen_up_to IS
    'The highest entry id the member was shown on the list. Entries with a higher id count as new for them. An id rather than a time, because the creation time of an entry can be set back by hand.';

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
