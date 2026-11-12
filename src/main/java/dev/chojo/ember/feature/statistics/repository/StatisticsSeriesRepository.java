/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.statistics.repository;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import dev.chojo.ember.feature.attendance.entity.AttendanceEntry.AttendanceStatus;
import dev.chojo.ember.feature.cluster.entity.StationKind;
import dev.chojo.ember.feature.federation.entity.FederationPartner.FederationStatus;
import dev.chojo.ember.feature.statistics.entity.AdminStatistics.DayCount;
import dev.chojo.ember.feature.statistics.entity.InstanceSeries.FederationFigures;
import dev.chojo.ember.feature.statistics.entity.InstanceSeries.FederationShareKind;
import dev.chojo.ember.feature.statistics.entity.InstanceSeries.GrowthDay;
import dev.chojo.ember.feature.statistics.entity.InstanceSeries.GrowthSeries;
import dev.chojo.ember.feature.statistics.entity.InstanceSeries.MonthCount;
import dev.chojo.ember.feature.statistics.entity.InstanceSeries.PartnerStatusCount;
import dev.chojo.ember.feature.statistics.entity.InstanceSeries.PeopleDay;
import dev.chojo.ember.feature.statistics.entity.InstanceSeries.ShareCount;
import dev.chojo.ember.feature.statistics.entity.InstanceSeries.UsageSeries;
import dev.chojo.ember.feature.statistics.entity.PeopleByType;
import dev.chojo.ember.feature.statistics.entity.StationStatistics.AttendanceMonth;
import dev.chojo.ember.feature.statistics.entity.StatisticsWindow;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.List;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * Reads the series of the instance statistics page over a window of time.
 *
 * <p>People per day and the size of the instance come from {@code instance_usage_daily}, which only
 * holds days since it was first written; a series never reaches back before that day, so a young
 * recording does not draw a run of empty days. A day only the live count has touched carries no
 * snapshot yet (its account count is still zero) and is left out of the snapshot series. Attendance
 * and lending carry their own times and reach back as far as the window does.
 */
@Singleton
public class StatisticsSeriesRepository {

    private static final String REGULAR_STATION = "s.station_kind = :regular AND s.moved_away_at IS NULL";

    /**
     * The partnerships of regular stations still on this instance that the stations made themselves.
     * The links an association manages between its stations are a part of the association, not a
     * partnership.
     */
    private static final String STATION_PARTNERS = """
            federation_partner p
            JOIN station s ON s.id = p.station_id AND %s AND NOT p.cluster_managed""".formatted(REGULAR_STATION);

    /**
     * One partnership: two stations here pair up with a row on each side, which counts once; a
     * partner on another instance is the one row this side keeps.
     */
    private static final String PARTNERSHIP = """
            CASE WHEN p.remote_host IS NULL
                 THEN least(s.uid, p.partner_station_id)::TEXT || greatest(s.uid, p.partner_station_id)::TEXT
                 ELSE p.id::TEXT END""";

    private static String windowStart(StatisticsWindow window) {
        return switch (window) {
            case DAYS_30 -> "CURRENT_DATE - 29";
            case MONTHS_12 -> "(CURRENT_DATE - INTERVAL '12 months')::DATE";
            case ALL -> "'-infinity'::DATE";
        };
    }

    private static String bucket(StatisticsWindow window) {
        return window.byWeek() ? "week" : "day";
    }

    /**
     * People using the instance per day: today, the average day and the series.
     *
     * <p>The average is taken over complete days only, the thirty before today or the days recorded
     * before today when there are fewer, so a day still under way does not pull it down. Before
     * anything earlier is recorded it is today's figure.
     *
     * @param window how far back the series reaches
     * @return the usage
     */
    public UsageSeries usage(StatisticsWindow window) {
        return query("""
                        WITH today AS (
                            SELECT coalesce((SELECT active_accounts FROM instance_usage_daily WHERE day = CURRENT_DATE), 0)
                                AS active)
                        SELECT
                            today.active AS today,
                            CASE
                                WHEN min(day) IS NULL THEN 0
                                WHEN min(day) >= CURRENT_DATE THEN today.active
                                ELSE round(coalesce((SELECT sum(active_accounts)
                                                     FROM instance_usage_daily
                                                     WHERE day >= CURRENT_DATE - 30 AND day < CURRENT_DATE), 0)::NUMERIC
                                           / (CURRENT_DATE - greatest(CURRENT_DATE - 30, min(day))),
                                           1)
                            END::FLOAT8 AS average,
                            to_char(min(day), 'YYYY-MM-DD') AS recorded_since
                        FROM today LEFT JOIN instance_usage_daily ON TRUE
                        GROUP BY today.active;""")
                .single()
                .map(row -> new UsageSeries(
                        row.getInt("today"),
                        row.getDouble("average"),
                        row.getString("recorded_since"),
                        activeDays(window)))
                .first()
                .orElseThrow();
    }

