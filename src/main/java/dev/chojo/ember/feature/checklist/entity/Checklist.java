/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.checklist.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import dev.chojo.ember.feature.restriction.RestrictionMode;

import java.time.Instant;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * Top-level checklist record. Holds metadata and the restriction mode used to combine
 * the filter rows in {@code checklist_member_filter}.
 *
 * @param id               the checklist identifier
 * @param stationId        the owning station
 * @param name             display name
 * @param description      optional manager-only description (never blank, may be empty)
 * @param mode             AND/OR mode for combining the materialisation filter
 * @param createdAt        creation timestamp
 * @param createdBy        the creating member, or {@code null} after that member is deleted
 * @param lastRefreshedAt  timestamp of the most recent additive refresh, or {@code null} if never refreshed
 */
public record Checklist(
        int id,
        int stationId,
        String name,
        String description,
        RestrictionMode mode,
        Instant createdAt,
        Integer createdBy,
        Instant lastRefreshedAt) {

    /**
     * Creates a row mapping for database result set conversion.
     */
    public static RowMapping<Checklist> map() {
        return row -> new Checklist(
                row.getInt("id"),
                row.getInt("station_id"),
                row.getString("name"),
                row.getString("description"),
                row.getEnum("restriction_mode", RestrictionMode.class),
                row.get("created_at", INSTANT_TIMESTAMP),
                row.getObject("created_by", Integer.class),
                row.get("last_refreshed_at", INSTANT_TIMESTAMP));
    }
}
