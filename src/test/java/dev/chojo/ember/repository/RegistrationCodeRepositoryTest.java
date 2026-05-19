/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.repository;

import dev.chojo.ember.feature.members.entity.MemberGroup;
import dev.chojo.ember.feature.members.entity.RegistrationCode;
import dev.chojo.ember.feature.station.entity.Station;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RegistrationCodeRepositoryTest extends RepositoryTestBase {
    private static Station station;
    private static MemberGroup group;
    private static int codeId;

    @BeforeAll
    static void setup() {
        station = stationRepo.create("RegCode Station");
        group = memberGroupRepo.create(station.id(), "Newcomers");
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
    }

    @Test
    @Order(1)
    void create() {
        RegistrationCode code = registrationCodeRepo.create(station.id(), "JOIN2026", 10);
        assertNotNull(code);
        assertEquals("JOIN2026", code.code());
        assertEquals(10, code.maxUses());
        assertEquals(0, code.uses());
        assertTrue(code.hasUsesLeft());
        codeId = code.id();
    }

    @Test
    @Order(2)
    void findById() {
        assertTrue(registrationCodeRepo.findById(codeId).isPresent());
    }

    @Test
    @Order(3)
    void findByCode() {
        assertTrue(registrationCodeRepo.findByCode("JOIN2026").isPresent());
        assertTrue(registrationCodeRepo.findByCode("NOPE").isEmpty());
    }

    @Test
    @Order(4)
    void findByStationAndCode() {
        assertTrue(registrationCodeRepo
                .findByStationAndCode(station.id(), "JOIN2026")
                .isPresent());
    }

    @Test
    @Order(5)
    void findByStation() {
        assertEquals(1, registrationCodeRepo.findByStation(station.id()).size());
    }

    @Test
    @Order(6)
    void incrementUses() {
        assertTrue(registrationCodeRepo.incrementUses(codeId));
        assertEquals(1, registrationCodeRepo.findById(codeId).orElseThrow().uses());
    }

    // -- Code-Group assignments --

    @Test
    @Order(10)
    void addAndFindGroups() {
        registrationCodeRepo.addGroup(codeId, group.id());
        var groupIds = registrationCodeRepo.findGroupIds(codeId);
        assertEquals(1, groupIds.size());
        assertEquals(group.id(), groupIds.getFirst());
    }

    @Test
    @Order(11)
    void removeGroup() {
        assertTrue(registrationCodeRepo.removeGroup(codeId, group.id()));
        assertTrue(registrationCodeRepo.findGroupIds(codeId).isEmpty());
    }

    @Test
    @Order(99)
    void delete() {
        assertTrue(registrationCodeRepo.delete(codeId));
        assertTrue(registrationCodeRepo.findById(codeId).isEmpty());
    }
}
