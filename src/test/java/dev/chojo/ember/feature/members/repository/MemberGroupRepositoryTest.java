/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.repository;

import dev.chojo.ember.api.AccessManager;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.members.entity.MemberGroup;
import dev.chojo.ember.feature.members.entity.Permission;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MemberGroupRepositoryTest extends RepositoryTestBase {
    private static Station station;
    private static Account account;
    private static StationMember member;
    private static int groupId;

    @BeforeAll
    static void setup() {
        station = stationRepo.create("Group Station");
        account = accountRepo.create("group@test.com", "Group", "User");
        member = stationMemberRepo.create(station.id(), account.id());
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    @Test
    @Order(1)
    void create() {
        MemberGroup group = memberGroupRepo.create(station.id(), "Alpha Team");
        assertNotNull(group);
        assertEquals("Alpha Team", group.name());
        groupId = group.id();
    }

    @Test
    @Order(2)
    void findById() {
        assertTrue(memberGroupRepo.findById(groupId).isPresent());
    }

    @Test
    @Order(3)
    void findByStation() {
        assertEquals(1, memberGroupRepo.findByStation(station.id()).size());
    }

    @Test
    @Order(4)
    void update() {
        assertTrue(memberGroupRepo.update(groupId, "Beta Team", null, 0));
        assertEquals(
                "Beta Team", memberGroupRepo.findById(groupId).orElseThrow().name());
    }

    // -- Members --

    @Test
    @Order(10)
    void addAndFindMembers() {
        memberGroupRepo.addMember(groupId, member.id());
        var members = memberGroupRepo.findMembers(groupId);
        assertEquals(1, members.size());
        assertEquals(member.id(), members.getFirst().id());
    }

    @Test
    @Order(11)
    void findGroupsForMember() {
        var groups = memberGroupRepo.findGroupsForMember(member.id());
        assertEquals(1, groups.size());
        assertEquals(groupId, groups.getFirst().id());
    }

    @Test
    @Order(12)
    void removeMember() {
        assertTrue(memberGroupRepo.removeMember(groupId, member.id()));
        assertTrue(memberGroupRepo.findMembers(groupId).isEmpty());
    }

    // -- Group Permissions --

    @Test
    @Order(20)
    void memberInGroupWithLoginPermissionHasLoginPermission() {
        // Add member to the group
        memberGroupRepo.addMember(groupId, member.id());

        // Assign LOGIN permission to the group
        Permission loginPerm =
                stationMemberRepo.findPermissionByName(StationPermission.LOGIN).orElseThrow();
        memberGroupRepo.addGroupPermission(groupId, loginPerm.id());

        // Resolve effective permissions through AccessManager (the same path used for session resolution)
        var accessManager =
                new AccessManager(accountRepo, stationMemberRepo, memberGroupRepo, null, null, null, null, null, null);
        Set<StationPermission> resolved = accessManager.resolveExpandedMemberPermissions(member.id());

        assertTrue(
                resolved.contains(StationPermission.LOGIN),
                "Member in a group with LOGIN permission should have LOGIN");
        assertTrue(resolved.contains(StationPermission.USER), "LOGIN expands to include USER");

        // Cleanup
        memberGroupRepo.removeGroupPermission(groupId, loginPerm.id());
        memberGroupRepo.removeMember(groupId, member.id());
    }

    @Test
    @Order(99)
    void delete() {
        assertTrue(memberGroupRepo.delete(groupId));
        assertTrue(memberGroupRepo.findById(groupId).isEmpty());
    }
}
