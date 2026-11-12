-- An appointment can take over from another one: a date of a series edited on its own becomes an
-- appointment of its own, and a series edited from one date on ends there and goes on as a new one.
-- The new appointment says where it came from, and the series keeps the dates it gave away.

ALTER TABLE ember_schema.station_event
    ADD COLUMN continues_event_id INTEGER NULL REFERENCES ember_schema.station_event (id) ON DELETE SET NULL,
    ADD COLUMN continues_from     DATE    NULL,
    ADD CONSTRAINT station_event_continues_names_a_date
        CHECK (continues_event_id IS NULL OR continues_from IS NOT NULL);

CREATE INDEX idx_station_event_continues_event_id
    ON ember_schema.station_event (continues_event_id)
    WHERE continues_event_id IS NOT NULL;

COMMENT ON COLUMN ember_schema.station_event.continues_event_id IS
    'The appointment this one took over from: the series a date was taken out of, or the series that ended where this one goes on. NULL for an appointment created on its own, or once that appointment is deleted.';
COMMENT ON COLUMN ember_schema.station_event.continues_from IS
    'The date of that appointment this one took over from: the single date taken out, or the first date of the continued series. NULL where continues_event_id never named one.';

CREATE TABLE ember_schema.station_event_detached_date
(
    event_id    INTEGER     NOT NULL REFERENCES ember_schema.station_event (id) ON DELETE CASCADE,
    event_date  DATE        NOT NULL,
    detached_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (event_id, event_date)
);

COMMENT ON TABLE ember_schema.station_event_detached_date IS
    'A date a series no longer holds because it became an appointment of its own. The series skips it like a date it does not fall on; it is not called off.';
COMMENT ON COLUMN ember_schema.station_event_detached_date.event_id IS 'The series the date was taken out of.';
COMMENT ON COLUMN ember_schema.station_event_detached_date.event_date IS 'The date taken out, on the station''s clock.';
COMMENT ON COLUMN ember_schema.station_event_detached_date.detached_at IS 'When the date was taken out.';
