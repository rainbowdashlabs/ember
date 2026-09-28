COMMENT ON TABLE ember_schema.event_equipment_handover IS
    'A piece that actually went out for one date of an appointment. The only claim on stock that is a row: everything still merely planned is derived from the recurrence rule when somebody asks.';

COMMENT ON COLUMN ember_schema.event_equipment_handover.event_date IS
    'The date the piece went out for, which is what pairs a handover with one occurrence of a series.';

COMMENT ON COLUMN ember_schema.event_equipment_handover.claim_from IS
    'When the piece left, taken from the occurrence and the line''s lead rather than from the clock, so the window a firm claim holds is the same window the loose one held.';

COMMENT ON COLUMN ember_schema.event_equipment_handover.claim_to IS
    'When the piece is due back, the occurrence''s end plus the line''s trail.';

COMMENT ON COLUMN ember_schema.event_equipment_need.event_date IS
    'Null where the line holds for every date the series produces, which is the ordinary case. Set where one single date says something of its own: a line for that date alone is added to the standing list, and where it names the same thing as a standing line it takes its place for that date. The one Dienst a year that also needs the trailer is written this way, without touching the series.';

COMMENT ON COLUMN ember_schema.event_field_date_value.field_id IS
    'The question this answer belongs to.';

COMMENT ON COLUMN ember_schema.event_partner_places.slot_budget IS
    'How many places this partner may fill on one date. NULL means no cap. Counted per occurrence, because a registration is per date and five places at a weekly appointment means five every week.';

COMMENT ON COLUMN ember_schema.federation_lending_request.event_date IS
    'The date of that appointment the request is for. Null exactly when event_id is.';
