/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.statistics.route;

import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.InstancePermission;
import dev.chojo.ember.api.auth.StationPermission;
import io.javalin.http.Context;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * Routes for station and system-wide statistics including member counts,
 * attendance summaries, and admin dashboard metrics.
 */
@Singleton
public class StatisticsRoutes implements Routes {

    @Inject
    public StatisticsRoutes() {}

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/statistics", this::getStatistics, StationPermission.STATION_STATISTICS);
        routes.get(prefix + "/admin/statistics", this::getAdminStatistics, InstancePermission.ADMINISTRATOR);
        routes.get(prefix + "/admin/overview", this::getAdminOverview, InstancePermission.ADMINISTRATOR);
    }

    @OpenApi(
            path = "/api/v1/statistics",
            methods = HttpMethod.GET,
            summary = "Get station statistics",
            tags = {"Statistics"},
            responses = @OpenApiResponse(status = "200"))
    private void getStatistics(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int stationId = session.stationId();

        var data = new LinkedHashMap<String, Object>();
        data.put("memberCount", queryInt("SELECT count(*) FROM station_member WHERE station_id = :sid", stationId));
        data.put(
                "groupCounts",
                queryMap(
                        "SELECT mg.name, count(mge.member_id) as cnt FROM member_group mg LEFT JOIN member_group_entry mge ON mge.group_id = mg.id WHERE mg.station_id = :sid GROUP BY mg.id, mg.name ORDER BY mg.name",
                        stationId));
        data.put("attendanceByMonth", queryMapList("""
                SELECT to_char(s.start_time AT TIME ZONE 'UTC', 'YYYY-MM') as month,
                       count(DISTINCT s.id) as sessions,
                       count(e.id) FILTER (WHERE e.status = 'PRESENT') as present,
                       count(e.id) FILTER (WHERE e.status = 'ABSENT') as absent,
                       count(e.id) FILTER (WHERE e.status = 'DECLINED') as declined
                FROM attendance_session s
                JOIN attendance_template t ON t.id = s.template_id
                LEFT JOIN attendance_entry e ON e.session_id = s.id
                WHERE t.station_id = :sid AND s.start_time >= now() - interval '12 months'
                GROUP BY month ORDER BY month""", stationId));
        data.put("inventoryStatus", queryMapList("""
                SELECT i.name,
                       count(ii.id) as total,
                       count(ii.id) FILTER (WHERE ii.assigned_to IS NOT NULL) as assigned,
                       count(ii.id) FILTER (WHERE ii.lost_at IS NOT NULL) as lost
                FROM inventory i
                LEFT JOIN inventory_item ii ON ii.inventory_id = i.id
                WHERE i.station_id = :sid
                GROUP BY i.id, i.name ORDER BY i.name""", stationId));
        data.put("eventRegistrations", queryMapList("""
                SELECT se.name,
                       count(er.id) FILTER (WHERE er.status = 'ACCEPTED') as accepted,
                       count(er.id) FILTER (WHERE er.status = 'PENDING') as pending,
                       count(er.id) FILTER (WHERE er.status = 'DECLINED') as declined
                FROM station_event se
                LEFT JOIN event_registration er ON er.event_id = se.id
                WHERE se.station_id = :sid AND se.start_time >= now()
                GROUP BY se.id, se.name
                HAVING count(er.id) > 0
                ORDER BY se.start_time""", stationId));
        data.put("userTypeCounts", queryMap("""
                SELECT sm.user_type AS name, count(sm.id) as cnt
                FROM station_member sm
                WHERE sm.station_id = :sid AND sm.former = FALSE
                GROUP BY sm.user_type ORDER BY cnt DESC""", stationId));

        ctx.json(data);
    }

    private int queryInt(String sql, int stationId) {
        return query(sql)
                .single(call().bind("sid", stationId))
                .map(row -> row.getInt(1))
                .first()
                .orElse(0);
    }

    private Map<String, Integer> queryMap(String sql, int stationId) {
        var map = new LinkedHashMap<String, Integer>();
        query(sql)
                .single(call().bind("sid", stationId))
                .map(row -> new String[] {row.getString(1), String.valueOf(row.getInt(2))})
                .all()
                .forEach(r -> map.put(r[0], Integer.parseInt(r[1])));
        return map;
    }

    @OpenApi(
            path = "/api/v1/admin/statistics",
            methods = HttpMethod.GET,
            summary = "Get admin-level statistics",
            tags = {"Statistics"},
            responses = @OpenApiResponse(status = "200"))
    private void getAdminStatistics(Context ctx) {
        var data = new LinkedHashMap<String, Object>();

        // Email queue
        data.put("emailPending", globalInt("SELECT count(*) FROM email_queue WHERE status = 'PENDING'"));
        data.put("emailSending", globalInt("SELECT count(*) FROM email_queue WHERE status = 'SENDING'"));
        data.put(
                "emailSentToday",
                globalInt("SELECT COALESCE(count, 0) FROM email_daily_count WHERE day = CURRENT_DATE"));
        data.put("emailFailed", globalInt("SELECT count(*) FROM email_queue WHERE status = 'FAILED'"));
        data.put("emailSent", globalInt("SELECT count(*) FROM email_queue WHERE status = 'SENT'"));
        data.put("emailByDay", globalMapList("""
                SELECT day::text, count FROM email_daily_count ORDER BY day DESC LIMIT 30"""));
        data.put("emailByStatus", globalMapList("""
                SELECT status, count(*) as cnt FROM email_queue GROUP BY status ORDER BY status"""));

        // Accounts & stations
        data.put("totalAccounts", globalInt("SELECT count(*) FROM account"));
        data.put("totalStations", globalInt("SELECT count(*) FROM station"));
        data.put("totalMembers", globalInt("SELECT count(*) FROM station_member"));

        // Active sessions (logged in within 7 days)
        data.put("activeSessions", globalInt("""
                SELECT count(*) FROM account_session WHERE last_used_at > now() - interval '7 days'"""));

        // Pending applications
        data.put("pendingApplications", globalInt("SELECT count(*) FROM station_application WHERE status = 'pending'"));

        // Attendance sessions this month
        data.put("sessionsThisMonth", globalInt("""
                SELECT count(*) FROM attendance_session WHERE start_time >= date_trunc('month', now())"""));

        // Totals
        data.put("totalInventoryItems", globalInt("SELECT count(*) FROM inventory_item"));
        data.put("totalEvents", globalInt("SELECT count(*) FROM station_event"));
        data.put("totalAttendanceSessions", globalInt("SELECT count(*) FROM attendance_session"));
        data.put("totalAttendanceEntries", globalInt("SELECT count(*) FROM attendance_entry"));
        data.put("totalProfileFields", globalInt("SELECT count(*) FROM profile_field"));
        data.put("totalGroups", globalInt("SELECT count(*) FROM member_group"));

        // Account verification status
        data.put("accountsVerified", globalInt("SELECT count(*) FROM account WHERE email_verified = TRUE"));
        data.put("accountsUnverified", globalInt("SELECT count(*) FROM account WHERE email_verified = FALSE"));
        data.put(
                "stationsSetupComplete",
                globalInt("SELECT count(*) FROM station WHERE setup_completed_at IS NOT NULL"));
        data.put("stationsSetupPending", globalInt("SELECT count(*) FROM station WHERE setup_completed_at IS NULL"));

        // Session activity - last 30 days, zero-filled
        data.put("sessionsByDay", globalMapList("""
                SELECT to_char(d.day, 'YYYY-MM-DD') AS day,
                       COALESCE(count(se.id), 0)::INT AS count
                FROM generate_series(CURRENT_DATE - interval '29 days', CURRENT_DATE, interval '1 day') AS d(day)
                LEFT JOIN account_session se ON se.created_at::date = d.day
                GROUP BY d.day ORDER BY d.day"""));

        // Top 10 stations by active member count
        data.put("topStationsByMembers", globalMapList("""
                SELECT s.name, count(sm.id)::INT AS member_count
                FROM station s
                LEFT JOIN station_member sm ON sm.station_id = s.id AND sm.former = FALSE
                GROUP BY s.id, s.name
                HAVING count(sm.id) > 0
                ORDER BY member_count DESC, s.name
                LIMIT 10"""));

        ctx.json(data);
    }

    @OpenApi(
            path = "/api/v1/admin/overview",
            methods = HttpMethod.GET,
            summary = "Aggregated 'needs attention' metrics for the instance admin dashboard",
            tags = {"Statistics"},
            responses = @OpenApiResponse(status = "200"))
    private void getAdminOverview(Context ctx) {
        var data = new LinkedHashMap<String, Object>();

        data.put("emailFailed", globalInt("SELECT count(*) FROM email_queue WHERE status = 'FAILED'"));
        data.put("emailPending", globalInt("SELECT count(*) FROM email_queue WHERE status = 'PENDING'"));
        data.put(
                "emailStuckSending",
                globalInt(
                        "SELECT count(*) FROM email_queue WHERE status = 'SENDING' AND created_at < now() - interval '10 minutes'"));

        data.put(
                "stationApplicationsPending",
                globalInt("SELECT count(*) FROM station_application WHERE status = 'PENDING'"));
        data.put("stationsSetupPending", globalInt("SELECT count(*) FROM station WHERE setup_completed_at IS NULL"));
        data.put("accountsUnverified", globalInt("SELECT count(*) FROM account WHERE email_verified = FALSE"));

        data.put(
                "federationPartnersPending",
                globalInt("SELECT count(*) FROM federation_partner WHERE status = 'PENDING'"));
        data.put(
                "discoveryPeersUnreachable",
                globalInt("SELECT count(*) FROM discovery_peer WHERE reachable = FALSE AND blocked = FALSE"));

        data.put("problemReportsOpen", globalInt("SELECT count(*) FROM problem_report WHERE acknowledged = FALSE"));

        data.put("recentApplications", globalMapList("""
                SELECT id, first_name || ' ' || last_name AS name, station_name, created_at
                FROM station_application
                WHERE status = 'PENDING'
                ORDER BY created_at DESC
                LIMIT 5"""));
        data.put("recentProblemReports", globalMapList("""
                SELECT id, reporter_name, page_url, created_at
                FROM problem_report
                WHERE acknowledged = FALSE
                ORDER BY created_at DESC
                LIMIT 5"""));

        ctx.json(data);
    }

    private int globalInt(String sql) {
        return query(sql).single().map(row -> row.getInt(1)).first().orElse(0);
    }

    private List<Map<String, Object>> globalMapList(String sql) {
        return query(sql)
                .single()
                .map(row -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    var meta = row.getMetaData();
                    for (int i = 1; i <= meta.getColumnCount(); i++) {
                        m.put(meta.getColumnLabel(i), row.getObject(i));
                    }
                    return m;
                })
                .all();
    }

    private List<Map<String, Object>> queryMapList(String sql, int stationId) {
        return query(sql)
                .single(call().bind("sid", stationId))
                .map(row -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    var meta = row.getMetaData();
                    for (int i = 1; i <= meta.getColumnCount(); i++) {
                        m.put(meta.getColumnLabel(i), row.getObject(i));
                    }
                    return m;
                })
                .all();
    }
}
