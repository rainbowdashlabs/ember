/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.service;

import dev.chojo.ember.feature.members.entity.MemberGroup;
import dev.chojo.ember.feature.members.service.RegistrationCodeService;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RegistrationCodeServiceTest extends RepositoryTestBase {
    private static RegistrationCodeService service;
    private static Station station;
    private static MemberGroup group;
    private static int codeId;

    @BeforeAll
    static void setup() {
        service = new RegistrationCodeService(registrationCodeRepo);
        station = stationRepo.create("RegCodeSvc Station");
        group = memberGroupRepo.create(station.id(), "Newcomers");
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
    }

    @Test
    @Order(1)
    void create() {
        var code = service.create(station.id(), "WELCOME2026", 5);
        assertNotNull(code);
        assertEquals("WELCOME2026", code.code());
        assertEquals(5, code.maxUses());
        assertEquals(0, code.uses());
        assertTrue(code.hasUsesLeft());
        codeId = code.id();
    }

    @Test
    @Order(2)
    void findById() {
        var found = service.findById(codeId);
        assertTrue(found.isPresent());
        assertEquals("WELCOME2026", found.get().code());
    }

    @Test
    @Order(3)
    void findByIdNotFound() {
        assertTrue(service.findById(99999).isEmpty());
    }

    @Test
    @Order(4)
    void findByStation() {
        var codes = service.findByStation(station.id());
        assertFalse(codes.isEmpty());
        assertTrue(codes.stream().anyMatch(c -> c.id() == codeId));
    }

    @Test
    @Order(10)
    void findGroupIdsEmpty() {
        var groupIds = service.findGroupIds(codeId);
        assertTrue(groupIds.isEmpty());
    }

    @Test
    @Order(11)
    void setGroupsAdds() {
        var result = service.setGroups(codeId, List.of(group.id()));
        assertEquals(1, result.size());
        assertEquals(group.id(), result.getFirst());
    }

    @Test
    @Order(12)
    void setGroupsIdempotent() {
        var result = service.setGroups(codeId, List.of(group.id()));
        assertEquals(1, result.size());
    }

    @Test
    @Order(13)
    void setGroupsClears() {
        var result = service.setGroups(codeId, List.of());
        assertTrue(result.isEmpty());
    }

    @Test
    @Order(20)
    void setGroupsAddMultiple() {
        var group2 = memberGroupRepo.create(station.id(), "Seniors");
        var result = service.setGroups(codeId, List.of(group.id(), group2.id()));
        assertEquals(2, result.size());
        // Switch to just one
        var result2 = service.setGroups(codeId, List.of(group2.id()));
        assertEquals(1, result2.size());
        assertEquals(group2.id(), result2.getFirst());
        memberGroupRepo.delete(group2.id());
    }

    @Test
    @Order(99)
    void delete() {
        assertTrue(service.delete(codeId));
        assertTrue(service.findById(codeId).isEmpty());
    }
}
