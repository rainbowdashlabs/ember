/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.repository;

import de.chojo.sadu.postgresql.types.PostgreSqlTypes;
import dev.chojo.ember.feature.events.entity.AppointmentOwner;
import dev.chojo.ember.feature.events.entity.EventAttachment;
import dev.chojo.ember.util.sql.SqlSupport;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * The files appointments hand over, and the files a template hands every appointment made from it.
 * Every read joins the media library, because an attachment without its file name and size is not
 * something a reader can be handed.
 *
 * <p>Both live in tables of the same shape; {@link AppointmentOwner} says which one a call means. A
 * template's attachment reads back with the template's id where an appointment's carries the
 * appointment's.
 */
@Singleton
public class EventAttachmentRepository {

    private static final String SELECT_COLUMNS = """
            a.id, a.%s AS event_id, a.file_id, a.label, a.internal, a.sort_order, a.created_at,
            f.file_name, f.mime_type, f.file_size, f.content_hash""";

    private static String table(AppointmentOwner owner) {
        return switch (owner) {
            case EVENT -> "event_attachment";
            case TEMPLATE -> "event_template_attachment";
        };
    }

    private static String ownerColumn(AppointmentOwner owner) {
        return switch (owner) {
            case EVENT -> "event_id";
            case TEMPLATE -> "template_id";
        };
    }

    private static String columns(AppointmentOwner owner) {
        return SELECT_COLUMNS.formatted(ownerColumn(owner));
    }

    /**
     * Attaches a file to an appointment or a template, placing it last.
     */
    public EventAttachment attach(
            AppointmentOwner owner, int ownerId, int fileId, @Nullable String label, boolean internal) {
        int id = SqlSupport.insertReturning(
                """
                INSERT INTO %1$s(%2$s, file_id, label, internal, sort_order)
                VALUES (
                    :owner_id,
                    :file_id,
                    :label,
                    :internal,
                    COALESCE((SELECT MAX(sort_order) + 1 FROM %1$s WHERE %2$s = :owner_id), 0))
                RETURNING id;""".formatted(table(owner), ownerColumn(owner)),
                call().bind("owner_id", ownerId)
                        .bind("file_id", fileId)
                        .bind("label", label)
                        .bind("internal", internal),
                row -> row.getInt("id"));
        return findById(owner, id).orElseThrow();
    }

    public Optional<EventAttachment> findById(AppointmentOwner owner, int attachmentId) {
        return query("""
                SELECT %s
                FROM %s a
                JOIN station_file f ON f.id = a.file_id
                WHERE a.id = :id;""", columns(owner), table(owner))
                .single(call().bind("id", attachmentId))
                .map(EventAttachment.map())
                .first();
    }

    public List<EventAttachment> findByOwner(AppointmentOwner owner, int ownerId) {
        return query("""
                SELECT %s
                FROM %s a
                JOIN station_file f ON f.id = a.file_id
                WHERE a.%s = :owner_id
                ORDER BY a.sort_order, a.id;""", columns(owner), table(owner), ownerColumn(owner))
                .single(call().bind("owner_id", ownerId))
                .map(EventAttachment.map())
                .all();
    }

    /**
     * The attachments of several appointments at once, so a listing does not ask once per row.
     */
    public Map<Integer, List<EventAttachment>> findByEventIds(List<Integer> eventIds) {
        Map<Integer, List<EventAttachment>> out = new HashMap<>();
        if (eventIds == null || eventIds.isEmpty()) return out;
        var rows = query("""
                SELECT %s
                FROM event_attachment a
                JOIN station_file f ON f.id = a.file_id
                WHERE a.event_id = ANY(:event_ids)
                ORDER BY a.event_id, a.sort_order, a.id;""", columns(AppointmentOwner.EVENT))
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
    public boolean update(AppointmentOwner owner, int attachmentId, @Nullable String label, boolean internal) {
        return query("UPDATE %s SET label = :label, internal = :internal WHERE id = :id;", table(owner))
                .single(call().bind("id", attachmentId).bind("label", label).bind("internal", internal))
                .update()
                .changed();
    }

    /**
     * Writes the order whoever wrote the appointment or template put the files in. Ids that do not
     * belong to it are ignored rather than moved, so a stale screen cannot reorder somebody else's.
     */
    public void reorder(AppointmentOwner owner, int ownerId, List<Integer> attachmentIds) {
        for (int i = 0; i < attachmentIds.size(); i++) {
            query(
                            "UPDATE %s SET sort_order = :sort_order WHERE id = :id AND %s = :owner_id;",
                            table(owner), ownerColumn(owner))
                    .single(call().bind("id", attachmentIds.get(i))
                            .bind("owner_id", ownerId)
                            .bind("sort_order", i))
                    .update();
        }
    }

    public boolean detach(AppointmentOwner owner, int attachmentId) {
        return SqlSupport.deleteById(table(owner), attachmentId);
    }
}
