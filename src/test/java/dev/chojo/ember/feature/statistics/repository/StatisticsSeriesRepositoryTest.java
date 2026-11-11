/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.statistics.repository;

import de.chojo.sadu.queries.converter.StandardValueConverter;
import dev.chojo.ember.feature.attendance.entity.AttendanceEntry.AttendanceStatus;
import dev.chojo.ember.feature.attendance.entity.AttendanceEntry.EntrySource;
import dev.chojo.ember.feature.discovery.entity.PeerSource;
import dev.chojo.ember.feature.federation.entity.FederationPartner.FederationStatus;
import dev.chojo.ember.feature.statistics.entity.AdminStatistics;
import dev.chojo.ember.feature.statistics.entity.AdminStatistics.DayCount;
import dev.chojo.ember.feature.statistics.entity.InstanceSeries;
import dev.chojo.ember.feature.statistics.entity.InstanceSeries.FederationFigures;
import dev.chojo.ember.feature.statistics.entity.InstanceSeries.FederationShareKind;
import dev.chojo.ember.feature.statistics.entity.InstanceSeries.GrowthDay;
import dev.chojo.ember.feature.statistics.entity.InstanceSeries.MonthCount;
import dev.chojo.ember.feature.statistics.entity.InstanceSeries.PartnerStatusCount;
import dev.chojo.ember.feature.statistics.entity.InstanceSeries.PeopleDay;
import dev.chojo.ember.feature.statistics.entity.InstanceSeries.PeopleFigures;
import dev.chojo.ember.feature.statistics.entity.InstanceSeries.ShareCount;
import dev.chojo.ember.feature.statistics.entity.StationStatistics.AttendanceMonth;
import dev.chojo.ember.feature.statistics.entity.StatisticsWindow;
import dev.chojo.ember.repository.RepositoryTestBase;
import dev.chojo.ember.util.Json;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The series of the instance statistics over each window: people per day, growth and people by type
 * from the snapshot, attendance per month and federation.
 */
class StatisticsSeriesRepositoryTest extends RepositoryTestBase {
    private static StatisticsSeriesRepository series;

    @BeforeAll
    static void init() {
        series = new StatisticsSeriesRepository();
    }

    private static String day(String expression) {
        return query("SELECT to_char(%s, 'YYYY-MM-DD') AS day;", expression)
                .single()
                .map(row -> row.getString("day"))
                .first()
                .orElseThrow();
    }

    /**
     * Writes one day of the week starting ten weeks ago for each of its seven days: three people a day
     * and ten on the last, and a snapshot that grows by one account and one person a day.
     */
    private static String writeAWeekTenWeeksAgo() {
        query("""
                        INSERT INTO instance_usage_daily (day, active_accounts, accounts, people, people_member, memberships)
                        SELECT
                            date_trunc('week', CURRENT_DATE - 70)::DATE + n,
                            CASE WHEN n = 6 THEN 10 ELSE 3 END,
                            20 + n,
                            30 + n,
                            30 + n,
                            40 + n
                        FROM generate_series(0, 6) AS n
                        ON CONFLICT (day) DO UPDATE SET
                            active_accounts = excluded.active_accounts,
                            accounts        = excluded.accounts,
                            people          = excluded.people,
                            people_member   = excluded.people_member,
                            memberships     = excluded.memberships;""").single().update();
        return day("date_trunc('week', CURRENT_DATE - 70)");
    }

    private static void writeDay(int daysAgo, int active, int accounts) {
        query("""
                        INSERT INTO instance_usage_daily (day, active_accounts, accounts)
                        VALUES (CURRENT_DATE - :ago, :active, :accounts)
                        ON CONFLICT (day) DO UPDATE SET
                            active_accounts = excluded.active_accounts,
                            accounts        = excluded.accounts;""")
                .single(call().bind("ago", daysAgo).bind("active", active).bind("accounts", accounts))
                .update();
    }

