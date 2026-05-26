/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.service;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.protocol.service.TestProtocolService;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TestProtocolServiceTest extends RepositoryTestBase {
    private static TestProtocolService service;
    private static Station station;
    private static Account account;
    private static StationMember member;
    private static int protocolId;
    private static int sectionId;
    private static int itemId;
    private static int runId;

    @BeforeAll
    static void setup() {
        service = new TestProtocolService(testProtocolRepo);
        station = stationRepo.create("ProtocolSvcStation");
        account = accountRepo.create("protocol-svc@test.com", "Protocol", "SvcTester");
        member = stationMemberRepo.create(station.id(), account.id());
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    // -- Protocols --

    @Test
    @Order(1)
    void createProtocol() {
        var p = service.createProtocol(station.id(), "Driving Test", "Road safety", 75);
        assertNotNull(p);
        assertEquals("Driving Test", p.name());
        protocolId = p.id();
    }

    @Test
    @Order(2)
    void findProtocols() {
        var protocols = service.findProtocols(station.id());
        assertTrue(protocols.stream().anyMatch(p -> p.id() == protocolId));
    }

    @Test
    @Order(3)
    void findProtocol() {
        assertTrue(service.findProtocol(protocolId).isPresent());
        assertTrue(service.findProtocol(99999).isEmpty());
    }

    @Test
    @Order(4)
    void searchProtocols() {
        var results = service.searchProtocols(station.id(), "Driving");
        assertTrue(results.stream().anyMatch(p -> p.id() == protocolId));

        var empty = service.searchProtocols(station.id(), "xyzzy_xyz99999");
        assertTrue(empty.isEmpty());
    }

    @Test
    @Order(5)
    void updateProtocol() {
        assertTrue(service.updateProtocol(protocolId, "Updated Test", "Updated", 80));
    }

    // -- Sections --

    @Test
    @Order(10)
    void createSection() {
        var section = service.createSection(protocolId, null, "Theory", "Theory section", 50, 30, 0);
        assertNotNull(section);
        assertEquals("Theory", section.name());
        sectionId = section.id();
    }

    @Test
    @Order(11)
    void findSections() {
        assertFalse(service.findSections(protocolId).isEmpty());
    }

    @Test
    @Order(12)
    void updateSection() {
        assertTrue(service.updateSection(sectionId, "Theory Updated", "Updated", 60, 40, 1));
    }

    // -- Items --

    @Test
    @Order(20)
    void createItem() {
        var item = service.createItem(sectionId, "Knows stop signs", "Stop sign check", 10.0, 0);
        assertNotNull(item);
        assertEquals("Knows stop signs", item.label());
        itemId = item.id();
    }

    @Test
    @Order(21)
    void findItems() {
        assertFalse(service.findItems(sectionId).isEmpty());
    }

    @Test
    @Order(22)
    void findAllItemsByProtocol() {
        var items = service.findAllItemsByProtocol(protocolId);
        assertTrue(items.stream().anyMatch(i -> i.id() == itemId));
    }

    @Test
    @Order(23)
    void updateItem() {
        assertTrue(service.updateItem(itemId, "Updated label", "Updated desc", 12.0, 1));
    }

    // -- Runs --

    @Test
    @Order(30)
    void createRun() {
        var run = service.createRun(protocolId, station.id(), "Run 2026-01", LocalDate.of(2026, 1, 15), member.id());
        assertNotNull(run);
        assertEquals("Run 2026-01", run.name());
        runId = run.id();
    }

    @Test
    @Order(31)
    void findRuns() {
        assertTrue(service.findRuns(station.id()).stream().anyMatch(r -> r.id() == runId));
    }

    @Test
    @Order(32)
    void findRun() {
        assertTrue(service.findRun(runId).isPresent());
    }

    @Test
    @Order(33)
    void updateRun() {
        assertTrue(service.updateRun(runId, "Updated Run", LocalDate.of(2026, 2, 1)));
    }

    // -- Run Members --

    @Test
    @Order(40)
    void addRunMember() {
        var rm = service.addRunMember(runId, member.id());
        assertNotNull(rm);
    }

    @Test
    @Order(41)
    void addRunMembers() {
        // Add same member again (idempotent)
        service.addRunMembers(runId, List.of(member.id()));
        var members = service.findRunMembers(runId);
        assertFalse(members.isEmpty());
    }

    @Test
    @Order(42)
    void findRunMember() {
        assertTrue(service.findRunMember(runId, member.id()).isPresent());
        assertTrue(service.findRunMember(runId, 99999).isEmpty());
    }

    @Test
    @Order(43)
    void lockAndUnlock() {
        assertTrue(service.lockMember(runId, member.id(), member.id()));
        assertTrue(service.unlockMember(runId, member.id()));
    }

    @Test
    @Order(44)
    void lockNonexistentMember() {
        assertFalse(service.lockMember(runId, 99999, member.id()));
    }

    // -- Checks and Score --

    @Test
    @Order(50)
    void saveChecks() {
        service.saveChecks(runId, member.id(), Map.of(itemId, true), member.id(), protocolId);
        // No exception = success
    }

    @Test
    @Order(51)
    void saveChecksForNonexistentMember() {
        // Should silently do nothing
        service.saveChecks(runId, 99999, Map.of(itemId, true), member.id(), protocolId);
    }

    @Test
    @Order(52)
    void findChecks() {
        var checks = service.findChecks(runId, member.id());
        assertFalse(checks.isEmpty());
    }

    @Test
    @Order(53)
    void findChecksForNonexistentMember() {
        assertTrue(service.findChecks(runId, 99999).isEmpty());
    }

    // -- Section Done --

    @Test
    @Order(60)
    void toggleSectionDone() {
        // Toggle on
        service.toggleSectionDone(runId, member.id(), sectionId, member.id());
        var done = service.findDoneSections(runId, member.id());
        assertTrue(done.contains(sectionId));

        // Toggle off
        service.toggleSectionDone(runId, member.id(), sectionId, member.id());
        done = service.findDoneSections(runId, member.id());
        assertFalse(done.contains(sectionId));
    }

    @Test
    @Order(61)
    void toggleSectionDoneNonexistentMember() {
        // Should silently do nothing
        service.toggleSectionDone(runId, 99999, sectionId, member.id());
    }

    @Test
    @Order(62)
    void findDoneSectionsNonexistentMember() {
        assertTrue(service.findDoneSections(runId, 99999).isEmpty());
    }

    @Test
    @Order(63)
    void countDoneSections() {
        var rm = testProtocolRepo.findRunMember(runId, member.id()).orElseThrow();
        assertEquals(0, service.countDoneSections(rm.id()));
    }

    // -- Complete Member --

    @Test
    @Order(70)
    void completeMember() {
        // Re-save checks to have scored items
        service.saveChecks(runId, member.id(), Map.of(itemId, true), member.id(), protocolId);
        assertTrue(service.completeMember(runId, member.id(), protocolId));
    }

    @Test
    @Order(71)
    void completeMemberNonexistent() {
        assertFalse(service.completeMember(runId, 99999, protocolId));
    }

    @Test
    @Order(72)
    void closeRun() {
        assertTrue(service.closeRun(runId));
    }

    // -- Cleanup --

    @Test
    @Order(90)
    void deleteItem() {
        assertTrue(service.deleteItem(itemId));
    }

    @Test
    @Order(91)
    void deleteSection() {
        assertTrue(service.deleteSection(sectionId));
    }

    @Test
    @Order(92)
    void deleteRun() {
        assertTrue(service.deleteRun(runId));
    }

    @Test
    @Order(99)
    void deleteProtocol() {
        assertTrue(service.deleteProtocol(protocolId));
    }
}
