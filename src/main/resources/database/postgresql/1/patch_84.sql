UPDATE ember_schema.profile_field
SET required = FALSE
WHERE required
  AND field_type IN ('AGE', 'SECTION', 'SPACER');

UPDATE ember_schema.profile_field_assignment a
SET required_override = NULL
FROM ember_schema.profile_field f
WHERE f.id = a.field_id
  AND a.required_override IS NOT NULL
  AND f.field_type IN ('AGE', 'SECTION', 'SPACER');

UPDATE ember_schema.cluster_profile_field
SET required = FALSE
WHERE required
  AND field_type IN ('AGE', 'SECTION', 'SPACER');

UPDATE ember_schema.cluster_profile_field_assignment a
SET required_override = NULL
FROM ember_schema.cluster_profile_field f
WHERE f.id = a.field_id
  AND a.required_override IS NOT NULL
  AND f.field_type IN ('AGE', 'SECTION', 'SPACER');
