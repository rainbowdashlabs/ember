/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.procedure.repository;

import de.chojo.sadu.postgresql.types.PostgreSqlTypes;
import dev.chojo.ember.feature.procedure.entity.Procedure;
import dev.chojo.ember.feature.procedure.entity.ProcedureItem;
import dev.chojo.ember.feature.procedure.entity.ProcedureStatus;
import dev.chojo.ember.feature.procedure.entity.ProcedureStepKind;
import dev.chojo.ember.feature.procedure.entity.ProcedureTemplate;
import dev.chojo.ember.feature.procedure.entity.ProcedureTemplateItem;
import dev.chojo.ember.feature.procedure.entity.StepKindChoice;
import dev.chojo.ember.feature.restriction.RestrictionSql;
import dev.chojo.ember.feature.restriction.RestrictionType;
import dev.chojo.ember.util.sql.WhereBuilder;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
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
    /**
     * Whether the managing party of procedure {@code p} takes in {@code :member_id}. Unlike other
     * restrictions, a procedure with no party is run by nobody beyond those who edit procedures.
     */
    private static final String PARTY_ADMITS =
            "(EXISTS (SELECT 1 FROM procedure_restriction r WHERE r.procedure_id = p.id)" + " AND "
                    + RestrictionSql.admits(RestrictionType.PROCEDURE, "p.id", ":member_id") + ")";

    private static final String PROCEDURE_TEMPLATE_COLUMNS =
            "id, station_id, name, description, archived, created_by, created_at";
    private static final String PROCEDURE_TEMPLATE_ITEM_COLUMNS =
            "id, template_id, title, description, public, user_assigned, position, kind, document_template_id";
    private static final String PROCEDURE_COLUMNS = """
            id, station_id, template_id, name, description, public, status, assigned_by, due_at, \
            created_at, resolved_at, event_id, event_date, about_member_id, resolved_by""";
    private static final String PROCEDURE_ITEM_COLUMNS =
            "id, procedure_id, title, description, note, public, user_assigned, position, checked, checked_at, checked_by,"
                    + " kind, document_template_id, generation_id";

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

    public ProcedureTemplate createTemplate(int stationId, String name, @Nullable String description, int createdBy) {
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

    public boolean updateTemplate(int id, String name, @Nullable String description) {
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
            int templateId,
            String title,
            @Nullable String description,
            boolean isPublic,
            boolean userAssigned,
            int position) {
        return createTemplateItem(
                templateId, title, description, isPublic, userAssigned, position, StepKindChoice.CHECK);
    }

    /**
     * Adds a step to a template, asking for what it is given in the same write.
     *
     * @param kind what the step asks for
     * @return the step
     */
    public ProcedureTemplateItem createTemplateItem(
            int templateId,
            String title,
            @Nullable String description,
            boolean isPublic,
            boolean userAssigned,
            int position,
            StepKindChoice kind) {
        return insertReturning(
                """
                INSERT
                INTO
                    procedure_template_item(template_id, title, description, public, user_assigned, position, kind,
                                            document_template_id)
                VALUES
                    (:template_id, :title, :description, :public, :user_assigned, :position, :kind,
                     :document_template_id)
                RETURNING %s;""",
                call().bind("template_id", templateId)
                        .bind("title", title)
                        .bind("description", description)
                        .bind("public", isPublic)
                        .bind("user_assigned", userAssigned)
                        .bind("position", position)
                        .bind("kind", kind.kind().name())
                        .bind("document_template_id", kind.documentTemplateId()),
                ProcedureTemplateItem.map(),
                PROCEDURE_TEMPLATE_ITEM_COLUMNS);
    }

    public boolean updateTemplateItem(
            int id, String title, @Nullable String description, boolean isPublic, boolean userAssigned, int position) {
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

    /**
     * Makes a template step a step ticked by hand or a document to sign.
     *
     * @param id   the step
     * @param kind what the step asks for
     * @return whether the step was there
     */
    public boolean setTemplateItemKind(int id, StepKindChoice kind) {
        return query("""
                UPDATE procedure_template_item
                SET kind = :kind, document_template_id = :document_template_id
                WHERE id = :id;""")
                .single(call().bind("kind", kind.kind().name())
                        .bind("document_template_id", kind.documentTemplateId())
                        .bind("id", id))
                .update()
                .changed();
    }

    public boolean deleteTemplateItem(int id) {
        return deleteById("procedure_template_item", id);
    }

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

    public List<Procedure> findProceduresByStation(int stationId, @Nullable ProcedureStatus status) {
        var where = WhereBuilder.create().add("AND status = :status", "status", status);
        return query("""
                SELECT %s FROM procedure
                WHERE station_id = :station_id %s
                ORDER BY created_at DESC;""", PROCEDURE_COLUMNS, where.fragment())
                .single(where.apply(call().bind("station_id", stationId)))
                .map(Procedure.map())
                .all();
    }

    /**
     * The procedures whose managing party takes the member in. A procedure with no party is run by
     * nobody beyond those who edit procedures, so it is never among them.
     *
     * @param stationId  the station
     * @param memberId   the member
     * @param status     the state asked for, or null for both
     * @param publicOnly whether to leave out procedures that are not public
     * @return the procedures, newest first
     */
    public List<Procedure> findProceduresRunBy(
            int stationId, int memberId, @Nullable ProcedureStatus status, boolean publicOnly) {
        var where = WhereBuilder.create()
                .add("AND p.status = :status", "status", status)
                .addIf(publicOnly, "AND p.public");
        return query("""
                SELECT
                    %s
                FROM
                    procedure p
                WHERE p.station_id = :station_id
                  AND %s
                  %s
                ORDER BY p.created_at DESC;""", alias("p", PROCEDURE_COLUMNS), PARTY_ADMITS, where.fragment())
                .single(where.apply(call().bind("station_id", stationId).bind("member_id", memberId)))
                .map(Procedure.map())
                .all();
    }

    /**
     * The public procedures of a station about any of the given members, as the member and their
     * guardians read them among their own tasks.
     *
     * @param stationId the station
     * @param memberIds the members, the reader and those in their care
     * @param status    only procedures in this state, or null for all
     * @return the procedures, newest first
     */
    public List<Procedure> findPublicAbout(
            int stationId, Collection<Integer> memberIds, @Nullable ProcedureStatus status) {
        if (memberIds.isEmpty()) return List.of();
        var where = WhereBuilder.create().add("AND status = :status", "status", status);
        return query("""
                SELECT %s
                FROM procedure
                WHERE station_id = :station_id
                  AND public
                  AND about_member_id = ANY(:member_ids)
                  %s
                ORDER BY created_at DESC;""", PROCEDURE_COLUMNS, where.fragment())
                .single(where.apply(call().bind("station_id", stationId)
                        .bind("member_ids", List.copyOf(memberIds), PostgreSqlTypes.INTEGER)))
                .map(Procedure.map())
                .all();
    }

    /**
     * How many open public procedures of a station are about any of the given members and are not run by
     * the reader, who counts those among the procedures they run.
     *
     * @param stationId the station
     * @param memberId  the reader
     * @param memberIds the members, the reader and those in their care
     * @return the count
     */
    public int countOpenPublicAboutNotRunBy(int stationId, int memberId, Collection<Integer> memberIds) {
        if (memberIds.isEmpty()) return 0;
        return query("""
                SELECT count(*) AS count
                FROM procedure p
                WHERE p.station_id = :station_id
                  AND p.public
                  AND p.status = 'OPEN'
                  AND p.about_member_id = ANY(:member_ids)
                  AND NOT %s;""", PARTY_ADMITS)
                .single(call().bind("station_id", stationId)
                        .bind("member_id", memberId)
                        .bind("member_ids", List.copyOf(memberIds), PostgreSqlTypes.INTEGER))
                .map(row -> row.getInt("count"))
                .first()
                .orElse(0);
    }

    /**
     * The open procedures about a member that fall due before the given time and whose document steps were
     * not reminded of for it yet.
     *
     * @param now   the time from which they count as falling due
     * @param until the time they fall due before
     * @param limit how many to read at most
     * @return the procedures, soonest due first
     */
    public List<Procedure> findFallingDue(Instant now, Instant until, int limit) {
        return query("""
                SELECT %s
                FROM procedure
                WHERE status = 'OPEN'
                  AND about_member_id IS NOT NULL
                  AND due_reminded_at IS NULL
                  AND due_at > :now
                  AND due_at <= :until
                ORDER BY due_at
                LIMIT :limit;""", PROCEDURE_COLUMNS)
                .single(call().bind("now", now, INSTANT_TIMESTAMP)
                        .bind("until", until, INSTANT_TIMESTAMP)
                        .bind("limit", limit))
                .map(Procedure.map())
                .all();
    }

    /**
     * Records that a procedure's document steps were reminded of because it falls due, unless that was
     * recorded already.
     *
     * @param id    the procedure
     * @param dueAt the due date it was read with, which has to stand still
     * @param now   when
     * @return whether it was not recorded yet and is still due then
     */
    public boolean markDueReminded(int id, Instant dueAt, Instant now) {
        return query("""
                UPDATE procedure
                SET due_reminded_at = :now
                WHERE id = :id
                  AND due_reminded_at IS NULL
                  AND due_at = :due_at;""")
                .single(call().bind("now", now, INSTANT_TIMESTAMP)
                        .bind("due_at", dueAt, INSTANT_TIMESTAMP)
                        .bind("id", id))
                .update()
                .changed();
    }

    public Optional<Procedure> findProcedureById(int id) {
        return findById("procedure", PROCEDURE_COLUMNS, id, Procedure.map());
    }

    public Procedure createProcedure(
            int stationId,
            @Nullable Integer templateId,
            String name,
            @Nullable String description,
            boolean isPublic,
            int assignedBy,
            @Nullable Instant dueAt,
            @Nullable Integer eventId,
            @Nullable LocalDate eventDate,
            @Nullable Integer aboutMemberId) {
        return insertReturning(
                """
                INSERT INTO
                    procedure(station_id, template_id, name, description, public, assigned_by, due_at,
                              event_id, event_date, about_member_id)
                VALUES
                    (:station_id, :template_id, :name, :description, :public, :assigned_by, :due_at,
                     :event_id, :event_date, :about_member_id)
                RETURNING %s;""",
                call().bind("station_id", stationId)
                        .bind("template_id", templateId)
                        .bind("name", name)
                        .bind("description", description)
                        .bind("public", isPublic)
                        .bind("assigned_by", assignedBy)
                        .bind("due_at", dueAt, INSTANT_TIMESTAMP)
                        .bind("event_id", eventId)
                        .bind("event_date", eventDate)
                        .bind("about_member_id", aboutMemberId),
                Procedure.map(),
                PROCEDURE_COLUMNS);
    }

    /**
     * Every procedure prepared for one date of one appointment, newest first.
     *
     * <p>This is what stops a second press of the same button making a second list for the same
     * date: the caller is offered what is already there instead.
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

    public boolean updateProcedure(
            int id, String name, @Nullable String description, boolean isPublic, @Nullable Instant dueAt) {
        return query("""
                UPDATE procedure
                SET
                    name        = :name,
                    description = :description,
                    public      = :public,
                    due_reminded_at = CASE WHEN due_at IS DISTINCT FROM :due_at THEN NULL ELSE due_reminded_at END,
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

    /**
     * Resolves an open procedure, recording who did.
     *
     * @param id         the procedure
     * @param resolvedBy who resolves it
     * @return whether it was open
     */
    public boolean resolveProcedure(int id, int resolvedBy) {
        return query("""
                UPDATE procedure
                SET
                    status      = 'RESOLVED',
                    resolved_at = now(),
                    resolved_by = :resolved_by
                WHERE id = :id
                  AND status = 'OPEN';""")
                .single(call().bind("id", id).bind("resolved_by", resolvedBy))
                .update()
                .changed();
    }

    public boolean reopenProcedure(int id) {
        return query("""
                UPDATE procedure
                SET status = 'OPEN', resolved_at = NULL, resolved_by = NULL
                WHERE id = :id
                  AND status = 'RESOLVED';""").single(call().bind("id", id)).update().changed();
    }

    public boolean deleteProcedure(int id) {
        return deleteById("procedure", id);
    }

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
            int procedureId,
            String title,
            @Nullable String description,
            boolean isPublic,
            boolean userAssigned,
            int position) {
        return createItem(procedureId, title, description, isPublic, userAssigned, position, StepKindChoice.CHECK);
    }

    /**
     * Adds a step to a procedure, asking for what it is given in the same write.
     *
     * @param kind what the step asks for
     * @return the step
     */
    public ProcedureItem createItem(
            int procedureId,
            String title,
            @Nullable String description,
            boolean isPublic,
            boolean userAssigned,
            int position,
            StepKindChoice kind) {
        return insertReturning(
                """
                INSERT
                INTO
                    procedure_item(procedure_id, title, description, public, user_assigned, position, kind,
                                   document_template_id)
                VALUES
                    (:procedure_id, :title, :description, :public, :user_assigned, :position, :kind,
                     :document_template_id)
                RETURNING %s;""",
                call().bind("procedure_id", procedureId)
                        .bind("title", title)
                        .bind("description", description)
                        .bind("public", isPublic)
                        .bind("user_assigned", userAssigned)
                        .bind("position", position)
                        .bind("kind", kind.kind().name())
                        .bind("document_template_id", kind.documentTemplateId()),
                ProcedureItem.map(),
                PROCEDURE_ITEM_COLUMNS);
    }

    public boolean updateItem(
            int id, String title, @Nullable String description, boolean isPublic, boolean userAssigned, int position) {
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

    /**
     * Makes a step of a procedure a step ticked by hand or a document to sign, while it has generated
     * nothing yet. A step turned into a document step loses its tick, since only the signatures settle it.
     *
     * @param id   the step
     * @param kind what the step asks for
     * @return whether the step was there and had generated nothing yet
     */
    public boolean setItemKind(int id, StepKindChoice kind) {
        return query("""
                UPDATE procedure_item
                SET kind                 = :kind,
                    document_template_id = :document_template_id,
                    checked              = checked AND NOT :asks_to_sign,
                    checked_at           = CASE WHEN :asks_to_sign THEN NULL ELSE checked_at END,
                    checked_by           = CASE WHEN :asks_to_sign THEN NULL ELSE checked_by END
                WHERE id = :id
                  AND generation_id IS NULL;""")
                .single(call().bind("kind", kind.kind().name())
                        .bind("document_template_id", kind.documentTemplateId())
                        .bind("asks_to_sign", kind.kind() == ProcedureStepKind.DOCUMENT)
                        .bind("id", id))
                .update()
                .changed();
    }

    /**
     * Sets the member a procedure is about.
     *
     * @param procedureId   the procedure
     * @param aboutMemberId the member
     * @return whether the procedure was there
     */
    public boolean setAbout(int procedureId, int aboutMemberId) {
        return query("UPDATE procedure SET about_member_id = :about_member_id WHERE id = :id;")
                .single(call().bind("about_member_id", aboutMemberId).bind("id", procedureId))
                .update()
                .changed();
    }

    /**
     * Links a document step to the document it generated, unless another one was linked first.
     *
     * @param itemId       the step
     * @param generationId the generation log entry of its document
     * @return whether the step had no document yet and now has this one
     */
    public boolean linkGeneration(int itemId, int generationId) {
        return query("""
                UPDATE procedure_item
                SET generation_id = :generation_id
                WHERE id = :id
                  AND kind = 'DOCUMENT'
                  AND generation_id IS NULL;""")
                .single(call().bind("generation_id", generationId).bind("id", itemId))
                .update()
                .changed();
    }

    /**
     * Lets go of the document a step generated, so it asks for a new one.
     *
     * @param itemId       the step
     * @param generationId the document it is expected to hold
     * @return whether the step held that document
     */
    public boolean unlinkGeneration(int itemId, int generationId) {
        return query("""
                UPDATE procedure_item
                SET generation_id = NULL
                WHERE id = :id
                  AND generation_id = :generation_id;""")
                .single(call().bind("generation_id", generationId).bind("id", itemId))
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

    /**
     * The step that generated a document, if a step did.
     *
     * @param generationId the generation log entry of the document
     * @return the step
     */
    public Optional<ProcedureItem> findItemByGeneration(int generationId) {
        return query("SELECT %s FROM procedure_item WHERE generation_id = :generation_id;", PROCEDURE_ITEM_COLUMNS)
                .single(call().bind("generation_id", generationId))
                .map(ProcedureItem.map())
                .first();
    }

    /**
     * Ticks a document step because its document stands as agreed, by whoever settled it last.
     *
     * @param id        the step
     * @param settledBy who settled the document
     * @return whether it was not ticked yet
     */
    public boolean checkBySignatures(int id, int settledBy) {
        return query("""
                UPDATE procedure_item
                SET checked = TRUE, checked_at = now(), checked_by = :checked_by
                WHERE id = :id
                  AND kind = 'DOCUMENT'
                  AND checked = FALSE;""")
                .single(call().bind("id", id).bind("checked_by", settledBy))
                .update()
                .changed();
    }

    /**
     * Moves a document step from one document to its corrected version.
     *
     * @param replacedGenerationId the document it held
     * @param generationId         the corrected document
     * @return whether a step held the replaced document
     */
    public boolean relinkGeneration(int replacedGenerationId, int generationId) {
        return query("""
                UPDATE procedure_item
                SET generation_id = :generation_id
                WHERE generation_id = :replaced;""")
                .single(call().bind("generation_id", generationId).bind("replaced", replacedGenerationId))
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

    public ProcedureItem snapshotTemplateItem(int procedureId, ProcedureTemplateItem item) {
        return insertReturning(
                """
                INSERT
                INTO
                    procedure_item(procedure_id, title, description, public, user_assigned, position, kind,
                                   document_template_id)
                VALUES
                    (:procedure_id, :title, :description, :public, :user_assigned, :position, :kind,
                     :document_template_id)
                RETURNING %s;""",
                call().bind("procedure_id", procedureId)
                        .bind("title", item.title())
                        .bind("description", item.description())
                        .bind("public", item.isPublic())
                        .bind("user_assigned", item.userAssigned())
                        .bind("position", item.position())
                        .bind("kind", item.kind().name())
                        .bind("document_template_id", item.documentTemplateId()),
                ProcedureItem.map(),
                PROCEDURE_ITEM_COLUMNS);
    }

    /**
     * How many open public procedures the member's party runs that hold a step the member may tick now.
     * The party is asked once per procedure that has such a step, not once per step.
     *
     * @param stationId the station
     * @param memberId  the member
     * @return the count
     */
    public int countOpenRunByWithAvailableItems(int stationId, int memberId) {
        return count(
                """
                SELECT
                    count(*)
                FROM
                    procedure p
                WHERE p.station_id = :station_id
                  AND p.status = 'OPEN'
                  AND p.public
                  AND exists (
                    SELECT
                        1
                    FROM
                        procedure_item pi
                    WHERE pi.procedure_id = p.id
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
                                     )
                             )
                  AND %s;""".formatted(PARTY_ADMITS),
                call().bind("station_id", stationId).bind("member_id", memberId));
    }

    public int countOpenByStation(int stationId) {
        return count(
                "SELECT count(*) FROM procedure WHERE station_id = :station_id AND status = 'OPEN';",
                call().bind("station_id", stationId));
    }
}
