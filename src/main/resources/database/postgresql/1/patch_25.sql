INSERT INTO ember_schema.account (email, first_name, last_name, email_verified, creating_station_id)
SELECT DISTINCT ON (i.email) i.email, i.first_name, i.last_name, TRUE, i.station_id
FROM ember_schema.station_member_invite i
WHERE i.accepted_at IS NULL
  AND NOT EXISTS (SELECT 1 FROM ember_schema.account a WHERE a.email = i.email)
ORDER BY i.email, i.created_at;

INSERT INTO ember_schema.station_member (station_id, account_id, user_type)
SELECT DISTINCT ON (i.station_id, a.id) i.station_id, a.id, i.user_type
FROM ember_schema.station_member_invite i
JOIN ember_schema.account a ON a.email = i.email
WHERE i.accepted_at IS NULL
ORDER BY i.station_id, a.id, i.created_at
ON CONFLICT (station_id, account_id) DO NOTHING;

INSERT INTO ember_schema.member_group_entry (group_id, member_id)
SELECT DISTINCT i.group_id, sm.id
FROM ember_schema.station_member_invite i
JOIN ember_schema.account a ON a.email = i.email
JOIN ember_schema.station_member sm ON sm.station_id = i.station_id AND sm.account_id = a.id
WHERE i.accepted_at IS NULL
  AND i.group_id IS NOT NULL
ON CONFLICT DO NOTHING;

INSERT INTO ember_schema.member_manager (manager_id, managed_id)
SELECT DISTINCT gsm.id, msm.id
FROM ember_schema.station_member_invite g
JOIN ember_schema.station_member_invite p ON p.id = g.guardian_of_invite_id
JOIN ember_schema.account ga ON ga.email = g.email
JOIN ember_schema.station_member gsm ON gsm.station_id = g.station_id AND gsm.account_id = ga.id
JOIN ember_schema.account pa ON pa.email = p.email
JOIN ember_schema.station_member msm ON msm.station_id = p.station_id AND msm.account_id = pa.id
WHERE g.accepted_at IS NULL
  AND g.guardian_of_invite_id IS NOT NULL
ON CONFLICT DO NOTHING;

DROP TABLE ember_schema.station_member_invite;
