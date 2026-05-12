ALTER TABLE ember_schema.account RENAME COLUMN name TO first_name;
ALTER TABLE ember_schema.account ADD COLUMN last_name TEXT NOT NULL DEFAULT '';
