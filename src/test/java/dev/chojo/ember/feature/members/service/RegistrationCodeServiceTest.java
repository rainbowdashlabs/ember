/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.RefusalResponse;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.members.entity.MemberGroup;
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
        service = new RegistrationCodeService(registrationCodeRepo, newGroupMemberships());
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
        var found = service.findInStation(station.id(), codeId);
        assertTrue(found.isPresent());
        assertEquals("WELCOME2026", found.get().code());
    }

    @Test
    @Order(3)
    void findByIdNotFound() {
        assertTrue(service.findInStation(station.id(), 99999).isEmpty());
    }

    @Test
    @Order(4)
    void aCodeOfAnotherStationIsNotReachable() {
        int elsewhere = station.id() + 100_000;
        assertTrue(service.findInStation(elsewhere, codeId).isEmpty());
        assertEquals(
                Refusal.REGISTRATION_CODE_NOT_HERE_FOR_GROUPS,
                assertThrows(RefusalResponse.class, () -> service.findGroupIds(elsewhere, codeId))
                        .refusal());
        assertEquals(
                Refusal.REGISTRATION_CODE_NOT_HERE_TO_CHANGE_GROUPS,
                assertThrows(RefusalResponse.class, () -> service.setGroups(elsewhere, codeId, List.of()))
                        .refusal());
        assertEquals(
                Refusal.REGISTRATION_CODE_NOT_DELETED,
                assertThrows(RefusalResponse.class, () -> service.delete(elsewhere, codeId))
                        .refusal());
        assertTrue(service.findInStation(station.id(), codeId).isPresent());
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
        var groupIds = service.findGroupIds(station.id(), codeId);
        assertTrue(groupIds.isEmpty());
    }

    @Test
    @Order(11)
    void setGroupsAdds() {
        var result = service.setGroups(station.id(), codeId, List.of(group.id()));
        assertEquals(1, result.size());
        assertEquals(group.id(), result.getFirst());
    }

    @Test
    @Order(12)
    void setGroupsIdempotent() {
        var result = service.setGroups(station.id(), codeId, List.of(group.id()));
        assertEquals(1, result.size());
    }

    @Test
    @Order(13)
    void setGroupsClears() {
        var result = service.setGroups(station.id(), codeId, List.of());
        assertTrue(result.isEmpty());
    }

    @Test
    @Order(20)
    void setGroupsAddMultiple() {
        var group2 = memberGroupRepo.create(station.id(), "Seniors");
        var result = service.setGroups(station.id(), codeId, List.of(group.id(), group2.id()));
        assertEquals(2, result.size());
        // Switch to just one
        var result2 = service.setGroups(station.id(), codeId, List.of(group2.id()));
        assertEquals(1, result2.size());
        assertEquals(group2.id(), result2.getFirst());
        memberGroupRepo.delete(group2.id());
    }

    @Test
    @Order(21)
    void setGroupsRefusesAGroupThatTakesNoMembers() {
        var trainers = memberGroupRepo.create(station.id(), "Trainers");
        memberGroupRepo.replaceUserTypes(trainers.id(), List.of(StationUserType.TEAM));
        var elsewhere = stationRepo.create("Code Elsewhere");
        var foreign = memberGroupRepo.create(elsewhere.id(), "Foreign");

        assertEquals(
                Refusal.REGISTRATION_CODE_GROUP_WRONG_USER_TYPE,
                assertThrows(
                                RefusalResponse.class,
                                () -> service.setGroups(station.id(), codeId, List.of(trainers.id())))
                        .refusal());
        assertEquals(
                Refusal.REGISTRATION_CODE_GROUP_NOT_HERE,
                assertThrows(
                                RefusalResponse.class,
                                () -> service.setGroups(station.id(), codeId, List.of(foreign.id())))
                        .refusal());

        memberGroupRepo.delete(trainers.id());
        stationRepo.delete(elsewhere.id());
    }

    @Test
    @Order(99)
    void delete() {
        service.delete(station.id(), codeId);
        assertTrue(service.findInStation(station.id(), codeId).isEmpty());
    }
}
