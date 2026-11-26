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
