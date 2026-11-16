/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.repository;

import jakarta.inject.Singleton;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * What a copied appointment takes over from its original besides what the editor writes: the files it
 * hands over, the documents it asks participants to bring and the equipment it needs.
 *
 * <p>Only what the original holds itself is taken. What it shows from an event group stays with the
 * event group, since a copy belongs to none.
 */
@Singleton
public class EventCopyRepository {

    /**
     * Links every file of the original to the copy, in the original's order and with its labels, after
     * the files the copy already carries. The files are the same files of the library; nothing is
     * duplicated, and a file the copy already carries, from a template applied to it, is not linked a
     * second time.
     *
     * @param sourceEventId the original
     * @param targetEventId the copy
     * @return how many files were linked
     */
    public int copyAttachments(int sourceEventId, int targetEventId) {
        return query("""
                INSERT INTO event_attachment(event_id, file_id, label, internal, sort_order)
                SELECT :target_id, a.file_id, a.label, a.internal,
                       a.sort_order + (SELECT coalesce(max(t.sort_order) + 1, 0)
                                       FROM event_attachment t
                                       WHERE t.event_id = :target_id)
                FROM event_attachment a
                WHERE a.event_id = :source_id
                  AND NOT EXISTS (SELECT 1
                                  FROM event_attachment t
                                  WHERE t.event_id = :target_id
                                    AND t.file_id = a.file_id)
                ORDER BY a.sort_order, a.id;""")
                .single(call().bind("source_id", sourceEventId).bind("target_id", targetEventId))
                .insert()
                .rows();
    }

    /**
     * Hands the documents the original asks participants to bring to the copy, in the original's order.
     *
     * @param sourceEventId the original
     * @param targetEventId the copy
     * @return how many were handed over
     */
    public int copyDocumentRequirements(int sourceEventId, int targetEventId) {
        return query("""
                INSERT INTO event_document_requirement(event_id, template_id, position)
                SELECT :target_id, template_id, position
                FROM event_document_requirement
                WHERE event_id = :source_id
                ON CONFLICT DO NOTHING;""")
                .single(call().bind("source_id", sourceEventId).bind("target_id", targetEventId))
                .insert()
                .rows();
    }

    /**
     * Hands the equipment lines the original needs on every date to the copy, in the original's order.
     * A line written for one single date belongs to that date of the original and stays behind.
     *
     * @param sourceEventId the original
     * @param targetEventId the copy
     * @return how many lines were handed over
     */
    public int copyEquipmentNeeds(int sourceEventId, int targetEventId) {
        return query("""
                INSERT INTO event_equipment_need(event_id, event_date, item_id, art_id, inventory_id, quantity,
                                                 lead_minutes, trail_minutes, position)
                SELECT :target_id, NULL, item_id, art_id, inventory_id, quantity, lead_minutes, trail_minutes, position
                FROM event_equipment_need
                WHERE event_id = :source_id
                  AND event_date IS NULL
                ORDER BY position, id;""")
                .single(call().bind("source_id", sourceEventId).bind("target_id", targetEventId))
                .insert()
                .rows();
    }
}
