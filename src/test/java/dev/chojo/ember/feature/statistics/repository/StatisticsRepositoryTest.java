/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.statistics.repository;

import dev.chojo.ember.feature.mail.entity.EmailQueueStatus;
import dev.chojo.ember.feature.statistics.entity.AdminStatistics;
import dev.chojo.ember.feature.statistics.entity.AdminStatistics.EmailStatusCount;
import dev.chojo.ember.repository.RepositoryTestBase;
import dev.chojo.ember.util.Json;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    private static Map<EmailQueueStatus, Integer> byStatus(AdminStatistics stats) {
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

        for (int i = 0; i < 4; i++) emailQueueRepo.enqueue("stats" + i + "@test.com", "Subject", "Body");
        var sending = emailQueueRepo.fetchPending(3, true);
        emailQueueRepo.markSent(sending.get(0).id());
        emailQueueRepo.markFailed(sending.get(1).id());

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
        assertEquals(
                before.counts().emailFailed() + 1, statistics.adminOverview().emailFailed());
    }

    @Test
    void theDailySeriesCoverThirtyDays() {
        var stats = statistics.adminStatistics();

        assertEquals(30, stats.registrationsByDay().size());
        assertEquals(30, stats.sessionsByDay().size());
    }

    @Test
    void theJsonKeepsTheShapeThePagesRead() {
        var admin = Json.MAPPER.valueToTree(statistics.adminStatistics());
        assertTrue(admin.has("pendingApplications"), "the counts sit at the top level");
        assertTrue(admin.has("emailByDay"));
        assertTrue(admin.has("totalClusters"));
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
