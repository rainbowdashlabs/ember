CREATE TABLE ember_schema.saved_filter
(
    id           SERIAL PRIMARY KEY,
    account_id   INTEGER NOT NULL REFERENCES ember_schema.account (id) ON DELETE CASCADE,
    table_type   TEXT    NOT NULL,
    name         TEXT    NOT NULL,
    filter_data  JSONB   NOT NULL DEFAULT '{}',
    position     INTEGER NOT NULL DEFAULT 0,
    UNIQUE (account_id, table_type, name)
);
