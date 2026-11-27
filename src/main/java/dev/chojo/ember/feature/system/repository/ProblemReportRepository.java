/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.repository;

import dev.chojo.ember.feature.system.entity.ProblemReport;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;
import static dev.chojo.ember.util.sql.SqlSupport.count;
import static dev.chojo.ember.util.sql.SqlSupport.deleteById;
import static dev.chojo.ember.util.sql.SqlSupport.insertReturning;

@Singleton
public class ProblemReportRepository {
    private static final String PROBLEM_REPORT_COLUMNS = """
            id, station_id, member_id, reporter_name, message, page_url, user_roles, recent_requests, \
            browser_info, screen_size, screenshot_file_id, acknowledged, acknowledged_at, forwarded_at, \
            created_at""";

    public ProblemReport create(
            int stationId,
            Integer memberId,
            String reporterName,
            String message,
            String pageUrl,
            String userRoles,
            String recentRequests,
            String browserInfo,
            String screenSize,
            Integer screenshotFileId) {
        return insertReturning(
                """
                INSERT INTO problem_report(station_id, member_id, reporter_name, message, page_url, user_roles, recent_requests, browser_info, screen_size, screenshot_file_id)
                VALUES(:station_id, :member_id, :reporter_name, :message, :page_url, :user_roles, :recent_requests::JSONB, :browser_info, :screen_size, :screenshot_file_id)
                RETURNING %s;""".formatted(PROBLEM_REPORT_COLUMNS),
                call().bind("station_id", stationId)
                        .bind("member_id", memberId)
                        .bind("reporter_name", reporterName)
                        .bind("message", message)
                        .bind("page_url", pageUrl)
                        .bind("user_roles", userRoles)
                        .bind("recent_requests", recentRequests)
                        .bind("browser_info", browserInfo)
                        .bind("screen_size", screenSize)
                        .bind("screenshot_file_id", screenshotFileId),
                ProblemReport.map(),
                PROBLEM_REPORT_COLUMNS);
    }

    public List<ProblemReport> findAll(boolean includeAcknowledged) {
        return query(
                        "SELECT %s FROM problem_report WHERE (acknowledged = FALSE OR :include_acknowledged) ORDER BY created_at DESC;",
                        PROBLEM_REPORT_COLUMNS)
                .single(call().bind("include_acknowledged", includeAcknowledged))
                .map(ProblemReport.map())
                .all();
    }

    /** One report, for the screens that act on a single one rather than on the list. */
    public Optional<ProblemReport> findById(int id) {
        return query("SELECT %s FROM problem_report WHERE id = :id;", PROBLEM_REPORT_COLUMNS)
                .single(call().bind("id", id))
                .map(ProblemReport.map())
                .first();
    }

    /**
     * How many reports nobody has looked at yet, which the sidebar shows so an operator does not have to
     * open the page to find out whether there is anything on it.
     *
     * @return the number of unacknowledged reports
     */
    public int countUnacknowledged() {
        return count("SELECT count(*) FROM problem_report WHERE acknowledged = FALSE;", call());
    }

    /** Marking one dealt with is what starts its thirty days, so the date goes down with the flag. */
    public boolean acknowledge(int id) {
        return query("UPDATE problem_report SET acknowledged = TRUE, acknowledged_at = now() WHERE id = :id;")
                .single(call().bind("id", id))
                .update()
                .changed();
    }

    public int acknowledgeAll() {
        return query("""
                        UPDATE problem_report SET acknowledged = TRUE, acknowledged_at = now()
                        WHERE acknowledged = FALSE;""").single(call()).update().rows();
    }

    /** Records that a report has gone to a beacon, so nothing sends it twice. */
    public boolean markForwarded(int id) {
        return query("UPDATE problem_report SET forwarded_at = now() WHERE id = :id AND forwarded_at IS NULL;")
                .single(call().bind("id", id))
                .update()
                .changed();
    }

    /**
     * The reports that have been dealt with for longer than the given moment, which the sweep removes.
     *
     * <p>Read before deleting rather than deleted in one statement, because each one may have a picture
     * whose bytes live outside the database and have to be removed with it.
     */
    public List<ProblemReport> findAcknowledgedBefore(Instant moment) {
        return query("""
                        SELECT %s FROM problem_report
                        WHERE acknowledged = TRUE AND acknowledged_at IS NOT NULL AND acknowledged_at < :moment
                        ORDER BY acknowledged_at;""", PROBLEM_REPORT_COLUMNS)
                .single(call().bind("moment", moment, INSTANT_TIMESTAMP))
                .map(ProblemReport.map())
                .all();
    }

    public boolean delete(int id) {
        return deleteById("problem_report", id);
    }
}
