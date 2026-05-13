/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api.v1;

import de.chojo.sadu.queries.api.call.Call;
import de.chojo.sadu.queries.api.query.Query;
import dev.chojo.ember.api.Roles;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
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

@Singleton
public class StatisticsRoutes implements Routes {

    @Inject
    public StatisticsRoutes() {}

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/statistics", this::getStatistics, Roles.TEAM);
        routes.get(prefix + "/admin/statistics", this::getAdminStatistics, Roles.ADMIN);
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
        data.put("roleCounts", queryMap("""
                SELECT r.name, count(smr.member_id) as cnt
                FROM role r
                LEFT JOIN station_member_role smr ON smr.role_id = r.id
                LEFT JOIN station_member sm ON sm.id = smr.member_id AND sm.station_id = :sid
                WHERE smr.member_id IS NOT NULL
                GROUP BY r.id, r.name ORDER BY cnt DESC""", stationId));

        ctx.json(data);
    }

    private int queryInt(String sql, int stationId) {
        return Query.query(sql)
                .single(Call.of().bind("sid", stationId))
                .map(row -> row.getInt(1))
                .first()
                .orElse(0);
    }

    private Map<String, Integer> queryMap(String sql, int stationId) {
        var map = new LinkedHashMap<String, Integer>();
        Query.query(sql)
                .single(Call.of().bind("sid", stationId))
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

        ctx.json(data);
    }

    private int globalInt(String sql) {
        return Query.query(sql).single().map(row -> row.getInt(1)).first().orElse(0);
    }

    private List<Map<String, Object>> globalMapList(String sql) {
        return Query.query(sql)
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
        return Query.query(sql)
                .single(Call.of().bind("sid", stationId))
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
