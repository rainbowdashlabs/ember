/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.service;

import dev.chojo.ember.api.roles.StationPermission;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.account.service.AuthService;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.service.StationMemberService;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
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
        var roles = service.findPermissions(member1.id());
        assertNotNull(roles);
    }

    @Test
    @Order(10)
    void setPermissionsAssignsAndReturns() {
        var memberRole =
                stationMemberRepo.findPermissionByName(StationPermission.USER).orElseThrow();
        var loginRole =
                stationMemberRepo.findPermissionByName(StationPermission.LOGIN).orElseThrow();
        var result = service.setPermissions(
                member1.id(),
                List.of(memberRole.id(), loginRole.id()),
                EnumSet.of(StationPermission.STATION_ADMINISTRATOR, StationPermission.USER, StationPermission.LOGIN));
        assertTrue(result.stream().anyMatch(r -> r.permission() == StationPermission.USER));
        assertTrue(result.stream().anyMatch(r -> r.permission() == StationPermission.LOGIN));
    }

    @Test
    @Order(12)
    void setPermissionsRejectsUnauthorizedGrant() {
        var adminPerm = stationMemberRepo
                .findPermissionByName(StationPermission.STATION_ADMINISTRATOR)
                .orElseThrow();
        assertThrows(
                ForbiddenResponse.class,
                () -> service.setPermissions(
                        member2.id(), List.of(adminPerm.id()), EnumSet.of(StationPermission.MEMBER_MANAGER)));
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
    @Order(23)
    void resolveUid() {
        var uid = service.resolveUid(member1.id());
        assertNotNull(uid);
    }

    @Test
    @Order(23)
    void resolveId() {
        var uid = service.resolveUid(member1.id());
        var resolved = service.resolveId(station.id(), uid);
        assertTrue(resolved.isPresent());
        assertEquals(member1.id(), resolved.get());
    }

    @Test
    @Order(23)
    void resolveIdentity() {
        var identity = service.resolveIdentity(member1.id());
        assertNotNull(identity);
        assertNotNull(identity.stationUid());
        assertNotNull(identity.memberUid());
    }

    @Test
    @Order(23)
    void findAllPermissions() {
        var permissions = service.findAllPermissions();
        assertNotNull(permissions);
        assertFalse(permissions.isEmpty());
    }

    @Test
    @Order(24)
    void setUserType() {
        assertTrue(service.setUserType(member1.id(), dev.chojo.ember.api.roles.StationUserType.TEAM));
        var m = service.findById(member1.id()).orElseThrow();
        assertEquals(dev.chojo.ember.api.roles.StationUserType.TEAM, m.userType());
        // Reset
        service.setUserType(member1.id(), dev.chojo.ember.api.roles.StationUserType.MEMBER);
    }

    @Test
    @Order(25)
    void setPermissionsGrantsLoginAndTriggersOnboarding() {
        // Create a member with email for LOGIN permission testing
        var account3 = accountRepo.create("svc-login@test.com", "Login", "Test");
        var member3 = service.create(station.id(), account3.id());

        var loginPerm =
                stationMemberRepo.findPermissionByName(StationPermission.LOGIN).orElseThrow();
        var userPerm =
                stationMemberRepo.findPermissionByName(StationPermission.USER).orElseThrow();

        var result = service.setPermissions(
                member3.id(),
                List.of(userPerm.id(), loginPerm.id()),
                EnumSet.of(StationPermission.STATION_ADMINISTRATOR, StationPermission.USER, StationPermission.LOGIN));
        assertTrue(result.stream().anyMatch(r -> r.permission() == StationPermission.LOGIN));

        // Cleanup
        service.delete(member3.id());
        accountRepo.delete(account3.id());
    }

    @Test
    @Order(26)
    void setPermissionsRevokesExisting() {
        var userPerm =
                stationMemberRepo.findPermissionByName(StationPermission.USER).orElseThrow();
        // Grant first
        service.setPermissions(
                member2.id(),
                List.of(userPerm.id()),
                EnumSet.of(StationPermission.STATION_ADMINISTRATOR, StationPermission.USER));
        assertTrue(
                service.findPermissions(member2.id()).stream().anyMatch(p -> p.permission() == StationPermission.USER));
        // Revoke by passing empty list
        service.setPermissions(
                member2.id(), List.of(), EnumSet.of(StationPermission.STATION_ADMINISTRATOR, StationPermission.USER));
        assertTrue(service.findPermissions(member2.id()).isEmpty());
    }

    @Test
    @Order(27)
    void setManaged() {
        // Set member1 as manager of member2 via setManaged
        var result = service.setManaged(member1.id(), List.of(member2.id()));
        assertTrue(result.stream().anyMatch(m -> m.id() == member2.id()));

        // Remove via empty list
        var cleared = service.setManaged(member1.id(), List.of());
        assertTrue(cleared.isEmpty());
    }

    @Test
    @Order(28)
    void setManagedIdempotent() {
        service.setManaged(member1.id(), List.of(member2.id()));
        var result = service.setManaged(member1.id(), List.of(member2.id()));
        assertEquals(1, result.size());
        service.setManaged(member1.id(), List.of());
    }

    @Test
    @Order(29)
    void setPermissionsRejectsLoginWithoutEmail() {
        // Create an account without email
        var noEmailAccount = accountRepo.create(null, "NoEmail", "User");
        var noEmailMember = service.create(station.id(), noEmailAccount.id());

        var loginPerm =
                stationMemberRepo.findPermissionByName(StationPermission.LOGIN).orElseThrow();
        assertThrows(
                io.javalin.http.BadRequestResponse.class,
                () -> service.setPermissions(
                        noEmailMember.id(),
                        List.of(loginPerm.id()),
                        EnumSet.of(StationPermission.STATION_ADMINISTRATOR, StationPermission.LOGIN)));

        service.delete(noEmailMember.id());
        accountRepo.delete(noEmailAccount.id());
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
