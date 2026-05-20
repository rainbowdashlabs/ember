/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.service;

import dev.chojo.ember.api.Roles;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.service.FormerMemberService;
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
class FormerMemberServiceTest extends RepositoryTestBase {
    private static FormerMemberService service;
    private static Station station;
    private static Account account;
    private static StationMember member;

    @BeforeAll
    static void setup() {
        service = new FormerMemberService(
                stationMemberRepo,
                accountRepo,
                inventoryRepo,
                exchangeRepo,
                memberGroupRepo,
                attendanceRepo,
                profileFieldRepo);
        station = stationRepo.create("FormerStation");
        account = accountRepo.create("former@test.com", "Former", "Member");
        member = stationMemberRepo.create(station.id(), account.id());

        // Assign MEMBER + LOGIN roles
        stationMemberRepo.findRoleByName(Roles.MEMBER).ifPresent(r -> stationMemberRepo.addRole(member.id(), r.id()));
        stationMemberRepo.findRoleByName(Roles.LOGIN).ifPresent(r -> stationMemberRepo.addRole(member.id(), r.id()));
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    @Test
    @Order(1)
    void canMarkFormerReturnsNullForValidMember() {
        assertNull(service.canMarkFormer(member.id()));
    }

    @Test
    @Order(2)
    void canMarkFormerRejectsNonExistent() {
        assertNotNull(service.canMarkFormer(999999));
    }

    @Test
    @Order(10)
    void markFormerSetsFlag() {
        service.markFormer(member.id());
        var updated = stationMemberRepo.findById(member.id()).orElseThrow();
        assertTrue(updated.former());
    }

    @Test
    @Order(11)
    void markedFormerHasNoRoles() {
        var roles = stationMemberRepo.findRoles(member.id());
        assertTrue(roles.isEmpty());
    }

    @Test
    @Order(12)
    void canMarkFormerRejectsAlreadyFormer() {
        assertNotNull(service.canMarkFormer(member.id()));
    }

    @Test
    @Order(13)
    void formerMemberPreservesDisplayName() {
        var updated = stationMemberRepo.findById(member.id()).orElseThrow();
        assertEquals("Former Member", updated.displayName());
    }

    @Test
    @Order(20)
    void reactivateRestoresRoles() {
        service.reactivate(member.id());
        var updated = stationMemberRepo.findById(member.id()).orElseThrow();
        assertFalse(updated.former());
        var roles = stationMemberRepo.findRoles(member.id());
        assertTrue(roles.stream().anyMatch(r -> r.role() == Roles.LOGIN));
        assertTrue(roles.stream().anyMatch(r -> r.role() == Roles.MEMBER));
    }

    @Test
    @Order(21)
    void reactivateRejectsNonFormer() {
        assertThrows(IllegalStateException.class, () -> service.reactivate(member.id()));
    }
}
