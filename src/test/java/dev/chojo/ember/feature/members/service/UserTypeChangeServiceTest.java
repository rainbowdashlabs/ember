/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.members.entity.MemberGroup;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** A member's type changes, and the groups bound to other types follow it. */
class UserTypeChangeServiceTest extends RepositoryTestBase {

    @Test
    void aTypeChangeSaysAndDoesWhichGroupsAreLeft() {
        var service = new UserTypeChangeService(stationMemberRepo, newGroupMemberships());
        var station = stationRepo.create("Type Station " + System.nanoTime());
        var account = accountRepo.create("type-" + System.nanoTime() + "@test.com", "Ty", "Pe");
        var member = stationMemberRepo.create(station.id(), account.id());
        stationMemberRepo.setUserType(member.id(), StationUserType.MEMBER);
        var children = memberGroupRepo.create(station.id(), "Kinder");
        memberGroupRepo.replaceUserTypes(children.id(), List.of(StationUserType.MEMBER));
        var everyone = memberGroupRepo.create(station.id(), "Alle");
        memberGroupRepo.addMember(children.id(), member.id());
        memberGroupRepo.addMember(everyone.id(), member.id());

        assertEquals(List.of("Kinder"), names(service.consequences(member.id(), StationUserType.TEAM)));
        assertEquals(List.of("Kinder"), names(service.change(member.id(), StationUserType.TEAM)));

        assertEquals(
                StationUserType.TEAM,
                stationMemberRepo.findById(member.id()).orElseThrow().userType());
        assertEquals(List.of("Alle"), names(memberGroupRepo.findGroupsForMember(member.id())));
        stationRepo.delete(station.id());
    }

    private static List<String> names(List<MemberGroup> groups) {
        return groups.stream().map(MemberGroup::name).toList();
    }
}
