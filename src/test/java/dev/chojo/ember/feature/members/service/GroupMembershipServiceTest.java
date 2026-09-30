/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.RefusalResponse;
import dev.chojo.ember.api.StepUpRequiredException;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.api.auth.StepUpCategory;
import dev.chojo.ember.api.auth.StepUpGuard;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.feature.members.entity.MemberGroup;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.MemberGroupSetRepository;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The one way into a group: the user type binding, the set, the station and the rights a group
 * grants, for the people who change memberships and for the flows that do it on their own.
 */
class GroupMembershipServiceTest extends RepositoryTestBase {
    private final MemberGroupSetRepository sets = new MemberGroupSetRepository();
    private StepUpGuard stepUp;
    private MemberNameResolver names;
    private GroupMembershipService service;
    private Station station;
    private StationMember manager;
    private StationMember child;
    private StationMember parent;

    @BeforeEach
    void freshStation() {
        stepUp = mock(StepUpGuard.class);
        names = mock(MemberNameResolver.class);
        service = new GroupMembershipService(
                memberGroupRepo, stationMemberRepo, stepUp, () -> names, new DomainEventBus(Set.of()));
        station = stationRepo.create("Membership Station " + System.nanoTime());
        manager = member(StationUserType.MANAGER);
        child = member(StationUserType.MEMBER);
        parent = member(StationUserType.GUARDIAN);
    }

    private StationMember member(StationUserType type) {
        var account = accountRepo.create("membership-" + System.nanoTime() + "@test.com", "Mem", type.name());
        var created = stationMemberRepo.create(station.id(), account.id());
        stationMemberRepo.setUserType(created.id(), type);
        return stationMemberRepo.findById(created.id()).orElseThrow();
    }

    private UserSession managerHolding(StationPermission... permissions) {
        return signedIn(manager, permissions);
    }

    private MemberGroup group(String name, Integer setId, StationUserType... types) {
        var group = memberGroupRepo.create(station.id(), name);
        memberGroupRepo.assignSet(group.id(), setId);
        memberGroupRepo.replaceUserTypes(group.id(), List.of(types));
        return memberGroupRepo.findById(group.id()).orElseThrow();
    }

    private void grants(MemberGroup group, StationPermission permission) {
        memberGroupRepo.addGroupPermission(
                group.id(),
                stationMemberRepo.findPermissionByName(permission).orElseThrow().id());
    }

    private List<Integer> groupIdsOf(StationMember member) {
        return memberGroupRepo.findGroupsForMember(member.id()).stream()
                .map(MemberGroup::id)
                .sorted()
                .toList();
    }

    private static Refusal refusalOf(Runnable call) {
        return assertThrows(RefusalResponse.class, call::run).refusal();
    }

    @Test
    void aMemberMovesBetweenTheGroupsOfASetFromTheirOwnPage() {
        int levels = sets.create(station.id(), "Stufen").id();
        var beginners = group("Anfänger", levels);
        var advanced = group("Fortgeschritten", levels);
        var swimmers = group("Schwimmer", null);
        memberGroupRepo.addMember(beginners.id(), child.id());

        var after = service.replaceGroupsOfMember(
                child, List.of(advanced.id(), swimmers.id()), managerHolding(StationPermission.MEMBER_EDIT));

        assertEquals(
                List.of(advanced.id(), swimmers.id()),
                after.stream().map(MemberGroup::id).sorted().toList());
        assertEquals(List.of(advanced.id(), swimmers.id()), groupIdsOf(child));
        verify(stepUp, never()).require(any(), any());
    }

    @Test
    void twoGroupsOfOneSetAreRefusedForOneMember() {
        int levels = sets.create(station.id(), "Stufen").id();
        var beginners = group("Anfänger", levels);
        var advanced = group("Fortgeschritten", levels);

        assertEquals(
                Refusal.GROUP_SET_TWO_CHOSEN,
                refusalOf(() -> service.replaceGroupsOfMember(
                        child, List.of(beginners.id(), advanced.id()), managerHolding())));
        assertEquals(List.of(), groupIdsOf(child));
    }

    @Test
    void aGroupBoundToOtherTypesIsRefusedForAMember() {
        var trainers = group("Ausbilder", null, StationUserType.TEAM, StationUserType.MANAGER);

        assertEquals(
                Refusal.GROUP_WRONG_USER_TYPE_FOR_MEMBER,
                refusalOf(() -> service.replaceGroupsOfMember(child, List.of(trainers.id()), managerHolding())));
    }

    @Test
    void aGroupOfAnotherStationIsRefusedForAMember() {
        var elsewhere = stationRepo.create("Elsewhere " + System.nanoTime());
        var foreign = memberGroupRepo.create(elsewhere.id(), "Fremd");

        assertEquals(
                Refusal.GROUP_NOT_HERE_FOR_MEMBER,
                refusalOf(() -> service.replaceGroupsOfMember(child, List.of(foreign.id()), managerHolding())));
        stationRepo.delete(elsewhere.id());
    }

