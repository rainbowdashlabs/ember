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
 * The boolean state and optional free-text note at a single (entry, column) intersection.
 * Created lazily on first PUT; absence of a row means unchecked with no note.
 *
 * @param id        the cell identifier
 * @param entryId   the owning entry
 * @param columnId  the owning column
 * @param checked   whether the cell is ticked
 * @param note      optional free-text note ({@code null} or empty means no note)
 * @param updatedAt last-write timestamp
 * @param updatedBy the member who last wrote the cell, or {@code null} after that member is deleted
 */
public record ChecklistCell(
        int id, int entryId, int columnId, boolean checked, String note, Instant updatedAt, Integer updatedBy) {

    public static RowMapping<ChecklistCell> map() {
        return row -> new ChecklistCell(
                row.getInt("id"),
                row.getInt("entry_id"),
                row.getInt("column_id"),
                row.getBoolean("checked"),
                row.getString("note"),
                row.get("updated_at", INSTANT_TIMESTAMP),
                row.getObject("updated_by", Integer.class));
    }
}
