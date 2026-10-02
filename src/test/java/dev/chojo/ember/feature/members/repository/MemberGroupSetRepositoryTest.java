/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.repository;

import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.members.entity.MemberGroup;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The sets of groups and the user type bindings, down to the triggers and the index that keep a
 * member in one group of a set whoever writes the membership.
 */
class MemberGroupSetRepositoryTest extends RepositoryTestBase {
    private static final MemberGroupSetRepository sets = new MemberGroupSetRepository();
    private static Station station;
    private static Account firstAccount;
    private static Account secondAccount;
    private static StationMember first;
    private static StationMember second;

    @BeforeAll
    static void setup() {
        station = stationRepo.create("Group Set Station");
        firstAccount = accountRepo.create("set-first@test.com", "First", "Member");
        secondAccount = accountRepo.create("set-second@test.com", "Second", "Member");
        first = stationMemberRepo.create(station.id(), firstAccount.id());
        second = stationMemberRepo.create(station.id(), secondAccount.id());
        stationMemberRepo.setUserType(first.id(), StationUserType.MEMBER);
        stationMemberRepo.setUserType(second.id(), StationUserType.GUARDIAN);
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accountRepo.delete(firstAccount.id());
        accountRepo.delete(secondAccount.id());
    }

    @Test
    void setsAreCreatedRenamedListedAndDeleted() {
        var set = sets.create(station.id(), "Stufen");
        assertEquals("Stufen", sets.findById(set.id()).orElseThrow().name());
        assertTrue(sets.nameTaken(station.id(), "Stufen", null));
        assertFalse(sets.nameTaken(station.id(), "Stufen", set.id()));

        assertTrue(sets.rename(set.id(), station.id(), "Level"));
        assertFalse(sets.rename(set.id(), station.id() + 1000, "Anderswo"));
        assertEquals(
                List.of("Level"),
                sets.findByStation(station.id()).stream().map(s -> s.name()).toList());

        assertFalse(sets.delete(set.id(), station.id() + 1000));
        assertTrue(sets.delete(set.id(), station.id()));
        assertTrue(sets.findById(set.id()).isEmpty());
    }

    @Test
    void aMembershipTakesTheSetOfItsGroupAndOnlyOneGroupOfASetIsAllowed() {
        var set = sets.create(station.id(), "Ausbildung");
        MemberGroup beginners = memberGroupRepo.create(station.id(), "Set Anfänger");
        MemberGroup advanced = memberGroupRepo.create(station.id(), "Set Fortgeschritten");
        memberGroupRepo.assignSet(beginners.id(), set.id());
        memberGroupRepo.assignSet(advanced.id(), set.id());
        assertEquals(
                set.id(), memberGroupRepo.findById(beginners.id()).orElseThrow().groupSetId());

        memberGroupRepo.addMember(beginners.id(), first.id());
        assertEquals(
                Map.of(first.id(), beginners.id()),
                memberGroupRepo.findGroupInSet(set.id(), List.of(first.id(), second.id())));
        assertEquals(Map.of(), memberGroupRepo.findGroupInSet(set.id(), List.of()));

        assertThrows(RuntimeException.class, () -> memberGroupRepo.addMember(advanced.id(), first.id()));

        memberGroupRepo.removeMember(beginners.id(), first.id());
        memberGroupRepo.addMember(advanced.id(), first.id());
        assertEquals(Map.of(first.id(), advanced.id()), memberGroupRepo.findGroupInSet(set.id(), List.of(first.id())));

        sets.delete(set.id(), station.id());
        assertNull(memberGroupRepo.findById(advanced.id()).orElseThrow().groupSetId());
        assertEquals(Map.of(), memberGroupRepo.findGroupInSet(set.id(), List.of(first.id())));
        memberGroupRepo.addMember(beginners.id(), first.id());

        memberGroupRepo.delete(beginners.id());
        memberGroupRepo.delete(advanced.id());
    }

    @Test
    void movingAGroupIntoASetWhoseMembersOverlapFails() {
        var set = sets.create(station.id(), "Überschneidung");
        MemberGroup one = memberGroupRepo.create(station.id(), "Overlap One");
        MemberGroup two = memberGroupRepo.create(station.id(), "Overlap Two");
        memberGroupRepo.addMember(one.id(), first.id());
        memberGroupRepo.addMember(two.id(), first.id());
        memberGroupRepo.addMember(two.id(), second.id());

        assertEquals(
                Map.of(first.id(), Set.of(one.id(), two.id())),
                memberGroupRepo.findOverlaps(List.of(one.id(), two.id())));
        assertEquals(Map.of(), memberGroupRepo.findOverlaps(List.of(one.id())));

        memberGroupRepo.assignSet(one.id(), set.id());
        assertThrows(RuntimeException.class, () -> memberGroupRepo.assignSet(two.id(), set.id()));
        assertNull(memberGroupRepo.findById(two.id()).orElseThrow().groupSetId());

        memberGroupRepo.delete(one.id());
        memberGroupRepo.delete(two.id());
        sets.delete(set.id(), station.id());
    }

    @Test
    void aGroupIsBoundToUserTypes() {
        MemberGroup group = memberGroupRepo.create(station.id(), "Bound");
        assertTrue(group.userTypes().isEmpty());
        assertTrue(group.admits(StationUserType.GUARDIAN));
        memberGroupRepo.addMember(group.id(), first.id());
        memberGroupRepo.addMember(group.id(), second.id());

        memberGroupRepo.replaceUserTypes(group.id(), List.of(StationUserType.TEAM, StationUserType.MEMBER));
        MemberGroup bound = memberGroupRepo.findById(group.id()).orElseThrow();
        assertEquals(List.of(StationUserType.MEMBER, StationUserType.TEAM), bound.userTypes());
        assertFalse(bound.admits(StationUserType.GUARDIAN));
        assertEquals(
                List.of(second.id()),
                memberGroupRepo.findMembersNotOfTypes(group.id(), bound.userTypes()).stream()
                        .map(StationMember::id)
                        .toList());
        assertEquals(
                bound.userTypes(),
                memberGroupRepo.findGroupsForMember(first.id()).stream()
                        .filter(g -> g.id() == group.id())
                        .findFirst()
                        .orElseThrow()
                        .userTypes());

        memberGroupRepo.replaceUserTypes(group.id(), List.of());
        assertTrue(
                memberGroupRepo.findById(group.id()).orElseThrow().userTypes().isEmpty());

        memberGroupRepo.delete(group.id());
    }
}
