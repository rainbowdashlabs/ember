/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.service;

import dev.chojo.ember.api.Roles;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.service.EventService;
import dev.chojo.ember.feature.members.entity.Role;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EventEligibilityTest extends RepositoryTestBase {
    private static EventService service;
    private static Station station;
    private static int memberRoleId;
    private static int teamRoleId;
    private static int groupId;
    private static int tagId;

    @BeforeAll
    static void setup() {
        service = new EventService(eventRepo);
        station = stationRepo.create("EligibilityStation");

        var memberRole = stationMemberRepo.findRoleByName(Roles.MEMBER);
        memberRoleId = memberRole.map(Role::id).orElse(-1);

        var teamRole = stationMemberRepo.findRoleByName(Roles.TEAM);
        teamRoleId = teamRole.map(Role::id).orElse(-1);

        var group = memberGroupRepo.create(station.id(), "TestGroup");
        groupId = group.id();

        var tag = userTagRepo.create(station.id(), "TestTag");
        tagId = tag.id();
    }

    private int createEvent() {
        return eventRepo
                .create(
                        station.id(),
                        "Test Event",
                        "",
                        StationEvent.EventType.ONE_TIME,
                        null,
                        Instant.now(),
                        Instant.now().plusSeconds(3600),
                        null,
                        false,
                        null,
                        false,
                        null)
                .id();
    }

    @Test
    void noRestrictionsAllowsAll() {
        int eventId = createEvent();
        assertTrue(service.isMemberEligible(eventId, EnumSet.of(Roles.MEMBER), List.of(), List.of()));
    }

    @Test
    void roleRestrictionMatchesWhenMemberHasRole() {
        int eventId = createEvent();
        service.setRestrictions(eventId, List.of(memberRoleId), List.of(), List.of());
        assertTrue(service.isMemberEligible(eventId, EnumSet.of(Roles.MEMBER, Roles.USER), List.of(), List.of()));
    }

    @Test
    void roleRestrictionRejectsWhenMemberLacksRole() {
        int eventId = createEvent();
        service.setRestrictions(eventId, List.of(teamRoleId), List.of(), List.of());
        assertFalse(service.isMemberEligible(eventId, EnumSet.of(Roles.MEMBER, Roles.USER), List.of(), List.of()));
    }

    @Test
    void groupRestrictionMatchesWhenMemberInGroup() {
        int eventId = createEvent();
        service.setRestrictions(eventId, List.of(), List.of(groupId), List.of());
        assertTrue(service.isMemberEligible(eventId, EnumSet.of(Roles.MEMBER), List.of(groupId), List.of()));
    }

    @Test
    void groupRestrictionRejectsWhenMemberNotInGroup() {
        int eventId = createEvent();
        service.setRestrictions(eventId, List.of(), List.of(groupId), List.of());
        assertFalse(service.isMemberEligible(eventId, EnumSet.of(Roles.MEMBER), List.of(), List.of()));
    }

    @Test
    void tagRestrictionMatchesWhenMemberHasTag() {
        int eventId = createEvent();
        service.setRestrictions(eventId, List.of(), List.of(), List.of(tagId));
        assertTrue(service.isMemberEligible(eventId, EnumSet.of(Roles.MEMBER), List.of(), List.of(tagId)));
    }

    @Test
    void tagRestrictionRejectsWhenMemberLacksTag() {
        int eventId = createEvent();
        service.setRestrictions(eventId, List.of(), List.of(), List.of(tagId));
        assertFalse(service.isMemberEligible(eventId, EnumSet.of(Roles.MEMBER), List.of(), List.of()));
    }

    @Test
    void combinedRestrictionsRequireAllToMatch() {
        int eventId = createEvent();
        service.setRestrictions(eventId, List.of(memberRoleId), List.of(groupId), List.of(tagId));

        // Has role + group + tag => eligible
        assertTrue(service.isMemberEligible(
                eventId, EnumSet.of(Roles.MEMBER, Roles.USER), List.of(groupId), List.of(tagId)));

        // Has role + group but NOT tag => not eligible
        assertFalse(
                service.isMemberEligible(eventId, EnumSet.of(Roles.MEMBER, Roles.USER), List.of(groupId), List.of()));

        // Has role + tag but NOT group => not eligible
        assertFalse(service.isMemberEligible(eventId, EnumSet.of(Roles.MEMBER, Roles.USER), List.of(), List.of(tagId)));

        // Has group + tag but NOT role => not eligible
        assertFalse(service.isMemberEligible(eventId, EnumSet.of(Roles.GUARDIAN), List.of(groupId), List.of(tagId)));
    }

    @Test
    void partialRestrictionsUseAndLogic() {
        int eventId = createEvent();
        // Only role + tag restrictions (no group)
        service.setRestrictions(eventId, List.of(memberRoleId), List.of(), List.of(tagId));

        // Has role + tag => eligible (group not restricted)
        assertTrue(service.isMemberEligible(eventId, EnumSet.of(Roles.MEMBER, Roles.USER), List.of(), List.of(tagId)));

        // Has role but NOT tag => not eligible
        assertFalse(service.isMemberEligible(eventId, EnumSet.of(Roles.MEMBER, Roles.USER), List.of(), List.of()));
    }
}
