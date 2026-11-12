/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.members.entity.MemberCard;
import dev.chojo.ember.feature.members.entity.SharedMemberCard;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.service.StationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A person's cards seen from messages: one per station both belong to, each read with the rights the
 * reader holds there.
 */
class SharedMemberCardsTest {
    private static final int READER = 1;
    private static final int PERSON = 2;

    private StationMemberService memberService;
    private MemberPermissionResolver permissions;
    private StationService stationService;
    private MemberCardService cardService;
    private SharedMemberCards shared;

    private static StationMember member(int id, int stationId, int accountId, boolean former) {
        return new StationMember(
                id,
                stationId,
                UUID.fromString("00000000-0000-0000-0000-%012d".formatted(id)),
                accountId,
                former,
                null,
                "Name " + id,
                StationUserType.MEMBER,
                LocalDate.of(2020, 1, 1));
    }

    private void station(int id, String name) {
        var station = mock(Station.class);
        when(station.id()).thenReturn(id);
        when(station.uid()).thenReturn(UUID.fromString("00000000-0000-0000-0000-%012d".formatted(900 + id)));
        when(station.name()).thenReturn(name);
        when(stationService.findById(id)).thenReturn(Optional.of(station));
    }

    private void cardOf(StationMember person) {
        var card = new MemberCard(
                new MemberIdentity(UUID.randomUUID(), person.uid()),
                person.displayName(),
                false,
                List.of(),
                List.of(),
                List.of(),
                List.of());
        when(cardService.find(eq(person.stationId()), any(), eq(person.uid()), anyBoolean()))
                .thenReturn(Optional.of(card));
    }

    @BeforeEach
    void setup() {
        memberService = mock(StationMemberService.class);
        permissions = mock(MemberPermissionResolver.class);
        stationService = mock(StationService.class);
        cardService = mock(MemberCardService.class);
        shared = new SharedMemberCards(memberService, permissions, stationService, cardService);
        when(permissions.resolve(any(StationMember.class))).thenReturn(EnumSet.noneOf(StationPermission.class));
        station(10, "Süd");
        station(11, "Nord");
        station(12, "West");
    }

    @Test
    void onlyStationsBothCurrentlyBelongToGiveACardOrderedByName() {
        var readerSouth = member(100, 10, READER, false);
        var readerNorth = member(101, 11, READER, false);
        var readerWest = member(102, 12, READER, false);
        var personSouth = member(200, 10, PERSON, false);
        var personNorth = member(201, 11, PERSON, false);
        var personWestFormer = member(202, 12, PERSON, true);
        when(memberService.findByAccount(READER)).thenReturn(List.of(readerSouth, readerNorth, readerWest));
        when(memberService.findByAccount(PERSON)).thenReturn(List.of(personSouth, personNorth, personWestFormer));
        cardOf(personSouth);
        cardOf(personNorth);

        List<SharedMemberCard> cards = shared.between(READER, PERSON);

        assertEquals(
                List.of("Nord", "Süd"),
                cards.stream().map(SharedMemberCard::stationName).toList());
        verify(cardService, never()).find(eq(12), any(), eq(personWestFormer.uid()), anyBoolean());
    }

    @Test
    void privateTagsFollowTheReadersRightAtEachStation() {
        var readerSouth = member(100, 10, READER, false);
        var readerNorth = member(101, 11, READER, false);
        var personSouth = member(200, 10, PERSON, false);
        var personNorth = member(201, 11, PERSON, false);
        when(memberService.findByAccount(READER)).thenReturn(List.of(readerSouth, readerNorth));
        when(memberService.findByAccount(PERSON)).thenReturn(List.of(personSouth, personNorth));
        when(permissions.resolve(readerSouth)).thenReturn(EnumSet.of(StationPermission.MEMBER_READ));
        cardOf(personSouth);
        cardOf(personNorth);

        shared.between(READER, PERSON);

        verify(cardService).find(eq(10), any(), eq(personSouth.uid()), eq(true));
        verify(cardService).find(eq(11), any(), eq(personNorth.uid()), eq(false));
    }

    @Test
    void peopleWithoutASharedStationHaveNoCards() {
        when(memberService.findByAccount(READER)).thenReturn(List.of(member(100, 10, READER, false)));
        when(memberService.findByAccount(PERSON)).thenReturn(List.of(member(201, 11, PERSON, false)));

        assertTrue(shared.between(READER, PERSON).isEmpty());
    }
}
