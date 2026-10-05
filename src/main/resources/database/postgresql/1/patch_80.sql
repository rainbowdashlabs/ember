INSERT INTO ember_schema.station_permission (name)
VALUES ('PROTOCOL_SHARE'), ('TEST_CATALOG_SHARE')
ON CONFLICT (name) DO NOTHING;

ALTER TABLE ember_schema.test_protocol_item
    ADD COLUMN IF NOT EXISTS bonus BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN ember_schema.test_protocol_item.bonus
    IS 'True for a bonus point: it adds to the score when checked but not to the maximum of its section or protocol.';

CREATE TABLE IF NOT EXISTS ember_schema.test_protocol_run_examiner
(
    run_id     INT NOT NULL REFERENCES ember_schema.test_protocol_run (id) ON DELETE CASCADE,
    section_id INT NOT NULL REFERENCES ember_schema.test_protocol_section (id) ON DELETE CASCADE,
    member_id  INT NOT NULL REFERENCES ember_schema.station_member (id) ON DELETE CASCADE,
    PRIMARY KEY (run_id, section_id, member_id)
);

CREATE INDEX IF NOT EXISTS test_protocol_run_examiner_member_idx
    ON ember_schema.test_protocol_run_examiner (member_id);

COMMENT ON TABLE ember_schema.test_protocol_run_examiner
    IS 'Who examines which section of a test run. An assignment covers the section and everything under it; a run with no rows here is graded by every tester, as before examiners existed.';
