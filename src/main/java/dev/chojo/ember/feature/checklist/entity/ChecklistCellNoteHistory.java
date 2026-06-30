/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.checklist.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

import java.time.Instant;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * Append-only log entry recording a single change to a checklist cell's note.
 *
 * @param id        the history row identifier
 * @param cellId    the cell whose note changed
 * @param oldNote   the previous note value, or {@code null} if there was none
 * @param newNote   the new note value, or {@code null} if cleared
 * @param changedBy the member who made the change, or {@code null} after that member is deleted
 * @param changedAt when the change happened
 */
public record ChecklistCellNoteHistory(
        int id, int cellId, String oldNote, String newNote, Integer changedBy, Instant changedAt) {

    public static RowMapping<ChecklistCellNoteHistory> map() {
        return row -> new ChecklistCellNoteHistory(
                row.getInt("id"),
                row.getInt("cell_id"),
                row.getString("old_note"),
                row.getString("new_note"),
                row.getObject("changed_by", Integer.class),
                row.get("changed_at", INSTANT_TIMESTAMP));
    }
}
