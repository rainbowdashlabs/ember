-- Add generated full_name column to account table
ALTER TABLE ember_schema.account ADD COLUMN full_name TEXT GENERATED ALWAYS AS (TRIM(first_name || ' ' || last_name)) STORED;