    @Test
    void joiningAGroupNeedsTheRightsItGrantsAndAFreshProof() {
        var admins = group("Verwaltung", null);
        grants(admins, StationPermission.MEMBER_MANAGE_GROUP);

        assertEquals(
                Refusal.GROUP_GRANTS_MORE_THAN_YOURS_FOR_MEMBER,
                refusalOf(() -> service.replaceGroupsOfMember(
                        child, List.of(admins.id()), managerHolding(StationPermission.MEMBER_EDIT))));

        doThrow(new StepUpRequiredException(StepUpCategory.ROLE_CHANGE, Set.of()))
                .when(stepUp)
                .require(any(), any());
        assertThrows(
                StepUpRequiredException.class,
                () -> service.replaceGroupsOfMember(
                        child, List.of(admins.id()), managerHolding(StationPermission.MEMBER_MANAGE_GROUP)));
        assertEquals(List.of(), groupIdsOf(child));
    }

    @Test
    void aStationAdministratorPutsSomebodyIntoAGroupGrantingLess() {
        var admins = group("Verwaltung", null);
        grants(admins, StationPermission.MEMBER_MANAGE_GROUP);

        service.replaceGroupsOfMember(
                child, List.of(admins.id()), managerHolding(StationPermission.STATION_ADMINISTRATOR));

        assertEquals(List.of(admins.id()), groupIdsOf(child));
        verify(stepUp).require(any(), any());
    }

    @Test
    void theGroupPageRefusesMembersOfAnotherStation() {
        var crew = group("Crew", null);
        var elsewhere = stationRepo.create("Elsewhere " + System.nanoTime());
        var account = accountRepo.create("foreign-" + System.nanoTime() + "@test.com", "For", "Eign");
        var stranger = stationMemberRepo.create(elsewhere.id(), account.id());

        assertEquals(
                Refusal.GROUP_MEMBER_NOT_HERE,
                refusalOf(() -> service.setMembers(crew, List.of(stranger.id()), false, managerHolding())));
        assertTrue(memberGroupRepo.findMembers(crew.id()).isEmpty());
        stationRepo.delete(elsewhere.id());
    }

    @Test
    void theGroupPageRefusesMembersOfTheWrongTypeByName() {
        var trainers = group("Ausbilder", null, StationUserType.TEAM, StationUserType.MANAGER);
        when(names.identified(parent.id())).thenReturn("Paula Parent");

        var refused = assertThrows(
                GroupRuleRefused.class,
                () -> service.setMembers(trainers, List.of(manager.id(), parent.id()), false, managerHolding()));

        assertEquals(Refusal.GROUP_WRONG_USER_TYPE_ON_ADD, refused.refusal());
        assertEquals(
                List.of(new GroupRuleRefused.Conflict(parent.id(), "Paula Parent", List.of("Ausbilder"))),
                refused.conflicts());
        assertTrue(refused.body() instanceof GroupRuleRefused.Body body
                && body.conflicts().size() == 1);
    }

    @Test
    void theGroupPageMovesMembersOutOfTheirOtherGroupOfTheSetOnlyWhenAsked() {
        int levels = sets.create(station.id(), "Stufen").id();
        var beginners = group("Anfänger", levels);
        var advanced = group("Fortgeschritten", levels);
        memberGroupRepo.addMember(beginners.id(), child.id());

        var refused = assertThrows(
                GroupRuleRefused.class,
                () -> service.setMembers(advanced, List.of(child.id()), false, managerHolding()));
        assertEquals(Refusal.GROUP_SET_ALREADY_IN, refused.refusal());
        assertEquals("#" + child.id(), refused.conflicts().getFirst().memberName());
        assertEquals(List.of("Anfänger"), refused.conflicts().getFirst().groups());

        var members = service.setMembers(advanced, List.of(child.id()), true, managerHolding());

        assertEquals(
                List.of(child.id()), members.stream().map(StationMember::id).toList());
        assertEquals(List.of(advanced.id()), groupIdsOf(child));
    }

    @Test
    void theGroupPageNeedsTheRightsAGroupGrantsToAddButOnlyAProofToRemove() {
        var admins = group("Verwaltung", null);
        grants(admins, StationPermission.MEMBER_MANAGE_GROUP);
        memberGroupRepo.addMember(admins.id(), parent.id());

        assertEquals(
                Refusal.GROUP_GRANTS_MORE_THAN_YOURS_ON_ADD,
                refusalOf(() -> service.setMembers(
                        admins,
                        List.of(parent.id(), child.id()),
                        false,
                        managerHolding(StationPermission.MEMBER_EDIT))));

        service.setMembers(admins, List.of(), false, managerHolding(StationPermission.MEMBER_EDIT));

        assertTrue(memberGroupRepo.findMembers(admins.id()).isEmpty());
        verify(stepUp).require(any(), any());
    }

