/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.api.refusal.EventRefusal;
import dev.chojo.ember.api.refusal.GeneralRefusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.members.service.MemberIdentityFactory;
import dev.chojo.ember.feature.members.service.MemberNameResolver;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The station check every event route goes through, and the reads a registration list shares.
 */
class EventGuardsTest {

    @Test
    void anAppointmentIsOnlyEverTheStationsOwn() {
        var crud = mock(EventCrudService.class);
        var own = mock(StationEvent.class);
        when(own.stationId()).thenReturn(3);
        var foreign = mock(StationEvent.class);
        when(foreign.stationId()).thenReturn(4);
        when(crud.findById(1)).thenReturn(Optional.of(own));
        when(crud.findById(2)).thenReturn(Optional.of(foreign));
        when(crud.findById(3)).thenReturn(Optional.empty());
        var session = StationSession.of(TestSessions.member(3));

        assertSame(own, EventOwnership.requireOwnedEvent(crud, 1, session));
        assertEquals(
                GeneralRefusal.NOT_YOURS_TO_OPEN,
                assertThrows(RefusalResponse.class, () -> EventOwnership.requireOwnedEvent(crud, 2, session))
                        .refusal());
        assertEquals(
                EventRefusal.EVENT_NOT_HERE,
                assertThrows(RefusalResponse.class, () -> EventOwnership.requireOwnedEvent(crud, 3, session))
                        .refusal());
    }

    @Test
    void aListReadsItsMembersOnceAndEachAppointmentNameOnce() {
        var members = mock(StationMemberRepository.class);
        var crud = mock(EventCrudService.class);
        var names = mock(MemberNameResolver.class);
        var member = new StationMember(11, 3, UUID.randomUUID(), 1, false, null, "Mara", StationUserType.MEMBER, null);
        when(members.findByIds(List.of(11, 12))).thenReturn(List.of(member));
        when(names.called(11)).thenReturn("Mara");
        var event = mock(StationEvent.class);
        when(event.name()).thenReturn("Übung");
        when(crud.findById(9)).thenReturn(Optional.of(event));
        var reader = new RegistrationRowLookups.Reader(
                members, crud, mock(EventFieldService.class), names, mock(MemberIdentityFactory.class));

        var lookups = reader.forMembers(List.of(11, 12, 11));

        assertEquals("Mara", lookups.member(11).name());
        assertEquals("", lookups.member(12).name());
        assertNull(lookups.member(12).identity());
        assertEquals("Übung", lookups.eventName(9));
        assertEquals("Übung", lookups.eventName(9));
        assertNull(lookups.createdByName(null));
        verify(crud, times(1)).findById(9);
    }
}
