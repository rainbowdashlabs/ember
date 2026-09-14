/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.repository;

import de.chojo.sadu.postgresql.types.PostgreSqlTypes;
import dev.chojo.ember.feature.events.entity.EventAttachment;
import dev.chojo.ember.util.sql.SqlSupport;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * The files events hand over. Every read joins the media library, because an attachment without its
 * file name and size is not something a reader can be handed.
 */
@Singleton
public class EventAttachmentRepository {

    private static final String SELECT_COLUMNS = """
            a.id, a.event_id, a.file_id, a.label, a.internal, a.sort_order, a.created_at,
            f.file_name, f.mime_type, f.file_size, f.content_hash""";

    /**
     * Attaches a file to an event, placing it last.
     */
    public EventAttachment attach(int eventId, int fileId, String label, boolean internal) {
        int id = SqlSupport.insertReturning(
                """
                INSERT INTO event_attachment(event_id, file_id, label, internal, sort_order)
                VALUES (
                    :event_id,
                    :file_id,
                    :label,
                    :internal,
                    COALESCE((SELECT MAX(sort_order) + 1 FROM event_attachment WHERE event_id = :event_id), 0))
                RETURNING id;""",
                call().bind("event_id", eventId)
                        .bind("file_id", fileId)
                        .bind("label", label)
                        .bind("internal", internal),
                row -> row.getInt("id"));
        return findById(id).orElseThrow();
    }

    public Optional<EventAttachment> findById(int attachmentId) {
        return query("""
                SELECT %s
                FROM event_attachment a
                JOIN station_file f ON f.id = a.file_id
                WHERE a.id = :id;""", SELECT_COLUMNS)
                .single(call().bind("id", attachmentId))
                .map(EventAttachment.map())
                .first();
    }

    public List<EventAttachment> findByEvent(int eventId) {
        return query("""
                SELECT %s
                FROM event_attachment a
                JOIN station_file f ON f.id = a.file_id
                WHERE a.event_id = :event_id
                ORDER BY a.sort_order, a.id;""", SELECT_COLUMNS)
                .single(call().bind("event_id", eventId))
                .map(EventAttachment.map())
                .all();
    }

    /**
     * The attachments of several events at once, so a listing does not ask once per row.
     */
    public Map<Integer, List<EventAttachment>> findByEventIds(List<Integer> eventIds) {
        Map<Integer, List<EventAttachment>> out = new HashMap<>();
        if (eventIds == null || eventIds.isEmpty()) return out;
        var rows = query("""
                SELECT %s
                FROM event_attachment a
                JOIN station_file f ON f.id = a.file_id
                WHERE a.event_id = ANY(:event_ids)
                ORDER BY a.event_id, a.sort_order, a.id;""", SELECT_COLUMNS)
                .single(call().bind("event_ids", eventIds, PostgreSqlTypes.INTEGER))
                .map(EventAttachment.map())
                .all();
        for (var row : rows) {
            out.computeIfAbsent(row.eventId(), _ -> new ArrayList<>()).add(row);
        }
        return out;
    }

    /**
     * Writes the label and whether the file is kept back from the room.
     */
    public boolean update(int attachmentId, String label, boolean internal) {
        return query("UPDATE event_attachment SET label = :label, internal = :internal WHERE id = :id;")
                .single(call().bind("id", attachmentId).bind("label", label).bind("internal", internal))
                .update()
                .changed();
    }

    /**
     * Writes the order whoever wrote the event put the files in. Ids that do not belong to the event
     * are ignored rather than moved, so a stale screen cannot reorder somebody else's event.
     */
    public void reorder(int eventId, List<Integer> attachmentIds) {
        for (int i = 0; i < attachmentIds.size(); i++) {
            query("UPDATE event_attachment SET sort_order = :sort_order WHERE id = :id AND event_id = :event_id;")
                    .single(call().bind("id", attachmentIds.get(i))
                            .bind("event_id", eventId)
                            .bind("sort_order", i))
                    .update();
        }
    }

    public boolean detach(int attachmentId) {
        return SqlSupport.deleteById("event_attachment", attachmentId);
    }
}
