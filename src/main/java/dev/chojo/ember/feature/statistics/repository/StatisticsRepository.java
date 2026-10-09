/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.statistics.repository;

import dev.chojo.ember.feature.attendance.entity.AttendanceEntry.AttendanceStatus;
import dev.chojo.ember.feature.cluster.entity.StationKind;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.repository.RefusalSql;
import dev.chojo.ember.feature.federation.entity.FederationPartner.FederationStatus;
import dev.chojo.ember.feature.mail.entity.EmailQueueStatus;
import dev.chojo.ember.feature.station.entity.ApplicationStatus;
import dev.chojo.ember.feature.statistics.entity.AdminCounts;
import dev.chojo.ember.feature.statistics.entity.AdminOverview;
import dev.chojo.ember.feature.statistics.entity.AdminOverview.RecentApplication;
import dev.chojo.ember.feature.statistics.entity.AdminOverview.RecentProblemReport;
import dev.chojo.ember.feature.statistics.entity.AdminStatistics;
import dev.chojo.ember.feature.statistics.entity.AdminStatistics.AttendanceStatusCount;
import dev.chojo.ember.feature.statistics.entity.AdminStatistics.DayCount;
import dev.chojo.ember.feature.statistics.entity.AdminStatistics.EmailStatusCount;
import dev.chojo.ember.feature.statistics.entity.AdminStatistics.StationMembers;
import dev.chojo.ember.feature.statistics.entity.StationStatistics;
import dev.chojo.ember.feature.statistics.entity.StationStatistics.AttendanceMonth;
import dev.chojo.ember.feature.statistics.entity.StationStatistics.EventRegistrations;
import dev.chojo.ember.feature.statistics.entity.StationStatistics.InventoryStatus;
import jakarta.inject.Singleton;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * Reads the figures of the station statistics page, the instance statistics page and the instance
 * administrator's dashboard.
 *
 * <p>Counts over one table are taken together with {@code count(*) FILTER (...)}, and every status
 * is bound from its enum, so a figure cannot drift from the values the rest of the backend writes.
 */
@Singleton
public class StatisticsRepository {

    private static final String DAILY_SERIES = """
            generate_series(CURRENT_DATE - interval '29 days', CURRENT_DATE, interval '1 day') AS d(day)""";

    /**
     * The figures of one station's statistics page.
     *
     * @param stationId the station
     * @return its figures
     */
    public StationStatistics stationStatistics(int stationId) {
        return new StationStatistics(
                memberCount(stationId),
                groupCounts(stationId),
                attendanceByMonth(stationId),
                inventoryStatus(stationId),
                eventRegistrations(stationId),
                userTypeCounts(stationId));
    }

    private int memberCount(int stationId) {
        return query("""
                        SELECT count(*) FROM station_member WHERE station_id = :station_id;""")
                .single(call().bind("station_id", stationId))
                .map(row -> row.getInt(1))
                .first()
                .orElse(0);
    }

    private Map<String, Integer> groupCounts(int stationId) {
        return namedCounts(query("""
                        SELECT
                            mg.name, count(mge.member_id) AS cnt
                        FROM
                            member_group mg
                            LEFT JOIN member_group_entry mge ON mge.group_id = mg.id
                        WHERE mg.station_id = :station_id
                        GROUP BY mg.id, mg.name
                        ORDER BY mg.name;""")
                .single(call().bind("station_id", stationId))
                .map(row -> new NamedCount(row.getString("name"), row.getInt("cnt")))
                .all());
    }

    private Map<String, Integer> userTypeCounts(int stationId) {
        return namedCounts(query("""
                        SELECT
                            sm.user_type AS name, count(sm.id) AS cnt
                        FROM
                            station_member sm
                        WHERE sm.station_id = :station_id
                          AND sm.former = FALSE
                        GROUP BY sm.user_type
                        ORDER BY cnt DESC;""")
                .single(call().bind("station_id", stationId))
                .map(row -> new NamedCount(row.getString("name"), row.getInt("cnt")))
                .all());
    }

    private static Map<String, Integer> namedCounts(List<NamedCount> rows) {
        var counts = new LinkedHashMap<String, Integer>();
        rows.forEach(row -> counts.put(row.name(), row.count()));
        return counts;
    }

    /** One row of a count grouped by a name, which may be missing. */
    private record NamedCount(String name, int count) {}

