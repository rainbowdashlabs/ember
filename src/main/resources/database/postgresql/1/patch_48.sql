-- What an appointment needs, and borrowing what the station has not got.
--
-- The inventory knows what the station owns and who holds what. An appointment knows who is coming.
-- Neither knew that the Leistungsmarsch on Saturday needs fourteen sets of protective gear and a
-- trailer, so the station answered that on paper every time.
--
-- Two levels, and they are deliberately not the same thing. The need is written once on the
-- appointment and holds for every evening it produces, because a weekly Dienst wants the same gear
-- fifty times a year. The claim on stock is per evening, because only a single window can hold
-- anything: "every Tuesday forever" is not something an availability query can honour.
--
-- A loose claim is never stored. A search over a window resolves the recurrence rule for that window
-- and derives the claims inside it, which is what the calendar already does for the evenings
-- themselves. That removes a horizon to choose, a job to refill it and a cleanup when a series is
-- thinned, because a series that no longer produces an evening no longer produces its claim either.
-- Only the firm claim, written when the gear actually leaves the shelf, is a row.

CREATE TABLE ember_schema.event_equipment_need
(
    id            SERIAL PRIMARY KEY,
    event_id      INT NOT NULL REFERENCES ember_schema.station_event (id) ON DELETE CASCADE,
    event_date    DATE,
    item_id       INT REFERENCES ember_schema.inventory_item (id) ON DELETE CASCADE,
    art_id        INT REFERENCES ember_schema.inventory_art (id) ON DELETE CASCADE,
    inventory_id  INT REFERENCES ember_schema.inventory (id) ON DELETE CASCADE,
    quantity      INT NOT NULL DEFAULT 1,
    lead_minutes  INT NOT NULL DEFAULT 1440,
    trail_minutes INT NOT NULL DEFAULT 1440,
    position      INT NOT NULL DEFAULT 0,
    CONSTRAINT chk_event_need_one_target CHECK (num_nonnulls(item_id, art_id, inventory_id) = 1),
    CONSTRAINT chk_event_need_quantity CHECK (quantity >= 1),
    CONSTRAINT chk_event_need_named_item_single CHECK (item_id IS NULL OR quantity = 1),
    CONSTRAINT chk_event_need_lead_trail CHECK (lead_minutes >= 0 AND trail_minutes >= 0),
    CONSTRAINT event_equipment_need_target
        UNIQUE NULLS NOT DISTINCT (event_id, event_date, item_id, art_id, inventory_id)
);

CREATE INDEX idx_event_equipment_need_event ON ember_schema.event_equipment_need (event_id);
CREATE INDEX idx_event_equipment_need_item
    ON ember_schema.event_equipment_need (item_id) WHERE item_id IS NOT NULL;
CREATE INDEX idx_event_equipment_need_art
    ON ember_schema.event_equipment_need (art_id) WHERE art_id IS NOT NULL;
CREATE INDEX idx_event_equipment_need_inventory
    ON ember_schema.event_equipment_need (inventory_id) WHERE inventory_id IS NOT NULL;

COMMENT ON TABLE ember_schema.event_equipment_need IS
    'One line of what an appointment needs: a named piece, a count of one kind of thing, or a count out of a whole inventory. Exactly one of the three, which is the line shape a collection already carries.';
COMMENT ON COLUMN ember_schema.event_equipment_need.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.event_equipment_need.event_id IS
    'The appointment this line belongs to.';
COMMENT ON COLUMN ember_schema.event_equipment_need.event_date IS
    'Null where the line holds for every evening the series produces, which is the ordinary case. Set where one single evening says something of its own: a line for that date alone is added to the standing list, and where it names the same thing as a standing line it takes its place for that evening. The one Dienst a year that also needs the trailer is written this way, without touching the series.';
COMMENT ON COLUMN ember_schema.event_equipment_need.item_id IS
    'The named piece this line asks for. Exactly one of item_id/art_id/inventory_id is set.';
COMMENT ON COLUMN ember_schema.event_equipment_need.art_id IS
    'The kind of thing a counted line asks for, which is how a line says four blue radios rather than four of whatever is in the drawer. Exactly one of item_id/art_id/inventory_id is set.';
