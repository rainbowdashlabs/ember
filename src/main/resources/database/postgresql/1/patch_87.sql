-- What the instance administration needs to say what is waiting and how the instance is used.
--
-- A failed mail keeps why it failed and whether an administrator has taken note of it, so the warning
-- on the overview can end without deleting the mail. A station remembers when it was made and since
-- when it waits for its manager, and every reminder sent to that manager is written down, which is
-- what keeps an automatic reminder from going out twice. An account remembers the last day it was
-- used, and one row a day holds how many people used the instance and how large it was, because
-- nothing else keeps a history of either.

ALTER TABLE ember_schema.email_queue
    ADD COLUMN failure_reason  TEXT      NULL,
    ADD COLUMN acknowledged_at TIMESTAMP NULL,
    ADD COLUMN acknowledged_by INT       NULL REFERENCES ember_schema.account (id) ON DELETE SET NULL;

CREATE INDEX idx_email_queue_acknowledged_by
    ON ember_schema.email_queue (acknowledged_by)
    WHERE acknowledged_by IS NOT NULL;

CREATE INDEX idx_email_queue_unacknowledged_failures
    ON ember_schema.email_queue (station_id, created_at)
    WHERE acknowledged_at IS NULL AND (status = 'FAILED' OR delivery_status IN ('HARD_BOUNCE', 'BLOCKED', 'SPAM'));

COMMENT ON COLUMN ember_schema.email_queue.failure_reason IS
    'Why the queue gave up on the email: NO_PROVIDER, NO_PROVIDER_LEFT, PROVIDER_REFUSED, PROVIDER_BROKEN or RETRIES_EXHAUSTED. NULL for an email the queue did not give up on, and for failures recorded before the reason was kept.';
COMMENT ON COLUMN ember_schema.email_queue.acknowledged_at IS
    'When an instance administrator took note that the email did not arrive. Ends the warning for it; the email stays until the usual cleanup removes it.';
COMMENT ON COLUMN ember_schema.email_queue.acknowledged_by IS
    'The administrator who took note of the failure. NULL once that account is deleted, and for failures nobody took note of.';

ALTER TABLE ember_schema.station
    ADD COLUMN created_at    TIMESTAMPTZ NULL,
    ADD COLUMN waiting_since TIMESTAMPTZ NULL;

ALTER TABLE ember_schema.station
    ALTER COLUMN created_at SET DEFAULT now();

UPDATE ember_schema.station
SET waiting_since = now()
WHERE setup_completed_at IS NULL
  AND moved_away_at IS NULL
  AND station_kind <> 'CLUSTER_HOME';

COMMENT ON COLUMN ember_schema.station.created_at IS
    'When the station was made. NULL for stations made before this was recorded.';
COMMENT ON COLUMN ember_schema.station.waiting_since IS
    'Since when the station waits for its manager to set it up: set when a manager is named on a station that is not set up yet. The automatic reminders count from here. NULL for a station nobody waits for.';

CREATE TABLE ember_schema.station_manager_reminder
(
    id            SERIAL PRIMARY KEY,
    station_id    INT         NOT NULL REFERENCES ember_schema.station (id) ON DELETE CASCADE,
    account_id    INT         NULL REFERENCES ember_schema.account (id) ON DELETE SET NULL,
    kind          TEXT        NOT NULL CHECK (kind IN ('SETUP_LINK', 'SETUP_REMINDER')),
    trigger       TEXT        NOT NULL CHECK (trigger IN ('MANUAL', 'AFTER_7_DAYS', 'AFTER_21_DAYS')),
    waiting_since TIMESTAMPTZ NOT NULL,
    sent_at       TIMESTAMPTZ NULL,
    sent_by       INT         NULL REFERENCES ember_schema.account (id) ON DELETE SET NULL
);

CREATE UNIQUE INDEX uq_station_manager_reminder_automatic
    ON ember_schema.station_manager_reminder (station_id, trigger, waiting_since)
    WHERE trigger <> 'MANUAL';

CREATE INDEX idx_station_manager_reminder_station
    ON ember_schema.station_manager_reminder (station_id, sent_at DESC);

COMMENT ON TABLE ember_schema.station_manager_reminder IS
    'Every reminder sent, or deliberately skipped, to the manager of a station that is not set up yet. An automatic reminder is written once per trigger and waiting period, which is what keeps it from going out twice.';
COMMENT ON COLUMN ember_schema.station_manager_reminder.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.station_manager_reminder.station_id IS
    'The station waiting for its manager. Deleting the station deletes its reminders.';
COMMENT ON COLUMN ember_schema.station_manager_reminder.account_id IS
    'The manager the reminder was written to. NULL once that account is deleted.';
COMMENT ON COLUMN ember_schema.station_manager_reminder.kind IS
    'SETUP_LINK: a fresh link to choose a password, for a manager who never signed in. SETUP_REMINDER: a note that the station waits to be set up, for a manager who can sign in.';
COMMENT ON COLUMN ember_schema.station_manager_reminder.trigger IS
    'What sent it: MANUAL for an administrator, AFTER_7_DAYS and AFTER_21_DAYS for the automatic reminders.';
COMMENT ON COLUMN ember_schema.station_manager_reminder.waiting_since IS
    'The station''s waiting_since when the reminder was written, so naming a new manager starts the automatic reminders afresh.';
COMMENT ON COLUMN ember_schema.station_manager_reminder.sent_at IS
    'When the mail was queued. NULL for an automatic reminder that was skipped because one had gone out by hand shortly before.';
COMMENT ON COLUMN ember_schema.station_manager_reminder.sent_by IS
    'The administrator who sent it by hand. NULL for an automatic reminder, and once that account is deleted.';