    private List<AttendanceMonth> attendanceByMonth(int stationId) {
        return query("""
                        SELECT
                            to_char(s.start_time, 'YYYY-MM')                   AS month,
                            count(DISTINCT s.id)                               AS sessions,
                            count(e.id) FILTER (WHERE e.status = :present)     AS present,
                            count(e.id) FILTER (WHERE e.status = :absent)      AS absent,
                            count(e.id) FILTER (WHERE e.status = :declined)    AS declined
                        FROM
                            attendance_session s
                            JOIN attendance_template t ON t.id = s.template_id
                            LEFT JOIN attendance_entry e ON e.session_id = s.id
                        WHERE t.station_id = :station_id
                          AND s.start_time >= now() - interval '12 months'
                        GROUP BY month
                        ORDER BY month;""")
                .single(call().bind("station_id", stationId)
                        .bind("present", AttendanceStatus.PRESENT)
                        .bind("absent", AttendanceStatus.ABSENT)
                        .bind("declined", AttendanceStatus.DECLINED))
                .map(row -> new AttendanceMonth(
                        row.getString("month"),
                        row.getInt("sessions"),
                        row.getInt("present"),
                        row.getInt("absent"),
                        row.getInt("declined")))
                .all();
    }

    private List<InventoryStatus> inventoryStatus(int stationId) {
        return query("""
                        SELECT
                            i.name,
                            count(ii.id)                                         AS total,
                            count(ii.id) FILTER (WHERE ii.assigned_to IS NOT NULL) AS assigned,
                            count(ii.id) FILTER (WHERE ii.lost_at IS NOT NULL)     AS lost
                        FROM
                            inventory i
                            LEFT JOIN inventory_item ii ON ii.inventory_id = i.id
                        WHERE i.station_id = :station_id
                        GROUP BY i.id, i.name
                        ORDER BY i.name;""")
                .single(call().bind("station_id", stationId))
                .map(row -> new InventoryStatus(
                        row.getString("name"), row.getInt("total"), row.getInt("assigned"), row.getInt("lost")))
                .all();
    }

    private List<EventRegistrations> eventRegistrations(int stationId) {
        return query("""
                        SELECT
                            se.name,
                            count(er.id) FILTER (WHERE er.status = :accepted)                 AS accepted,
                            count(er.id) FILTER (WHERE er.status = :pending)                  AS pending,
                            count(er.id) FILTER (WHERE %s)                                    AS declined
                        FROM
                            station_event se
                            LEFT JOIN event_registration er ON er.event_id = se.id
                        WHERE se.station_id = :station_id
                          AND se.start_time >= now()
                        GROUP BY se.id, se.name
                        HAVING count(er.id) > 0
                        ORDER BY se.start_time;""", RefusalSql.NOT_COMING)
                .single(call().bind("station_id", stationId)
                        .bind("accepted", RegistrationStatus.ACCEPTED)
                        .bind("pending", RegistrationStatus.PENDING))
                .map(row -> new EventRegistrations(
                        row.getString("name"), row.getInt("accepted"), row.getInt("pending"), row.getInt("declined")))
                .all();
    }

    /**
     * The figures of the instance statistics page.
     *
     * @return the figures
     */
    public AdminStatistics adminStatistics() {
        return new AdminStatistics(
                adminCounts(),
                emailByDay(),
                emailByStatus(),
                attendanceByStatus(),
                registrationsByDay(),
                sessionsByDay(),
                topStationsByMembers());
    }

