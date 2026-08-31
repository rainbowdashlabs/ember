/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.procedure.repository;

import dev.chojo.ember.feature.procedure.entity.Procedure;
import dev.chojo.ember.feature.procedure.entity.ProcedureItem;
import dev.chojo.ember.feature.procedure.entity.ProcedureStatus;
import dev.chojo.ember.feature.procedure.entity.ProcedureTemplate;
import dev.chojo.ember.feature.procedure.entity.ProcedureTemplateItem;
import dev.chojo.ember.util.sql.WhereBuilder;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;
import static dev.chojo.ember.util.sql.SqlSupport.alias;
import static dev.chojo.ember.util.sql.SqlSupport.count;
import static dev.chojo.ember.util.sql.SqlSupport.deleteById;
import static dev.chojo.ember.util.sql.SqlSupport.findById;
import static dev.chojo.ember.util.sql.SqlSupport.insertReturning;

@Singleton
public class ProcedureRepository {
    private static final String PROCEDURE_TEMPLATE_COLUMNS =
            "id, station_id, name, description, archived, created_by, created_at";
    private static final String PROCEDURE_TEMPLATE_ITEM_COLUMNS =
            "id, template_id, title, description, public, user_assigned, position";
    private static final String PROCEDURE_COLUMNS = """
            id, station_id, template_id, name, description, public, status, assigned_by, due_at, \
            created_at, resolved_at, event_id, event_date""";
    private static final String PROCEDURE_ITEM_COLUMNS =
            "id, procedure_id, title, description, note, public, user_assigned, position, checked, checked_at, checked_by";

    // ── Templates ──

    public List<ProcedureTemplate> findTemplatesByStation(int stationId, boolean includeArchived) {
        var where = WhereBuilder.create().addIf(!includeArchived, "AND archived = FALSE");
        return query(
                        "SELECT %s FROM procedure_template WHERE station_id = :station_id %s ORDER BY created_at DESC;",
                        PROCEDURE_TEMPLATE_COLUMNS, where.fragment())
                .single(where.apply(call().bind("station_id", stationId)))
                .map(ProcedureTemplate.map())
                .all();
    }

    public Optional<ProcedureTemplate> findTemplateById(int id) {
        return findById("procedure_template", PROCEDURE_TEMPLATE_COLUMNS, id, ProcedureTemplate.map());
    }

    public ProcedureTemplate createTemplate(int stationId, String name, String description, int createdBy) {
        return insertReturning(
                """

                    INSERT
                INTO
                    procedure_template(station_id, name, description, created_by)
                VALUES
                    (:station_id, :name, :description, :created_by)
                RETURNING %s;""",
                call().bind("station_id", stationId)
                        .bind("name", name)
                        .bind("description", description)
                        .bind("created_by", createdBy),
                ProcedureTemplate.map(),
                PROCEDURE_TEMPLATE_COLUMNS);
    }

    public boolean updateTemplate(int id, String name, String description) {
        return query("UPDATE procedure_template SET name = :name, description = :description WHERE id = :id;")
                .single(call().bind("name", name)
                        .bind("description", description)
                        .bind("id", id))
                .update()
                .changed();
    }

    public boolean archiveTemplate(int id) {
        return query("UPDATE procedure_template SET archived = TRUE WHERE id = :id;")
                .single(call().bind("id", id))
                .update()
                .changed();
    }

    // ── Template Items ──

    public List<ProcedureTemplateItem> findTemplateItems(int templateId) {
        return query("""
                SELECT %s
                FROM
                    procedure_template_item
                WHERE template_id = :template_id
                ORDER BY position;""", PROCEDURE_TEMPLATE_ITEM_COLUMNS)
                .single(call().bind("template_id", templateId))
                .map(ProcedureTemplateItem.map())
                .all();
    }

    public ProcedureTemplateItem createTemplateItem(
            int templateId, String title, String description, boolean isPublic, boolean userAssigned, int position) {
        return insertReturning(
                """
                INSERT
                INTO
                    procedure_template_item(template_id, title, description, public, user_assigned, position)
                VALUES
                    (:template_id, :title, :description, :public, :user_assigned, :position)
                RETURNING %s;""",
                call().bind("template_id", templateId)
                        .bind("title", title)
                        .bind("description", description)
                        .bind("public", isPublic)
                        .bind("user_assigned", userAssigned)
                        .bind("position", position),
                ProcedureTemplateItem.map(),
                PROCEDURE_TEMPLATE_ITEM_COLUMNS);
    }

