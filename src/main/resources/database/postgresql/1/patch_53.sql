ALTER TABLE ember_schema.waiting_list_entry
    ADD COLUMN invitation_answer      TEXT      NULL,
    ADD COLUMN invitation_answered_at TIMESTAMP NULL,
    ADD COLUMN invitation_answer_note TEXT      NOT NULL DEFAULT '',
    ADD CONSTRAINT waiting_list_entry_answer_needs_time
        CHECK (invitation_answer IS NULL OR invitation_answered_at IS NOT NULL);

COMMENT ON COLUMN ember_schema.waiting_list_entry.invitation_answer IS
    'What they answered to the invitation they currently hold: COMING, NOT_INTERESTED or DATE_DOES_NOT_SUIT. NULL while the invitation is unanswered. A refusal is kept here rather than moving the entry out of the open section, because an answer that vanishes on arrival is the same failure as no answer at all. Cleared whenever a new invitation replaces the one it answered.';
COMMENT ON COLUMN ember_schema.waiting_list_entry.invitation_answered_at IS
    'When that answer was given, so a station can see how long an invitation has been sitting unanswered. NULL exactly when invitation_answer is NULL.';
COMMENT ON COLUMN ember_schema.waiting_list_entry.invitation_answer_note IS
    'What they wrote alongside the answer, empty when they wrote nothing. Given by whoever holds the entry link rather than by a member, which is why it is kept apart from the station''s own notes on the entry.';