    private List<DayCount> activeDays(StatisticsWindow window) {
        return query("""
                        WITH bounds AS (
                            SELECT CASE WHEN min(day) IS NOT NULL THEN greatest(%s, min(day)) END AS first_day
                            FROM instance_usage_daily)
                        SELECT
                            to_char(date_trunc('%s', d.day), 'YYYY-MM-DD') AS day,
                            round(avg(coalesce(u.active_accounts, 0)))::INT AS count
                        FROM
                            bounds,
                            generate_series(bounds.first_day, CURRENT_DATE, INTERVAL '1 day') AS d(day)
                            LEFT JOIN instance_usage_daily u ON u.day = d.day::DATE
                        GROUP BY 1
                        ORDER BY 1;""", windowStart(window), bucket(window))
                .single()
                .map(row -> new DayCount(row.getString("day"), row.getInt("count")))
                .all();
    }

    /**
     * The size of the instance from the daily snapshot: per day, or per week from the last snapshot
     * of each week.
     *
     * @param window how far back the series reaches
     * @return the growth series
     */
    public GrowthSeries growth(StatisticsWindow window) {
        var days = snapshots(
                window,
                row -> new GrowthDay(
                        row.getString("bucket"),
                        row.getInt("accounts"),
                        row.getInt("members"),
                        row.getInt("stations"),
                        row.getInt("events"),
                        row.getInt("attendance_entries"),
                        row.getInt("inventory_items"),
                        row.getInt("clusters")));
        return new GrowthSeries(snapshotsSince(), days);
    }

    /**
     * The people by member type from the daily snapshot: per day, or per week from the last snapshot
     * of each week.
     *
     * @param window how far back the series reaches
     * @return the counts per day
     */
    public List<PeopleDay> peopleByDay(StatisticsWindow window) {
        return snapshots(
                window,
                row -> new PeopleDay(
                        row.getString("bucket"),
                        new PeopleByType(
                                row.getInt("people"),
                                row.getInt("people_trial"),
                                row.getInt("people_member"),
                                row.getInt("people_guardian"),
                                row.getInt("people_team"),
                                row.getInt("people_manager"),
                                row.getInt("memberships"))));
    }

    private <T> List<T> snapshots(StatisticsWindow window, RowMapping<T> mapping) {
        return query("""
                        SELECT DISTINCT ON (date_trunc('%1$s', day::TIMESTAMP))
                            to_char(date_trunc('%1$s', day::TIMESTAMP), 'YYYY-MM-DD') AS bucket,
                            accounts, members, stations, events, attendance_entries, inventory_items, clusters,
                            people, people_trial, people_member, people_guardian, people_team, people_manager,
                            memberships
                        FROM instance_usage_daily
                        WHERE accounts > 0
                          AND day >= %2$s
                        ORDER BY date_trunc('%1$s', day::TIMESTAMP), day DESC;""", bucket(window), windowStart(window))
                .single()
                .map(mapping)
                .all();
    }

    private @Nullable String snapshotsSince() {
        return query("""
                        SELECT to_char(min(day), 'YYYY-MM-DD') AS since
                        FROM instance_usage_daily
                        WHERE accounts > 0;""").single().map(row -> row.getString("since")).first().orElse(null);
    }