    public boolean updateTemplateItem(
            int id, String title, String description, boolean isPublic, boolean userAssigned, int position) {
        return query("""
                UPDATE procedure_template_item
                SET
                    title         = :title,
                    description   = :description,
                    public        = :public,
                    user_assigned = :user_assigned,
                    position      = :position
                WHERE id = :id;""")
                .single(call().bind("title", title)
                        .bind("description", description)
                        .bind("public", isPublic)
                        .bind("user_assigned", userAssigned)
                        .bind("position", position)
                        .bind("id", id))
                .update()
                .changed();
    }

    public boolean deleteTemplateItem(int id) {
        return deleteById("procedure_template_item", id);
    }

    // ── Template Item Dependencies ──

    public List<int[]> findTemplateItemDependencies(int templateId) {
        return query("""
                SELECT
                    d.item_id,
                    d.depends_on_item_id
                FROM
                    procedure_template_item_dependency d
                        JOIN procedure_template_item i
                        ON i.id = d.item_id
                WHERE i.template_id = :template_id;""")
                .single(call().bind("template_id", templateId))
                .map(row -> new int[] {row.getInt("item_id"), row.getInt("depends_on_item_id")})
                .all();
    }

    public void setTemplateItemDependencies(int templateId, List<int[]> dependencies) {
        query("""
                DELETE
                FROM
                    procedure_template_item_dependency
                WHERE item_id IN (
                    SELECT id
                    FROM procedure_template_item
                    WHERE template_id = :template_id
                                 );""").single(call().bind("template_id", templateId)).delete();
        for (int[] dep : dependencies) {
            query("""
                    INSERT
                    INTO
                        procedure_template_item_dependency(item_id, depends_on_item_id)
                    VALUES
                        (:item_id, :depends_on_item_id)
                    ON CONFLICT DO NOTHING;""")
                    .single(call().bind("item_id", dep[0]).bind("depends_on_item_id", dep[1]))
                    .insert();
        }
    }

    // ── Procedures ──

    public List<Procedure> findProceduresByStation(int stationId, ProcedureStatus status) {
        var where = WhereBuilder.create().add("AND status = :status", "status", status);
        return query("""
                SELECT %s FROM procedure
                WHERE station_id = :station_id %s
                ORDER BY created_at DESC;""", PROCEDURE_COLUMNS, where.fragment())
                .single(where.apply(call().bind("station_id", stationId)))
                .map(Procedure.map())
                .all();
    }

    public List<Procedure> findProceduresByAssignee(
            int stationId, int memberId, ProcedureStatus status, boolean publicOnly) {
        var where = WhereBuilder.create()
                .add("AND p.status = :status", "status", status)
                .addIf(publicOnly, "AND p.public");
        return query("""
                SELECT
                    %s
                FROM
                    procedure p
                        JOIN procedure_assignee pa
                        ON pa.procedure_id = p.id
                WHERE p.station_id = :station_id
                  AND pa.member_id = :member_id
                  %s
                ORDER BY p.created_at DESC;""", alias("p", PROCEDURE_COLUMNS), where.fragment())
                .single(where.apply(call().bind("station_id", stationId).bind("member_id", memberId)))
                .map(Procedure.map())
                .all();
    }

    public Optional<Procedure> findProcedureById(int id) {
        return findById("procedure", PROCEDURE_COLUMNS, id, Procedure.map());
    }

    public Procedure createProcedure(
            int stationId,
            Integer templateId,
            String name,
            String description,
            boolean isPublic,
            int assignedBy,
            Instant dueAt,
            Integer eventId,
            LocalDate eventDate) {
        return insertReturning(
                """
                INSERT INTO
                    procedure(station_id, template_id, name, description, public, assigned_by, due_at,
                              event_id, event_date)
                VALUES
                    (:station_id, :template_id, :name, :description, :public, :assigned_by, :due_at,
                     :event_id, :event_date)
                RETURNING %s;""",
                call().bind("station_id", stationId)
                        .bind("template_id", templateId)
                        .bind("name", name)
                        .bind("description", description)
                        .bind("public", isPublic)
                        .bind("assigned_by", assignedBy)
                        .bind("due_at", dueAt, INSTANT_TIMESTAMP)
                        .bind("event_id", eventId)
                        .bind("event_date", eventDate),
                Procedure.map(),
                PROCEDURE_COLUMNS);
    }

