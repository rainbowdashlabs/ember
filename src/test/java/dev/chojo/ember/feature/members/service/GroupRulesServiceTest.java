/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.RefusalResponse;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.api.auth.StepUpGuard;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.feature.members.entity.MemberGroup;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.MemberGroupSetRepository;
import dev.chojo.ember.feature.members.service.GroupRulesService.GroupRules;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Setting the rules of a group that already has members: a binding that some of them do not fit,
 * and a set in which some of them would be twice.
 */
class GroupRulesServiceTest extends RepositoryTestBase {
    private final MemberGroupSetRepository sets = new MemberGroupSetRepository();
    private StepUpGuard stepUp;
    private GroupRulesService service;
    private Station station;
    private StationMember child;
    private StationMember parent;
    private UserSession manager;

    @BeforeEach
    void freshStation() {
        stepUp = mock(StepUpGuard.class);
        var memberships = new GroupMembershipService(
                memberGroupRepo,
                stationMemberRepo,
                stepUp,
                () -> mock(MemberNameResolver.class),
                new DomainEventBus(Set.of()));
        service = new GroupRulesService(memberGroupRepo, sets, memberships);
        station = stationRepo.create("Rules Station " + System.nanoTime());
        child = member(StationUserType.MEMBER);
        parent = member(StationUserType.GUARDIAN);
        manager = signedIn(member(StationUserType.MANAGER), StationPermission.MEMBER_MANAGE_GROUP);
    }

    private StationMember member(StationUserType type) {
        var account = accountRepo.create("rules-" + System.nanoTime() + "@test.com", "Rule", type.name());
        var created = stationMemberRepo.create(station.id(), account.id());
        stationMemberRepo.setUserType(created.id(), type);
        return stationMemberRepo.findById(created.id()).orElseThrow();
    }

    @Test
    void aGroupIsCreatedWithItsRules() {
        int levels = sets.create(station.id(), "Stufen").id();

        var group = service.create(
                station.id(), "Anfänger", new GroupRules(levels, Set.of(StationUserType.MEMBER)), manager);

        assertEquals(levels, group.groupSetId());
        assertEquals(List.of(StationUserType.MEMBER), group.userTypes());
        var plain = service.create(station.id(), "Alle", null, manager);
        assertNull(plain.groupSetId());
        assertTrue(plain.userTypes().isEmpty());
    }

    @Test
    void aSetOfAnotherStationIsRefused() {
        var elsewhere = stationRepo.create("Elsewhere " + System.nanoTime());
        int foreignSet = sets.create(elsewhere.id(), "Fremd").id();

        var refused = assertThrows(
                RefusalResponse.class,
                () -> service.create(station.id(), "Anfänger", new GroupRules(foreignSet, Set.of()), manager));

        assertEquals(Refusal.GROUP_SET_NOT_HERE_FOR_GROUP, refused.refusal());
        stationRepo.delete(elsewhere.id());
    }

    @Test
    void aBindingThatExcludesMembersIsRefusedUnlessTheyAreToBeTakenOut() {
        MemberGroup crew = service.create(station.id(), "Crew", null, manager);
        memberGroupRepo.addMember(crew.id(), child.id());
        memberGroupRepo.addMember(crew.id(), parent.id());
        var onlyMembers = new GroupRules(null, Set.of(StationUserType.MEMBER));

        var refused = assertThrows(
                GroupRuleRefused.class, () -> service.update(crew, "Crew", null, 0, onlyMembers, false, manager));
        assertEquals(Refusal.GROUP_BINDING_EXCLUDES_MEMBERS, refused.refusal());
        assertEquals(
                List.of(parent.id()),
                refused.conflicts().stream()
                        .map(GroupRuleRefused.GroupConflict::memberId)
                        .toList());
        assertTrue(memberGroupRepo.findById(crew.id()).orElseThrow().userTypes().isEmpty());

        var bound = service.update(crew, "Jugend", "#ff0000", 3, onlyMembers, true, manager);

        assertEquals("Jugend", bound.name());
        assertEquals(List.of(StationUserType.MEMBER), bound.userTypes());
        assertEquals(
                List.of(child.id()),
                memberGroupRepo.findMembers(crew.id()).stream()
                        .map(StationMember::id)
                        .toList());
        verify(stepUp, never()).require(any(), any());
    }

    @Test
    void formingASetWhoseGroupsShareMembersIsRefusedWithTheList() {
        int levels = sets.create(station.id(), "Stufen").id();
        MemberGroup beginners = service.create(station.id(), "Anfänger", new GroupRules(levels, Set.of()), manager);
        MemberGroup advanced = service.create(station.id(), "Fortgeschritten", null, manager);
        memberGroupRepo.addMember(beginners.id(), child.id());
        memberGroupRepo.addMember(advanced.id(), child.id());
        memberGroupRepo.addMember(advanced.id(), parent.id());

        var refused = assertThrows(
                GroupRuleRefused.class,
                () -> service.update(
                        advanced, "Fortgeschritten", null, 0, new GroupRules(levels, Set.of()), false, manager));

        assertEquals(Refusal.GROUP_SET_MEMBERS_OVERLAP, refused.refusal());
        assertEquals(1, refused.conflicts().size());
        assertEquals(child.id(), refused.conflicts().getFirst().memberId());
        assertEquals(
                List.of("Anfänger", "Fortgeschritten"),
                refused.conflicts().getFirst().groups());
        assertNull(memberGroupRepo.findById(advanced.id()).orElseThrow().groupSetId());

        memberGroupRepo.removeMember(advanced.id(), child.id());
        var joined =
                service.update(advanced, "Fortgeschritten", null, 0, new GroupRules(levels, Set.of()), false, manager);
        assertEquals(levels, joined.groupSetId());
    }

    @Test
    void anUpdateWithoutRulesLeavesThemAsTheyAre() {
        int levels = sets.create(station.id(), "Stufen").id();
        MemberGroup group = service.create(
                station.id(), "Anfänger", new GroupRules(levels, Set.of(StationUserType.MEMBER)), manager);

        var renamed = service.update(group, "Neu", null, 1, null, false, manager);

        assertEquals("Neu", renamed.name());
        assertEquals(levels, renamed.groupSetId());
        assertEquals(List.of(StationUserType.MEMBER), renamed.userTypes());
    }
}
