/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.procedure.repository;

import de.chojo.sadu.queries.api.call.Call;
import dev.chojo.ember.feature.procedure.entity.Procedure;
import dev.chojo.ember.feature.procedure.entity.ProcedureItem;
import dev.chojo.ember.feature.procedure.entity.ProcedureStatus;
import dev.chojo.ember.feature.procedure.entity.ProcedureTemplate;
import dev.chojo.ember.feature.procedure.entity.ProcedureTemplateItem;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

@Singleton
public class ProcedureRepository {

    // ── Templates ──

    public List<ProcedureTemplate> findTemplatesByStation(int stationId, boolean includeArchived) {
        var sql = includeArchived
                ? "SELECT * FROM procedure_template WHERE station_id = :station_id ORDER BY created_at DESC;"
                : "SELECT * FROM procedure_template WHERE station_id = :station_id AND archived = FALSE ORDER BY created_at DESC;";
        return query(sql)
                .single(Call.of().bind("station_id", stationId))
                .map(ProcedureTemplate.map())
                .all();
    }

    public Optional<ProcedureTemplate> findTemplateById(int id) {
        return query("SELECT * FROM procedure_template WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .map(ProcedureTemplate.map())
                .first();
    }

    public ProcedureTemplate createTemplate(int stationId, String name, String description, int createdBy) {
        return query("""
                
                    INSERT
                INTO
                    procedure_template(station_id, name, description, created_by)
                VALUES
                    (:station_id, :name, :description, :created_by)
                RETURNING *;""")
                .single(Call.of()
                            .bind("station_id", stationId)
                            .bind("name", name)
                            .bind("description", description)
                            .bind("created_by", createdBy))
                .map(ProcedureTemplate.map())
                .first()
                .orElseThrow();
    }

    public boolean updateTemplate(int id, String name, String description) {
        return query("UPDATE procedure_template SET name = :name, description = :description WHERE id = :id;")
                .single(Call.of()
                            .bind("name", name)
                            .bind("description", description)
                            .bind("id", id))
                .update()
                .changed();
    }

    public boolean archiveTemplate(int id) {
        return query("UPDATE procedure_template SET archived = TRUE WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .update()
                .changed();
    }

    // ── Template Items ──

    public List<ProcedureTemplateItem> findTemplateItems(int templateId) {
        return query("""
                SELECT *
                FROM
                    procedure_template_item
                WHERE template_id = :template_id
                ORDER BY position;""")
                .single(Call.of().bind("template_id", templateId))
                .map(ProcedureTemplateItem.map())
                .all();
    }

    public ProcedureTemplateItem createTemplateItem(
            int templateId, String title, String description, boolean isPublic, boolean userAssigned, int position) {
        return query("""
                INSERT
                INTO
                    procedure_template_item(template_id, title, description, public, user_assigned, position)
                VALUES
                    (:template_id, :title, :description, :public, :user_assigned, :position)
                RETURNING *;""")
                .single(Call.of()
                            .bind("template_id", templateId)
                            .bind("title", title)
                            .bind("description", description)
                            .bind("public", isPublic)
                            .bind("user_assigned", userAssigned)
                            .bind("position", position))
                .map(ProcedureTemplateItem.map())
                .first()
                .orElseThrow();
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
                .single(Call.of()
                            .bind("title", title)
                            .bind("description", description)
                            .bind("public", isPublic)
                            .bind("user_assigned", userAssigned)
                            .bind("position", position)
                            .bind("id", id))
                .update()
                .changed();
    }

    public boolean deleteTemplateItem(int id) {
        return query("DELETE FROM procedure_template_item WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .delete()
                .changed();
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
                .single(Call.of().bind("template_id", templateId))
                .map(row -> new int[]{row.getInt("item_id"), row.getInt("depends_on_item_id")})
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
                                 );""").single(Call.of().bind("template_id", templateId)).delete();
        for (int[] dep : dependencies) {
            query("""
                    INSERT
                    INTO
                        procedure_template_item_dependency(item_id, depends_on_item_id)
                    VALUES
                        (:item_id, :depends_on_item_id)
                    ON CONFLICT DO NOTHING;""")
                    .single(Call.of().bind("item_id", dep[0]).bind("depends_on_item_id", dep[1]))
                    .insert();
        }
    }

    // ── Procedures ──

    public List<Procedure> findProceduresByStation(int stationId, ProcedureStatus status) {
        if (status != null) {
            return query("""
                    SELECT * FROM procedure
                    WHERE station_id = :station_id AND status = :status
                    ORDER BY created_at DESC;""")
                    .single(Call.of().bind("station_id", stationId).bind("status", status))
                    .map(Procedure.map())
                    .all();
        }
        return query("SELECT * FROM procedure WHERE station_id = :station_id ORDER BY created_at DESC;")
                .single(Call.of().bind("station_id", stationId))
                .map(Procedure.map())
                .all();
    }

    public List<Procedure> findProceduresByAssignee(
            int stationId, int memberId, ProcedureStatus status, boolean publicOnly) {
        var publicFilter = publicOnly ? " AND p.public" : "";
        if (status != null) {
            return query("""
                    SELECT
                        p.*
                    FROM
                        procedure p
                            JOIN procedure_assignee pa
                            ON pa.procedure_id = p.id
                    WHERE p.station_id = :station_id
                      AND pa.member_id = :member_id
                      AND p.status = :status %s
                    ORDER BY p.created_at DESC;""", publicFilter)
                    .single(Call.of()
                                .bind("station_id", stationId)
                                .bind("member_id", memberId)
                                .bind("status", status))
                    .map(Procedure.map())
                    .all();
        }
        return query("""
                SELECT
                    p.*
                FROM
                    procedure p
                        JOIN procedure_assignee pa
                        ON pa.procedure_id = p.id
                WHERE p.station_id = :station_id
                  AND pa.member_id = :member_id %s
                ORDER BY p.created_at DESC;""", publicFilter)
                .single(Call.of().bind("station_id", stationId).bind("member_id", memberId))
                .map(Procedure.map())
                .all();
    }

    public Optional<Procedure> findProcedureById(int id) {
        return query("SELECT * FROM procedure WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .map(Procedure.map())
                .first();
    }

    public Procedure createProcedure(
            int stationId,
            Integer templateId,
            String name,
            String description,
            boolean isPublic,
            int assignedBy,
            Instant dueAt) {
        return query("""
                INSERT INTO procedure(station_id, template_id, name, description, public, assigned_by, due_at)
                VALUES(:station_id, :template_id, :name, :description, :public, :assigned_by, :due_at)
                RETURNING *;""")
                .single(Call.of()
                            .bind("station_id", stationId)
                            .bind("template_id", templateId)
                            .bind("name", name)
                            .bind("description", description)
                            .bind("public", isPublic)
                            .bind("assigned_by", assignedBy)
                            .bind("due_at", dueAt, INSTANT_TIMESTAMP))
                .map(Procedure.map())
                .first()
                .orElseThrow();
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
                .single(Call.of()
                            .bind("name", name)
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
                          AND status = 'OPEN';""")
                .single(Call.of().bind("id", id))
                .update()
                .changed();
    }

    public boolean reopenProcedure(int id) {
        return query(
                "UPDATE procedure SET status = 'OPEN', resolved_at = NULL WHERE id = :id AND status = 'RESOLVED';")
                .single(Call.of().bind("id", id))
                .update()
                .changed();
    }

    public boolean deleteProcedure(int id) {
        return query("DELETE FROM procedure WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .delete()
                .changed();
    }

    // ── Assignees ──

    public List<Integer> findAssigneeIds(int procedureId) {
        return query("SELECT member_id FROM procedure_assignee WHERE procedure_id = :procedure_id;")
                .single(Call.of().bind("procedure_id", procedureId))
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
                .single(Call.of().bind("procedure_id", procedureId).bind("member_id", memberId))
                .insert();
    }

    public boolean removeAssignee(int procedureId, int memberId) {
        return query(
                "DELETE FROM procedure_assignee WHERE procedure_id = :procedure_id AND member_id = :member_id;")
                .single(Call.of().bind("procedure_id", procedureId).bind("member_id", memberId))
                .delete()
                .changed();
    }

    // ── Procedure Items ──

    public List<ProcedureItem> findItems(int procedureId) {
        return query("SELECT * FROM procedure_item WHERE procedure_id = :procedure_id ORDER BY position;")
                .single(Call.of().bind("procedure_id", procedureId))
                .map(ProcedureItem.map())
                .all();
    }

    public Optional<ProcedureItem> findItemById(int id) {
        return query("SELECT * FROM procedure_item WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .map(ProcedureItem.map())
                .first();
    }

    public ProcedureItem createItem(
            int procedureId, String title, String description, boolean isPublic, boolean userAssigned, int position) {
        return query("""
                INSERT
                INTO
                    procedure_item(procedure_id, title, description, public, user_assigned, position)
                VALUES
                    (:procedure_id, :title, :description, :public, :user_assigned, :position)
                RETURNING *;""")
                .single(Call.of()
                            .bind("procedure_id", procedureId)
                            .bind("title", title)
                            .bind("description", description)
                            .bind("public", isPublic)
                            .bind("user_assigned", userAssigned)
                            .bind("position", position))
                .map(ProcedureItem.map())
                .first()
                .orElseThrow();
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
                .single(Call.of()
                            .bind("title", title)
                            .bind("description", description)
                            .bind("public", isPublic)
                            .bind("user_assigned", userAssigned)
                            .bind("position", position)
                            .bind("id", id))
                .update()
                .changed();
    }

    public boolean deleteItem(int id) {
        return query("DELETE FROM procedure_item WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .delete()
                .changed();
    }

    public boolean checkItem(int id, int checkedBy) {
        return query("""
                UPDATE procedure_item SET checked = TRUE, checked_at = now(), checked_by = :checked_by
                WHERE id = :id AND checked = FALSE;""")
                .single(Call.of().bind("checked_by", checkedBy).bind("id", id))
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
                  AND checked;""")
                .single(Call.of().bind("id", id))
                .update()
                .changed();
    }

    public boolean updateItemNote(int id, String note) {
        return query("UPDATE procedure_item SET note = :note WHERE id = :id;")
                .single(Call.of().bind("note", note).bind("id", id))
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
                .single(Call.of().bind("procedure_id", procedureId))
                .map(row -> new int[]{row.getInt("item_id"), row.getInt("depends_on_item_id")})
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
                                 );""")
                .single(Call.of().bind("procedure_id", procedureId)).delete();
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
                .single(Call.of().bind("item_id", itemId).bind("depends_on_item_id", dependsOnItemId))
                .insert();
    }

    // ── Snapshot (Template → Procedure) ──

    public ProcedureItem snapshotTemplateItem(int procedureId, ProcedureTemplateItem item) {
        return query("""
                INSERT
                INTO
                    procedure_item(procedure_id, title, description, public, user_assigned, position)
                VALUES
                    (:procedure_id, :title, :description, :public, :user_assigned, :position)
                RETURNING *;""")
                .single(Call.of()
                            .bind("procedure_id", procedureId)
                            .bind("title", item.title())
                            .bind("description", item.description())
                            .bind("public", item.isPublic())
                            .bind("user_assigned", item.userAssigned())
                            .bind("position", item.position()))
                .map(ProcedureItem.map())
                .first()
                .orElseThrow();
    }

    // ── Sidebar Counts ──

    public int countOpenByAssigneeWithAvailableItems(int stationId, int memberId) {
        return query("""
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
                                 );""")
                .single(Call.of().bind("station_id", stationId).bind("member_id", memberId))
                .map(row -> row.getInt("count"))
                .first()
                .orElse(0);
    }

    public int countOpenByStation(int stationId) {
        return query("SELECT count(*) FROM procedure WHERE station_id = :station_id AND status = 'OPEN';")
                .single(Call.of().bind("station_id", stationId))
                .map(row -> row.getInt("count"))
                .first()
                .orElse(0);
    }
}