    /**
     * Every procedure prepared for one evening of one appointment, newest first.
     *
     * <p>This is what stops a second press of the same button making a second list for the same
     * evening: the caller is offered what is already there instead.
     *
     * @param stationId the station the appointment belongs to
     * @param eventId   the appointment
     * @param eventDate the one occurrence of it
     * @return the procedures recorded against that occurrence
     */
    public List<Procedure> findProceduresByOccurrence(int stationId, int eventId, LocalDate eventDate) {
        return query("""
                SELECT %s FROM procedure
                WHERE station_id = :station_id
                  AND event_id = :event_id
                  AND event_date = :event_date
                ORDER BY created_at DESC;""", PROCEDURE_COLUMNS)
                .single(call().bind("station_id", stationId)
                        .bind("event_id", eventId)
                        .bind("event_date", eventDate))
                .map(Procedure.map())
                .all();
    }

    public boolean updateProcedure(int id, String name, String description, boolean isPublic, Instant dueAt) {
        return query("""
                UPDATE procedure
                SET
                    name        = :name,
                    description = :description,
                    public      = :public,
                    due_at      = :due_at
                WHERE id = :id;""")
                .single(call().bind("name", name)
                        .bind("description", description)
                        .bind("public", isPublic)
                        .bind("due_at", dueAt, INSTANT_TIMESTAMP)
                        .bind("id", id))
                .update()
                .changed();
    }

    public boolean resolveProcedure(int id) {
        return query("""
                UPDATE procedure
                SET
                    status      = 'RESOLVED',
                    resolved_at = now()
                WHERE id = :id
                  AND status = 'OPEN';""").single(call().bind("id", id)).update().changed();
    }

    public boolean reopenProcedure(int id) {
        return query("UPDATE procedure SET status = 'OPEN', resolved_at = NULL WHERE id = :id AND status = 'RESOLVED';")
                .single(call().bind("id", id))
                .update()
                .changed();
    }

    public boolean deleteProcedure(int id) {
        return deleteById("procedure", id);
    }

    // ── Assignees ──

    public List<Integer> findAssigneeIds(int procedureId) {
        return query("SELECT member_id FROM procedure_assignee WHERE procedure_id = :procedure_id;")
                .single(call().bind("procedure_id", procedureId))
                .map(row -> row.getInt("member_id"))
                .all();
    }

    public void addAssignee(int procedureId, int memberId) {
        query("""
                INSERT
                INTO
                    procedure_assignee(procedure_id, member_id)
                VALUES
                    (:procedure_id, :member_id)
                ON CONFLICT DO NOTHING;""")
                .single(call().bind("procedure_id", procedureId).bind("member_id", memberId))
                .insert();
    }

    public boolean removeAssignee(int procedureId, int memberId) {
        return query("DELETE FROM procedure_assignee WHERE procedure_id = :procedure_id AND member_id = :member_id;")
                .single(call().bind("procedure_id", procedureId).bind("member_id", memberId))
                .delete()
                .changed();
    }

    // ── Procedure Items ──

    public List<ProcedureItem> findItems(int procedureId) {
        return query(
                        "SELECT %s FROM procedure_item WHERE procedure_id = :procedure_id ORDER BY position;",
                        PROCEDURE_ITEM_COLUMNS)
                .single(call().bind("procedure_id", procedureId))
                .map(ProcedureItem.map())
                .all();
    }

    public Optional<ProcedureItem> findItemById(int id) {
        return findById("procedure_item", PROCEDURE_ITEM_COLUMNS, id, ProcedureItem.map());
    }

    public ProcedureItem createItem(
            int procedureId, String title, String description, boolean isPublic, boolean userAssigned, int position) {
        return insertReturning(
                """
                INSERT
                INTO
                    procedure_item(procedure_id, title, description, public, user_assigned, position)
                VALUES
                    (:procedure_id, :title, :description, :public, :user_assigned, :position)
                RETURNING %s;""",
                call().bind("procedure_id", procedureId)
                        .bind("title", title)
                        .bind("description", description)
                        .bind("public", isPublic)
                        .bind("user_assigned", userAssigned)
                        .bind("position", position),
                ProcedureItem.map(),
                PROCEDURE_ITEM_COLUMNS);
    }

    public boolean updateItem(
            int id, String title, String description, boolean isPublic, boolean userAssigned, int position) {
        return query("""
                UPDATE procedure_item
                SET
                    title         = :title,
                    description   = :description,
                    public        = :public,
                    user_assigned = :user_assigned,
                    position      = :position
                WHERE id = :id;""")
                .single(call().bind("title", title)
                        .bind("description", description)
                        .bind("public", isPublic)
                        .bind("user_assigned", userAssigned)
                        .bind("position", position)
                        .bind("id", id))
                .update()
                .changed();
    }

