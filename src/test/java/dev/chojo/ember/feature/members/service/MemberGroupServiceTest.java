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
import dev.chojo.ember.feature.form.entity.FormPurpose;
import dev.chojo.ember.feature.members.entity.MemberGroup;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.restriction.RestrictionSelection;
import dev.chojo.ember.feature.restriction.RestrictionType;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.time.Instant;
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
        var group = memberGroupRepo.create(station.id(), "Anfänger");
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
    void findMembers() {
        memberGroupRepo.addMember(groupId, member.id());
        var members = service.findMembers(groupId);
        assertEquals(
                List.of(member.id()), members.stream().map(StationMember::id).toList());
    }

    @Test
    @Order(11)
    void findGroupsForMember() {
        var groups = service.findGroupsForMember(member.id());
        assertTrue(groups.stream().anyMatch(g -> g.id() == groupId));
    }

    @Test
    @Order(20)
    void setGroupRoles() {
        var memberRole =
                stationMemberRepo.findPermissionByName(StationPermission.USER).orElseThrow();
        service.setGroupPermissions(
                groupId,
                List.of(memberRole.id()),
                EnumSet.of(StationPermission.STATION_ADMINISTRATOR, StationPermission.USER));
        var roles = service.findGroupPermissions(groupId);
        assertTrue(roles.stream().anyMatch(r -> r.permission() == StationPermission.USER));
    }

    @Test
    @Order(40)
    void delete() {
        assertTrue(service.delete(groupId));
        assertTrue(service.findById(groupId).isEmpty());
    }

    @Test
    @Order(50)
    void convertToTag() {
        var group2 = memberGroupRepo.create(station.id(), "ToBeTag");
        memberGroupRepo.addMember(group2.id(), member.id());

        service.convertToTag(group2.id());

        assertTrue(service.findById(group2.id()).isEmpty());

        var tags = userTagRepo.findByStation(station.id());
        assertTrue(tags.stream().anyMatch(t -> "ToBeTag".equals(t.name())));

        tags.stream().filter(t -> "ToBeTag".equals(t.name())).findFirst().ifPresent(t -> userTagRepo.delete(t.id()));
    }

    /** A news entry limited to one group, and a form limited to that group and to members. */
    private static MemberGroup groupLimitingTwoThings(String name) {
        var group = memberGroupRepo.create(station.id(), name);
        var onlyThem = new RestrictionSelection(List.of(), List.of(group.id()), List.of(), List.of(), null);
        var news = newsRepo.create(
                station.id(), name, "nur", "<p>nur</p>", stationMemberRepo.resolveIdentity(member.id()));
        restrictionRepo.setRestrictions(RestrictionType.NEWS, news.id(), onlyThem);
        var form = formRepo.create(
                station.id(),
                name,
                "nur",
                false,
                true,
                false,
                Instant.parse("2026-06-01T00:00:00Z"),
                Instant.parse("2026-07-01T00:00:00Z"),
                member.id(),
                FormPurpose.INTERNAL);
        restrictionRepo.setRestrictions(
                RestrictionType.FORM,
                form.id(),
                new RestrictionSelection(
                        List.of(StationUserType.MEMBER), List.of(group.id()), List.of(), List.of(), null));
        return group;
    }

    @Test
    @Order(60)
    void aGroupSomethingIsLimitedToStaysAndSaysHowMany() {
        var group = groupLimitingTwoThings("Nur Atemschutz");

        var refusal = assertThrows(RefusalResponse.class, () -> service.delete(group.id()));

        assertEquals(MemberRefusal.GROUP_STILL_LIMITS_CONTENT_ON_DELETE, refusal.refusal());
        assertTrue(refusal.getMessage().endsWith(": 2"), refusal.getMessage());
        assertTrue(service.findById(group.id()).isPresent());
    }

    @Test
    @Order(61)
    void aGroupSomethingIsLimitedToIsNotTurnedIntoATag() {
        var group = groupLimitingTwoThings("Nur Maschinisten");

        var refusal = assertThrows(RefusalResponse.class, () -> service.convertToTag(group.id()));

        assertEquals(MemberRefusal.GROUP_STILL_LIMITS_CONTENT_ON_CONVERT, refusal.refusal());
        assertTrue(service.findById(group.id()).isPresent());
        assertTrue(
                userTagRepo.findByStation(station.id()).stream().noneMatch(t -> "Nur Maschinisten".equals(t.name())));
    }
}
