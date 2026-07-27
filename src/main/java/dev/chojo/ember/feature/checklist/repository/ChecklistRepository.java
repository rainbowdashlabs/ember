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
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * Persistence for checklists, their columns, member-filter rows, entries, cells, and note history.
 * Member resolution against the materialisation filter is delegated to the service layer.
 */
@Singleton
public class ChecklistRepository {

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
        return query("SELECT * FROM checklist WHERE id = :id;")
                .single(call().bind("id", id))
                .map(Checklist.map())
                .first();
    }

    public Checklist create(int stationId, String name, String description, RestrictionMode mode, int createdBy) {
        return query("""
                        INSERT INTO
                            checklist(station_id, name, description, restriction_mode, created_by, last_refreshed_at)
                        VALUES
                            (:station_id, :name, :description, :mode, :created_by, :refreshed_at)
                        RETURNING *;""")
                .single(call().bind("station_id", stationId)
                        .bind("name", name)
                        .bind("description", description != null ? description : "")
                        .bind("mode", mode.name())
                        .bind("created_by", createdBy)
                        .bind("refreshed_at", Instant.now(), INSTANT_TIMESTAMP))
                .map(Checklist.map())
                .first()
                .orElseThrow();
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
        return query("DELETE FROM checklist WHERE id = :id;")
                .single(call().bind("id", id))
                .delete()
                .changed();
    }

    public List<ChecklistColumn> findColumns(int checklistId) {
        return query("SELECT * FROM checklist_column WHERE checklist_id = :checklist_id ORDER BY position, id;")
                .single(call().bind("checklist_id", checklistId))
                .map(ChecklistColumn.map())
                .all();
    }

    public Optional<ChecklistColumn> findColumn(int columnId) {
        return query("SELECT * FROM checklist_column WHERE id = :id;")
                .single(call().bind("id", columnId))
                .map(ChecklistColumn.map())
                .first();
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
        return query("""
                        INSERT INTO
                            checklist_column(checklist_id, position, label, description)
                        VALUES
                            (:checklist_id, :position, :label, :description)
                        RETURNING *;""")
                .single(call().bind("checklist_id", checklistId)
                        .bind("position", position)
                        .bind("label", label)
                        .bind("description", description != null ? description : ""))
                .map(ChecklistColumn.map())
                .first()
                .orElseThrow();
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
     * {@code orderedIds}. Runs in two passes to sidestep the {@code UNIQUE(checklist_id, position)}
     * constraint: pass one moves each involved column into a distinct negative slot, pass two lands
     * them on their final 0-based positions.
     */
    public void reorderColumns(int checklistId, List<Integer> orderedIds) {
        for (int i = 0; i < orderedIds.size(); i++) {
            query("""
                            UPDATE checklist_column
                               SET position = :position
                             WHERE id = :id AND checklist_id = :checklist_id;""")
                    .single(call().bind("id", orderedIds.get(i))
                            .bind("position", -(i + 1))
                            .bind("checklist_id", checklistId))
                    .update();
        }
        for (int i = 0; i < orderedIds.size(); i++) {
            query("""
                            UPDATE checklist_column
                               SET position = :position
                             WHERE id = :id AND checklist_id = :checklist_id;""")
                    .single(call().bind("id", orderedIds.get(i))
                            .bind("position", i)
                            .bind("checklist_id", checklistId))
                    .update();
        }
    }

    public int countCheckedCellsInColumn(int columnId) {
        return query("SELECT count(*) AS cnt FROM checklist_cell WHERE column_id = :col AND checked = TRUE;")
                .single(call().bind("col", columnId))
                .map(row -> row.getInt("cnt"))
                .first()
                .orElse(0);
    }

    public boolean deleteColumn(int columnId) {
        return query("DELETE FROM checklist_column WHERE id = :id;")
                .single(call().bind("id", columnId))
                .delete()
                .changed();
    }

    public List<Restriction> findFilterRows(int checklistId) {
        return query("SELECT * FROM checklist_member_filter WHERE checklist_id = :checklist_id ORDER BY id;")
                .single(call().bind("checklist_id", checklistId))
                .map(Restriction.map())
                .all();
    }

    public void replaceFilter(
            int checklistId,
            List<StationUserType> userTypes,
            List<Integer> groupIds,
            List<Integer> tagIds,
            List<Integer> memberIds) {
        query("DELETE FROM checklist_member_filter WHERE checklist_id = :checklist_id;")
                .single(call().bind("checklist_id", checklistId))
                .delete();

        for (StationUserType userType : userTypes) {
            query("INSERT INTO checklist_member_filter(checklist_id, user_type) VALUES (:checklist_id, :user_type);")
                    .single(call().bind("checklist_id", checklistId).bind("user_type", userType))
                    .insert();
        }
        for (int groupId : groupIds) {
            query("INSERT INTO checklist_member_filter(checklist_id, group_id) VALUES (:checklist_id, :group_id);")
                    .single(call().bind("checklist_id", checklistId).bind("group_id", groupId))
                    .insert();
        }
        for (int tagId : tagIds) {
            query("INSERT INTO checklist_member_filter(checklist_id, tag_id) VALUES (:checklist_id, :tag_id);")
                    .single(call().bind("checklist_id", checklistId).bind("tag_id", tagId))
                    .insert();
        }
        for (int memberId : memberIds) {
            query("INSERT INTO checklist_member_filter(checklist_id, member_id) VALUES (:checklist_id, :member_id);")
                    .single(call().bind("checklist_id", checklistId).bind("member_id", memberId))
                    .insert();
        }
    }

    public List<ChecklistEntry> findEntries(int checklistId, boolean includeDeleted) {
        String predicate = includeDeleted ? "" : "AND deleted_at IS NULL";
        return query("""
                        SELECT *
                          FROM checklist_entry
                         WHERE checklist_id = :checklist_id %s
                         ORDER BY added_at, id;""", predicate)
                .single(call().bind("checklist_id", checklistId))
                .map(ChecklistEntry.map())
                .all();
    }

    public Optional<ChecklistEntry> findEntry(int entryId) {
        return query("SELECT * FROM checklist_entry WHERE id = :id;")
                .single(call().bind("id", entryId))
                .map(ChecklistEntry.map())
                .first();
    }

    public Optional<ChecklistEntry> findEntryByMember(int checklistId, int memberId) {
        return query("SELECT * FROM checklist_entry WHERE checklist_id = :checklist_id AND member_id = :member_id;")
                .single(call().bind("checklist_id", checklistId).bind("member_id", memberId))
                .map(ChecklistEntry.map())
                .first();
    }

    public ChecklistEntry createEntry(int checklistId, int memberId) {
        return query("""
                        INSERT INTO
                            checklist_entry(checklist_id, member_id)
                        VALUES
                            (:checklist_id, :member_id)
                        RETURNING *;""")
                .single(call().bind("checklist_id", checklistId).bind("member_id", memberId))
                .map(ChecklistEntry.map())
                .first()
                .orElseThrow();
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
                        SELECT cc.*
                          FROM checklist_cell cc
                          JOIN checklist_entry ce ON ce.id = cc.entry_id
                         WHERE ce.checklist_id = :checklist_id;""")
                .single(call().bind("checklist_id", checklistId))
                .map(ChecklistCell.map())
                .all();
    }

    public Optional<ChecklistCell> findCell(int entryId, int columnId) {
        return query("SELECT * FROM checklist_cell WHERE entry_id = :entry_id AND column_id = :column_id;")
                .single(call().bind("entry_id", entryId).bind("column_id", columnId))
                .map(ChecklistCell.map())
                .first();
    }

    public ChecklistCell upsertCell(int entryId, int columnId, boolean checked, String note, int updatedBy) {
        return query("""
                        INSERT INTO
                            checklist_cell(entry_id, column_id, checked, note, updated_at, updated_by)
                        VALUES
                            (:entry_id, :column_id, :checked, :note, :ts, :updated_by)
                        ON CONFLICT (entry_id, column_id) DO UPDATE
                            SET checked = excluded.checked,
                                note = excluded.note,
                                updated_at = excluded.updated_at,
                                updated_by = excluded.updated_by
                        RETURNING *;""")
                .single(call().bind("entry_id", entryId)
                        .bind("column_id", columnId)
                        .bind("checked", checked)
                        .bind("note", note)
                        .bind("ts", Instant.now(), INSTANT_TIMESTAMP)
                        .bind("updated_by", updatedBy))
                .map(ChecklistCell.map())
                .first()
                .orElseThrow();
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
        query("""
                INSERT INTO
                    checklist_cell_note_history(cell_id, old_note, new_note, changed_by)
                VALUES
                    (:cell_id, :old_note, :new_note, :changed_by)
                RETURNING *;""")
                .single(call().bind("cell_id", cellId)
                        .bind("old_note", oldNote)
                        .bind("new_note", newNote)
                        .bind("changed_by", changedBy))
                .map(ChecklistCellNoteHistory.map())
                .first()
                .orElseThrow();
    }

    public List<ChecklistCellNoteHistory> findNoteHistory(int cellId) {
        return query(
                        "SELECT * FROM checklist_cell_note_history WHERE cell_id = :cell_id ORDER BY changed_at DESC, id DESC;")
                .single(call().bind("cell_id", cellId))
                .map(ChecklistCellNoteHistory.map())
                .all();
    }
}
