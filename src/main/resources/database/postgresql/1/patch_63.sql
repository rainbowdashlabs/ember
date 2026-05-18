-- Track which member manager triggered actions on behalf of members
ALTER TABLE ember_schema.member_absence
    ADD COLUMN created_by INTEGER REFERENCES ember_schema.station_member (id) ON DELETE SET NULL;

ALTER TABLE ember_schema.equipment_exchange_request
    ADD COLUMN created_by INTEGER REFERENCES ember_schema.station_member (id) ON DELETE SET NULL;

ALTER TABLE ember_schema.event_registration
    ADD COLUMN created_by INTEGER REFERENCES ember_schema.station_member (id) ON DELETE SET NULL;