    public boolean deleteItem(int id) {
        return deleteById("procedure_item", id);
    }

    public boolean checkItem(int id, int checkedBy) {
        return query("""
                UPDATE procedure_item SET checked = TRUE, checked_at = now(), checked_by = :checked_by
                WHERE id = :id AND checked = FALSE;""")
                .single(call().bind("checked_by", checkedBy).bind("id", id))
                .update()
                .changed();
    }

    public boolean uncheckItem(int id) {
        return query("""
                UPDATE procedure_item
                SET
                    checked    = FALSE,
                    checked_at = NULL,
                    checked_by = NULL
                WHERE id = :id
                  AND checked;""").single(call().bind("id", id)).update().changed();
    }

    public boolean updateItemNote(int id, String note) {
        return query("UPDATE procedure_item SET note = :note WHERE id = :id;")
                .single(call().bind("note", note).bind("id", id))
                .update()
                .changed();
    }

    // ── Procedure Item Dependencies ──

    public List<int[]> findItemDependencies(int procedureId) {
        return query("""
                SELECT
                    d.item_id,
                    d.depends_on_item_id
                FROM
                    procedure_item_dependency d
                        JOIN procedure_item i
                        ON i.id = d.item_id
                WHERE i.procedure_id = :procedure_id;""")
                .single(call().bind("procedure_id", procedureId))
                .map(row -> new int[] {row.getInt("item_id"), row.getInt("depends_on_item_id")})
                .all();
    }

    public void setItemDependencies(int procedureId, List<int[]> dependencies) {
        query("""
                DELETE
                FROM
                    procedure_item_dependency
                WHERE item_id IN (
                    SELECT id
                    FROM procedure_item
                    WHERE procedure_id = :procedure_id
                                 );""").single(call().bind("procedure_id", procedureId)).delete();
        for (int[] dep : dependencies) {
            addItemDependency(dep[0], dep[1]);
        }
    }

    public void addItemDependency(int itemId, int dependsOnItemId) {
        query("""
                INSERT
                INTO
                    procedure_item_dependency(item_id, depends_on_item_id)
                VALUES
                    (:item_id, :depends_on_item_id)
                ON CONFLICT DO NOTHING;""")
                .single(call().bind("item_id", itemId).bind("depends_on_item_id", dependsOnItemId))
                .insert();
    }

    // ── Snapshot (Template → Procedure) ──

    public ProcedureItem snapshotTemplateItem(int procedureId, ProcedureTemplateItem item) {
        return insertReturning(
                """
                INSERT
                INTO
                    procedure_item(procedure_id, title, description, public, user_assigned, position)
                VALUES
                    (:procedure_id, :title, :description, :public, :user_assigned, :position)
                RETURNING %s;""",
                call().bind("procedure_id", procedureId)
                        .bind("title", item.title())
                        .bind("description", item.description())
                        .bind("public", item.isPublic())
                        .bind("user_assigned", item.userAssigned())
                        .bind("position", item.position()),
                ProcedureItem.map(),
                PROCEDURE_ITEM_COLUMNS);
    }

    // ── Sidebar Counts ──

    public int countOpenByAssigneeWithAvailableItems(int stationId, int memberId) {
        return count("""
                SELECT
                    count(DISTINCT p.id)
                FROM
                    procedure p
                        JOIN procedure_assignee pa
                        ON pa.procedure_id = p.id
                        JOIN procedure_item pi
                        ON pi.procedure_id = p.id
                WHERE p.station_id = :station_id
                  AND pa.member_id = :member_id
                  AND p.status = 'OPEN'
                  AND NOT pi.checked
                  AND pi.public
                  AND pi.user_assigned
                  AND NOT exists (
                    SELECT
                        1
                    FROM
                        procedure_item_dependency d
                            JOIN procedure_item dep
                            ON dep.id = d.depends_on_item_id
                    WHERE d.item_id = pi.id
                      AND NOT dep.checked
                                 );""", call().bind("station_id", stationId).bind("member_id", memberId));
    }

    public int countOpenByStation(int stationId) {
        return count(
                "SELECT count(*) FROM procedure WHERE station_id = :station_id AND status = 'OPEN';",
                call().bind("station_id", stationId));
    }
}