    @Test
    void anAutomaticJoinLeavesOutAGroupThatDoesNotFit() {
        int levels = sets.create(station.id(), "Stufen").id();
        var beginners = group("Anfänger", levels);
        var advanced = group("Fortgeschritten", levels);
        var trainers = group("Ausbilder", null, StationUserType.TEAM);
        var elsewhere = stationRepo.create("Elsewhere " + System.nanoTime());
        var foreign = memberGroupRepo.create(elsewhere.id(), "Fremd");

        assertTrue(service.joinAutomatically(beginners.id(), child.id()));
        assertTrue(service.joinAutomatically(beginners.id(), child.id()));
        assertFalse(service.joinAutomatically(advanced.id(), child.id()));
        assertFalse(service.joinAutomatically(trainers.id(), child.id()));
        assertFalse(service.joinAutomatically(foreign.id(), child.id()));
        assertFalse(service.joinAutomatically(-1, child.id()));

        assertEquals(List.of(beginners.id()), groupIdsOf(child));
        service.leaveAutomatically(beginners.id(), child.id());
        assertEquals(List.of(), groupIdsOf(child));
        stationRepo.delete(elsewhere.id());
    }

    @Test
    void anInvitationIsRefusedAGroupOfAnotherStationOrOneGrantingMore() {
        var admins = group("Verwaltung", null);
        grants(admins, StationPermission.MEMBER_MANAGE_GROUP);
        var elsewhere = stationRepo.create("Elsewhere " + System.nanoTime());
        var foreign = memberGroupRepo.create(elsewhere.id(), "Fremd");

        service.requireInvitableInto(station.id(), null, managerHolding());
        service.requireInvitableInto(station.id(), admins.id(), managerHolding(StationPermission.MEMBER_MANAGE_GROUP));
        assertEquals(
                Refusal.GROUP_GRANTS_MORE_THAN_YOURS_ON_INVITE,
                refusalOf(() -> service.requireInvitableInto(station.id(), admins.id(), managerHolding())));
        assertEquals(
                Refusal.INVITE_GROUP_NOT_HERE,
                refusalOf(() -> service.requireInvitableInto(station.id(), foreign.id(), managerHolding())));
        stationRepo.delete(elsewhere.id());
    }

    @Test
    void anAutomaticFlowIsRefusedAGroupThatDoesNotTakeItsPeople() {
        var trainers = group("Ausbilder", null, StationUserType.TEAM);
        var anyone = group("Alle", null);

        service.requireAdmits(
                station.id(),
                null,
                StationUserType.MEMBER,
                Refusal.WAITING_LIST_JOIN_GROUP_NOT_HERE,
                Refusal.WAITING_LIST_JOIN_GROUP_WRONG_USER_TYPE);
        service.requireAdmits(
                station.id(),
                anyone.id(),
                StationUserType.MEMBER,
                Refusal.WAITING_LIST_JOIN_GROUP_NOT_HERE,
                Refusal.WAITING_LIST_JOIN_GROUP_WRONG_USER_TYPE);
        assertEquals(
                Refusal.WAITING_LIST_JOIN_GROUP_WRONG_USER_TYPE,
                refusalOf(() -> service.requireAdmits(
                        station.id(),
                        trainers.id(),
                        StationUserType.MEMBER,
                        Refusal.WAITING_LIST_JOIN_GROUP_NOT_HERE,
                        Refusal.WAITING_LIST_JOIN_GROUP_WRONG_USER_TYPE)));
        assertEquals(
                Refusal.WAITING_LIST_JOIN_GROUP_NOT_HERE,
                refusalOf(() -> service.requireAdmits(
                        station.id() + 100_000,
                        trainers.id(),
                        StationUserType.MEMBER,
                        Refusal.WAITING_LIST_JOIN_GROUP_NOT_HERE,
                        Refusal.WAITING_LIST_JOIN_GROUP_WRONG_USER_TYPE)));
    }

    @Test
    void aTypeChangeLeavesTheGroupsThatDoNotTakeTheNewType() {
        var children = group("Kinder", null, StationUserType.MEMBER);
        var everyone = group("Alle", null);
        memberGroupRepo.addMember(children.id(), child.id());
        memberGroupRepo.addMember(everyone.id(), child.id());

        assertEquals(
                List.of(children.id()),
                service.groupsUnfitFor(child.id(), StationUserType.TEAM).stream()
                        .map(MemberGroup::id)
                        .toList());
        assertEquals(List.of(), service.groupsUnfitFor(child.id(), StationUserType.MEMBER));

        var left = service.leaveGroupsUnfitFor(child.id(), StationUserType.TEAM);

        assertEquals(List.of(children.id()), left.stream().map(MemberGroup::id).toList());
        assertEquals(List.of(everyone.id()), groupIdsOf(child));
        assertEquals(List.of(), service.leaveGroupsUnfitFor(child.id(), StationUserType.TEAM));
    }
}