    private static Optional<DayCount> pointOn(List<DayCount> days, String day) {
        return days.stream().filter(point -> point.day().equals(day)).findFirst();
    }

    @Test
    void theLastThirtyDaysAreDrawnDayByDay() {
        writeDay(3, 7, 0);
        writeDay(4, 2, 15);

        var usage = series.usage(StatisticsWindow.DAYS_30);

        var days = usage.days();
        assertTrue(days.size() >= 5 && days.size() <= 30, "from the recording or the window, whichever is later");
        assertEquals(day("CURRENT_DATE"), days.getLast().day());
        assertEquals(7, pointOn(days, day("CURRENT_DATE - 3")).orElseThrow().count());
        assertTrue(usage.average30() > 0);
        assertNotNull(usage.recordedSince());

        var growth = series.growth(StatisticsWindow.DAYS_30);
        assertTrue(
                growth.days().stream().noneMatch(point -> point.day().equals(day("CURRENT_DATE - 3"))),
                "a day the snapshot never ran on is left out");
        assertEquals(
                15,
                growth.days().stream()
                        .filter(point -> point.day().equals(day("CURRENT_DATE - 4")))
                        .findFirst()
                        .orElseThrow()
                        .accounts());
    }

    @Test
    void theAverageLeavesTheDayUnderWayOut() {
        writeDay(2, 6, 0);
        double before = series.usage(StatisticsWindow.DAYS_30).average30();

        query("""
                        INSERT INTO instance_usage_daily (day, active_accounts) VALUES (CURRENT_DATE, 1000)
                        ON CONFLICT (day) DO UPDATE SET active_accounts = instance_usage_daily.active_accounts + 1000;""").single().update();

        var usage = series.usage(StatisticsWindow.DAYS_30);
        assertEquals(before, usage.average30());
        assertTrue(usage.today() >= 1000);
    }

    @Test
    void longerWindowsAreDrawnWeekByWeek() {
        String week = writeAWeekTenWeeksAgo();

        var usage = series.usage(StatisticsWindow.MONTHS_12);
        assertEquals(4, pointOn(usage.days(), week).orElseThrow().count(), "the average of the week's days");
        assertTrue(pointOn(series.usage(StatisticsWindow.ALL).days(), week).isPresent());
        assertTrue(pointOn(series.usage(StatisticsWindow.DAYS_30).days(), week).isEmpty());
        assertTrue(usage.recordedSince() != null && usage.recordedSince().compareTo(week) <= 0);

        GrowthDay growth = series.growth(StatisticsWindow.MONTHS_12).days().stream()
                .filter(point -> point.day().equals(week))
                .findFirst()
                .orElseThrow();
        assertEquals(26, growth.accounts(), "the week keeps its last snapshot");
        var since = series.growth(StatisticsWindow.ALL).recordedSince();
        assertTrue(since != null && since.compareTo(week) <= 0);

        PeopleDay people = series.peopleByDay(StatisticsWindow.ALL).stream()
                .filter(point -> point.day().equals(week))
                .findFirst()
                .orElseThrow();
        assertEquals(36, people.counts().people());
        assertEquals(36, people.counts().member());
        assertEquals(46, people.counts().memberships());
    }

    private static Map<String, AttendanceMonth> byMonth(StatisticsWindow window) {
        return series.attendanceByMonth(window).stream()
                .collect(Collectors.toMap(AttendanceMonth::month, month -> month));
    }

    private static int memberOf(int stationId, String name) {
        var account = accountRepo.create(name + "-" + UUID.randomUUID() + "@test.com", name, "Series", true);
        return stationMemberRepo.create(stationId, account.id()).id();
    }

