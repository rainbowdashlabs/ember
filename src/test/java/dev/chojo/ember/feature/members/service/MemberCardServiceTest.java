/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.members.entity.MemberCard.MemberCardLabel;
import dev.chojo.ember.feature.members.entity.MemberGroup;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.entity.UserTag;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The card somebody at a station sees when looking at a member's name. */
class MemberCardServiceTest {
    private static final int STATION_ID = 3;
    private static final UUID STATION_UID = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID MEMBER_UID = UUID.fromString("00000000-0000-0000-0000-000000000020");

    private StationMemberService memberService;
    private MemberNameResolver nameResolver;
    private UserTagService tagService;
    private MemberGroupService groupService;
    private MemberCardService cards;

    private static StationMember member(int id, int stationId, boolean former) {
        return new StationMember(
                id,
                stationId,
                UUID.fromString("00000000-0000-0000-0000-%012d".formatted(id)),
                id,
                former,
                null,
                "Frozen Name " + id,
                StationUserType.MEMBER,
                LocalDate.of(2020, 1, 1));
    }

    @BeforeEach
    void setup() {
        memberService = mock(StationMemberService.class);
        nameResolver = mock(MemberNameResolver.class);
        tagService = mock(UserTagService.class);
        groupService = mock(MemberGroupService.class);
        var identityFactory = mock(MemberIdentityFactory.class);
        when(identityFactory.enrich(any())).thenAnswer(call -> call.getArgument(0));
        cards = new MemberCardService(memberService, nameResolver, identityFactory, tagService, groupService);
        when(memberService.resolveId(STATION_ID, MEMBER_UID)).thenReturn(Optional.of(20));
    }

    @Test
    void aCardNamesTheMemberAndEverythingAroundThem() {
        when(memberService.findById(20)).thenReturn(Optional.of(member(20, STATION_ID, false)));
        when(nameResolver.identified(20)).thenReturn("Maximilian \"Max\" Hoffmann");
        when(memberService.findManagers(20)).thenReturn(List.of(member(21, STATION_ID, false)));
        when(memberService.findManaged(20)).thenReturn(List.of(member(22, STATION_ID, false)));
        when(tagService.findTagsForMember(20))
                .thenReturn(List.of(
                        new UserTag(1, STATION_ID, "Driver", null, false, 1),
                        new UserTag(2, STATION_ID, "Medic", "#ff0000", true, 5)));
        when(groupService.findGroupsForMember(20))
                .thenReturn(List.of(
                        new MemberGroup(1, STATION_ID, "Youth", "#00ff00", 2, null, List.of()),
                        new MemberGroup(2, STATION_ID, "Board", null, 9, null, List.of())));

        var card = cards.find(STATION_ID, STATION_UID, MEMBER_UID).orElseThrow();

        assertEquals(new MemberIdentity(STATION_UID, MEMBER_UID), card.identity());
        assertEquals("Maximilian \"Max\" Hoffmann", card.name());
        assertFalse(card.former());
        assertEquals(
                List.of(new MemberIdentity(
                        STATION_UID, member(21, STATION_ID, false).uid())),
                card.parents());
        assertEquals(
                List.of(new MemberIdentity(
                        STATION_UID, member(22, STATION_ID, false).uid())),
                card.children());
        assertEquals(
                List.of(new MemberCardLabel("Medic", "#ff0000"), new MemberCardLabel("Driver", null)), card.tags());
        assertEquals(
                List.of(new MemberCardLabel("Board", null), new MemberCardLabel("Youth", "#00ff00")), card.groups());
    }

    /** Their relations ended when they left, so a former member's card names them and nothing more. */
    @Test
    void aFormerMembersCardCarriesOnlyTheirName() {
        when(memberService.findById(20)).thenReturn(Optional.of(member(20, STATION_ID, true)));
        when(nameResolver.identified(20)).thenReturn("Frozen Name 20");

        var card = cards.find(STATION_ID, STATION_UID, MEMBER_UID).orElseThrow();

        assertTrue(card.former());
        assertEquals("Frozen Name 20", card.name());
        assertTrue(card.parents().isEmpty());
        assertTrue(card.children().isEmpty());
        assertTrue(card.tags().isEmpty());
        assertTrue(card.groups().isEmpty());
    }

    @Test
    void aMemberWithoutAResolvedNameIsCalledByTheNameStoredWithThem() {
        when(memberService.findById(20)).thenReturn(Optional.of(member(20, STATION_ID, true)));

        assertEquals(
                "Frozen Name 20",
                cards.find(STATION_ID, STATION_UID, MEMBER_UID).orElseThrow().name());
    }

    @Test
    void aUidNamingNobodyHereHasNoCard() {
        assertTrue(cards.find(STATION_ID, STATION_UID, UUID.randomUUID()).isEmpty());
    }

    @Test
    void aMemberOfAnotherStationHasNoCardHere() {
        when(memberService.findById(20)).thenReturn(Optional.of(member(20, 99, false)));

        assertTrue(cards.find(STATION_ID, STATION_UID, MEMBER_UID).isEmpty());
    }
}
