/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.repository;

import de.chojo.sadu.queries.api.call.Call;
import de.chojo.sadu.queries.api.query.Query;
import dev.chojo.ember.feature.system.entity.ProblemReport;
import jakarta.inject.Singleton;

import java.util.List;

@Singleton
public class ProblemReportRepository {

    public ProblemReport create(
            int stationId,
            Integer memberId,
            String reporterName,
            String message,
            String pageUrl,
            String userRoles,
            String recentRequests,
            String browserInfo,
            String screenSize) {
        return Query.query("""
                        INSERT INTO problem_report(station_id, member_id, reporter_name, message, page_url, user_roles, recent_requests, browser_info, screen_size)
                        VALUES(:station_id, :member_id, :reporter_name, :message, :page_url, :user_roles, :recent_requests::jsonb, :browser_info, :screen_size)
                        RETURNING *;""")
                .single(Call.of()
                        .bind("station_id", stationId)
                        .bind("member_id", memberId)
                        .bind("reporter_name", reporterName)
                        .bind("message", message)
                        .bind("page_url", pageUrl)
                        .bind("user_roles", userRoles)
                        .bind("recent_requests", recentRequests)
                        .bind("browser_info", browserInfo)
                        .bind("screen_size", screenSize))
                .map(ProblemReport.map())
                .first()
                .orElseThrow();
    }

    public List<ProblemReport> findAll(boolean includeAcknowledged) {
        return Query.query(
                        "SELECT * FROM problem_report WHERE (acknowledged = FALSE OR :include_acknowledged) ORDER BY created_at DESC;")
                .single(Call.of().bind("include_acknowledged", includeAcknowledged))
                .map(ProblemReport.map())
                .all();
    }

    public boolean acknowledge(int id) {
        return Query.query("UPDATE problem_report SET acknowledged = TRUE WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .update()
                .changed();
    }

    public int acknowledgeAll() {
        return Query.query("UPDATE problem_report SET acknowledged = TRUE WHERE acknowledged = FALSE;")
                .single(Call.of())
                .update()
                .rows();
    }

    public boolean delete(int id) {
        return Query.query("DELETE FROM problem_report WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .delete()
                .changed();
    }
}
