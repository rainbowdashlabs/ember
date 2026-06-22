/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.service;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.members.entity.ProfileFieldConfig;
import dev.chojo.ember.feature.members.entity.ProfileFieldScope;
import dev.chojo.ember.feature.members.entity.ProfileFieldType;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.station.entity.StationModule;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.List;
import java.util.Map;
import java.util.Set;

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
        exportService = new StationExportService();

        var station = stationRepo.create("Export Test Station");
        stationId = station.id();

        Account account = accountRepo.create("export-test@example.com", "Max", "Mustermann", true);
        member = stationMemberRepo.create(stationId, account.id());

        var group = memberGroupRepo.create(stationId, "Anfänger");
        memberGroupRepo.addMember(group.id(), member.id());

        profileFieldRepo.create(
                stationId,
                "Telefon",
                ProfileFieldType.TEXT,
                ProfileFieldConfig.parse("{}"),
                0,
                ProfileFieldScope.MEMBER);
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
        var data = exportService.exportTable(stationId, "station_member", 0, 500);

        var members = (List<Map<String, Object>>) data.get("station_member");
        assertFalse(members.isEmpty());
        // account_id is in ignoredColumns — the importer matches via account_email lookup instead.
        assertNull(members.getFirst().get("account_id"));
        assertEquals("export-test@example.com", members.getFirst().get("account_email"));
    }

    @Test
    @Order(4)
    @SuppressWarnings("unchecked")
    void exportTableContainsGroups() {
        var data = exportService.exportTable(stationId, "member_group", 0, 500);

        var groups = (List<Map<String, Object>>) data.get("member_group");
        assertFalse(groups.isEmpty());
        assertEquals("Anfänger", groups.getFirst().get("name"));
    }

    @Test
    @Order(5)
    @SuppressWarnings("unchecked")
    void exportTableContainsProfileFields() {
        var data = exportService.exportTable(stationId, "profile_field", 0, 500);

        var fields = (List<Map<String, Object>>) data.get("profile_field");
        assertFalse(fields.isEmpty());
        assertEquals("Telefon", fields.getFirst().get("name"));
    }

    @Test
    @Order(6)
    @SuppressWarnings("unchecked")
    void paginationWorks() {
        // Export with limit 1 should return exactly 1 member
        var page1 = exportService.exportTable(stationId, "station_member", 0, 1);
        var members1 = (List<Map<String, Object>>) page1.get("station_member");
        assertEquals(1, members1.size());

        // Page 2 with offset 1 should be empty (only 1 member)
        var page2 = exportService.exportTable(stationId, "station_member", 1, 1);
        var members2 = (List<Map<String, Object>>) page2.get("station_member");
        assertTrue(members2.isEmpty());
    }

    @Test
    @Order(10)
    @SuppressWarnings("unchecked")
    void exportTableContainsAccountsThroughCustomScope() {
        var data = exportService.exportTable(stationId, "account", 0, 500);

        var accounts = (List<Map<String, Object>>) data.get("account");
        assertFalse(accounts.isEmpty());
        assertEquals("export-test@example.com", accounts.getFirst().get("email"));
        assertEquals("Max", accounts.getFirst().get("first_name"));
    }

    @Test
    @Order(11)
    @SuppressWarnings("unchecked")
    void exportTableContainsDisabledModulesAsFlatList() {
        stationRepo.setDisabledModules(stationId, Set.of(StationModule.LOST_AND_FOUND));
        var data = exportService.exportTable(stationId, "station_disabled_module", 0, 500);

        var modules = (List<Object>) data.get("station_disabled_module");
        assertFalse(modules.isEmpty());
        assertEquals("LOST_AND_FOUND", modules.getFirst());
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
