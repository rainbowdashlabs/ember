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

-- A station that shares an appointment may hand a partner a number of places and let them decide who
-- fills them. The obvious home for that was the table naming the partners a share is aimed at, and it
-- is the wrong one twice over: saving the sharing screen deletes and rewrites every row in it, which
-- would throw the budget away, and a share aimed at every partner has no rows in it at all. So this
-- is a table of its own, written only by the screen that sets it and untouched by sharing.

CREATE TABLE ember_schema.event_partner_places
(
    event_id         INTEGER NOT NULL REFERENCES ember_schema.station_event (id) ON DELETE CASCADE,
    partner_id       INTEGER NOT NULL REFERENCES ember_schema.federation_partner (id) ON DELETE CASCADE,
    slot_budget      INTEGER NULL,
    partner_confirms BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (event_id, partner_id),
    CONSTRAINT event_partner_places_budget_is_not_negative CHECK (slot_budget IS NULL OR slot_budget >= 0),

    -- Handing somebody places and then choosing their people for them is not a thing anybody wants,
    -- so a budget means they decide. Deciding without a budget is allowed and means no cap.
    CONSTRAINT event_partner_places_budget_implies_deciding CHECK (slot_budget IS NULL OR partner_confirms)
);

COMMENT ON TABLE ember_schema.event_partner_places IS
    'What a partner station may do with a shared appointment: how many places it may fill, and whether it decides who fills them. Separate from the sharing tables because saving a share rewrites those wholesale.';
COMMENT ON COLUMN ember_schema.event_partner_places.slot_budget IS
    'How many places this partner may fill on one date. NULL means no cap. Counted per occurrence, because a registration is per date and five places at a weekly evening means five every week.';
COMMENT ON COLUMN ember_schema.event_partner_places.partner_confirms IS
    'Whether this partner confirms its own members rather than the host doing it. False everywhere until somebody says otherwise, which is how it worked before this existed.';
