-- Finishes the two chains that arrived unwalkable on every station that already existed.
--
-- The chains a station starts with were written into the database before the rules that say what a
-- chain has to look like existed. Two of them break those rules: the return to the association ends
-- with the gear still in the post, and the issue from the association is a single step, which asks
-- for gear and never says it arrived. A station opening its chains is told both are broken, on the
-- first day, about something nobody there wrote.
--
-- Only chains still standing exactly as they were written are touched. A station that changed one of
-- them made its own decision and keeps it, wrong or not.

INSERT INTO ember_schema.movement_flow_step (flow_id, position, label, actor, subject, custody_after, picks_item)
SELECT f.id, 2, 'Erhalten', 'OWNER', 'OUTGOING', 'WITH_OWNER', FALSE
FROM ember_schema.movement_flow f
WHERE f.station_id IS NOT NULL
  AND f.name = 'Rückgabe an den Träger'
  AND (SELECT count(*) FROM ember_schema.movement_flow_step s WHERE s.flow_id = f.id) = 2
  AND EXISTS (SELECT 1
              FROM ember_schema.movement_flow_step s
              WHERE s.flow_id = f.id
                AND s.position = 1
                AND s.label = 'An den Träger geschickt'
                AND s.custody_after = 'IN_TRANSIT');

UPDATE ember_schema.movement_flow_step s
SET position = 1
WHERE s.position = 0
  AND s.label = 'Vom Träger erhalten'
  AND (SELECT count(*) FROM ember_schema.movement_flow_step x WHERE x.flow_id = s.flow_id) = 1
  AND EXISTS (SELECT 1
              FROM ember_schema.movement_flow f
              WHERE f.id = s.flow_id
                AND f.station_id IS NOT NULL
                AND f.name = 'Ausgabe durch den Träger');

INSERT INTO ember_schema.movement_flow_step (flow_id, position, label, actor, subject, custody_after, picks_item)
SELECT f.id, 0, 'Bestellt', 'STATION', 'INCOMING', 'WITH_OWNER', FALSE
FROM ember_schema.movement_flow f
WHERE f.station_id IS NOT NULL
  AND f.name = 'Ausgabe durch den Träger'
  AND (SELECT count(*) FROM ember_schema.movement_flow_step s WHERE s.flow_id = f.id) = 1
  AND EXISTS (SELECT 1
              FROM ember_schema.movement_flow_step s
              WHERE s.flow_id = f.id
                AND s.position = 1
                AND s.label = 'Vom Träger erhalten');