    private AdminCounts adminCounts() {
        return query("""
                        SELECT
                            e.pending, e.sending, e.sent, e.failed,
                            (SELECT COALESCE(max(count), 0) FROM email_daily_count WHERE day = CURRENT_DATE)
                                                                                         AS sent_today,
                            (SELECT count(*) FROM mail_provider_block)                    AS provider_blocks,
                            a.total AS accounts, a.verified, a.unverified,
                            s.regular AS stations, s.setup_complete, s.setup_pending,
                            (SELECT count(*) FROM cluster)                                AS clusters,
                            (SELECT count(*) FROM station_member)                         AS members,
                            (SELECT count(*) FROM account_session
                             WHERE last_used_at > now() - interval '7 days')              AS active_sessions,
                            (SELECT count(*) FROM station_application
                             WHERE status = :application_pending)                         AS pending_applications,
                            att.total AS attendance_sessions, att.this_month AS sessions_this_month,
                            (SELECT count(*) FROM attendance_entry)                       AS attendance_entries,
                            (SELECT count(*) FROM inventory_item)                         AS inventory_items,
                            ev.total AS events, ev.upcoming AS events_upcoming,
                            (SELECT count(*) FROM event_registration)                     AS event_registrations,
                            (SELECT count(*) FROM profile_field)                          AS profile_fields,
                            (SELECT count(*) FROM member_group)                           AS member_groups,
                            (SELECT count(DISTINCT account_id) FROM account_2fa_factor
                             WHERE disabled_at IS NULL)                                   AS accounts_with_2fa
                        FROM
                            (SELECT
                                 count(*) FILTER (WHERE status = :email_pending) AS pending,
                                 count(*) FILTER (WHERE status = :email_sending) AS sending,
                                 count(*) FILTER (WHERE status = :email_sent)    AS sent,
                                 count(*) FILTER (WHERE status = :email_failed)  AS failed
                             FROM email_queue) e,
                            (SELECT
                                 count(*)                                       AS total,
                                 count(*) FILTER (WHERE email_verified = TRUE)  AS verified,
                                 count(*) FILTER (WHERE email_verified = FALSE) AS unverified
                             FROM account) a,
                            (SELECT
                                 count(*) FILTER (WHERE station_kind = :regular)          AS regular,
                                 count(*) FILTER (WHERE setup_completed_at IS NOT NULL) AS setup_complete,
                                 count(*) FILTER (WHERE setup_completed_at IS NULL)     AS setup_pending
                             FROM station) s,
                            (SELECT
                                 count(*)                                                         AS total,
                                 count(*) FILTER (WHERE start_time >= date_trunc('month', now())) AS this_month
                             FROM attendance_session) att,
                            (SELECT
                                 count(*)                                    AS total,
                                 count(*) FILTER (WHERE start_time >= now()) AS upcoming
                             FROM station_event) ev;""")
                .single(call().bind("email_pending", EmailQueueStatus.PENDING)
                        .bind("email_sending", EmailQueueStatus.SENDING)
                        .bind("email_sent", EmailQueueStatus.SENT)
                        .bind("email_failed", EmailQueueStatus.FAILED)
                        .bind("application_pending", ApplicationStatus.PENDING)
                        .bind("regular", StationKind.REGULAR))
                .map(row -> new AdminCounts(
                        row.getInt("pending"),
                        row.getInt("sending"),
                        row.getInt("sent_today"),
                        row.getInt("failed"),
                        row.getInt("sent"),
                        row.getInt("provider_blocks"),
                        row.getInt("accounts"),
                        row.getInt("stations"),
                        row.getInt("clusters"),
                        row.getInt("members"),
                        row.getInt("active_sessions"),
                        row.getInt("pending_applications"),
                        row.getInt("sessions_this_month"),
                        row.getInt("inventory_items"),
                        row.getInt("events"),
                        row.getInt("attendance_sessions"),
                        row.getInt("attendance_entries"),
                        row.getInt("profile_fields"),
                        row.getInt("member_groups"),
                        row.getInt("verified"),
                        row.getInt("unverified"),
                        row.getInt("setup_complete"),
                        row.getInt("setup_pending"),
                        row.getInt("accounts_with_2fa"),
                        row.getInt("events_upcoming"),
                        row.getInt("event_registrations")))
                .first()
                .orElseThrow();
    }

    private List<DayCount> emailByDay() {
        return query("""
                        SELECT day::TEXT AS day, count FROM email_daily_count ORDER BY day DESC LIMIT 30;""")
                .single()
                .map(row -> new DayCount(row.getString("day"), row.getInt("count")))
                .all();
    }

    private List<EmailStatusCount> emailByStatus() {
        return query("""
                        SELECT status, count(*) AS cnt FROM email_queue GROUP BY status ORDER BY status;""")
                .single()
                .map(row -> new EmailStatusCount(row.getEnum("status", EmailQueueStatus.class), row.getInt("cnt")))
                .all();
    }

    private List<AttendanceStatusCount> attendanceByStatus() {
        return query("""
                        SELECT status, count(*) AS cnt FROM attendance_entry GROUP BY status ORDER BY status;""")
                .single()
                .map(row -> new AttendanceStatusCount(row.getEnum("status", AttendanceStatus.class), row.getInt("cnt")))
                .all();
    }

    private List<DayCount> registrationsByDay() {
        return query("""
                        SELECT
                            to_char(d.day, 'YYYY-MM-DD') AS day,
                            count(er.id)                 AS count
                        FROM
                            %s
                            LEFT JOIN event_registration er ON er.created_at::DATE = d.day
                        GROUP BY d.day
                        ORDER BY d.day;""", DAILY_SERIES)
                .single()
                .map(row -> new DayCount(row.getString("day"), row.getInt("count")))
                .all();
    }

