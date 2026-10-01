/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.route;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.RefusalResponse;
import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.service.EventCrudService;
import dev.chojo.ember.feature.events.service.EventRestrictionService;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.service.GuardianPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class EventVisibilityTest {

    private static final int STATION_ID = 7;
    private static final int EVENT_ID = 42;
    private static final int MEMBER_ID = 3;
    private static final int WARD_ID = 4;

    private EventCrudService crudService;
    private EventRestrictionService restrictionService;
    private GuardianPolicy guardianPolicy;
    private EventVisibility visibility;
    private StationEvent event;

    @BeforeEach
    void setup() {
        crudService = mock(EventCrudService.class);
        restrictionService = mock(EventRestrictionService.class);
        guardianPolicy = mock(GuardianPolicy.class);
        visibility = new EventVisibility(crudService, restrictionService, guardianPolicy);
        event = mock(StationEvent.class);
        when(event.id()).thenReturn(EVENT_ID);
        when(event.stationId()).thenReturn(STATION_ID);
        when(crudService.findById(EVENT_ID)).thenReturn(Optional.of(event));
    }

    private StationSession sessionWith(Set<StationPermission> permissions, int stationId) {
        var user = mock(UserSession.class);
        when(user.stationId()).thenReturn(stationId);
        when(user.permissions()).thenReturn(permissions);
        when(user.hasPermission(any(StationPermission.class)))
                .thenAnswer(call -> permissions.contains(call.<StationPermission>getArgument(0)));
        when(guardianPolicy.household(user)).thenReturn(List.of(MEMBER_ID, WARD_ID));
        return new StationSession(user, stationId, UUID.randomUUID(), mock(StationMember.class));
    }

    @Test
    void aMemberInTheAudienceReadsTheEvent() {
        var session = sessionWith(Set.of(StationPermission.USER), STATION_ID);
        when(restrictionService.canViewAny(eq(EVENT_ID), eq(List.of(MEMBER_ID, WARD_ID)), any()))
                .thenReturn(true);

        assertSame(event, visibility.requireVisibleEvent(session, EVENT_ID));
    }

    @Test
    void aMemberOutsideTheAudienceIsRefused() {
        var session = sessionWith(Set.of(StationPermission.USER), STATION_ID);
        when(restrictionService.canViewAny(anyInt(), any(), any())).thenReturn(false);

        var refusal = assertThrows(RefusalResponse.class, () -> visibility.requireVisibleEvent(session, EVENT_ID));
        assertEquals(Refusal.EVENT_NOT_YOURS_TO_SEE, refusal.refusal());
    }

    @Test
    void anEventEditorReadsAHiddenEvent() {
        var session = sessionWith(Set.of(StationPermission.USER, StationPermission.EVENT_EDIT), STATION_ID);

        assertSame(event, visibility.requireVisibleEvent(session, EVENT_ID));
        verifyNoInteractions(restrictionService);
    }

    @Test
    void anotherStationsEventIsRefusedBeforeItsAudienceIsAsked() {
        var session = sessionWith(Set.of(StationPermission.USER, StationPermission.EVENT_EDIT), STATION_ID + 1);

        assertThrows(RefusalResponse.class, () -> visibility.requireVisibleEvent(session, EVENT_ID));
        verifyNoInteractions(restrictionService);
    }

    @Test
    void aMissingEventIsNotFound() {
        var session = sessionWith(Set.of(StationPermission.USER), STATION_ID);

        var refusal = assertThrows(RefusalResponse.class, () -> visibility.requireVisibleEvent(session, EVENT_ID + 1));
        assertEquals(Refusal.EVENT_NOT_HERE, refusal.refusal());
    }

    private record Row(int eventId) {}

    @Test
    void aListingKeepsOnlyTheRowsOfEventsTheMemberSees() {
        var session = sessionWith(Set.of(StationPermission.USER), STATION_ID);
        var other = mock(StationEvent.class);
        when(other.id()).thenReturn(EVENT_ID + 1);
        when(crudService.findFilteredForMembers(STATION_ID, List.of(MEMBER_ID, WARD_ID), null, null))
                .thenReturn(List.of(event));

        var kept = visibility.keepVisible(session, List.of(new Row(EVENT_ID), new Row(EVENT_ID + 1)), Row::eventId);

        assertEquals(List.of(new Row(EVENT_ID)), kept);
    }

    @Test
    void anEventManagerKeepsEveryRow() {
        var session = sessionWith(Set.of(StationPermission.USER, StationPermission.EVENT_MANAGER), STATION_ID);
        var rows = List.of(new Row(EVENT_ID), new Row(EVENT_ID + 1));

        assertSame(rows, visibility.keepVisible(session, rows, Row::eventId));
        verifyNoInteractions(restrictionService);
    }
}
