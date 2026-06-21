-- Stable per-account UUID used to key account-shaped resources (e.g. avatars) so
-- they can be referenced without leaking the internal SERIAL id and without
-- depending on the per-station station_member.uid that used to host them.

ALTER TABLE ember_schema.account
    ADD COLUMN uid UUID NOT NULL DEFAULT gen_random_uuid();

ALTER TABLE ember_schema.account
    ADD CONSTRAINT account_uid_unique UNIQUE (uid);

COMMENT ON COLUMN ember_schema.account.uid IS
    'Stable per-account UUID, used as the public key for account-scoped resources (avatars, federated identity).';
