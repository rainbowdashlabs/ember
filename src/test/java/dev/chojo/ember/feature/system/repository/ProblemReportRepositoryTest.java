/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.repository;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ProblemReportRepositoryTest extends RepositoryTestBase {
    private static Station station;
    private static Account account;
    private static StationMember member;
    private static int reportId;

    @BeforeAll
    static void setup() {
        station = stationRepo.create("ProblemReportStation");
        account = accountRepo.create("problem@test.com", "Problem", "Reporter");
        member = stationMemberRepo.create(station.id(), account.id());
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    @Test
    @Order(1)
    void create() {
        var report = problemReportRepo.create(
                station.id(),
                member.id(),
                "Problem Reporter",
                "Something is broken",
                "http://localhost/station/events",
                "LOGIN, USER",
                "[{\"method\":\"GET\",\"url\":\"/events\",\"status\":200}]",
                "Mozilla/5.0",
                "1920x1080");
        assertNotNull(report);
        assertTrue(report.id() > 0);
        assertEquals("Something is broken", report.message());
        assertEquals(station.id(), report.stationId());
        assertFalse(report.acknowledged());
        reportId = report.id();
    }

    @Test
    @Order(2)
    void findAll() {
        var reports = problemReportRepo.findAll(false);
        assertFalse(reports.isEmpty());
        assertTrue(reports.stream().anyMatch(r -> r.id() == reportId));
    }

    @Test
    @Order(3)
    void acknowledge() {
        assertTrue(problemReportRepo.acknowledge(reportId));
        var reports = problemReportRepo.findAll(false);
        assertTrue(reports.stream().noneMatch(r -> r.id() == reportId));
        var allReports = problemReportRepo.findAll(true);
        assertTrue(allReports.stream().anyMatch(r -> r.id() == reportId && r.acknowledged()));
    }

    @Test
    @Order(4)
    void acknowledgeAll() {
        // Create another report
        problemReportRepo.create(station.id(), null, "Anon", "Another issue", null, null, null, null, null);
        int count = problemReportRepo.acknowledgeAll();
        assertTrue(count >= 1);
        assertTrue(problemReportRepo.findAll(false).isEmpty());
    }

    @Test
    @Order(5)
    void delete() {
        assertTrue(problemReportRepo.delete(reportId));
        assertFalse(problemReportRepo.delete(reportId));
    }
}
