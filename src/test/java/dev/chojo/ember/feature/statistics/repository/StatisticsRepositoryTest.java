/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.statistics.repository;

import dev.chojo.ember.feature.attendance.entity.AttendanceEntry.AttendanceStatus;
import dev.chojo.ember.feature.attendance.entity.AttendanceEntry.EntrySource;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.inventory.entity.InventoryItemMetadata;
import dev.chojo.ember.feature.inventory.entity.InventoryType;
import dev.chojo.ember.feature.inventory.entity.ItemCustody;
import dev.chojo.ember.feature.mail.entity.EmailQueueStatus;
import dev.chojo.ember.feature.mail.entity.MailDeliveryStatus;
import dev.chojo.ember.feature.mail.entity.MailFailureReason;
import dev.chojo.ember.feature.statistics.entity.AdminStatistics.EmailStatusCount;
import dev.chojo.ember.feature.statistics.entity.InstanceTotals;
import dev.chojo.ember.feature.statistics.entity.StationStatistics.AttendanceMonth;
import dev.chojo.ember.feature.statistics.entity.StationStatistics.EventRegistrations;
import dev.chojo.ember.feature.statistics.entity.StationStatistics.InventoryStatus;
import dev.chojo.ember.repository.RepositoryTestBase;
import dev.chojo.ember.util.Json;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatisticsRepositoryTest extends RepositoryTestBase {
    private static StatisticsRepository statistics;

    @BeforeAll
    static void init() {
        statistics = new StatisticsRepository();
    }

    private static int applyFor(String stationName) {
        return stationApplicationRepo
                .create(
                        "Ada",
                        "Lovelace",
                        stationName + "@test.com",
                        stationName,
                        "Hello",
                        UUID.randomUUID().toString())
                .id();
    }

    private static Map<EmailQueueStatus, Integer> byStatus(InstanceTotals stats) {
        return stats.emailByStatus().stream()
                .collect(Collectors.toMap(EmailStatusCount::status, EmailStatusCount::cnt));
    }

    @Test
    void pendingApplicationsCountTheApplicationsWaitingForADecision() {
        var before = statistics.adminStatistics().counts().pendingApplications();
        var overviewBefore = statistics.adminOverview().stationApplicationsPending();

        int first = applyFor("Pending One");
        int second = applyFor("Pending Two");
        applyFor("Still Unverified");
        int denied = applyFor("Denied");
        stationApplicationRepo.verify(first);
        stationApplicationRepo.verify(second);
        stationApplicationRepo.verify(denied);
        stationApplicationRepo.deny(denied, "no");

        assertEquals(before + 2, statistics.adminStatistics().counts().pendingApplications());
        var overview = statistics.adminOverview();
        assertEquals(overviewBefore + 2, overview.stationApplicationsPending());
        assertTrue(overview.recentApplications().stream().anyMatch(app -> app.id() == first));
        assertTrue(overview.recentApplications().stream().anyMatch(app -> app.id() == second));
        assertTrue(overview.recentApplications().stream().noneMatch(app -> app.id() == denied));
        var application = overview.recentApplications().stream()
                .filter(app -> app.id() == first)
                .findFirst()
                .orElseThrow();
        assertEquals("Ada Lovelace", application.name());
        assertEquals("Pending One", application.stationName());
    }

    @Test
    void accountsStationsMembersAndGroupsAreCounted() {
        var before = statistics.adminStatistics().counts();

        var station = stationRepo.create("Statistics Station");
        var verified = accountRepo.create("stats-verified@test.com", "Veri", "Fied", true);
        var unverified = accountRepo.create("stats-unverified@test.com", "Unveri", "Fied", false);
        var member = stationMemberRepo.create(station.id(), verified.id());
        stationMemberRepo.create(station.id(), unverified.id());
        var group = memberGroupRepo.create(station.id(), "Crew");
        memberGroupRepo.addMember(group.id(), member.id());
        memberGroupRepo.create(station.id(), "Empty");

        var after = statistics.adminStatistics().counts();
        assertEquals(before.totalAccounts() + 2, after.totalAccounts());
        assertEquals(before.accountsVerified() + 1, after.accountsVerified());
        assertEquals(before.accountsUnverified() + 1, after.accountsUnverified());
        assertEquals(before.totalStations() + 1, after.totalStations());
        assertEquals(before.stationsSetupPending() + 1, after.stationsSetupPending());
        assertEquals(before.totalMembers() + 2, after.totalMembers());
        assertEquals(before.totalGroups() + 2, after.totalGroups());

        var stationStats = statistics.stationStatistics(station.id());
        assertEquals(2, stationStats.memberCount());
        assertEquals(Map.of("Crew", 1, "Empty", 0), stationStats.groupCounts());
        assertEquals(
                2,
                stationStats.userTypeCounts().values().stream()
                        .mapToInt(Integer::intValue)
                        .sum());
        assertTrue(stationStats.attendanceByMonth().isEmpty());
        assertTrue(stationStats.eventRegistrations().isEmpty());

        var top = statistics.adminStatistics().topStationsByMembers();
        assertTrue(top.stream().anyMatch(s -> s.name().equals("Statistics Station") && s.memberCount() == 2));
    }

    @Test
    void theMailQueueIsCountedPerStatus() {
        var before = statistics.adminStatistics();
        var beforeByStatus = byStatus(before);
        int overviewBefore = statistics.adminOverview().emailFailed();

        for (int i = 0; i < 4; i++) emailQueueRepo.enqueue("stats" + i + "@test.com", "Subject", "Body");
        var sending = emailQueueRepo.fetchPending(3, true);
        emailQueueRepo.markSent(sending.get(0).id());
        emailQueueRepo.markFailed(sending.get(1).id(), MailFailureReason.RETRIES_EXHAUSTED);

        var after = statistics.adminStatistics();
        assertEquals(before.counts().emailPending() + 1, after.counts().emailPending());
        assertEquals(before.counts().emailSending() + 1, after.counts().emailSending());
        assertEquals(before.counts().emailSent() + 1, after.counts().emailSent());
        assertEquals(before.counts().emailFailed() + 1, after.counts().emailFailed());
        var afterByStatus = byStatus(after);
        assertEquals(
                beforeByStatus.getOrDefault(EmailQueueStatus.PENDING, 0) + 1,
                afterByStatus.get(EmailQueueStatus.PENDING));
        assertEquals(afterByStatus.get(EmailQueueStatus.FAILED), after.counts().emailFailed());
        assertEquals(overviewBefore + 1, statistics.adminOverview().emailFailed());
    }

    @Test
    void theOverviewLeavesOutAssociationsAndStandInAddresses() {
        var before = statistics.adminOverview();
        var beforeCounts = statistics.adminStatistics().counts();

        clusterService.create("Overview Association", "Der Träger");
        stationRepo.create("Overview Station");
        accountRepo.create("overview-unconfirmed@test.com", "Un", "Confirmed", false);
        accountRepo.create("overview-stand-in@member.local", "Stand", "In", false);

        var after = statistics.adminOverview();
        assertEquals(before.accountsUnverified() + 1, after.accountsUnverified());
        assertEquals(
                beforeCounts.stationsSetupPending() + 1,
                statistics.adminStatistics().counts().stationsSetupPending());
    }

    @Test
    void theOverviewCountsMailsThatDidNotArriveUntilSomebodyTakesNote() {
        int before = statistics.adminOverview().emailFailed();
        var station = stationRepo.create("Overview Mail Station");

        emailQueueRepo.enqueue("bounced@test.com", "Bounced", "Body", station.id());
        emailQueueRepo.enqueue("deferred@test.com", "Deferred", "Body", station.id());
        emailQueueRepo.enqueue("given-up@test.com", "Given up", "Body");
        var sending = emailQueueRepo.fetchPending(10, true);
        int givenUp = 0;
        for (var mail : sending) {
            switch (mail.recipient()) {
                case "bounced@test.com" -> {
                    emailQueueRepo.markSent(mail.id());
                    emailQueueRepo.recordDelivery(mail.id(), MailDeliveryStatus.HARD_BOUNCE, "no such user", null);
                }
                case "deferred@test.com" -> {
                    emailQueueRepo.markSent(mail.id());
                    emailQueueRepo.recordDelivery(mail.id(), MailDeliveryStatus.DEFERRED, null, null);
                }
                case "given-up@test.com" -> {
                    givenUp = mail.id();
                    emailQueueRepo.markFailed(mail.id(), MailFailureReason.NO_PROVIDER);
                }
                default -> emailQueueRepo.requeue(mail.id());
            }
        }

        assertEquals(before + 2, statistics.adminOverview().emailFailed());

        emailQueueRepo.acknowledgeInstanceFailure(
                givenUp,
                accountRepo.create("overview-admin@test.com", "Ad", "Min").id());

        assertEquals(before + 1, statistics.adminOverview().emailFailed());
    }

    @Test
    void thePendingAndStuckCardsCountTheInstancesOwnMailsTheMailLogShows() {
        var before = statistics.adminOverview();
        var station = stationRepo.create("Overview Queue Station");

        emailQueueRepo.enqueue("waiting-instance@test.com", "Waiting", "Body");
        emailQueueRepo.enqueue("waiting-station@test.com", "Waiting", "Body", station.id());
        assertEquals(before.emailPending() + 1, statistics.adminOverview().emailPending());

        emailQueueRepo.enqueue("old-but-fresh-claim@test.com", "Claim", "Body");
        emailQueueRepo.enqueue("left-behind@test.com", "Claim", "Body");
        emailQueueRepo.enqueue("left-behind-station@test.com", "Claim", "Body", station.id());
        query("""
                UPDATE email_queue
                SET status     = 'SENDING',
                    created_at = now() - INTERVAL '1 day',
                    claimed_at = CASE WHEN recipient = 'old-but-fresh-claim@test.com' THEN now()
                                      ELSE now() - INTERVAL '1 hour' END
                WHERE recipient IN ('old-but-fresh-claim@test.com', 'left-behind@test.com',
                                    'left-behind-station@test.com');""").single(call()).update();

        assertEquals(
                before.emailStuckSending() + 1,
                statistics.adminOverview().emailStuckSending(),
                "only the instance's mail left behind past the stuck threshold");
    }

    private static int memberOf(int stationId, String email) {
        var account = accountRepo.create(email, "Stat", "Member", true);
        return stationMemberRepo.create(stationId, account.id()).id();
    }

    private static StationEvent eventAt(int stationId, String name, Instant start) {
        return eventAt(stationId, name, start, true);
    }

    private static StationEvent eventAt(int stationId, String name, Instant start, boolean requiresRegistration) {
        return eventRepo.create(
                stationId,
                name,
                "desc",
                StationEvent.EventType.ONE_TIME,
                null,
                start,
                start.plus(Duration.ofHours(1)),
                null,
                requiresRegistration,
                null,
                false,
                null,
                null,
                null,
                null,
                null);
    }

    @Test
    void attendanceIsCountedPerMonthAndStatus() {
        var station = stationRepo.create("Attendance Statistics Station");
        int present = memberOf(station.id(), "att-present@test.com");
        int absent = memberOf(station.id(), "att-absent@test.com");
        int declined = memberOf(station.id(), "att-declined@test.com");
        var template = attendanceRepo.createTemplate(station.id(), "Drill");
        Instant start = Instant.now().minus(Duration.ofHours(2));
        var session = attendanceRepo.createSession(
                template.id(), start, start.plus(Duration.ofHours(1)), null, "Drill", null);
        attendanceRepo.createSession(template.id(), start, start.plus(Duration.ofHours(1)), null, "Empty", null);
        attendanceRepo.createEntry(session.id(), present, AttendanceStatus.PRESENT, EntrySource.EXPECTED);
        attendanceRepo.createEntry(session.id(), absent, AttendanceStatus.ABSENT, EntrySource.EXPECTED);
        attendanceRepo.createEntry(session.id(), declined, AttendanceStatus.DECLINED, EntrySource.EXPECTED);

        var months = statistics.stationStatistics(station.id()).attendanceByMonth();

        String month = YearMonth.from(start.atZone(ZoneId.systemDefault())).toString();
        assertEquals(List.of(new AttendanceMonth(month, 2, 1, 1, 1)), months);
    }

    @Test
    void inventoriesCountTheirAssignedAndLostPieces() {
        var station = stationRepo.create("Inventory Statistics Station");
        int member = memberOf(station.id(), "inv-holder@test.com");
        var radios = inventoryRepo.create(station.id(), "Funk", InventoryType.INTERNAL, false, false);
        inventoryRepo.create(station.id(), "Leer", InventoryType.INTERNAL, false, false);
        var handedOut = inventoryRepo.createItem(radios.id(), "F-1", "Funk", null, InventoryItemMetadata.empty());
        var lost = inventoryRepo.createItem(radios.id(), "F-2", "Funk", null, InventoryItemMetadata.empty());
        inventoryRepo.createItem(radios.id(), "F-3", "Funk", null, InventoryItemMetadata.empty());
        inventoryRepo.updateCustody(handedOut.id(), ItemCustody.WITH_MEMBER, station.id(), member, null);
        inventoryRepo.updateCustody(lost.id(), ItemCustody.LOST, station.id(), null, null);

        var inventories = statistics.stationStatistics(station.id()).inventoryStatus();

        assertEquals(List.of(new InventoryStatus("Funk", 3, 1, 1), new InventoryStatus("Leer", 0, 0, 0)), inventories);
    }

    @Test
    void upcomingEventsWithAnswersCountThemPerStatus() {
        var station = stationRepo.create("Event Statistics Station");
        int first = memberOf(station.id(), "ev-first@test.com");
        int second = memberOf(station.id(), "ev-second@test.com");
        var upcoming = eventAt(station.id(), "Kommend", Instant.now().plus(Duration.ofDays(5)));
        eventAt(station.id(), "Unbeantwortet", Instant.now().plus(Duration.ofDays(6)));
        var past = eventAt(station.id(), "Vorbei", Instant.now().minus(Duration.ofDays(5)));
        LocalDate day = LocalDate.now().plusDays(5);
        eventRegistrationRepo.create(upcoming.id(), first, day, RegistrationStatus.ACCEPTED, null);
        eventRegistrationRepo.create(upcoming.id(), second, day, RegistrationStatus.PENDING, null);
        eventRegistrationRepo.create(upcoming.id(), first, day.plusDays(7), RegistrationStatus.DECLINED, null);
        eventRegistrationRepo.create(upcoming.id(), second, day.plusDays(7), RegistrationStatus.WITHDRAWN, null);
        eventRegistrationRepo.create(past.id(), first, LocalDate.now().minusDays(5), RegistrationStatus.ACCEPTED, null);

        var registrations = statistics.stationStatistics(station.id()).eventRegistrations();

        assertEquals(List.of(new EventRegistrations("Kommend", 1, 1, 2)), registrations);
    }

    /**
     * A series that went on as a new one, and a date taken out of it, count in one row with the series
     * they came from, under the name of the newest of them.
     */
    @Test
    void linkedAppointmentsCountAsOneUnderTheNewestName() {
        var station = stationRepo.create("Linked Statistics Station");
        int first = memberOf(station.id(), "ev-linked-first@test.com");
        int second = memberOf(station.id(), "ev-linked-second@test.com");
        var series = eventAt(station.id(), "Dienst", Instant.now().plus(Duration.ofDays(2)));
        var continued = eventAt(station.id(), "Dienst neu", Instant.now().plus(Duration.ofDays(9)));
        var detached = eventAt(station.id(), "Dienst einzeln", Instant.now().plus(Duration.ofDays(16)));
        eventRepo.setContinues(continued.id(), series.id(), LocalDate.now().plusDays(9));
        eventRepo.setContinues(detached.id(), continued.id(), LocalDate.now().plusDays(16));
        eventRegistrationRepo.create(
                series.id(), first, LocalDate.now().plusDays(2), RegistrationStatus.ACCEPTED, null);
        eventRegistrationRepo.create(
                continued.id(), second, LocalDate.now().plusDays(9), RegistrationStatus.PENDING, null);
        eventRegistrationRepo.create(
                detached.id(), first, LocalDate.now().plusDays(16), RegistrationStatus.ACCEPTED, null);

        var registrations = statistics.stationStatistics(station.id()).eventRegistrations();

        assertEquals(List.of(new EventRegistrations("Dienst einzeln", 2, 1, 0)), registrations);
    }

    /** A series that already began does not bring its past places into the row of its continuation. */
    @Test
    void aStartedSeriesDoesNotJoinItsContinuation() {
        var station = stationRepo.create("Started Statistics Station");
        int first = memberOf(station.id(), "ev-started-first@test.com");
        var started = eventAt(station.id(), "Dienst", Instant.now().minus(Duration.ofDays(60)));
        var continued = eventAt(station.id(), "Dienst neu", Instant.now().plus(Duration.ofDays(9)));
        eventRepo.setContinues(continued.id(), started.id(), LocalDate.now().plusDays(9));
        eventRegistrationRepo.create(
                started.id(), first, LocalDate.now().minusDays(30), RegistrationStatus.ACCEPTED, null);
        eventRegistrationRepo.create(
                continued.id(), first, LocalDate.now().plusDays(9), RegistrationStatus.PENDING, null);

        var registrations = statistics.stationStatistics(station.id()).eventRegistrations();

        assertEquals(List.of(new EventRegistrations("Dienst neu", 0, 1, 0)), registrations);
    }

    /**
     * Where everybody is expected, a withdrawal is a refusal taken back, so only the refusal still
     * standing counts as staying away.
     */
    @Test
    void aRefusalTakenBackWhereEverybodyIsExpectedIsNotCountedAsDeclined() {
        var station = stationRepo.create("Expected Statistics Station");
        int first = memberOf(station.id(), "ev-expected-first@test.com");
        int second = memberOf(station.id(), "ev-expected-second@test.com");
        var expected = eventAt(station.id(), "Dienstabend", Instant.now().plus(Duration.ofDays(5)), false);
        LocalDate day = LocalDate.now().plusDays(5);
        eventRegistrationRepo.create(expected.id(), first, day, RegistrationStatus.DECLINED, null);
        eventRegistrationRepo.create(expected.id(), second, day, RegistrationStatus.WITHDRAWN, null);

        var registrations = statistics.stationStatistics(station.id()).eventRegistrations();

        assertEquals(List.of(new EventRegistrations("Dienstabend", 0, 0, 1)), registrations);
    }

    @Test
    void theOverviewListsOnlyOpenProblemReports() {
        var station = stationRepo.create("Problem Statistics Station");
        int before = statistics.adminOverview().problemReportsOpen();
        var open = problemReportRepo.create(
                station.id(), null, "Grace", "Broken", "/stations", null, null, null, null, null);
        var handled = problemReportRepo.create(
                station.id(), null, "Linus", "Fixed", "/account", null, null, null, null, null);
        problemReportRepo.acknowledge(handled.id());

        var overview = statistics.adminOverview();

        assertEquals(before + 1, overview.problemReportsOpen());
        var report = overview.recentProblemReports().stream()
                .filter(r -> r.id() == open.id())
                .findFirst()
                .orElseThrow();
        assertEquals("Grace", report.reporterName());
        assertEquals("/stations", report.pageUrl());
        assertNotNull(report.createdAt());
        assertTrue(overview.recentProblemReports().stream().noneMatch(r -> r.id() == handled.id()));
    }

    @Test
    void theDailySeriesCoverThirtyDays() {
        var stats = statistics.adminStatistics();

        assertEquals(30, stats.signUpsByDay().size());
    }

    @Test
    void theJsonKeepsTheShapeThePagesRead() {
        var admin = Json.MAPPER.valueToTree(statistics.adminStatistics());
        assertTrue(admin.has("emailByDay"));
        assertTrue(admin.get("counts").has("totalClusters"));
        statistics.adminStatistics().topStationsByMembers().stream()
                .findFirst()
                .ifPresent(
                        _ -> assertTrue(admin.get("topStationsByMembers").get(0).has("member_count")));

        applyFor("Json Shape");
        var pending = stationApplicationRepo.findAll().stream()
                .filter(app -> app.stationName().equals("Json Shape"))
                .findFirst()
                .orElseThrow();
        stationApplicationRepo.verify(pending.id());
        var overview = Json.MAPPER.valueToTree(statistics.adminOverview());
        var application = overview.get("recentApplications").get(0);
        assertTrue(application.has("station_name"));
        assertTrue(application.has("created_at"));
        assertTrue(overview.has("stationApplicationsPending"));
    }
}