    /**
     * Attendance entries per month in the window, by the start of their sheet, with the sheets held.
     *
     * @param window how far back the series reaches; a month is counted whole once the window
     *               touches it
     * @return one row per month with any sheet, oldest first
     */
    public List<AttendanceMonth> attendanceByMonth(StatisticsWindow window) {
        return query("""
                        SELECT
                            to_char(s.start_time, 'YYYY-MM')                AS month,
                            count(DISTINCT s.id)                            AS sessions,
                            count(e.id) FILTER (WHERE e.status = :present)  AS present,
                            count(e.id) FILTER (WHERE e.status = :absent)   AS absent,
                            count(e.id) FILTER (WHERE e.status = :declined) AS declined
                        FROM
                            attendance_session s
                            LEFT JOIN attendance_entry e ON e.session_id = s.id
                        WHERE s.start_time >= date_trunc('month', (%s)::TIMESTAMP)
                          AND s.start_time < date_trunc('month', now()) + INTERVAL '1 month'
                        GROUP BY month
                        ORDER BY month;""", windowStart(window))
                .single(call().bind("present", AttendanceStatus.PRESENT)
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

    /**
     * Federation across the instance: partnerships, what is shared, lending in the window, the other
     * instances discovery knows, and the associations.
     *
     * @param window how far back the lending requests reach
     * @return the figures
     */
    public FederationFigures federation(StatisticsWindow window) {
        var byStatus = partnersByStatus();
        var shares = sharesByKind();
        var lending = lendingByMonth(window);
        return query("""
                        SELECT
                            p.local, p.remote, p.stations, p.instances,
                            (SELECT count(*) FROM discovery_peer)                                AS peers_known,
                            (SELECT count(*) FROM discovery_peer WHERE reachable AND NOT blocked) AS peers_reachable,
                            (SELECT count(*) FROM cluster)                                       AS associations,
                            (SELECT count(*) FROM station s
                             WHERE s.cluster_id IS NOT NULL AND %1$s)                            AS in_associations
                        FROM
                            (SELECT
                                 count(DISTINCT %2$s) FILTER (WHERE p.remote_host IS NULL) AS local,
                                 count(*) FILTER (WHERE p.remote_host IS NOT NULL)         AS remote,
                                 count(DISTINCT p.station_id)                              AS stations,
                                 count(DISTINCT p.remote_host)                             AS instances
                             FROM %3$s
                             WHERE p.status = :active) p;""", REGULAR_STATION, PARTNERSHIP, STATION_PARTNERS)
                .single(call().bind("active", FederationStatus.ACTIVE).bind("regular", StationKind.REGULAR))
                .map(row -> new FederationFigures(
                        byStatus,
                        row.getInt("local"),
                        row.getInt("remote"),
                        row.getInt("stations"),
                        row.getInt("instances"),
                        shares,
                        lending,
                        row.getInt("peers_known"),
                        row.getInt("peers_reachable"),
                        row.getInt("associations"),
                        row.getInt("in_associations")))
                .first()
                .orElseThrow();
    }

    private List<PartnerStatusCount> partnersByStatus() {
        return query("""
                        SELECT p.status, count(DISTINCT %s) AS count
                        FROM %s
                        GROUP BY p.status
                        ORDER BY p.status;""", PARTNERSHIP, STATION_PARTNERS)
                .single(call().bind("regular", StationKind.REGULAR))
                .map(row -> new PartnerStatusCount(row.getEnum("status", FederationStatus.class), row.getInt("count")))
                .all();
    }

    private List<ShareCount> sharesByKind() {
        return query("""
                        SELECT
                            (SELECT count(*) FROM federation_kb_share)               AS knowledge_base,
                            (SELECT count(*) FROM federation_board_share)            AS board,
                            (SELECT count(*) FROM federation_quiz_share)             AS quiz,
                            (SELECT count(*) FROM federation_protocol_share)         AS protocol,
                            (SELECT count(*) FROM event_federation_share)            AS event,
                            (SELECT count(*) FROM federation_inventory_share_target) AS inventory;""")
                .single()
                .map(row -> List.of(
                        new ShareCount(FederationShareKind.KNOWLEDGE_BASE, row.getInt("knowledge_base")),
                        new ShareCount(FederationShareKind.BOARD, row.getInt("board")),
                        new ShareCount(FederationShareKind.QUIZ, row.getInt("quiz")),
                        new ShareCount(FederationShareKind.PROTOCOL, row.getInt("protocol")),
                        new ShareCount(FederationShareKind.EVENT, row.getInt("event")),
                        new ShareCount(FederationShareKind.INVENTORY, row.getInt("inventory"))))
                .first()
                .orElseThrow();
    }

    private List<MonthCount> lendingByMonth(StatisticsWindow window) {
        return query("""
                        SELECT
                            to_char(created_at, 'YYYY-MM') AS month,
                            count(*)                       AS count
                        FROM federation_lending_request
                        WHERE created_at >= date_trunc('month', (%s)::TIMESTAMP)
                          AND created_at < date_trunc('month', now()) + INTERVAL '1 month'
                        GROUP BY month
                        ORDER BY month;""", windowStart(window))
                .single()
                .map(row -> new MonthCount(row.getString("month"), row.getInt("count")))
                .all();
    }
}