COMMENT ON COLUMN ember_schema.event_equipment_need.inventory_id IS
    'The inventory a counted line draws from, for the inventories that hold one thing in many copies and therefore carry no kinds. Exactly one of item_id/art_id/inventory_id is set.';
COMMENT ON COLUMN ember_schema.event_equipment_need.quantity IS
    'How many pieces the line asks for. Always 1 on a named-piece line, because a named piece is one piece.';
COMMENT ON COLUMN ember_schema.event_equipment_need.lead_minutes IS
    'How long before the appointment begins the gear is already gone from the shelf. The radios are fetched the evening before, so the period the equipment is away is not the period the appointment lasts, and a request asking only for the Saturday would leave the owner finding an empty shelf on the Friday. A day is the default because a day either way is the ordinary case.';
COMMENT ON COLUMN ember_schema.event_equipment_need.trail_minutes IS
    'How long after the appointment ends the gear is still away, counted the same way as the lead. Both belong to the need rather than to a lending request, so a station''s own reservation and a borrowing request cannot disagree about when the shelf is empty.';
COMMENT ON COLUMN ember_schema.event_equipment_need.position IS 'Display order within the appointment.';

-- The firm claim: what actually left the shelf.
--
-- A loose claim says four blue ones and does not care which four, because picking fourteen particular
-- jackets in March for a June exercise is not planning, it is busywork that will be redone on the day.
-- At handover the claim gains the pieces that went, which is the same step a lending request line
-- already takes when a count becomes a named piece.

CREATE TABLE ember_schema.event_equipment_handover
(
    id          SERIAL PRIMARY KEY,
    need_id     INT         NOT NULL REFERENCES ember_schema.event_equipment_need (id) ON DELETE CASCADE,
    event_date  DATE        NOT NULL,
    item_id     INT         NOT NULL REFERENCES ember_schema.inventory_item (id) ON DELETE CASCADE,
    claim_from  TIMESTAMPTZ NOT NULL,
    claim_to    TIMESTAMPTZ NOT NULL,
    handed_by   INT REFERENCES ember_schema.station_member (id) ON DELETE SET NULL,
    handed_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    returned_at TIMESTAMPTZ,
    UNIQUE (need_id, event_date, item_id),
    CONSTRAINT chk_event_handover_window CHECK (claim_to > claim_from)
);

CREATE INDEX idx_event_equipment_handover_item ON ember_schema.event_equipment_handover (item_id);
CREATE INDEX idx_event_equipment_handover_window
    ON ember_schema.event_equipment_handover (claim_from, claim_to);

COMMENT ON TABLE ember_schema.event_equipment_handover IS
    'A piece that actually went out for one evening of an appointment. The only claim on stock that is a row: everything still merely planned is derived from the recurrence rule when somebody asks.';
COMMENT ON COLUMN ember_schema.event_equipment_handover.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.event_equipment_handover.need_id IS 'The line this piece was handed over against.';
COMMENT ON COLUMN ember_schema.event_equipment_handover.event_date IS
    'The evening the piece went out for, which is what pairs a handover with one occurrence of a series.';
COMMENT ON COLUMN ember_schema.event_equipment_handover.item_id IS 'The piece that went.';
COMMENT ON COLUMN ember_schema.event_equipment_handover.claim_from IS
    'When the piece left, taken from the evening and the line''s lead rather than from the clock, so the window a firm claim holds is the same window the loose one held.';
COMMENT ON COLUMN ember_schema.event_equipment_handover.claim_to IS
    'When the piece is due back, the evening''s end plus the line''s trail.';
COMMENT ON COLUMN ember_schema.event_equipment_handover.handed_by IS
    'The member who recorded the handover, or null once that member is gone.';
COMMENT ON COLUMN ember_schema.event_equipment_handover.handed_at IS 'When the handover was recorded.';
COMMENT ON COLUMN ember_schema.event_equipment_handover.returned_at IS
    'When the piece came back, null while it is still out. A returned piece claims nothing, whatever its window still says.';

-- A lending request knows what it is for.
--
-- The dates alone said when the shelf would be empty and never why, which is the question that
-- decides a yes. A title and a window answer it, and that is deliberately all: the sign-ups, the
-- custom fields and the description of an appointment are no business of another station and may be
-- restricted in the first place. The occasion is therefore a copy of the name rather than a link, so
-- that adding a field to an appointment can never quietly add it to a request.

