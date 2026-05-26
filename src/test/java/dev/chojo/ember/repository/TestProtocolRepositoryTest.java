/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.repository;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.station.entity.Station;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TestProtocolRepositoryTest extends RepositoryTestBase {
    private static Station station;
    private static Account account;
    private static StationMember member;
    private static int protocolId;
    private static int sectionId;
    private static int itemId;
    private static int runId;
    private static int runMemberId;

    @BeforeAll
    static void setup() {
        station = stationRepo.create("ProtocolRepoStation");
        account = accountRepo.create("protocol-repo@test.com", "Protocol", "Tester");
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
        var protocol = testProtocolRepo.createProtocol(station.id(), "Driving License", "Road safety test", 70);
        assertNotNull(protocol);
        assertEquals("Driving License", protocol.name());
        assertEquals(70, protocol.passThreshold());
        protocolId = protocol.id();
    }

    @Test
    @Order(2)
    void findProtocols() {
        var protocols = testProtocolRepo.findProtocols(station.id());
        assertFalse(protocols.isEmpty());
        assertTrue(protocols.stream().anyMatch(p -> p.id() == protocolId));
    }

    @Test
    @Order(3)
    void findProtocolById() {
        assertTrue(testProtocolRepo.findProtocolById(protocolId).isPresent());
        assertTrue(testProtocolRepo.findProtocolById(99999).isEmpty());
    }

    @Test
    @Order(4)
    void searchProtocols() {
        var results = testProtocolRepo.searchProtocols(station.id(), "Driving");
        assertTrue(results.stream().anyMatch(p -> p.id() == protocolId));

        var noResults = testProtocolRepo.searchProtocols(station.id(), "xyzzy99999");
        assertTrue(noResults.isEmpty());
    }

    @Test
    @Order(5)
    void updateProtocol() {
        assertTrue(testProtocolRepo.updateProtocol(protocolId, "Updated Protocol", "Updated desc", 80));
        var found = testProtocolRepo.findProtocolById(protocolId).orElseThrow();
        assertEquals("Updated Protocol", found.name());
        assertEquals(80, found.passThreshold());
    }

    // -- Sections --

    @Test
    @Order(10)
    void createSection() {
        var section = testProtocolRepo.createSection(protocolId, null, "Theory", "Theory section", 50, 30, 0);
        assertNotNull(section);
        assertEquals("Theory", section.name());
        sectionId = section.id();
    }

    @Test
    @Order(11)
    void findSections() {
        var sections = testProtocolRepo.findSections(protocolId);
        assertFalse(sections.isEmpty());
        assertTrue(sections.stream().anyMatch(s -> s.id() == sectionId));
    }

    @Test
    @Order(12)
    void updateSection() {
        assertTrue(testProtocolRepo.updateSection(sectionId, "Updated Theory", "Updated desc", 60, 40, 1));
        var sections = testProtocolRepo.findSections(protocolId);
        assertEquals(
                "Updated Theory",
                sections.stream()
                        .filter(s -> s.id() == sectionId)
                        .findFirst()
                        .orElseThrow()
                        .name());
    }

    // -- Items --

    @Test
    @Order(20)
    void createItem() {
        var item = testProtocolRepo.createItem(sectionId, "Knows traffic signs", "Check sign knowledge", 10.0, 0);
        assertNotNull(item);
        assertEquals("Knows traffic signs", item.label());
        itemId = item.id();
    }

    @Test
    @Order(21)
    void findItems() {
        var items = testProtocolRepo.findItems(sectionId);
        assertFalse(items.isEmpty());
        assertTrue(items.stream().anyMatch(i -> i.id() == itemId));
    }

    @Test
    @Order(22)
    void findAllItemsByProtocol() {
        var items = testProtocolRepo.findAllItemsByProtocol(protocolId);
        assertFalse(items.isEmpty());
        assertTrue(items.stream().anyMatch(i -> i.id() == itemId));
    }

    @Test
    @Order(23)
    void updateItem() {
        assertTrue(testProtocolRepo.updateItem(itemId, "Knows traffic signs (updated)", "Updated", 12.0, 1));
    }

    // -- Runs --

    @Test
    @Order(30)
    void createRun() {
        var run = testProtocolRepo.createRun(
                protocolId, station.id(), "Run 2026-01", LocalDate.of(2026, 1, 15), member.id());
        assertNotNull(run);
        assertEquals("Run 2026-01", run.name());
        runId = run.id();
    }

    @Test
    @Order(31)
    void findRuns() {
        var runs = testProtocolRepo.findRuns(station.id());
        assertFalse(runs.isEmpty());
        assertTrue(runs.stream().anyMatch(r -> r.id() == runId));
    }

    @Test
    @Order(32)
    void findRunById() {
        assertTrue(testProtocolRepo.findRunById(runId).isPresent());
        assertTrue(testProtocolRepo.findRunById(99999).isEmpty());
    }

    @Test
    @Order(33)
    void updateRun() {
        assertTrue(testProtocolRepo.updateRun(runId, "Updated Run", LocalDate.of(2026, 2, 1)));
        assertEquals(
                "Updated Run", testProtocolRepo.findRunById(runId).orElseThrow().name());
    }

    @Test
    @Order(34)
    void closeRun() {
        assertTrue(testProtocolRepo.closeRun(runId));
    }

    // -- Run Members --

    @Test
    @Order(40)
    void addRunMember() {
        var rm = testProtocolRepo.addRunMember(runId, member.id());
        assertNotNull(rm);
        assertEquals(member.id(), rm.memberId());
        runMemberId = rm.id();
    }

    @Test
    @Order(41)
    void addRunMemberIdempotent() {
        // Adding same member again should return existing
        var rm = testProtocolRepo.addRunMember(runId, member.id());
        assertEquals(runMemberId, rm.id());
    }

    @Test
    @Order(42)
    void findRunMembers() {
        var members = testProtocolRepo.findRunMembers(runId);
        assertFalse(members.isEmpty());
    }

    @Test
    @Order(43)
    void findRunMember() {
        var rm = testProtocolRepo.findRunMember(runId, member.id());
        assertTrue(rm.isPresent());
        assertTrue(testProtocolRepo.findRunMember(runId, 99999).isEmpty());
    }

    @Test
    @Order(44)
    void lockAndUnlockMember() {
        assertTrue(testProtocolRepo.lockMember(runMemberId, member.id()));
        assertTrue(testProtocolRepo.unlockMember(runMemberId));
    }

    @Test
    @Order(45)
    void updateScore() {
        testProtocolRepo.updateScore(runMemberId, 42.5);
        // No exception = success; score stored
    }

    @Test
    @Order(46)
    void upsertCheckAndFindChecks() {
        testProtocolRepo.upsertCheck(runMemberId, itemId, true, member.id());
        var checks = testProtocolRepo.findChecks(runMemberId);
        assertFalse(checks.isEmpty());
        assertTrue(checks.stream().anyMatch(c -> c.itemId() == itemId && c.checked()));

        // Upsert again to update
        testProtocolRepo.upsertCheck(runMemberId, itemId, false, member.id());
        var updated = testProtocolRepo.findChecks(runMemberId);
        assertTrue(updated.stream().anyMatch(c -> c.itemId() == itemId && !c.checked()));
    }

    @Test
    @Order(47)
    void sectionDone() {
        assertEquals(0, testProtocolRepo.countDoneSections(runMemberId));
        testProtocolRepo.markSectionDone(runMemberId, sectionId, member.id());
        assertEquals(1, testProtocolRepo.countDoneSections(runMemberId));

        var done = testProtocolRepo.findDoneSections(runMemberId);
        assertTrue(done.contains(sectionId));

        testProtocolRepo.unmarkSectionDone(runMemberId, sectionId);
        assertEquals(0, testProtocolRepo.countDoneSections(runMemberId));
    }

    @Test
    @Order(48)
    void completeMember() {
        assertTrue(testProtocolRepo.completeMember(runMemberId, 85.0));
        var completed = testProtocolRepo.findCompletedRunMembers(runId);
        assertFalse(completed.isEmpty());
    }

    // -- Cleanup --

    @Test
    @Order(90)
    void deleteItem() {
        assertTrue(testProtocolRepo.deleteItem(itemId));
        assertTrue(testProtocolRepo.findItems(sectionId).isEmpty());
    }

    @Test
    @Order(91)
    void deleteSection() {
        assertTrue(testProtocolRepo.deleteSection(sectionId));
    }

    @Test
    @Order(92)
    void deleteRun() {
        assertTrue(testProtocolRepo.deleteRun(runId));
        assertTrue(testProtocolRepo.findRunById(runId).isEmpty());
    }

    @Test
    @Order(99)
    void deleteProtocol() {
        assertTrue(testProtocolRepo.deleteProtocol(protocolId));
        assertTrue(testProtocolRepo.findProtocolById(protocolId).isEmpty());
    }
}
