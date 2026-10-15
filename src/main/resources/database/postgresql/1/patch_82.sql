INSERT INTO ember_schema.station_permission (name)
VALUES ('DOCUMENT_SIGN_OTHERS')
ON CONFLICT (name) DO NOTHING;

ALTER TABLE ember_schema.signing_evidence
    DROP CONSTRAINT IF EXISTS signing_evidence_capacity_check;

ALTER TABLE ember_schema.signing_evidence
    ADD CONSTRAINT signing_evidence_capacity_check
        CHECK (capacity IN ('ACCOUNT_HOLDER', 'GUARDIAN', 'MEMBER_THROUGH_ACCOUNT', 'WITNESSED'));

COMMENT ON CONSTRAINT signing_evidence_capacity_check ON ember_schema.signing_evidence IS
    'The capacities an act can be given in.';
COMMENT ON COLUMN ember_schema.signing_evidence.capacity IS
    'In what capacity the act was given: ACCOUNT_HOLDER for themselves, GUARDIAN on behalf of the member, MEMBER_THROUGH_ACCOUNT by the member through the account of somebody else, WITNESSED by a person who signed in front of a witness, whose account confirmed it.';
COMMENT ON COLUMN ember_schema.signing_evidence.account_member_id IS
    'The member at the station whose account confirmed the act, the witness for a WITNESSED act. NULL once that member was deleted.';
COMMENT ON COLUMN ember_schema.signing_evidence.account_holder_name IS
    'The official name of the account holder whose step-up confirmed the act, the witness for a WITNESSED act, as it was at signing.';
COMMENT ON COLUMN ember_schema.signing_evidence.member_id IS
    'The member a guardian signed for, who signed through the account, or who signed in front of the witness. NULL where the account holder signed for themselves, and once that member was deleted.';
COMMENT ON COLUMN ember_schema.signing_evidence.member_name IS
    'The official name of the member signed for, signing through the account, or signing in front of the witness, as it was at signing. NULL where the account holder signed for themselves.';
COMMENT ON COLUMN ember_schema.signing_evidence.guardian_position IS
    'Where the guardian stood in the member''s order of guardians at signing, counted from 0: the account holder when they acted as guardian or lent their account, the guardian who signed in front of a witness for a WITNESSED act. NULL where no guardian was involved.';
