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
 * The files appointments hand over, the files a template hands every appointment made from it, and
 * the files an event group hands over on every part. Every read joins the media library, because an
 * attachment without its file name and size is not something a reader can be handed.
 *
 * <p>All live in tables of the same shape; {@link AppointmentOwner} says which one a call means, and the
 * methods naming an event group mean the event group's. A template's attachment reads back with the
 * template's id where an appointment's carries the appointment's, and an event group's with the event
 * group's. Read for one of its parts, an event group's file carries the part's id and says it comes from
 * the event group.
 */
@Singleton
public class EventAttachmentRepository {

    private static final Holder EVENT = new Holder("event_attachment", "event_id", "FALSE");
    private static final Holder TEMPLATE = new Holder("event_template_attachment", "template_id", "FALSE");
    private static final Holder GROUP = new Holder("event_attachment", "event_group_id", "TRUE");

    private static final String FILE_COLUMNS = """
            a.file_id, a.label, a.internal, a.sort_order, a.created_at,
            f.file_name, f.mime_type, f.file_size, f.content_hash""";

    /**
     * The files that take effect for the appointments {@code :event_ids}: the files of each one's event
     * group first, carrying the appointment's id, then its own.
     */
    private static final String EFFECTIVE = """
            SELECT a.id, e.id AS event_id, TRUE AS from_event_group, 0 AS rank, %1$s
            FROM event_attachment a
                     JOIN station_file f ON f.id = a.file_id
                     JOIN event_group_set gs ON gs.event_group_id = a.event_group_id
                     JOIN station_event e ON e.event_group_set_id = gs.id
            WHERE e.id = ANY(:event_ids)
            UNION ALL
            SELECT a.id, a.event_id, FALSE, 1, %1$s
            FROM event_attachment a
                     JOIN station_file f ON f.id = a.file_id
            WHERE a.event_id = ANY(:event_ids)""".formatted(FILE_COLUMNS);

    /** Where an owner's files are kept: the table, the column naming the owner, and whether it is an event group. */
    private record Holder(String table, String column, String fromEventGroup) {
        String columns() {
            return "a.id, a.%s AS event_id, %s AS from_event_group, %s".formatted(column, fromEventGroup, FILE_COLUMNS);
        }
    }

    private static Holder holder(AppointmentOwner owner) {
        return switch (owner) {
            case EVENT -> EVENT;
            case TEMPLATE -> TEMPLATE;
        };
    }

    /**
     * Attaches a file to an appointment or a template, placing it last.
     */
    public EventAttachment attach(
            AppointmentOwner owner, int ownerId, int fileId, @Nullable String label, boolean internal) {
        return attach(holder(owner), ownerId, fileId, label, internal);
    }

    /**
     * Attaches a file to an event group, placing it last among the event group's files.
     */
    public EventAttachment attachToGroup(int groupId, int fileId, @Nullable String label, boolean internal) {
        return attach(GROUP, groupId, fileId, label, internal);
    }

    private EventAttachment attach(Holder holder, int ownerId, int fileId, @Nullable String label, boolean internal) {
        int id = SqlSupport.insertReturning(
                """
                INSERT INTO %1$s(%2$s, file_id, label, internal, sort_order)
                VALUES (
                    :owner_id,
                    :file_id,
                    :label,
                    :internal,
                    COALESCE((SELECT MAX(sort_order) + 1 FROM %1$s WHERE %2$s = :owner_id), 0))
                RETURNING id;""".formatted(holder.table(), holder.column()),
                call().bind("owner_id", ownerId)
                        .bind("file_id", fileId)
                        .bind("label", label)
                        .bind("internal", internal),
                row -> row.getInt("id"));
        return findById(holder, id).orElseThrow();
    }

    public Optional<EventAttachment> findById(AppointmentOwner owner, int attachmentId) {
        return findById(holder(owner), attachmentId);
    }

    /**
     * @param attachmentId the attachment
     * @return the file of an event group, empty for none or for an appointment's
     */
    public Optional<EventAttachment> findGroupAttachment(int attachmentId) {
        return findById(GROUP, attachmentId);
    }

