-- Documents get permissions of their own.
--
-- The store is already the station's: a document can be filed under nobody, and the code says so in
-- as many words. What it never had was a permission that admits it. Reading a document today means
-- holding the permission to read members, so the only way to let the equipment officer at the test
-- certificates is to hand them the whole member list.
--
-- The two rows are granted by implication rather than by migration: MEMBER_READ implies DOCUMENT_READ
-- and MEMBER_EDIT implies DOCUMENT_EDIT, in the permission enum. That is deliberate. Granting the new
-- permissions only to member managers would take the store away from a custom role holding
-- MEMBER_READ and nothing else, which is a regression nobody asked for, and it would need a data
-- migration over every station to avoid. Implication needs neither.
INSERT INTO ember_schema.station_permission (name)
VALUES ('DOCUMENT_READ'),
       ('DOCUMENT_EDIT')
ON CONFLICT (name) DO NOTHING;