ALTER TABLE ember_schema.federation_lending_request
    ADD COLUMN event_id   INT REFERENCES ember_schema.station_event (id) ON DELETE SET NULL,
    ADD COLUMN event_date DATE,
    ADD COLUMN occasion   TEXT NOT NULL DEFAULT '';

CREATE INDEX idx_federation_lending_request_event
    ON ember_schema.federation_lending_request (event_id) WHERE event_id IS NOT NULL;

COMMENT ON COLUMN ember_schema.federation_lending_request.event_id IS
    'The appointment the request was collected for, at the requesting station, or null where somebody is borrowing a trailer for a house move. Never sent to the owning station.';
COMMENT ON COLUMN ember_schema.federation_lending_request.event_date IS
    'The evening of that appointment the request is for. Null exactly when event_id is.';
COMMENT ON COLUMN ember_schema.federation_lending_request.occasion IS
    'What the request is for, in words, as the owning station reads it. A copy of the appointment''s name taken when the request was sent, so nothing else about the appointment ever travels and a later rename does not rewrite what was asked for. Empty where the request names no occasion.';

-- A request line answers a need, and may name a kind of thing.
--
-- Without the kind, a line could only ask for a whole drawer, which is the granularity that fails for
-- Funkgeräte: asking for four out of the radio drawer may be answered with the charging station and
-- the case. Without the need, a borrowed piece could not be counted towards the fourteen the
-- appointment asked for, and the panel would report a shortfall that was solved a week ago.

ALTER TABLE ember_schema.federation_lending_request_item
    ADD COLUMN art_id  INT REFERENCES ember_schema.inventory_art (id) ON DELETE SET NULL,
    ADD COLUMN need_id INT REFERENCES ember_schema.event_equipment_need (id) ON DELETE SET NULL;

CREATE INDEX idx_federation_lending_request_item_need
    ON ember_schema.federation_lending_request_item (need_id) WHERE need_id IS NOT NULL;

COMMENT ON COLUMN ember_schema.federation_lending_request_item.art_id IS
    'The kind of thing the line asks for, at the owning station. Null where the line names a whole inventory or a single piece.';
COMMENT ON COLUMN ember_schema.federation_lending_request_item.need_id IS
    'The line of an appointment''s needs this request was sent to fill, at the requesting station, or null where the request answers no need. What comes back on it counts towards that need like the station''s own gear does.';

-- A line asking for four can now record four pieces.
--
-- The assigned piece was a single column while the fulfilment loop called the same update repeatedly
-- on one row, so a line asking for four could only ever record the last one it looked at. Every count
-- above one was therefore decoration. Moving the assignment to a table of its own is the whole fix,
-- and the column goes rather than staying beside it, because two places recording the same fact
-- disagree within a month.

CREATE TABLE ember_schema.federation_lending_request_item_assignment
(
    id              SERIAL PRIMARY KEY,
    request_item_id INT NOT NULL REFERENCES ember_schema.federation_lending_request_item (id) ON DELETE CASCADE,
    item_id         INT NOT NULL REFERENCES ember_schema.inventory_item (id) ON DELETE CASCADE,
    UNIQUE (request_item_id, item_id)
);

CREATE INDEX idx_federation_lending_assignment_item
    ON ember_schema.federation_lending_request_item_assignment (item_id);

COMMENT ON TABLE ember_schema.federation_lending_request_item_assignment IS
    'Which pieces are set aside for one line of a lending request. A table rather than a column, because a line asking for four blue radios is answered with four of them.';
COMMENT ON COLUMN ember_schema.federation_lending_request_item_assignment.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.federation_lending_request_item_assignment.request_item_id IS
    'The line the piece is set aside for.';
COMMENT ON COLUMN ember_schema.federation_lending_request_item_assignment.item_id IS
    'The piece, always one belonging to the owning station.';

INSERT INTO ember_schema.federation_lending_request_item_assignment (request_item_id, item_id)
SELECT id, assigned_item_id
FROM ember_schema.federation_lending_request_item
WHERE assigned_item_id IS NOT NULL;

ALTER TABLE ember_schema.federation_lending_request_item
    DROP COLUMN assigned_item_id;
