/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.route;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.RefusalResponse;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.service.EventCrudService;
import dev.chojo.ember.feature.events.service.EventRestrictionService;
import dev.chojo.ember.feature.members.service.StationMemberService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

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
    private StationMemberService stationMemberService;
    private EventVisibility visibility;
    private StationEvent event;

    @BeforeEach
    void setup() {
        crudService = mock(EventCrudService.class);
        restrictionService = mock(EventRestrictionService.class);
        stationMemberService = mock(StationMemberService.class);
        visibility = new EventVisibility(crudService, restrictionService, stationMemberService);
        event = mock(StationEvent.class);
        when(event.id()).thenReturn(EVENT_ID);
        when(event.stationId()).thenReturn(STATION_ID);
        when(crudService.findById(EVENT_ID)).thenReturn(Optional.of(event));
    }

    private UserSession sessionWith(Set<StationPermission> permissions, int stationId) {
        var session = mock(UserSession.class);
        when(session.stationId()).thenReturn(stationId);
        when(session.permissions()).thenReturn(permissions);
        when(stationMemberService.findSpokenForIds(session)).thenReturn(List.of(MEMBER_ID, WARD_ID));
        return session;
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

        assertThrows(Exception.class, () -> visibility.requireVisibleEvent(session, EVENT_ID + 1));
    }
}
