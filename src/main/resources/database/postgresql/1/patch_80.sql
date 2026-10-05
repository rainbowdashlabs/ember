INSERT INTO ember_schema.station_permission (name)
VALUES ('PROTOCOL_SHARE'), ('TEST_CATALOG_SHARE')
ON CONFLICT (name) DO NOTHING;
