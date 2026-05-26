/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

import java.time.Instant;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

public record ProblemReport(
        int id,
        int stationId,
        Integer memberId,
        String reporterName,
        String message,
        String pageUrl,
        String userRoles,
        String recentRequests,
        String browserInfo,
        String screenSize,
        boolean acknowledged,
        Instant createdAt) {
    public static RowMapping<ProblemReport> map() {
        return row -> new ProblemReport(
                row.getInt("id"),
                row.getInt("station_id"),
                row.getObject("member_id", Integer.class),
                row.getString("reporter_name"),
                row.getString("message"),
                row.getString("page_url"),
                row.getString("user_roles"),
                row.getString("recent_requests"),
                row.getString("browser_info"),
                row.getString("screen_size"),
                row.getBoolean("acknowledged"),
                row.get("created_at", INSTANT_TIMESTAMP));
    }
}
