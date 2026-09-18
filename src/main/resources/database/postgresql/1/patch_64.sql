ALTER TABLE ember_schema.station
    ADD COLUMN notification_send_times TIME[]     NULL,
    ADD COLUMN notification_last_sent  TIMESTAMPTZ NULL;

COMMENT ON COLUMN ember_schema.station.notification_send_times IS
    'The times of day this station''s gathered notifications are mailed out, read on the station''s own clock. NULL leaves it to the number the operator set for the whole installation, which is what every station did before this existed. A station asking for hourly mail holds all twenty-four hours here, so the sweep only ever reads a list of times.';

COMMENT ON COLUMN ember_schema.station.notification_last_sent IS
    'When this station was last written to, which is what a send time is measured against. NULL where it never has been, so its first gathered notifications go out at the next time it asked for rather than at once.';

ALTER TABLE ember_schema.cluster
    ADD COLUMN notification_send_times TIME[]     NULL,
    ADD COLUMN notification_last_sent  TIMESTAMPTZ NULL;

COMMENT ON COLUMN ember_schema.cluster.notification_send_times IS
    'The times of day this cluster''s gathered notifications are mailed out. Read the station column of the same name: a cluster keeps its send times the same way, and sends through the instance''s own mail chain because a cluster spanning several stations has no address of its own that a reader would recognise.';

COMMENT ON COLUMN ember_schema.cluster.notification_last_sent IS
    'When this cluster was last written to. Read the station column of the same name.';
