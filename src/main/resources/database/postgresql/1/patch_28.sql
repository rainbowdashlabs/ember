-- Event role and group restrictions
-- When no restrictions exist, the event is open to all members.
-- When restrictions exist, only members matching at least one role OR one group are eligible.

CREATE TABLE ember_schema.event_role_restriction
(
    event_id INTEGER NOT NULL REFERENCES ember_schema.station_event (id) ON DELETE CASCADE,
    role_id  INTEGER NOT NULL REFERENCES ember_schema.role (id) ON DELETE CASCADE,
    PRIMARY KEY (event_id, role_id)
);

CREATE TABLE ember_schema.event_group_restriction
(
    event_id INTEGER NOT NULL REFERENCES ember_schema.station_event (id) ON DELETE CASCADE,
    group_id INTEGER NOT NULL REFERENCES ember_schema.member_group (id) ON DELETE CASCADE,
    PRIMARY KEY (event_id, group_id)
);
