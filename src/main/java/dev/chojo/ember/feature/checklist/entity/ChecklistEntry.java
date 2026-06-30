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
 * One member's row on a checklist. {@code deletedAt} carries the sticky soft-delete marker
 * that prevents the row from being resurrected by an additive refresh.
 *
 * @param id          the entry identifier
 * @param checklistId the owning checklist
 * @param memberId    the station member this row tracks
 * @param addedAt     when the row was first placed on the list
 * @param deletedAt   when the row was soft-deleted, or {@code null} if alive
 */
public record ChecklistEntry(int id, int checklistId, int memberId, Instant addedAt, Instant deletedAt) {

    public static RowMapping<ChecklistEntry> map() {
        return row -> new ChecklistEntry(
                row.getInt("id"),
                row.getInt("checklist_id"),
                row.getInt("member_id"),
                row.get("added_at", INSTANT_TIMESTAMP),
                row.get("deleted_at", INSTANT_TIMESTAMP));
    }
}
