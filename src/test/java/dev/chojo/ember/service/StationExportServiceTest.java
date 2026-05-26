/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.service;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.members.entity.ProfileFieldScope;
import dev.chojo.ember.feature.members.entity.ProfileFieldType;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.station.service.StationExportService;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("database")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class StationExportServiceTest extends RepositoryTestBase {
    private static StationExportService exportService;
    private static int stationId;
    private static StationMember member;

    @Test
    @Order(1)
    void setup() {
        exportService = new StationExportService(stationRepo);

        var station = stationRepo.create("Export Test Station");
        stationId = station.id();

        Account account = accountRepo.create("export-test@example.com", "Max", "Mustermann", true);
        member = stationMemberRepo.create(stationId, account.id());

        var group = memberGroupRepo.create(stationId, "Anfänger");
        memberGroupRepo.addMember(group.id(), member.id());

        profileFieldRepo.create(stationId, "Telefon", ProfileFieldType.TEXT, "{}", 0, ProfileFieldScope.MEMBER);
    }

    @Test
    @Order(2)
    @SuppressWarnings("unchecked")
    void exportTableContainsStationInfo() {
        var data = exportService.exportTable(stationId, "station", 0, 500);

        assertEquals("station", data.get("table"));
        assertNotNull(data.get("appVersion"));

        var station = (Map<String, Object>) data.get("station");
        assertEquals("Export Test Station", station.get("name"));
    }

    @Test
    @Order(3)
    @SuppressWarnings("unchecked")
    void exportTableContainsMembers() {
        var data = exportService.exportTable(stationId, "members", 0, 500);

        var members = (List<Map<String, Object>>) data.get("members");
        assertFalse(members.isEmpty());
        assertNull(members.getFirst().get("account_id"));
    }

    @Test
    @Order(4)
    @SuppressWarnings("unchecked")
    void exportTableContainsGroups() {
        var data = exportService.exportTable(stationId, "groups", 0, 500);

        var groups = (List<Map<String, Object>>) data.get("groups");
        assertFalse(groups.isEmpty());
        assertEquals("Anfänger", groups.getFirst().get("name"));
    }

    @Test
    @Order(5)
    @SuppressWarnings("unchecked")
    void exportTableContainsProfileFields() {
        var data = exportService.exportTable(stationId, "profileFields", 0, 500);

        var fields = (List<Map<String, Object>>) data.get("profileFields");
        assertFalse(fields.isEmpty());
        assertEquals("Telefon", fields.getFirst().get("name"));
    }

    @Test
    @Order(6)
    @SuppressWarnings("unchecked")
    void paginationWorks() {
        // Export with limit 1 should return exactly 1 member
        var page1 = exportService.exportTable(stationId, "members", 0, 1);
        var members1 = (List<Map<String, Object>>) page1.get("members");
        assertEquals(1, members1.size());

        // Page 2 with offset 1 should be empty (only 1 member)
        var page2 = exportService.exportTable(stationId, "members", 1, 1);
        var members2 = (List<Map<String, Object>>) page2.get("members");
        assertTrue(members2.isEmpty());
    }

    @Test
    @Order(7)
    void transferTokenWorks() {
        String token = exportService.createTransferToken(stationId);
        assertNotNull(token);

        var result = exportService.validateAndConsumeToken(token);
        assertTrue(result.isPresent());
        assertEquals(stationId, result.get());

        var secondUse = exportService.validateAndConsumeToken(token);
        assertTrue(secondUse.isEmpty());
    }

    @Test
    @Order(8)
    void validateTokenWithoutConsuming() {
        String token = exportService.createTransferToken(stationId);
        assertNotNull(token);

        // Validate without consuming — should work repeatedly
        var first = exportService.validateToken(token);
        assertTrue(first.isPresent());
        var second = exportService.validateToken(token);
        assertTrue(second.isPresent());

        // Now consume
        exportService.validateAndConsumeToken(token);
        var afterConsume = exportService.validateToken(token);
        assertTrue(afterConsume.isEmpty());
    }

    @Test
    @Order(9)
    void invalidTokenFails() {
        var result = exportService.validateAndConsumeToken("invalid-token-123");
        assertTrue(result.isEmpty());
    }
}
