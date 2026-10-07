/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.api.refusal.MemberRefusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.account.service.AuthService;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
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
        service = newStationMemberService(accountRepo, authService);
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
                EnumSet.of(StationPermission.STATION_ADMINISTRATOR, StationPermission.USER, StationPermission.LOGIN),
                null);
        assertTrue(result.stream().anyMatch(r -> r.permission() == StationPermission.USER));
        assertTrue(result.stream().anyMatch(r -> r.permission() == StationPermission.LOGIN));
    }

    @Test
    @Order(12)
    void setPermissionsRejectsUnauthorizedGrant() {
        var adminPerm = stationMemberRepo
                .findPermissionByName(StationPermission.STATION_ADMINISTRATOR)
                .orElseThrow();
        var refused = assertThrows(
                RefusalResponse.class,
                () -> service.setPermissions(
                        member2.id(), List.of(adminPerm.id()), EnumSet.of(StationPermission.MEMBER_MANAGER), null));
        assertEquals(MemberRefusal.MEMBER_PERMISSION_NOT_YOURS_TO_GRANT, refused.refusal());
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
        var result = service.setManagers(member2.id(), List.of(member1.id()), null);
        assertTrue(result.stream().anyMatch(m -> m.id() == member1.id()));

        var cleared = service.setManagers(member2.id(), List.of(), null);
        assertTrue(cleared.isEmpty());
    }

    @Test
    @Order(22)
    void setManagersIdempotent() {
        service.setManagers(member2.id(), List.of(member1.id()), null);
        var result = service.setManagers(member2.id(), List.of(member1.id()), null);
        assertEquals(1, result.size());
        service.setManagers(member2.id(), List.of(), null);
    }

    /**
     * The order the guardians are given in is the order they keep, so the member page decides who
     * is the first guardian, and a new guardian joins behind the ones already there.
     */
    @Test
    @Order(22)
    void guardiansKeepTheOrderTheyAreGivenIn() {
        var account = accountRepo.create("svc-order@test.com", "Order", "Guardian");
        var second = service.create(station.id(), account.id());
        service.setManagers(member2.id(), List.of(member1.id()), member1.id());
        stationMemberRepo.addManager(second.id(), member2.id());

        assertEquals(
                List.of(member1.id(), second.id()),
                service.findManagers(member2.id()).stream()
                        .map(StationMember::id)
                        .toList());
        assertEquals(
                List.of(second.id(), member1.id()),
                service.setManagers(member2.id(), List.of(second.id(), member1.id()), member1.id()).stream()
                        .map(StationMember::id)
                        .toList());

        service.setManagers(member2.id(), List.of(), null);
        stationMemberRepo.delete(second.id());
        accountRepo.delete(account.id());
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
    @Order(25)
    void setPermissionsGrantsLoginAndTriggersOnboarding() {
        var account3 = accountRepo.create("svc-login@test.com", "Login", "Test");
        var member3 = service.create(station.id(), account3.id());

        var loginPerm =
                stationMemberRepo.findPermissionByName(StationPermission.LOGIN).orElseThrow();
        var userPerm =
                stationMemberRepo.findPermissionByName(StationPermission.USER).orElseThrow();

        var result = service.setPermissions(
                member3.id(),
                List.of(userPerm.id(), loginPerm.id()),
                EnumSet.of(StationPermission.STATION_ADMINISTRATOR, StationPermission.USER, StationPermission.LOGIN),
                null);
        assertTrue(result.stream().anyMatch(r -> r.permission() == StationPermission.LOGIN));

        service.delete(member3.id());
        accountRepo.delete(account3.id());
    }

    @Test
    @Order(26)
    void setPermissionsRevokesExisting() {
        var userPerm =
                stationMemberRepo.findPermissionByName(StationPermission.USER).orElseThrow();
        service.setPermissions(
                member2.id(),
                List.of(userPerm.id()),
                EnumSet.of(StationPermission.STATION_ADMINISTRATOR, StationPermission.USER),
                null);
        assertTrue(
                service.findPermissions(member2.id()).stream().anyMatch(p -> p.permission() == StationPermission.USER));
        service.setPermissions(
                member2.id(),
                List.of(),
                EnumSet.of(StationPermission.STATION_ADMINISTRATOR, StationPermission.USER),
                null);
        assertTrue(service.findPermissions(member2.id()).isEmpty());
    }

    @Test
    @Order(27)
    void setManaged() {
        var result = service.setManaged(member1.id(), List.of(member2.id()), null);
        assertTrue(result.stream().anyMatch(m -> m.id() == member2.id()));

        var cleared = service.setManaged(member1.id(), List.of(), null);
        assertTrue(cleared.isEmpty());
    }

    @Test
    @Order(28)
    void setManagedIdempotent() {
        service.setManaged(member1.id(), List.of(member2.id()), null);
        var result = service.setManaged(member1.id(), List.of(member2.id()), null);
        assertEquals(1, result.size());
        service.setManaged(member1.id(), List.of(), null);
    }

    @Test
    @Order(29)
    void setPermissionsRejectsLoginWithoutEmail() {
        var noEmailAccount = accountRepo.create(null, "NoEmail", "User");
        var noEmailMember = service.create(station.id(), noEmailAccount.id());

        var loginPerm =
                stationMemberRepo.findPermissionByName(StationPermission.LOGIN).orElseThrow();
        var refused = assertThrows(
                RefusalResponse.class,
                () -> service.setPermissions(
                        noEmailMember.id(),
                        List.of(loginPerm.id()),
                        EnumSet.of(StationPermission.STATION_ADMINISTRATOR, StationPermission.LOGIN),
                        null));
        assertEquals(MemberRefusal.MEMBER_SIGN_IN_NEEDS_AN_ADDRESS, refused.refusal());

        service.delete(noEmailMember.id());
        accountRepo.delete(noEmailAccount.id());
    }

    @Test
    @Order(29)
    void setPermissionsRejectsSelfPermissionRemoval() {
        var userPerm =
                stationMemberRepo.findPermissionByName(StationPermission.USER).orElseThrow();
        var account3 = accountRepo.create("svc-self@test.com", "Self", "Remove");
        var member3 = service.create(station.id(), account3.id());
        service.setPermissions(
                member3.id(),
                List.of(userPerm.id()),
                EnumSet.of(StationPermission.STATION_ADMINISTRATOR, StationPermission.USER),
                null);

        var refused = assertThrows(
                RefusalResponse.class,
                () -> service.setPermissions(
                        member3.id(),
                        List.of(),
                        EnumSet.of(StationPermission.STATION_ADMINISTRATOR, StationPermission.USER),
                        member3.id()));
        assertEquals(MemberRefusal.MEMBER_OWN_PERMISSION_NOT_REMOVABLE, refused.refusal());

        service.delete(member3.id());
        accountRepo.delete(account3.id());
    }

    @Test
    @Order(29)
    void setPermissionsAllowsCallerToKeepOwnPermissions() {
        var userPerm =
                stationMemberRepo.findPermissionByName(StationPermission.USER).orElseThrow();
        var account3 = accountRepo.create("svc-self-keep@test.com", "Self", "Keep");
        var member3 = service.create(station.id(), account3.id());
        service.setPermissions(
                member3.id(),
                List.of(userPerm.id()),
                EnumSet.of(StationPermission.STATION_ADMINISTRATOR, StationPermission.USER),
                null);

        var result = service.setPermissions(
                member3.id(),
                List.of(userPerm.id()),
                EnumSet.of(StationPermission.STATION_ADMINISTRATOR, StationPermission.USER),
                member3.id());
        assertTrue(result.stream().anyMatch(p -> p.permission() == StationPermission.USER));

        service.delete(member3.id());
        accountRepo.delete(account3.id());
    }

    @Test
    @Order(29)
    void setPermissionsRejectsRemovingAdminFromOwner() {
        var adminPerm = stationMemberRepo
                .findPermissionByName(StationPermission.STATION_ADMINISTRATOR)
                .orElseThrow();
        var userPerm =
                stationMemberRepo.findPermissionByName(StationPermission.USER).orElseThrow();
        var ownerStation = stationRepo.create("OwnerProtectionStation");
        var ownerAccount = accountRepo.create("owner-protect@test.com", "Owner", "Protect");
        var ownerMember = service.create(ownerStation.id(), ownerAccount.id());
        service.setPermissions(
                ownerMember.id(),
                List.of(adminPerm.id(), userPerm.id()),
                EnumSet.of(StationPermission.STATION_ADMINISTRATOR, StationPermission.USER),
                null);
        stationRepo.setOwner(ownerStation.id(), ownerMember.id());

        var refused = assertThrows(
                RefusalResponse.class,
                () -> service.setPermissions(
                        ownerMember.id(),
                        List.of(userPerm.id()),
                        EnumSet.of(StationPermission.STATION_ADMINISTRATOR, StationPermission.USER),
                        null));
        assertEquals(MemberRefusal.MEMBER_OWNER_KEEPS_ADMINISTRATION, refused.refusal());

        stationRepo.setOwner(ownerStation.id(), null);
        service.delete(ownerMember.id());
        accountRepo.delete(ownerAccount.id());
        stationRepo.delete(ownerStation.id());
    }

    @Test
    @Order(29)
    void setManagedRejectsNonManageableUserType() {
        stationMemberRepo.setUserType(member2.id(), StationUserType.TEAM);
        var refused = assertThrows(
                RefusalResponse.class, () -> service.setManaged(member1.id(), List.of(member2.id()), null));
        assertEquals(MemberRefusal.MEMBER_TYPE_TAKES_NO_GUARDIANS, refused.refusal());
        stationMemberRepo.setUserType(member2.id(), StationUserType.MEMBER);
    }

    @Test
    @Order(29)
    void setJoinDate() {
        service.setJoinDate(member1.id(), java.time.LocalDate.of(2019, 3, 4));

        assertEquals(
                java.time.LocalDate.of(2019, 3, 4),
                service.findById(member1.id()).orElseThrow().joinDate());
    }

    @Test
    @Order(29)
    void noActiveMemberIsListedAsFormer() {
        assertTrue(service.findFormerByStation(station.id()).stream().noneMatch(m -> m.id() == member1.id()));
    }

    /**
     * What a station grants a user type is added to what the type carries by itself, and every
     * permission reaches the ones it includes.
     */
    @Test
    @Order(29)
    void userTypePermissionsAddToTheTypesOwnAndExpand() {
        var administrator = service.findAllPermissions().stream()
                .filter(p -> p.permission() == StationPermission.STATION_ADMINISTRATOR)
                .findFirst()
                .orElseThrow();

        var granted = service.setUserTypePermissions(station.id(), StationUserType.TEAM, List.of(administrator.id()));
        var effective = service.effectiveUserTypePermissions(station.id(), StationUserType.TEAM);

        assertEquals(
                List.of(administrator.id()), granted.stream().map(p -> p.id()).toList());
        assertEquals(granted, service.findUserTypePermissions(station.id(), StationUserType.TEAM));
        assertTrue(effective.contains(StationPermission.LOGIN.name()));
        assertTrue(effective.contains(StationPermission.STATION_ADMINISTRATOR.name()));
        assertTrue(effective.contains(StationPermission.MEMBER_EDIT.name()));
        service.setUserTypePermissions(station.id(), StationUserType.TEAM, List.of());
        assertFalse(service.effectiveUserTypePermissions(station.id(), StationUserType.TEAM)
                .contains(StationPermission.STATION_ADMINISTRATOR.name()));
    }

    @Test
    @Order(30)
    void delete() {
        var account3 = accountRepo.create("svc3@test.com", "Third", "Member");
        var member3 = service.create(station.id(), account3.id());
        assertTrue(service.delete(member3.id()));
        assertTrue(service.findById(member3.id()).isEmpty());
        accountRepo.delete(account3.id());
    }
}
