CREATE TABLE ember_schema.waiting_list_invitation_option
(
    entry_id     INTEGER NOT NULL REFERENCES ember_schema.waiting_list_entry (id) ON DELETE CASCADE,
    position     INTEGER NOT NULL,
    event_id     INTEGER NOT NULL REFERENCES ember_schema.station_event (id) ON DELETE CASCADE,
    event_date   DATE    NOT NULL,
    arrival_time TIME    NULL,
    PRIMARY KEY (entry_id, position),
    CONSTRAINT waiting_list_invitation_option_once UNIQUE (entry_id, event_id, event_date)
);

CREATE INDEX idx_waiting_list_invitation_option_event
    ON ember_schema.waiting_list_invitation_option (event_id, event_date);

COMMENT ON TABLE ember_schema.waiting_list_invitation_option IS
    'The appointments the current invitation of a waiting list entry offers, one row per date the person can pick from. No sign-up is created from any of them: they have not joined anything, so they are on no attendee list and count towards nothing the station plans from. Replaced as a whole whenever the station invites again or offers other dates.';
COMMENT ON COLUMN ember_schema.waiting_list_invitation_option.entry_id IS
    'The entry whose invitation offers this date. Deleting the entry deletes its options.';
COMMENT ON COLUMN ember_schema.waiting_list_invitation_option.position IS
    'Where the date stands among the ones offered, starting at 0. An answer names the date it picked by this position.';
COMMENT ON COLUMN ember_schema.waiting_list_invitation_option.event_id IS
    'The appointment offered. Deleting the appointment removes the option, which leaves the invitation with the dates that still exist.';
COMMENT ON COLUMN ember_schema.waiting_list_invitation_option.event_date IS
    'The one date of that appointment. An appointment repeats, so without a date the option would name every occurrence there has ever been.';
COMMENT ON COLUMN ember_schema.waiting_list_invitation_option.arrival_time IS
    'When they were asked to be there on that date, usually earlier than everybody else so somebody can meet them. NULL when the station named no time of its own.';

INSERT INTO ember_schema.waiting_list_invitation_option (entry_id, position, event_id, event_date, arrival_time)
SELECT id, 0, invited_event_id, invited_event_date, invited_arrival_time
FROM ember_schema.waiting_list_entry
WHERE invited_event_id IS NOT NULL
  AND invited_event_date IS NOT NULL;

ALTER TABLE ember_schema.waiting_list_entry
    ADD COLUMN invitation_round           INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN invitation_answer_position INTEGER NULL,
    ADD COLUMN trial_message              TEXT    NOT NULL DEFAULT '';

COMMENT ON COLUMN ember_schema.waiting_list_entry.trial_message IS
    'What the station wrote to the family when the trial began, empty when it wrote nothing. Shown on the page behind the entry link for as long as the trial runs, so a list that sends no mail still delivers it.';

COMMENT ON COLUMN ember_schema.waiting_list_entry.invitation_round IS
    'How often the entry has been invited or offered other dates. An answer names the round it answers and is refused when that is not the current one, so a click on a page opened before the dates changed cannot apply to the dates that replaced them.';
COMMENT ON COLUMN ember_schema.waiting_list_entry.invitation_answer_position IS
    'Which offered date a COMING answer picked, by its position among the options. NULL for any other answer and while the invitation is unanswered.';

UPDATE ember_schema.waiting_list_entry entry
SET invitation_answer_position = 0
WHERE entry.invitation_answer = 'COMING'
  AND EXISTS (SELECT 1
              FROM ember_schema.waiting_list_invitation_option invitation_option
              WHERE invitation_option.entry_id = entry.id);

DROP INDEX ember_schema.idx_waiting_list_entry_invited_event;

ALTER TABLE ember_schema.waiting_list_entry
    DROP CONSTRAINT waiting_list_entry_invited_event_needs_date,
    DROP COLUMN invited_event_id,
    DROP COLUMN invited_event_date,
    DROP COLUMN invited_arrival_time;
