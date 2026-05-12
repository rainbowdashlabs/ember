CREATE TABLE ember_schema.account_role
(
    account_id INTEGER NOT NULL REFERENCES ember_schema.account (id) ON DELETE CASCADE,
    role       TEXT    NOT NULL,
    PRIMARY KEY (account_id, role)
);