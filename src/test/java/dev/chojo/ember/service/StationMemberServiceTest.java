/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.service;

import dev.chojo.ember.api.Roles;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.account.service.AuthService;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.service.StationMemberService;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.ForbiddenResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class StationMemberServiceTest extends RepositoryTestBase {
    private static StationMemberService service;
    private static Station station;
    private static Account account1;
    private static Account account2;
    private static StationMember member1;
    private static StationMember member2;

    @BeforeAll
    static void setup() {
        var authService = mock(AuthService.class);
        service = new StationMemberService(stationMemberRepo, stationRepo, accountRepo, authService);
        station = stationRepo.create("MemberServiceStation");
        account1 = accountRepo.create("svc1@test.com", "First", "Member");
        account2 = accountRepo.create("svc2@test.com", "Second", "Member");
        member1 = service.create(station.id(), account1.id());
        member2 = service.create(station.id(), account2.id());
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accountRepo.delete(account1.id());
        accountRepo.delete(account2.id());
    }

    @Test
    @Order(1)
    void findByStation() {
        var members = service.findByStation(station.id());
        assertTrue(members.size() >= 2);
    }

    @Test
    @Order(2)
    void findById() {
        assertTrue(service.findById(member1.id()).isPresent());
        assertTrue(service.findById(999999).isEmpty());
    }

    @Test
    @Order(3)
    void findByAccount() {
        var members = service.findByAccount(account1.id());
        assertEquals(1, members.size());
        assertEquals(member1.id(), members.getFirst().id());
    }

    @Test
    @Order(4)
    void findByStationIncludeFormer() {
        var members = service.findByStation(station.id(), false);
        assertTrue(members.size() >= 2);
    }

    @Test
    @Order(5)
    void findRoles() {
        var roles = service.findRoles(member1.id());
        assertNotNull(roles);
    }

    @Test
    @Order(10)
    void setRolesAssignsAndReturns() {
        var memberRole = stationMemberRepo.findRoleByName(Roles.MEMBER).orElseThrow();
        var loginRole = stationMemberRepo.findRoleByName(Roles.LOGIN).orElseThrow();
        var result = service.setRoles(
                member1.id(),
                List.of(memberRole.id(), loginRole.id()),
                EnumSet.of(Roles.ADMIN, Roles.MEMBER, Roles.LOGIN));
        assertTrue(result.stream().anyMatch(r -> r.role() == Roles.MEMBER));
        assertTrue(result.stream().anyMatch(r -> r.role() == Roles.LOGIN));
    }

    @Test
    @Order(11)
    void setRolesRejectsConflictingRoles() {
        var memberRole = stationMemberRepo.findRoleByName(Roles.MEMBER).orElseThrow();
        var teamRole = stationMemberRepo.findRoleByName(Roles.TEAM).orElseThrow();
        assertThrows(
                BadRequestResponse.class,
                () -> service.setRoles(
                        member2.id(),
                        List.of(memberRole.id(), teamRole.id()),
                        EnumSet.of(Roles.ADMIN, Roles.MEMBER, Roles.TEAM)));
    }

    @Test
    @Order(12)
    void setRolesRejectsUnauthorizedGrant() {
        var adminRole = stationMemberRepo.findRoleByName(Roles.ADMIN).orElseThrow();
        assertThrows(
                ForbiddenResponse.class,
                () -> service.setRoles(member2.id(), List.of(adminRole.id()), EnumSet.of(Roles.MEMBER_MANAGER)));
    }

    @Test
    @Order(20)
    void managerRelations() {
        stationMemberRepo.addManager(member1.id(), member2.id());
        var managed = service.findManaged(member1.id());
        assertTrue(managed.stream().anyMatch(m -> m.id() == member2.id()));
        var managers = service.findManagers(member2.id());
        assertTrue(managers.stream().anyMatch(m -> m.id() == member1.id()));
        stationMemberRepo.removeAllManaged(member1.id());
    }

    @Test
    @Order(21)
    void setManagers() {
        // Set member1 as manager of member2
        var result = service.setManagers(member2.id(), List.of(member1.id()));
        assertTrue(result.stream().anyMatch(m -> m.id() == member1.id()));

        // Remove all managers
        var cleared = service.setManagers(member2.id(), List.of());
        assertTrue(cleared.isEmpty());
    }

    @Test
    @Order(22)
    void setManagersIdempotent() {
        service.setManagers(member2.id(), List.of(member1.id()));
        // Setting again with same list should be idempotent
        var result = service.setManagers(member2.id(), List.of(member1.id()));
        assertEquals(1, result.size());
        service.setManagers(member2.id(), List.of());
    }

    @Test
    @Order(30)
    void delete() {
        // Create a third member to delete
        var account3 = accountRepo.create("svc3@test.com", "Third", "Member");
        var member3 = service.create(station.id(), account3.id());
        assertTrue(service.delete(member3.id()));
        assertTrue(service.findById(member3.id()).isEmpty());
        accountRepo.delete(account3.id());
    }
}
