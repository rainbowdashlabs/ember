-- Push notifications: the key pair this installation signs every push with.

CREATE TABLE ember_schema.push_vapid_key
(
    id                  SMALLINT    NOT NULL PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    public_key          BYTEA       NOT NULL,
    wrapped_private_key BYTEA       NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE ember_schema.push_vapid_key IS
    'The one key pair this installation signs its push messages with. Browsers bind their subscriptions to the public key, so replacing it ends every subscription.';
COMMENT ON COLUMN ember_schema.push_vapid_key.id IS
    'Always 1: there is exactly one key pair per installation.';
COMMENT ON COLUMN ember_schema.push_vapid_key.public_key IS
    'The public key as an uncompressed P-256 point, 65 bytes, handed to browsers when they subscribe.';
COMMENT ON COLUMN ember_schema.push_vapid_key.wrapped_private_key IS
    'The private key, wrapped under the at-rest secret like the signing keys. Lost or unreadable, every subscription has to be made again.';
COMMENT ON COLUMN ember_schema.push_vapid_key.created_at IS
    'When the key pair was generated.';
