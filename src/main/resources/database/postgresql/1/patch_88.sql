DELETE
FROM ember_schema.document_template_restriction r
    USING ember_schema.document_template t
WHERE r.template_id = t.id
  AND NOT t.self_service;

DELETE
FROM ember_schema.document_template_station_use_restriction r
    USING ember_schema.document_template_station_use u
WHERE r.use_id = u.id
  AND NOT u.self_service;

COMMENT ON TABLE ember_schema.document_template_restriction IS
    'For whom of a station a template of the station is meant: the members its documents are generated for, by managers, runs, witnesses, appointments and self service. Empty means every member. A template of an association has none; each station sets its own audience in document_template_station_use_restriction. Shaped like event_restriction.';
COMMENT ON TABLE ember_schema.document_template_station_use_restriction IS
    'For whom of a station a template of its association is meant: the members its documents are generated for there, by managers, runs, witnesses, appointments and self service. Empty means every member. Shaped like event_restriction.';