    @Test
    void attendanceIsCountedPerMonthWithinTheWindow() {
        var recentBefore = byMonth(StatisticsWindow.DAYS_30);
        var allBefore = byMonth(StatisticsWindow.ALL);
        var station = stationRepo.create("Series Attendance " + UUID.randomUUID());
        var template = attendanceRepo.createTemplate(station.id(), "Übung");
        Instant now = Instant.now().minus(Duration.ofHours(2));
        Instant old = Instant.now().minus(Duration.ofDays(800));
        var recent =
                attendanceRepo.createSession(template.id(), now, now.plus(Duration.ofHours(1)), null, "Übung", null);
        var earlier =
                attendanceRepo.createSession(template.id(), old, old.plus(Duration.ofHours(1)), null, "Alt", null);
        attendanceRepo.createEntry(
                recent.id(), memberOf(station.id(), "a"), AttendanceStatus.PRESENT, EntrySource.EXPECTED);
        attendanceRepo.createEntry(
                recent.id(), memberOf(station.id(), "b"), AttendanceStatus.ABSENT, EntrySource.EXPECTED);
        attendanceRepo.createEntry(
                recent.id(), memberOf(station.id(), "c"), AttendanceStatus.DECLINED, EntrySource.EXPECTED);
        attendanceRepo.createEntry(
                earlier.id(), memberOf(station.id(), "d"), AttendanceStatus.PRESENT, EntrySource.EXPECTED);

        String thisMonth = YearMonth.from(now.atZone(ZoneId.systemDefault())).toString();
        String oldMonth = YearMonth.from(old.atZone(ZoneId.systemDefault())).toString();
        var empty = new AttendanceMonth(thisMonth, 0, 0, 0, 0);
        var before = recentBefore.getOrDefault(thisMonth, empty);
        var after = byMonth(StatisticsWindow.DAYS_30).get(thisMonth);
        assertEquals(before.sessions() + 1, after.sessions());
        assertEquals(before.present() + 1, after.present());
        assertEquals(before.absent() + 1, after.absent());
        assertEquals(before.declined() + 1, after.declined());

        assertFalse(byMonth(StatisticsWindow.MONTHS_12).containsKey(oldMonth), "older than the window");
        int oldBefore =
                allBefore.containsKey(oldMonth) ? allBefore.get(oldMonth).present() : 0;
        assertEquals(oldBefore + 1, byMonth(StatisticsWindow.ALL).get(oldMonth).present());
    }

    private static int countOf(FederationFigures figures, FederationStatus status) {
        return figures.partnersByStatus().stream()
                .filter(count -> count.status() == status)
                .mapToInt(PartnerStatusCount::count)
                .sum();
    }

    private static int lendingThisMonth(FederationFigures figures) {
        String month = query("SELECT to_char(now(), 'YYYY-MM') AS month;")
                .single()
                .map(row -> row.getString("month"))
                .first()
                .orElseThrow();
        return figures.lendingByMonth().stream()
                .filter(count -> count.month().equals(month))
                .mapToInt(MonthCount::count)
                .sum();
    }

    private static void partner(
            int stationId, UUID partnerUid, FederationStatus status, String remoteHost, boolean clusterManaged) {
        query("""
                        INSERT INTO federation_partner (station_id, partner_station_id, status, remote_host, cluster_managed)
                        VALUES (:station, :partner::UUID, :status, :host, :managed);""")
                .single(call().bind("station", stationId)
                        .bind("partner", partnerUid, StandardValueConverter.UUID_STRING)
                        .bind("status", status)
                        .bind("host", remoteHost)
                        .bind("managed", clusterManaged))
                .insert();
    }

