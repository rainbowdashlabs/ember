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
