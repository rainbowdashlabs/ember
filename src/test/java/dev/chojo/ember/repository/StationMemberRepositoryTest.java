/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.repository;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.members.entity.Permission;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.station.entity.Station;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.List;
import java.util.UUID;

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

    // -- Permissions --

    @Test
    @Order(9)
    void findAllPermissions() {
        var permissions = stationMemberRepo.findAllPermissions();
        assertFalse(permissions.isEmpty());
        assertTrue(permissions.stream().anyMatch(r -> r.permission() == StationPermission.LOGIN));
    }

    @Test
    @Order(9)
    void findPermissionByName() {
        assertTrue(
                stationMemberRepo.findPermissionByName(StationPermission.LOGIN).isPresent());
    }

    @Test
    @Order(10)
    void grantAndFindPermissions() {
        // Permission ID 1 = 'LOGIN' (seeded)
        stationMemberRepo.grantPermission(memberId1, 1);
        List<Permission> permissions = stationMemberRepo.findPermissions(memberId1);
        assertEquals(1, permissions.size());
        assertEquals(StationPermission.LOGIN, permissions.getFirst().permission());
    }

    @Test
    @Order(11)
    void hasLoginPermission() {
        assertTrue(stationMemberRepo.hasLoginPermission(account1.id()));
        assertFalse(stationMemberRepo.hasLoginPermission(account2.id()));
    }

    @Test
    @Order(12)
    void revokePermission() {
        assertTrue(stationMemberRepo.revokePermission(memberId1, 1));
        assertTrue(stationMemberRepo.findPermissions(memberId1).isEmpty());
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
    @Order(22)
    void findCompletions() {
        var completions = stationMemberRepo.findCompletions(station.id());
        assertNotNull(completions);
        assertTrue(
                completions.stream().allMatch(c -> c.name() != null && !c.name().isBlank()));
    }

    // -- Additional coverage --

    @Test
    @Order(30)
    void findByUid() {
        var member = stationMemberRepo.findById(memberId1).orElseThrow();
        var found = stationMemberRepo.findByUid(station.id(), member.uid());
        assertTrue(found.isPresent());
        assertEquals(memberId1, found.get().id());

        // non-existent UID returns empty
        assertTrue(stationMemberRepo.findByUid(station.id(), UUID.randomUUID()).isEmpty());
    }

    @Test
    @Order(31)
    void findAllByAccountId() {
        var members = stationMemberRepo.findAllByAccountId(account1.id());
        assertFalse(members.isEmpty());
        assertTrue(members.stream().allMatch(m -> m.accountId() == account1.id()));
    }

    @Test
    @Order(32)
    void findByStationIncludingFormer() {
        stationMemberRepo.setFormer(memberId2, true);
        var activeOnly = stationMemberRepo.findByStation(station.id(), false);
        assertEquals(1, activeOnly.size());

        var all = stationMemberRepo.findByStation(station.id(), true);
        assertEquals(2, all.size());

        stationMemberRepo.setFormer(memberId2, false);
    }

    @Test
    @Order(33)
    void findFormerByStation() {
        stationMemberRepo.setFormer(memberId2, true);
        var former = stationMemberRepo.findFormerByStation(station.id());
        assertEquals(1, former.size());
        assertEquals(memberId2, former.getFirst().id());
        stationMemberRepo.setFormer(memberId2, false);
    }

    @Test
    @Order(34)
    void findMembersWithPermission() {
        stationMemberRepo.grantPermission(memberId1, 1);
        var members = stationMemberRepo.findMembersWithPermission(station.id(), StationPermission.LOGIN);
        assertFalse(members.isEmpty());
        assertTrue(members.stream().anyMatch(m -> m.id() == memberId1));
        stationMemberRepo.revokePermission(memberId1, 1);
    }

    @Test
    @Order(35)
    void findByStationAndUserType() {
        stationMemberRepo.setUserType(memberId1, StationUserType.TEAM);
        var members = stationMemberRepo.findByStationAndUserType(station.id(), StationUserType.TEAM);
        assertFalse(members.isEmpty());
        assertTrue(members.stream().anyMatch(m -> m.id() == memberId1));
        stationMemberRepo.setUserType(memberId1, StationUserType.MEMBER);
    }

    @Test
    @Order(36)
    void findRichMembers() {
        var rich = stationMemberRepo.findRichMembers(station.id(), false);
        assertEquals(2, rich.size());
        for (var rm : rich) {
            assertNotNull(rm.name());
            assertNotNull(rm.uid());
        }
    }

    @Test
    @Order(37)
    void setDisplayNameAndClearAccount() {
        stationMemberRepo.setDisplayNameAndClearAccount(memberId2, "Custom Name");
        var member = stationMemberRepo.findById(memberId2).orElseThrow();
        assertEquals("Custom Name", member.displayName());
        assertNull(member.accountId());
    }

    @Test
    @Order(38)
    void revokeAllPermissions() {
        stationMemberRepo.grantPermission(memberId1, 1);
        assertFalse(stationMemberRepo.findPermissions(memberId1).isEmpty());
        stationMemberRepo.revokeAllPermissions(memberId1);
        assertTrue(stationMemberRepo.findPermissions(memberId1).isEmpty());
    }

    @Test
    @Order(39)
    void resolveUidAndIdCaching() {
        // resolveUid populates the cache
        var uid = stationMemberRepo.resolveUid(memberId1);
        assertNotNull(uid);
        // Call again to hit cache
        var uid2 = stationMemberRepo.resolveUid(memberId1);
        assertEquals(uid, uid2);

        // resolveId populates the cache
        var id = stationMemberRepo.resolveId(station.id(), uid);
        assertTrue(id.isPresent());
        assertEquals(memberId1, id.get());
        // Call again to hit cache
        var id2 = stationMemberRepo.resolveId(station.id(), uid);
        assertTrue(id2.isPresent());
        assertEquals(memberId1, id2.get());
    }

    @Test
    @Order(39)
    void invalidateMemberCache() {
        // Populate cache
        var uid = stationMemberRepo.resolveUid(memberId1);
        assertNotNull(uid);
        stationMemberRepo.resolveId(station.id(), uid);

        // Invalidate — should not throw
        stationMemberRepo.invalidateMemberCache(memberId1);

        // After invalidation, a fresh resolve should still work
        var freshUid = stationMemberRepo.resolveUid(memberId1);
        assertEquals(uid, freshUid);
    }

    @Test
    @Order(39)
    void resolveIdentity() {
        var identity = stationMemberRepo.resolveIdentity(memberId1);
        assertNotNull(identity);
        assertNotNull(identity.stationUid());
        assertNotNull(identity.memberUid());
    }

    @Test
    @Order(39)
    void setUserTypePermissions() {
        var allPerms = stationMemberRepo.findAllPermissions();
        var loginPerm = allPerms.stream()
                .filter(p -> p.permission() == StationPermission.LOGIN)
                .findFirst()
                .orElseThrow();

        stationMemberRepo.setUserTypePermissions(station.id(), StationUserType.GUARDIAN, List.of(loginPerm.id()));
        var perms = stationMemberRepo.findUserTypePermissions(station.id(), StationUserType.GUARDIAN);
        assertFalse(perms.isEmpty());
        assertTrue(perms.stream().anyMatch(p -> p.permission() == StationPermission.LOGIN));

        // Clear
        stationMemberRepo.setUserTypePermissions(station.id(), StationUserType.GUARDIAN, List.of());
        var cleared = stationMemberRepo.findUserTypePermissions(station.id(), StationUserType.GUARDIAN);
        assertTrue(cleared.isEmpty());
    }

    @Test
    @Order(39)
    void findUserTypePermissionsEmpty() {
        var perms = stationMemberRepo.findUserTypePermissions(station.id(), StationUserType.TRIAL);
        assertTrue(perms.isEmpty());
    }

    @Test
    @Order(40)
    void removeAllManagedAndManagers() {
        stationMemberRepo.addManager(memberId1, memberId2);
        assertFalse(stationMemberRepo.findManaged(memberId1).isEmpty());
        stationMemberRepo.removeAllManaged(memberId1);
        assertTrue(stationMemberRepo.findManaged(memberId1).isEmpty());

        stationMemberRepo.addManager(memberId1, memberId2);
        assertFalse(stationMemberRepo.findManagers(memberId2).isEmpty());
        stationMemberRepo.removeAllManagers(memberId2);
        assertTrue(stationMemberRepo.findManagers(memberId2).isEmpty());
    }

    @Test
    @Order(50)
    void searchForPickerAndFindPickerByUid() {
        var matches = stationMemberRepo.searchForPicker(station.id(), null, 50);
        assertFalse(matches.isEmpty());
        var first = matches.stream()
                .filter(p -> p.memberUid().equals(stationMemberRepo.resolveUid(memberId1)))
                .findFirst()
                .orElseThrow();
        assertNotNull(first.displayName());

        var byName = stationMemberRepo.searchForPicker(station.id(), "member", 50);
        assertFalse(byName.isEmpty());

        var noHit = stationMemberRepo.searchForPicker(station.id(), "definitely-not-a-name-xyzqq", 50);
        assertTrue(noHit.isEmpty());

        var byUid = stationMemberRepo.findPickerByUid(station.id(), first.memberUid());
        assertTrue(byUid.isPresent());
        assertEquals(first.memberUid(), byUid.orElseThrow().memberUid());

        assertTrue(stationMemberRepo
                .findPickerByUid(station.id(), UUID.randomUUID())
                .isEmpty());
    }

    @Test
    @Order(51)
    void findOfficersByGroupTagAndUids() {
        var group = memberGroupRepo.create(station.id(), "Officer Group");
        memberGroupRepo.addMember(group.id(), memberId1);
        var tag = userTagRepo.create(station.id(), "Officer Tag");
        userTagRepo.addMember(tag.id(), memberId1);
        try {
            var byGroup = stationMemberRepo.findOfficersByGroup(station.id(), group.id());
            assertTrue(byGroup.stream().anyMatch(m -> m.memberUid().equals(stationMemberRepo.resolveUid(memberId1))));

            var byTag = stationMemberRepo.findOfficersByTag(station.id(), tag.id());
            assertTrue(byTag.stream().anyMatch(m -> m.memberUid().equals(stationMemberRepo.resolveUid(memberId1))));

            var uid = stationMemberRepo.resolveUid(memberId1);
            var byUids = stationMemberRepo.findOfficersByUids(station.id(), List.of(uid));
            assertEquals(1, byUids.size());
            assertEquals(uid, byUids.getFirst().memberUid());

            assertTrue(stationMemberRepo
                    .findOfficersByUids(station.id(), List.of())
                    .isEmpty());
            assertTrue(stationMemberRepo.findOfficersByUids(station.id(), null).isEmpty());
        } finally {
            userTagRepo.removeMember(tag.id(), memberId1);
            userTagRepo.delete(tag.id());
            memberGroupRepo.delete(group.id());
        }
    }

    @Test
    @Order(99)
    void delete() {
        assertTrue(stationMemberRepo.delete(memberId1));
        assertTrue(stationMemberRepo.delete(memberId2));
        assertTrue(stationMemberRepo.findByStation(station.id()).isEmpty());
    }
}
