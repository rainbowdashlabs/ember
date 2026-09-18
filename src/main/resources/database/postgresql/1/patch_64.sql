-- A withdrawal stops being final the instant it lands.
--
-- Almost every accidental one is noticed within seconds, and there is no way back: a place given up
-- on an appointment past its deadline cannot be taken again at all. For five minutes it can now be
-- put back, which needs two things nothing wrote down before. When the answer was given, so the five
-- minutes can be measured, and what was held before it, so putting it back restores the place rather
-- than a seat at the end of the queue.

ALTER TABLE ember_schema.event_registration
    ADD COLUMN status_changed_at TIMESTAMP     NOT NULL DEFAULT now(),
    ADD COLUMN previous_status   TEXT          NULL;

COMMENT ON COLUMN ember_schema.event_registration.status_changed_at IS
    'When the status last changed. Its own column because created_at is bumped by some paths and not others, and a window measured off a value that means two things measures neither.';
COMMENT ON COLUMN ember_schema.event_registration.previous_status IS
    'What was held immediately before the current status, for as long as a withdrawal can be taken back. Restoring a place has to give back the place: somebody who held a confirmed seat and pressed the wrong button must not be handed a pending one.';

-- The same for a partner station's members, whose registrations are kept in their own table and
-- whose withdrawal has until now deleted the row outright.

ALTER TABLE ember_schema.event_federation_registration
    ADD COLUMN status_changed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    ADD COLUMN previous_status   TEXT                     NULL;

COMMENT ON COLUMN ember_schema.event_federation_registration.status_changed_at IS
    'When the status last changed, by the clock of the station that holds the appointment. Two instances have two clocks, and the one that owns the row is the one that decides whether a withdrawal can still be taken back.';
COMMENT ON COLUMN ember_schema.event_federation_registration.previous_status IS
    'What was held immediately before the current status, for as long as a withdrawal can be taken back.';

-- Existing rows have never changed status as far as anything can tell, so the moment they were
-- written is the truest thing available and nothing was held before it.

UPDATE ember_schema.event_registration SET status_changed_at = created_at;
UPDATE ember_schema.event_federation_registration SET status_changed_at = created_at;
