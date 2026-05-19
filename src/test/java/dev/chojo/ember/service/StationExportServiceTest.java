/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.service;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.members.entity.ProfileFieldScope;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

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

        // Create station with a member
        var station = stationRepo.create("Export Test Station");
        stationId = station.id();

        Account account = accountRepo.create("export-test@example.com", "Max", "Mustermann", true);
        member = stationMemberRepo.create(stationId, account.id());

        // Create a group and add member
        var group = memberGroupRepo.create(stationId, "Anfänger");
        memberGroupRepo.addMember(group.id(), member.id());

        // Create a profile field
        profileFieldRepo.create(stationId, "Telefon", "TEXT", "{}", 0, ProfileFieldScope.MEMBER);
    }

    @Test
    @Order(2)
    void exportContainsStationInfo() {
        var data = exportService.exportStation(stationId);

        assertEquals("Ember Station Export", data.get("exportType"));
        assertNotNull(data.get("appVersion"));
        assertNotNull(data.get("exportedAt"));

        @SuppressWarnings("unchecked")
        var station = (Map<String, Object>) data.get("station");
        assertEquals("Export Test Station", station.get("name"));
    }

    @Test
    @Order(3)
    void exportContainsMembers() {
        var data = exportService.exportStation(stationId);

        @SuppressWarnings("unchecked")
        var members = (List<Map<String, Object>>) data.get("members");
        assertFalse(members.isEmpty());
    }

    @Test
    @Order(4)
    void exportContainsGroups() {
        var data = exportService.exportStation(stationId);

        @SuppressWarnings("unchecked")
        var groups = (List<Map<String, Object>>) data.get("groups");
        assertFalse(groups.isEmpty());
        assertEquals("Anfänger", groups.getFirst().get("name"));
    }

    @Test
    @Order(5)
    void exportContainsProfileFields() {
        var data = exportService.exportStation(stationId);

        @SuppressWarnings("unchecked")
        var fields = (List<Map<String, Object>>) data.get("profileFields");
        assertFalse(fields.isEmpty());
        assertEquals("Telefon", fields.getFirst().get("name"));
    }

    @Test
    @Order(6)
    void exportExcludesAccountData() {
        var data = exportService.exportStation(stationId);

        // Members should not contain account_id
        @SuppressWarnings("unchecked")
        var members = (List<Map<String, Object>>) data.get("members");
        assertFalse(members.isEmpty());
        assertNull(members.getFirst().get("account_id"));

        // No sessions, credentials, or GDPR data
        assertNull(data.get("sessions"));
        assertNull(data.get("credentials"));
        assertNull(data.get("gdprConsent"));
    }

    @Test
    @Order(7)
    void transferTokenWorks() {
        String token = exportService.createTransferToken(stationId);
        assertNotNull(token);

        // Validate and consume
        var result = exportService.validateAndConsumeToken(token);
        assertTrue(result.isPresent());
        assertEquals(stationId, result.get());

        // Token is now consumed — second use should fail
        var secondUse = exportService.validateAndConsumeToken(token);
        assertTrue(secondUse.isEmpty());
    }

    @Test
    @Order(8)
    void invalidTokenFails() {
        var result = exportService.validateAndConsumeToken("invalid-token-123");
        assertTrue(result.isEmpty());
    }
}