    private List<DayCount> sessionsByDay() {
        return query("""
                        SELECT
                            to_char(d.day, 'YYYY-MM-DD') AS day,
                            count(se.id)                 AS count
                        FROM
                            %s
                            LEFT JOIN account_session se ON se.created_at::DATE = d.day
                        GROUP BY d.day
                        ORDER BY d.day;""", DAILY_SERIES)
                .single()
                .map(row -> new DayCount(row.getString("day"), row.getInt("count")))
                .all();
    }

    private List<StationMembers> topStationsByMembers() {
        return query("""
                        SELECT
                            s.name, count(sm.id) AS member_count
                        FROM
                            station s
                            LEFT JOIN station_member sm ON sm.station_id = s.id AND sm.former = FALSE
                        GROUP BY s.id, s.name
                        HAVING count(sm.id) > 0
                        ORDER BY member_count DESC, s.name
                        LIMIT 10;""")
                .single()
                .map(row -> new StationMembers(row.getString("name"), row.getInt("member_count")))
                .all();
    }

    /**
     * What needs an instance administrator's attention, as far as the database can count it.
     *
     * @return the counts and the newest entries behind them, with no signing keys counted as locked:
     *         whether a key opens is known only to the signing feature
     */
    public AdminOverview adminOverview() {
        var applications = recentApplications();
        var reports = recentProblemReports();
        return query("""
                        SELECT
                            e.failed, e.pending, e.stuck,
                            (SELECT count(*) FROM station_application
                             WHERE status = :application_pending)                      AS applications_pending,
                            (SELECT count(*) FROM station
                             WHERE setup_completed_at IS NULL)                         AS setup_pending,
                            (SELECT count(*) FROM account WHERE email_verified = FALSE) AS unverified,
                            (SELECT count(*) FROM federation_partner
                             WHERE status = :partner_pending)                          AS partners_pending,
                            (SELECT count(*) FROM discovery_peer
                             WHERE reachable = FALSE AND blocked = FALSE)              AS peers_unreachable,
                            (SELECT count(*) FROM problem_report
                             WHERE acknowledged = FALSE)                               AS reports_open
                        FROM
                            (SELECT
                                 count(*) FILTER (WHERE status = :email_failed)  AS failed,
                                 count(*) FILTER (WHERE status = :email_pending) AS pending,
                                 count(*) FILTER (WHERE status = :email_sending
                                                  AND created_at < now() - interval '10 minutes') AS stuck
                             FROM email_queue) e;""")
                .single(call().bind("email_failed", EmailQueueStatus.FAILED)
                        .bind("email_pending", EmailQueueStatus.PENDING)
                        .bind("email_sending", EmailQueueStatus.SENDING)
                        .bind("application_pending", ApplicationStatus.PENDING)
                        .bind("partner_pending", FederationStatus.PENDING))
                .map(row -> new AdminOverview(
                        row.getInt("failed"),
                        row.getInt("pending"),
                        row.getInt("stuck"),
                        row.getInt("applications_pending"),
                        row.getInt("setup_pending"),
                        row.getInt("unverified"),
                        row.getInt("partners_pending"),
                        row.getInt("peers_unreachable"),
                        row.getInt("reports_open"),
                        0,
                        applications,
                        reports))
                .first()
                .orElseThrow();
    }

    private List<RecentApplication> recentApplications() {
        return query("""
                        SELECT
                            id, first_name || ' ' || last_name AS name, station_name, created_at
                        FROM
                            station_application
                        WHERE status = :pending
                        ORDER BY created_at DESC
                        LIMIT 5;""")
                .single(call().bind("pending", ApplicationStatus.PENDING))
                .map(row -> new RecentApplication(
                        row.getInt("id"),
                        row.getString("name"),
                        row.getString("station_name"),
                        row.get("created_at", INSTANT_TIMESTAMP)))
                .all();
    }

    private List<RecentProblemReport> recentProblemReports() {
        return query("""
                        SELECT
                            id, reporter_name, page_url, created_at
                        FROM
                            problem_report
                        WHERE acknowledged = FALSE
                        ORDER BY created_at DESC
                        LIMIT 5;""")
                .single()
                .map(row -> new RecentProblemReport(
                        row.getInt("id"),
                        row.getString("reporter_name"),
                        row.getString("page_url"),
                        row.get("created_at", INSTANT_TIMESTAMP)))
                .all();
    }
}
