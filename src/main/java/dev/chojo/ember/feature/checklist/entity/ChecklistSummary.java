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
 * Compact projection used for the list view: one row per checklist with aggregate counts.
 *
 * @param id               the checklist identifier
 * @param name             display name
 * @param description      optional description
 * @param memberCount      number of alive (non-deleted) entries
 * @param columnCount      number of columns
 * @param lastRefreshedAt  timestamp of the most recent refresh, or {@code null} if never refreshed
 * @param createdAt        creation timestamp
 */
public record ChecklistSummary(
        int id,
        String name,
        String description,
        int memberCount,
        int columnCount,
        Instant lastRefreshedAt,
        Instant createdAt) {

    public static RowMapping<ChecklistSummary> map() {
        return row -> new ChecklistSummary(
                row.getInt("id"),
                row.getString("name"),
                row.getString("description"),
                row.getInt("member_count"),
                row.getInt("column_count"),
                row.get("last_refreshed_at", INSTANT_TIMESTAMP),
                row.get("created_at", INSTANT_TIMESTAMP));
    }
}
