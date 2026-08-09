/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.checklist.repository;

import de.chojo.sadu.postgresql.types.PostgreSqlTypes;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.checklist.entity.Checklist;
import dev.chojo.ember.feature.checklist.entity.ChecklistCell;
import dev.chojo.ember.feature.checklist.entity.ChecklistCellNoteHistory;
import dev.chojo.ember.feature.checklist.entity.ChecklistColumn;
import dev.chojo.ember.feature.checklist.entity.ChecklistEntry;
import dev.chojo.ember.feature.checklist.entity.ChecklistSummary;
import dev.chojo.ember.feature.restriction.Restriction;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import dev.chojo.ember.feature.restriction.RestrictionSelection;
import dev.chojo.ember.feature.restriction.RestrictionSql;
import dev.chojo.ember.util.sql.SqlSupport;
import dev.chojo.ember.util.sql.WhereBuilder;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;
import static dev.chojo.ember.util.sql.SqlSupport.alias;
import static dev.chojo.ember.util.sql.SqlSupport.count;
import static dev.chojo.ember.util.sql.SqlSupport.deleteById;
import static dev.chojo.ember.util.sql.SqlSupport.insertReturning;
import static dev.chojo.ember.util.sql.SqlSupport.reorder;

/**
 * Persistence for checklists, their columns, member-filter rows, entries, cells, and note history.
 * Member resolution against the materialisation filter is delegated to the service layer.
 */
@Singleton
public class ChecklistRepository {
    private static final String CHECKLIST_COLUMNS =
            "id, station_id, name, description, restriction_mode, created_at, created_by, last_refreshed_at";
    private static final String CHECKLIST_COLUMN_COLUMNS = "id, checklist_id, position, label, description";
    private static final String CHECKLIST_ENTRY_COLUMNS = "id, checklist_id, member_id, added_at, deleted_at";
    private static final String CHECKLIST_CELL_COLUMNS =
            "id, entry_id, column_id, checked, note, updated_at, updated_by";
    private static final String CHECKLIST_CELL_NOTE_HISTORY_COLUMNS =
            "id, cell_id, old_note, new_note, changed_by, changed_at";
    private static final String MEMBER_FILTER_TABLE = "checklist_member_filter";
    private static final String CHECKLIST_FK = "checklist_id";

    public List<ChecklistSummary> findSummariesByStation(int stationId) {
        return query("""
                        SELECT c.id,
                               c.name,
                               c.description,
                               c.last_refreshed_at,
                               c.created_at,
                               (SELECT count(*) FROM checklist_entry e
                                  WHERE e.checklist_id = c.id AND e.deleted_at IS NULL)::INT AS member_count,
                               (SELECT count(*) FROM checklist_column col
                                  WHERE col.checklist_id = c.id)::INT AS column_count
                          FROM checklist c
                         WHERE c.station_id = :station_id
                         ORDER BY c.created_at DESC;""")
                .single(call().bind("station_id", stationId))
                .map(ChecklistSummary.map())
                .all();
    }

    public Optional<Checklist> findById(int id) {
        return SqlSupport.findById("checklist", CHECKLIST_COLUMNS, id, Checklist.map());
    }

    public Checklist create(int stationId, String name, String description, RestrictionMode mode, int createdBy) {
        return insertReturning(
                """
                INSERT INTO
                    checklist(station_id, name, description, restriction_mode, created_by, last_refreshed_at)
                VALUES
                    (:station_id, :name, :description, :mode, :created_by, :refreshed_at)
                RETURNING %s;""",
                call().bind("station_id", stationId)
                        .bind("name", name)
                        .bind("description", description != null ? description : "")
                        .bind("mode", mode.name())
                        .bind("created_by", createdBy)
                        .bind("refreshed_at", Instant.now(), INSTANT_TIMESTAMP),
                Checklist.map(),
                CHECKLIST_COLUMNS);
    }

    public void updateMetadata(int id, String name, String description, RestrictionMode mode) {
        query("""
                        UPDATE checklist
                           SET name = :name,
                               description = :description,
                               restriction_mode = :mode
                         WHERE id = :id;""")
                .single(call().bind("id", id)
                        .bind("name", name)
                        .bind("description", description != null ? description : "")
                        .bind("mode", mode.name()))
                .update();
    }

    public void touchRefreshed(int id) {
        query("UPDATE checklist SET last_refreshed_at = :ts WHERE id = :id;")
                .single(call().bind("id", id).bind("ts", Instant.now(), INSTANT_TIMESTAMP))
                .update();
    }

    public boolean delete(int id) {
        return deleteById("checklist", id);
    }

