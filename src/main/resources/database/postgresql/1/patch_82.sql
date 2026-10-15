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

ALTER TABLE ember_schema.station
    ADD COLUMN IF NOT EXISTS witness_reach_mode TEXT NOT NULL DEFAULT 'AND' CHECK (witness_reach_mode IN ('AND', 'OR'));

COMMENT ON COLUMN ember_schema.station.witness_reach_mode IS
    'AND or OR, how the parts of witness_reach_restriction combine.';

CREATE TABLE IF NOT EXISTS ember_schema.witness_reach_restriction
(
    id         SERIAL PRIMARY KEY,
    station_id INT NOT NULL REFERENCES ember_schema.station (id) ON DELETE CASCADE,
    user_type  TEXT,
    group_id   INT REFERENCES ember_schema.member_group (id) ON DELETE CASCADE,
    tag_id     INT REFERENCES ember_schema.user_tag (id) ON DELETE CASCADE,
    member_id  INT REFERENCES ember_schema.station_member (id) ON DELETE CASCADE,
    CHECK (num_nonnulls(user_type, group_id, tag_id, member_id) = 1)
);

CREATE INDEX IF NOT EXISTS idx_witness_reach_restriction_station
    ON ember_schema.witness_reach_restriction (station_id);

COMMENT ON TABLE ember_schema.witness_reach_restriction IS
    'Whom the holders of DOCUMENT_SIGN_OTHERS at a station may collect signatures from in person, among its current members and trial users. Empty means all of them. Shaped like event_restriction.';
COMMENT ON COLUMN ember_schema.witness_reach_restriction.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.witness_reach_restriction.station_id IS 'The station whose witnesses the audience narrows.';
COMMENT ON COLUMN ember_schema.witness_reach_restriction.user_type IS
    'Required user type. Exactly one of user_type/group_id/tag_id/member_id must be set.';
COMMENT ON COLUMN ember_schema.witness_reach_restriction.group_id IS 'Required group membership.';
COMMENT ON COLUMN ember_schema.witness_reach_restriction.tag_id IS 'Required tag.';
COMMENT ON COLUMN ember_schema.witness_reach_restriction.member_id IS
    'Specific member (always OR-connected, bypasses AND/OR mode).';