    @Test
    void federationCountsPartnershipsPeersLendingAndAssociations() {
        var before = series.federation(StatisticsWindow.DAYS_30);
        var station = stationRepo.create("Series Federation " + UUID.randomUUID());
        var other = stationRepo.create("Series Partner " + UUID.randomUUID());
        partner(station.id(), other.uid(), FederationStatus.ACTIVE, null, false);
        partner(other.id(), station.uid(), FederationStatus.ACTIVE, null, false);
        partner(
                station.id(),
                UUID.randomUUID(),
                FederationStatus.ACTIVE,
                "peer-" + UUID.randomUUID() + ".example",
                false);
        var asking = stationRepo.create("Series Asking " + UUID.randomUUID());
        partner(asking.id(), UUID.randomUUID(), FederationStatus.PENDING, null, false);
        partner(asking.id(), station.uid(), FederationStatus.ACTIVE, null, true);
        var moved = stationRepo.create("Series Moved " + UUID.randomUUID());
        partner(moved.id(), UUID.randomUUID(), FederationStatus.ACTIVE, null, false);
        discoveryPeerRepo.upsert(
                "series-" + UUID.randomUUID(), "https://series.example", "fp-series", PeerSource.MANUAL, null);
        var home = stationRepo.create("Series Home " + UUID.randomUUID());
        stationRepo.markAsClusterHome(home.id());
        var cluster = clusterRepo.create("Series Verband " + UUID.randomUUID(), null, home.id());
        stationRepo.setCluster(station.id(), cluster.id());
        stationRepo.setCluster(moved.id(), cluster.id());
        stationRepo.markMovedAway(moved.id(), null);
        int requester = memberOf(station.id(), "lender");
        query("""
                        INSERT INTO federation_lending_request
                            (uid, requesting_station_uid, owning_station_uid, requested_date_from, created_by)
                        VALUES (:uid::UUID, :requesting::UUID, :owning::UUID, CURRENT_DATE, :member);""")
                .single(call().bind("uid", UUID.randomUUID(), StandardValueConverter.UUID_STRING)
                        .bind("requesting", station.uid(), StandardValueConverter.UUID_STRING)
                        .bind("owning", other.uid(), StandardValueConverter.UUID_STRING)
                        .bind("member", requester))
                .insert();

        var after = series.federation(StatisticsWindow.DAYS_30);

        assertEquals(countOf(before, FederationStatus.ACTIVE) + 2, countOf(after, FederationStatus.ACTIVE));
        assertEquals(countOf(before, FederationStatus.PENDING) + 1, countOf(after, FederationStatus.PENDING));
        assertEquals(before.localPartners() + 1, after.localPartners(), "a pair here counts once");
        assertEquals(before.remotePartners() + 1, after.remotePartners());
        assertEquals(
                before.stationsWithPartner() + 2,
                after.stationsWithPartner(),
                "both sides of the pair, not the association's link nor the station that moved away");
        assertEquals(before.partnerInstances() + 1, after.partnerInstances());
        assertEquals(before.peersKnown() + 1, after.peersKnown());
        assertEquals(before.peersReachable() + 1, after.peersReachable());
        assertEquals(before.associations() + 1, after.associations());
        assertEquals(
                before.stationsInAssociations() + 1,
                after.stationsInAssociations(),
                "a station that moved away is not in the association here");
        assertEquals(lendingThisMonth(before) + 1, lendingThisMonth(after));
        assertEquals(
                List.of(FederationShareKind.values()),
                after.sharesByKind().stream().map(ShareCount::kind).toList());
    }

    @Test
    void theJsonKeepsTheShapeThePageReads() {
        var usage = new UsageRepository();
        var window = StatisticsWindow.MONTHS_12;
        var over = new InstanceSeries(
                series.usage(window),
                series.growth(window),
                series.attendanceByMonth(window),
                series.federation(window),
                new PeopleFigures(usage.peopleByType(), series.peopleByDay(window)));

        var json =
                Json.MAPPER.valueToTree(AdminStatistics.of(new StatisticsRepository().adminStatistics(), window, over));

        assertTrue(json.has("pendingApplications"), "the counts sit at the top level");
        assertEquals("MONTHS_12", json.get("window").asText());
        assertTrue(json.get("activeByDay").has("average30"));
        assertTrue(json.get("growth").has("days"));
        assertTrue(json.get("peopleByType").get("current").has("guardian"));
        assertTrue(json.get("federation").has("sharesByKind"));
        assertTrue(json.has("signUpsByDay"));
    }
}