    public List<ChecklistColumn> findColumns(int checklistId) {
        return query(
                        "SELECT %s FROM checklist_column WHERE checklist_id = :checklist_id ORDER BY position, id;",
                        CHECKLIST_COLUMN_COLUMNS)
                .single(call().bind("checklist_id", checklistId))
                .map(ChecklistColumn.map())
                .all();
    }

    public Optional<ChecklistColumn> findColumn(int columnId) {
        return SqlSupport.findById("checklist_column", CHECKLIST_COLUMN_COLUMNS, columnId, ChecklistColumn.map());
    }

    public int nextColumnPosition(int checklistId) {
        return query(
                        "SELECT coalesce(max(position), -1) + 1 AS next FROM checklist_column WHERE checklist_id = :checklist_id;")
                .single(call().bind("checklist_id", checklistId))
                .map(row -> row.getInt("next"))
                .first()
                .orElse(0);
    }

    public ChecklistColumn createColumn(int checklistId, int position, String label, String description) {
        return insertReturning(
                """
                INSERT INTO
                    checklist_column(checklist_id, position, label, description)
                VALUES
                    (:checklist_id, :position, :label, :description)
                RETURNING %s;""",
                call().bind("checklist_id", checklistId)
                        .bind("position", position)
                        .bind("label", label)
                        .bind("description", description != null ? description : ""),
                ChecklistColumn.map(),
                CHECKLIST_COLUMN_COLUMNS);
    }

    public void updateColumn(int columnId, String label, String description, int position) {
        query("""
                        UPDATE checklist_column
                           SET label = :label,
                               description = :description,
                               position = :position
                         WHERE id = :id;""")
                .single(call().bind("id", columnId)
                        .bind("label", label)
                        .bind("description", description != null ? description : "")
                        .bind("position", position))
                .update();
    }

    /**
     * Rewrites the position of every column that belongs to {@code checklistId} to match
     * {@code orderedIds}, landing each column on its 0-based index in the list.
     */
    public void reorderColumns(int checklistId, List<Integer> orderedIds) {
        reorder("checklist_column", "position", "checklist_id", checklistId, orderedIds);
    }

    public int countCheckedCellsInColumn(int columnId) {
        return count(
                "SELECT count(*) AS cnt FROM checklist_cell WHERE column_id = :col AND checked = TRUE;",
                call().bind("col", columnId));
    }

    public boolean deleteColumn(int columnId) {
        return deleteById("checklist_column", columnId);
    }

    public List<Restriction> findFilterRows(int checklistId) {
        return RestrictionSql.findRows(MEMBER_FILTER_TABLE, CHECKLIST_FK, checklistId);
    }

    public void replaceFilter(
            int checklistId,
            List<StationUserType> userTypes,
            List<Integer> groupIds,
            List<Integer> tagIds,
            List<Integer> memberIds) {
        RestrictionSql.replace(
                MEMBER_FILTER_TABLE,
                CHECKLIST_FK,
                checklistId,
                new RestrictionSelection(userTypes, groupIds, tagIds, memberIds, null));
    }

    public List<ChecklistEntry> findEntries(int checklistId, boolean includeDeleted) {
        var where = WhereBuilder.create().addIf(!includeDeleted, "AND deleted_at IS NULL");
        return query("""
                        SELECT %s
                          FROM checklist_entry
                         WHERE checklist_id = :checklist_id %s
                         ORDER BY added_at, id;""", CHECKLIST_ENTRY_COLUMNS, where.fragment())
                .single(where.apply(call().bind("checklist_id", checklistId)))
                .map(ChecklistEntry.map())
                .all();
    }

    public Optional<ChecklistEntry> findEntry(int entryId) {
        return SqlSupport.findById("checklist_entry", CHECKLIST_ENTRY_COLUMNS, entryId, ChecklistEntry.map());
    }

    public Optional<ChecklistEntry> findEntryByMember(int checklistId, int memberId) {
        return query(
                        "SELECT %s FROM checklist_entry WHERE checklist_id = :checklist_id AND member_id = :member_id;",
                        CHECKLIST_ENTRY_COLUMNS)
                .single(call().bind("checklist_id", checklistId).bind("member_id", memberId))
                .map(ChecklistEntry.map())
                .first();
    }

    public ChecklistEntry createEntry(int checklistId, int memberId) {
        return insertReturning(
                """
                INSERT INTO
                    checklist_entry(checklist_id, member_id)
                VALUES
                    (:checklist_id, :member_id)
                RETURNING %s;""",
                call().bind("checklist_id", checklistId).bind("member_id", memberId),
                ChecklistEntry.map(),
                CHECKLIST_ENTRY_COLUMNS);
    }

    public void softDeleteEntry(int entryId) {
        query("UPDATE checklist_entry SET deleted_at = :ts WHERE id = :id;")
                .single(call().bind("id", entryId).bind("ts", Instant.now(), INSTANT_TIMESTAMP))
                .update();
    }

