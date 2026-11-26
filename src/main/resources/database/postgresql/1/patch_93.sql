-- An account can let member managers of other stations find it by name.

ALTER TABLE ember_schema.account_preference
    ADD COLUMN findable_by_name BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN ember_schema.account_preference.findable_by_name
    IS 'Whether member managers of any station of this instance may find the account by its name to invite it. Without it, only managers who share a station or an association with the account find it.';

COMMENT ON TABLE ember_schema.account_preference
    IS 'An account''s own choices that follow it to every device: how the app is laid out and whether it can be found by name. A row exists once the account changed something; an account without one has the defaults.';
