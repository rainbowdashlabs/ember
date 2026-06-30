/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.checklist.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

/**
 * One ordered yes/no question on a checklist.
 *
 * @param id          the column identifier
 * @param checklistId the owning checklist
 * @param position    display position (unique within a checklist)
 * @param label       the short column label
 * @param description optional long-form description shown on hover or in editors
 */
public record ChecklistColumn(int id, int checklistId, int position, String label, String description) {

    public static RowMapping<ChecklistColumn> map() {
        return row -> new ChecklistColumn(
                row.getInt("id"),
                row.getInt("checklist_id"),
                row.getInt("position"),
                row.getString("label"),
                row.getString("description"));
    }
}