    public void restoreEntry(int entryId) {
        query("UPDATE checklist_entry SET deleted_at = NULL WHERE id = :id;")
                .single(call().bind("id", entryId))
                .update();
    }

    public List<ChecklistCell> findCellsForChecklist(int checklistId) {
        return query("""
                        SELECT %s
                          FROM checklist_cell cc
                          JOIN checklist_entry ce ON ce.id = cc.entry_id
                         WHERE ce.checklist_id = :checklist_id;""", alias("cc", CHECKLIST_CELL_COLUMNS))
                .single(call().bind("checklist_id", checklistId))
                .map(ChecklistCell.map())
                .all();
    }

    public Optional<ChecklistCell> findCell(int entryId, int columnId) {
        return query(
                        "SELECT %s FROM checklist_cell WHERE entry_id = :entry_id AND column_id = :column_id;",
                        CHECKLIST_CELL_COLUMNS)
                .single(call().bind("entry_id", entryId).bind("column_id", columnId))
                .map(ChecklistCell.map())
                .first();
    }

    public ChecklistCell upsertCell(int entryId, int columnId, boolean checked, String note, int updatedBy) {
        return insertReturning(
                """
                INSERT INTO
                    checklist_cell(entry_id, column_id, checked, note, updated_at, updated_by)
                VALUES
                    (:entry_id, :column_id, :checked, :note, :ts, :updated_by)
                ON CONFLICT (entry_id, column_id) DO UPDATE
                    SET checked = excluded.checked,
                        note = excluded.note,
                        updated_at = excluded.updated_at,
                        updated_by = excluded.updated_by
                RETURNING %s;""",
                call().bind("entry_id", entryId)
                        .bind("column_id", columnId)
                        .bind("checked", checked)
                        .bind("note", note)
                        .bind("ts", Instant.now(), INSTANT_TIMESTAMP)
                        .bind("updated_by", updatedBy),
                ChecklistCell.map(),
                CHECKLIST_CELL_COLUMNS);
    }

    public int bulkSetChecked(List<Integer> entryIds, int columnId, boolean checked, int updatedBy) {
        if (entryIds.isEmpty()) return 0;
        int total = 0;
        total += query("""
                        UPDATE checklist_cell
                           SET checked = :checked,
                               updated_at = :ts,
                               updated_by = :updated_by
                         WHERE column_id = :col
                           AND entry_id = ANY(:entry_ids)
                           AND checked <> :checked;""")
                .single(call().bind("col", columnId)
                        .bind("entry_ids", entryIds, PostgreSqlTypes.INTEGER)
                        .bind("checked", checked)
                        .bind("ts", Instant.now(), INSTANT_TIMESTAMP)
                        .bind("updated_by", updatedBy))
                .update()
                .rows();

        total += query("""
                        INSERT INTO
                            checklist_cell(entry_id, column_id, checked, note, updated_at, updated_by)
                        SELECT e_id, :col, :checked, NULL, :ts, :updated_by
                          FROM unnest(:entry_ids::INT[]) AS e_id
                         WHERE NOT EXISTS (
                             SELECT 1 FROM checklist_cell
                              WHERE entry_id = e_id AND column_id = :col)
                           AND :checked = TRUE;""")
                .single(call().bind("col", columnId)
                        .bind("entry_ids", entryIds, PostgreSqlTypes.INTEGER)
                        .bind("checked", checked)
                        .bind("ts", Instant.now(), INSTANT_TIMESTAMP)
                        .bind("updated_by", updatedBy))
                .insert()
                .rows();
        return total;
    }

    public void appendNoteHistory(int cellId, String oldNote, String newNote, int changedBy) {
        insertReturning(
                """
                INSERT INTO
                    checklist_cell_note_history(cell_id, old_note, new_note, changed_by)
                VALUES
                    (:cell_id, :old_note, :new_note, :changed_by)
                RETURNING %s;""",
                call().bind("cell_id", cellId)
                        .bind("old_note", oldNote)
                        .bind("new_note", newNote)
                        .bind("changed_by", changedBy),
                ChecklistCellNoteHistory.map(),
                CHECKLIST_CELL_NOTE_HISTORY_COLUMNS);
    }

    public List<ChecklistCellNoteHistory> findNoteHistory(int cellId) {
        return query(
                        "SELECT %s FROM checklist_cell_note_history WHERE cell_id = :cell_id ORDER BY changed_at DESC, id DESC;",
                        CHECKLIST_CELL_NOTE_HISTORY_COLUMNS)
                .single(call().bind("cell_id", cellId))
                .map(ChecklistCellNoteHistory.map())
                .all();
    }
}
