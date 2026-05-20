/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.service;

import dev.chojo.ember.api.Roles;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.service.MemberGroupService;
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

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MemberGroupServiceTest extends RepositoryTestBase {
    private static MemberGroupService service;
    private static Station station;
    private static Account account;
    private static StationMember member;
    private static int groupId;

    @BeforeAll
    static void setup() {
        service = new MemberGroupService(memberGroupRepo, stationMemberRepo, userTagRepo);
        station = stationRepo.create("GroupStation");
        account = accountRepo.create("group-svc@test.com", "Group", "Tester");
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
        var group = service.create(station.id(), "Anfänger");
        assertNotNull(group);
        assertEquals("Anfänger", group.name());
        groupId = group.id();
    }

    @Test
    @Order(2)
    void findById() {
        assertTrue(service.findById(groupId).isPresent());
    }

    @Test
    @Order(3)
    void findByStation() {
        var groups = service.findByStation(station.id());
        assertTrue(groups.stream().anyMatch(g -> g.id() == groupId));
    }

    @Test
    @Order(10)
    void addMember() {
        service.setMembers(groupId, List.of(member.id()));
        var members = service.findMembers(groupId);
        assertTrue(members.stream().anyMatch(m -> m.id() == member.id()));
    }

    @Test
    @Order(11)
    void findGroupsForMember() {
        var groups = service.findGroupsForMember(member.id());
        assertTrue(groups.stream().anyMatch(g -> g.id() == groupId));
    }

    @Test
    @Order(12)
    void setMembers() {
        service.setMembers(groupId, List.of(member.id()));
        var members = service.findMembers(groupId);
        assertEquals(1, members.size());
    }

    @Test
    @Order(20)
    void setGroupRoles() {
        var memberRole = stationMemberRepo.findRoleByName(Roles.MEMBER).orElseThrow();
        service.setGroupRoles(groupId, List.of(memberRole.id()), EnumSet.of(Roles.ADMIN, Roles.MEMBER));
        var roles = service.findGroupRoles(groupId);
        assertTrue(roles.stream().anyMatch(r -> r.role() == Roles.MEMBER));
    }

    @Test
    @Order(30)
    void update() {
        var result = service.update(groupId, "Fortgeschritten");
        assertTrue(result.isPresent());
        assertEquals("Fortgeschritten", result.get().name());
    }

    @Test
    @Order(40)
    void delete() {
        assertTrue(service.delete(groupId));
        assertTrue(service.findById(groupId).isEmpty());
    }
}
