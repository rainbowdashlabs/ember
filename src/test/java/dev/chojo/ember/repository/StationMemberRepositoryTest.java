/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.repository;

import dev.chojo.ember.api.Roles;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.members.entity.Role;
import dev.chojo.ember.feature.members.entity.StationMember;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class StationMemberRepositoryTest extends RepositoryTestBase {
    private static Station station;
    private static Account account1;
    private static Account account2;
    private static int memberId1;
    private static int memberId2;

    @BeforeAll
    static void setup() {
        station = stationRepo.create("Member Test Station");
        account1 = accountRepo.create("member1@test.com", "Member", "One");
        account2 = accountRepo.create("member2@test.com", "Member", "Two");
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accountRepo.delete(account1.id());
        accountRepo.delete(account2.id());
    }

    @Test
    @Order(1)
    void create() {
        StationMember m1 = stationMemberRepo.create(station.id(), account1.id());
        assertNotNull(m1);
        assertEquals(station.id(), m1.stationId());
        assertEquals(account1.id(), m1.accountId());
        memberId1 = m1.id();

        StationMember m2 = stationMemberRepo.create(station.id(), account2.id());
        memberId2 = m2.id();
    }

    @Test
    @Order(2)
    void findById() {
        assertTrue(stationMemberRepo.findById(memberId1).isPresent());
    }

    @Test
    @Order(3)
    void findByStationAndAccount() {
        assertTrue(stationMemberRepo
                .findByStationAndAccount(station.id(), account1.id())
                .isPresent());
        assertTrue(
                stationMemberRepo.findByStationAndAccount(station.id(), 99999).isEmpty());
    }

    @Test
    @Order(4)
    void findByStation() {
        assertEquals(2, stationMemberRepo.findByStation(station.id()).size());
    }

    @Test
    @Order(5)
    void findByAccount() {
        assertFalse(stationMemberRepo.findByAccount(account1.id()).isEmpty());
    }

    // -- Roles --

    @Test
    @Order(9)
    void findAllRoles() {
        var roles = stationMemberRepo.findAllRoles();
        assertFalse(roles.isEmpty());
        assertTrue(roles.stream().anyMatch(r -> r.role() == Roles.LOGIN));
    }

    @Test
    @Order(9)
    void findRoleByName() {
        assertTrue(stationMemberRepo.findRoleByName(Roles.LOGIN).isPresent());
    }

    @Test
    @Order(10)
    void addAndFindRoles() {
        // Role ID 1 = 'login' (seeded)
        stationMemberRepo.addRole(memberId1, 1);
        List<Role> roles = stationMemberRepo.findRoles(memberId1);
        assertEquals(1, roles.size());
        assertEquals(Roles.LOGIN, roles.getFirst().role());
    }

    @Test
    @Order(11)
    void hasLoginRole() {
        assertTrue(stationMemberRepo.hasLoginRole(account1.id()));
        assertFalse(stationMemberRepo.hasLoginRole(account2.id()));
    }

    @Test
    @Order(12)
    void removeRole() {
        assertTrue(stationMemberRepo.removeRole(memberId1, 1));
        assertTrue(stationMemberRepo.findRoles(memberId1).isEmpty());
    }

    // -- Manager Relations --

    @Test
    @Order(20)
    void addAndFindManagers() {
        stationMemberRepo.addManager(memberId1, memberId2);
        var managed = stationMemberRepo.findManaged(memberId1);
        assertEquals(1, managed.size());
        assertEquals(memberId2, managed.getFirst().id());

        var managers = stationMemberRepo.findManagers(memberId2);
        assertEquals(1, managers.size());
        assertEquals(memberId1, managers.getFirst().id());
    }

    @Test
    @Order(21)
    void removeManager() {
        assertTrue(stationMemberRepo.removeManager(memberId1, memberId2));
        assertTrue(stationMemberRepo.findManaged(memberId1).isEmpty());
    }

    @Test
    @Order(99)
    void delete() {
        assertTrue(stationMemberRepo.delete(memberId1));
        assertTrue(stationMemberRepo.delete(memberId2));
        assertTrue(stationMemberRepo.findByStation(station.id()).isEmpty());
    }
}
