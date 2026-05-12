-- Force password change flag on credentials
ALTER TABLE ember_schema.account_credential
    ADD COLUMN force_password_change BOOLEAN NOT NULL DEFAULT FALSE;
