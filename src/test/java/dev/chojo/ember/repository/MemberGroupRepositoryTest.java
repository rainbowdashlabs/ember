/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.repository;

import dev.chojo.ember.api.AccessManager;
import dev.chojo.ember.api.Roles;
import dev.chojo.ember.entity.Account;
import dev.chojo.ember.entity.MemberGroup;
import dev.chojo.ember.entity.Role;
import dev.chojo.ember.entity.Station;
import dev.chojo.ember.entity.StationMember;
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
        assertTrue(memberGroupRepo.update(groupId, "Beta Team"));
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

    // -- Group Roles --

    @Test
    @Order(20)
    void memberInGroupWithTeamRoleHasTeamRole() {
        // Add member to the group
        memberGroupRepo.addMember(groupId, member.id());

        // Assign TEAM role to the group
        Role teamRole = stationMemberRepo.findRoleByName(Roles.TEAM).orElseThrow();
        memberGroupRepo.addGroupRole(groupId, teamRole.id());

        // Resolve effective roles through AccessManager (the same path used for session resolution)
        var accessManager = new AccessManager(accountRepo, stationMemberRepo, memberGroupRepo);
        Set<Roles> resolved = accessManager.resolveExpandedMemberRoles(member.id());

        assertTrue(resolved.contains(Roles.TEAM), "Member in a group with TEAM role should have TEAM");
        assertTrue(resolved.contains(Roles.LOGIN), "TEAM expands to include LOGIN");
        assertTrue(resolved.contains(Roles.USER), "TEAM expands to include USER");

        // Cleanup
        memberGroupRepo.removeGroupRole(groupId, teamRole.id());
        memberGroupRepo.removeMember(groupId, member.id());
    }

    @Test
    @Order(99)
    void delete() {
        assertTrue(memberGroupRepo.delete(groupId));
        assertTrue(memberGroupRepo.findById(groupId).isEmpty());
    }
}