ALTER TABLE ember_schema.account
    ADD COLUMN last_active_on DATE NULL;

COMMENT ON COLUMN ember_schema.account.last_active_on IS
    'The last day the account used the instance, in the database''s calendar. Written at most once a day per account; it is what the daily count of people using the instance is made from.';

CREATE TABLE ember_schema.instance_usage_daily
(
    day                        DATE PRIMARY KEY,
    active_accounts            INT NOT NULL DEFAULT 0,
    accounts                   INT NOT NULL DEFAULT 0,
    accounts_confirmed         INT NOT NULL DEFAULT 0,
    stations                   INT NOT NULL DEFAULT 0,
    stations_set_up            INT NOT NULL DEFAULT 0,
    members                    INT NOT NULL DEFAULT 0,
    former_members             INT NOT NULL DEFAULT 0,
    events                     INT NOT NULL DEFAULT 0,
    attendance_sessions        INT NOT NULL DEFAULT 0,
    attendance_entries         INT NOT NULL DEFAULT 0,
    inventory_items            INT NOT NULL DEFAULT 0,
    profile_fields             INT NOT NULL DEFAULT 0,
    member_groups              INT NOT NULL DEFAULT 0,
    clusters                   INT NOT NULL DEFAULT 0,
    federation_partners_active INT NOT NULL DEFAULT 0,
    knowledge_base_articles    INT NOT NULL DEFAULT 0,
    mails_sent                 INT NOT NULL DEFAULT 0,
    people                     INT NOT NULL DEFAULT 0,
    people_trial               INT NOT NULL DEFAULT 0,
    people_member              INT NOT NULL DEFAULT 0,
    people_guardian            INT NOT NULL DEFAULT 0,
    people_team                INT NOT NULL DEFAULT 0,
    people_manager             INT NOT NULL DEFAULT 0,
    memberships                INT NOT NULL DEFAULT 0
);

COMMENT ON TABLE ember_schema.instance_usage_daily IS
    'One row per day: how many people used the instance that day, and how large the instance was. Holds counts only, never who. Kept for good.';
COMMENT ON COLUMN ember_schema.instance_usage_daily.day IS 'The day, in the database''s calendar.';
COMMENT ON COLUMN ember_schema.instance_usage_daily.active_accounts IS
    'Accounts that used the instance that day, counted as it happens: one for each account the first time it is seen that day.';
COMMENT ON COLUMN ember_schema.instance_usage_daily.accounts IS 'Accounts at the last snapshot of the day.';
COMMENT ON COLUMN ember_schema.instance_usage_daily.accounts_confirmed IS
    'Accounts with a confirmed real address at the last snapshot of the day.';
COMMENT ON COLUMN ember_schema.instance_usage_daily.stations IS
    'Stations at the last snapshot of the day, without the home stations of associations.';
COMMENT ON COLUMN ember_schema.instance_usage_daily.stations_set_up IS
    'Stations whose setup was finished at the last snapshot of the day.';
COMMENT ON COLUMN ember_schema.instance_usage_daily.members IS
    'Memberships that are not archived at the last snapshot of the day.';
COMMENT ON COLUMN ember_schema.instance_usage_daily.former_members IS
    'Archived memberships at the last snapshot of the day.';
COMMENT ON COLUMN ember_schema.instance_usage_daily.events IS 'Appointments at the last snapshot of the day.';
COMMENT ON COLUMN ember_schema.instance_usage_daily.attendance_sessions IS
    'Attendance sheets at the last snapshot of the day.';
COMMENT ON COLUMN ember_schema.instance_usage_daily.attendance_entries IS
    'Entries on attendance sheets at the last snapshot of the day.';
COMMENT ON COLUMN ember_schema.instance_usage_daily.inventory_items IS
    'Pieces of equipment at the last snapshot of the day.';
COMMENT ON COLUMN ember_schema.instance_usage_daily.profile_fields IS
    'Profile questions of stations at the last snapshot of the day.';
COMMENT ON COLUMN ember_schema.instance_usage_daily.member_groups IS
    'Member groups at the last snapshot of the day.';
COMMENT ON COLUMN ember_schema.instance_usage_daily.clusters IS
    'Associations at the last snapshot of the day.';
COMMENT ON COLUMN ember_schema.instance_usage_daily.federation_partners_active IS
    'Active partnerships between stations at the last snapshot of the day, counted once per pair side.';
COMMENT ON COLUMN ember_schema.instance_usage_daily.knowledge_base_articles IS
    'Knowledge base files not in the trash at the last snapshot of the day.';
COMMENT ON COLUMN ember_schema.instance_usage_daily.mails_sent IS
    'Emails the queue sent that day.';
COMMENT ON COLUMN ember_schema.instance_usage_daily.people IS
    'Distinct people with at least one membership that is not archived, at the last snapshot of the day. A person is their account; a membership without an account is a person of its own.';
COMMENT ON COLUMN ember_schema.instance_usage_daily.people_trial IS
    'Distinct people who are trial members in at least one station, not archived there.';
COMMENT ON COLUMN ember_schema.instance_usage_daily.people_member IS
    'Distinct people who are members in at least one station, not archived there.';
COMMENT ON COLUMN ember_schema.instance_usage_daily.people_guardian IS
    'Distinct people whose member type is guardian in at least one station, not archived there.';
COMMENT ON COLUMN ember_schema.instance_usage_daily.people_team IS
    'Distinct people of the team in at least one station, not archived there.';
COMMENT ON COLUMN ember_schema.instance_usage_daily.people_manager IS
    'Distinct people who are managers in at least one station, not archived there.';
COMMENT ON COLUMN ember_schema.instance_usage_daily.memberships IS
    'Memberships that are not archived, so somebody in two stations counts twice.';
