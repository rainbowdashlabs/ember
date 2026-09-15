-- Files an event hands over, and a right to read what an event keeps back.
--
-- An evening has papers around it: the route sheet, the form to bring along, the plan the crew works
-- from. Some of them are for everybody who is coming, and some are for whoever runs it. Until now
-- there was nowhere to put either, so they travelled by mail or not at all.
--
-- The new right is read-only and is granted by the one that writes events, so no role loses anything
-- and no station needs its permissions migrated. Standing alone it opens what an event keeps back:
-- the material it needs, which until now could only be read by whoever keeps the whole inventory, and
-- the files marked internal below.
INSERT INTO ember_schema.station_permission (name)
VALUES ('EVENT_INTERNAL')
ON CONFLICT (name) DO NOTHING;

-- What an event hands over.
--
-- An attachment points at a file in the station media library rather than holding bytes, exactly as a
-- news attachment does, so it inherits the deduplication, the quota and the ownership of the library.
-- RESTRICT on the file is the same safety story: a file an event hands out cannot be deleted from the
-- library underneath it.
CREATE TABLE IF NOT EXISTS ember_schema.event_attachment
(
    id         SERIAL PRIMARY KEY,
    event_id   INTEGER     NOT NULL REFERENCES ember_schema.station_event (id) ON DELETE CASCADE,
    file_id    INTEGER     NOT NULL REFERENCES ember_schema.station_file (id) ON DELETE RESTRICT,
    label      TEXT,
    internal   BOOLEAN     NOT NULL DEFAULT FALSE,
    sort_order INTEGER     NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS event_attachment_event_idx ON ember_schema.event_attachment (event_id, sort_order);
CREATE INDEX IF NOT EXISTS event_attachment_file_idx ON ember_schema.event_attachment (file_id);

COMMENT ON TABLE ember_schema.event_attachment
    IS 'A file an event hands over, pointing at the station media library rather than holding bytes of its own.';
COMMENT ON COLUMN ember_schema.event_attachment.label
    IS 'What a reader sees instead of the file name, for the cases where the file name is not what should be read.';
COMMENT ON COLUMN ember_schema.event_attachment.internal
    IS 'True where the file is kept back from the room: readable only with the right to read what an event keeps internal, and never sent to a partner station. False, the default, is for everybody who may see the event.';
COMMENT ON COLUMN ember_schema.event_attachment.sort_order
    IS 'The order the files are handed over in, which is the order whoever wrote the event put them in.';
