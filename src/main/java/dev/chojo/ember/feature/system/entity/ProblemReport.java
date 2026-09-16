/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

import java.time.Instant;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * A problem somebody wrote down, and what the screen could say for itself.
 *
 * @param screenshotFileId the picture of the page, or null where the reporter gave none
 * @param acknowledgedAt   when it was marked dealt with, which is when its thirty days begin
 * @param forwardedAt      when it was passed on to a beacon, or null where it has not been
 */
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
        Integer screenshotFileId,
        boolean acknowledged,
        Instant acknowledgedAt,
        Instant forwardedAt,
        Instant createdAt) {

    /** Whether a picture came with it, which decides what a beacon is told and what a screen draws. */
    public boolean hasScreenshot() {
        return screenshotFileId != null;
    }

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
                row.getObject("screenshot_file_id", Integer.class),
                row.getBoolean("acknowledged"),
                row.get("acknowledged_at", INSTANT_TIMESTAMP),
                row.get("forwarded_at", INSTANT_TIMESTAMP),
                row.get("created_at", INSTANT_TIMESTAMP));
    }
}
