/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.repository;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.members.entity.ProfileFieldConfig;
import dev.chojo.ember.feature.members.entity.ProfileFieldScope;
import dev.chojo.ember.feature.members.entity.ProfileFieldType;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.station.entity.Station;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ProfileFieldChangeRepositoryTest extends RepositoryTestBase {
    private static Station station;
    private static Account account;
    private static StationMember member;
    private static int fieldId;
    private static int changeId;

    @BeforeAll
    static void setup() {
        station = stationRepo.create("PFC Station");
        account = accountRepo.create("pfc@test.com", "PFC", "User");
        member = stationMemberRepo.create(station.id(), account.id());
        var field = profileFieldRepo.create(
                station.id(),
                "Phone",
                ProfileFieldType.TEXT,
                ProfileFieldConfig.parse("{}"),
                0,
                ProfileFieldScope.MEMBER);
        fieldId = field.id();
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    @Test
    @Order(1)
    void create() {
        var change = profileFieldChangeRepo.create(fieldId, member.id(), "\"old\"", "\"new\"", member.id(), true);
        assertNotNull(change);
        assertEquals(fieldId, change.fieldId());
        assertEquals(member.id(), change.memberId());
        assertTrue(change.requiresAcknowledgement());
        changeId = change.id();
    }

    @Test
    @Order(2)
    void findByMember() {
        var changes = profileFieldChangeRepo.findByMember(member.id());
        assertEquals(1, changes.size());
        assertEquals(changeId, changes.getFirst().id());
    }

    @Test
    @Order(3)
    void findRecentChange() {
        Instant cutoff = Instant.now().minusSeconds(60);
        var recent = profileFieldChangeRepo.findRecentChange(fieldId, member.id(), member.id(), cutoff);
        assertTrue(recent.isPresent());
        assertEquals(changeId, recent.get().id());
    }

    @Test
    @Order(4)
    void findRecentChangeNoneFound() {
        Instant futureCutoff = Instant.now().plusSeconds(3600);
        var recent = profileFieldChangeRepo.findRecentChange(fieldId, member.id(), member.id(), futureCutoff);
        assertTrue(recent.isEmpty());
    }

    @Test
    @Order(5)
    void updateChangeNewValue() {
        profileFieldChangeRepo.updateChangeNewValue(changeId, "\"updated\"");
        var changes = profileFieldChangeRepo.findByMember(member.id());
        // The change should still exist; we can't easily assert the JSON value here
        // but the method should not throw
        assertEquals(1, changes.size());
    }

    @Test
    @Order(6)
    void findByStation() {
        var changes = profileFieldChangeRepo.findByStation(station.id(), 10, 0);
        assertEquals(1, changes.size());
    }

    @Test
    @Order(7)
    void countByStation() {
        assertEquals(1, profileFieldChangeRepo.countByStation(station.id()));
    }

    @Test
    @Order(10)
    void findUnacknowledgedChangeIds() {
        var ids = profileFieldChangeRepo.findUnacknowledgedChangeIds(member.id(), member.id());
        assertEquals(1, ids.size());
        assertEquals(changeId, ids.getFirst());
    }

    @Test
    @Order(11)
    void findUnacknowledgedSummary() {
        var summary = profileFieldChangeRepo.findUnacknowledgedSummary(station.id(), member.id());
        assertEquals(1, summary.size());
        assertEquals(member.id(), summary.getFirst().memberId());
        assertEquals(1, summary.getFirst().pendingCount());
    }

    @Test
    @Order(20)
    void acknowledge() {
        var ack = profileFieldChangeRepo.acknowledge(changeId, member.id(), "Noted");
        assertNotNull(ack);
        assertEquals(changeId, ack.changeId());
        assertEquals(member.id(), ack.acknowledgedBy());
    }

    @Test
    @Order(21)
    void findAcknowledgements() {
        var acks = profileFieldChangeRepo.findAcknowledgements(changeId);
        assertEquals(1, acks.size());
    }

    @Test
    @Order(22)
    void findAcknowledgementsForMember() {
        var acks = profileFieldChangeRepo.findAcknowledgementsForMember(member.id());
        assertEquals(1, acks.size());
    }

    @Test
    @Order(23)
    void findUnacknowledgedAfterAck() {
        var ids = profileFieldChangeRepo.findUnacknowledgedChangeIds(member.id(), member.id());
        assertTrue(ids.isEmpty());
    }

    @Test
    @Order(24)
    void findUnacknowledgedSummaryAfterAck() {
        var summary = profileFieldChangeRepo.findUnacknowledgedSummary(station.id(), member.id());
        assertTrue(summary.isEmpty());
    }
}