    private Optional<EventAttachment> findById(Holder holder, int attachmentId) {
        return query("""
                SELECT %s
                FROM %s a
                JOIN station_file f ON f.id = a.file_id
                WHERE a.id = :id
                  AND a.%s IS NOT NULL;""", holder.columns(), holder.table(), holder.column())
                .single(call().bind("id", attachmentId))
                .map(EventAttachment.map())
                .first();
    }

    public List<EventAttachment> findByOwner(AppointmentOwner owner, int ownerId) {
        return findByOwner(holder(owner), ownerId);
    }

    /**
     * @param groupId the event group
     * @return the files it hands over on every part, in their order
     */
    public List<EventAttachment> findByGroup(int groupId) {
        return findByOwner(GROUP, groupId);
    }

    private List<EventAttachment> findByOwner(Holder holder, int ownerId) {
        return query("""
                SELECT %s
                FROM %s a
                JOIN station_file f ON f.id = a.file_id
                WHERE a.%s = :owner_id
                ORDER BY a.sort_order, a.id;""", holder.columns(), holder.table(), holder.column())
                .single(call().bind("owner_id", ownerId))
                .map(EventAttachment.map())
                .all();
    }

    /**
     * The files that take effect for an appointment: for a part of an event group the event group's
     * files first, then its own.
     *
     * @param eventId the appointment
     * @return the files in their order, each carrying the appointment's id
     */
    public List<EventAttachment> findEffective(int eventId) {
        return findByEventIds(List.of(eventId)).getOrDefault(eventId, List.of());
    }

    /**
     * The files that take effect for several appointments at once, so a listing does not ask once per
     * row: for a part of an event group the event group's files first, then its own.
     */
    public Map<Integer, List<EventAttachment>> findByEventIds(List<Integer> eventIds) {
        Map<Integer, List<EventAttachment>> out = new HashMap<>();
        if (eventIds == null || eventIds.isEmpty()) return out;
        var rows = query("""
                %s
                ORDER BY event_id, rank, sort_order, id;""", EFFECTIVE)
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
        return update(holder(owner), attachmentId, label, internal);
    }

    /**
     * Writes the label of a file of an event group and whether it is kept back from the room.
     */
    public boolean updateGroupAttachment(int attachmentId, @Nullable String label, boolean internal) {
        return update(GROUP, attachmentId, label, internal);
    }

    private boolean update(Holder holder, int attachmentId, @Nullable String label, boolean internal) {
        return query(
                        "UPDATE %s SET label = :label, internal = :internal WHERE id = :id AND %s IS NOT NULL;",
                        holder.table(), holder.column())
                .single(call().bind("id", attachmentId).bind("label", label).bind("internal", internal))
                .update()
                .changed();
    }

    /**
     * Writes the order whoever wrote the appointment or template put the files in. Ids that do not
     * belong to it are ignored rather than moved, so a stale screen cannot reorder somebody else's.
     */
    public void reorder(AppointmentOwner owner, int ownerId, List<Integer> attachmentIds) {
        reorder(holder(owner), ownerId, attachmentIds);
    }

    /**
     * Writes the order of an event group's files, ignoring ids that are not the event group's.
     */
    public void reorderGroup(int groupId, List<Integer> attachmentIds) {
        reorder(GROUP, groupId, attachmentIds);
    }

    private void reorder(Holder holder, int ownerId, List<Integer> attachmentIds) {
        for (int i = 0; i < attachmentIds.size(); i++) {
            query(
                            "UPDATE %s SET sort_order = :sort_order WHERE id = :id AND %s = :owner_id;",
                            holder.table(), holder.column())
                    .single(call().bind("id", attachmentIds.get(i))
                            .bind("owner_id", ownerId)
                            .bind("sort_order", i))
                    .update();
        }
    }

    public boolean detach(AppointmentOwner owner, int attachmentId) {
        return detach(holder(owner), attachmentId);
    }

    /**
     * Takes a file off an event group, leaving it in the library.
     */
    public boolean detachFromGroup(int attachmentId) {
        return detach(GROUP, attachmentId);
    }

    private boolean detach(Holder holder, int attachmentId) {
        return query("DELETE FROM %s WHERE id = :id AND %s IS NOT NULL;", holder.table(), holder.column())
                .single(call().bind("id", attachmentId))
                .delete()
                .changed();
    }
}
