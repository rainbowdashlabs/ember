/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.EventRefusal;
import dev.chojo.ember.feature.events.entity.EventGroup;
import dev.chojo.ember.feature.events.service.EventGroupGuards;
import dev.chojo.ember.feature.events.service.EventGroupService;
import dev.chojo.ember.feature.generator.service.EventRequirementService;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import io.javalin.testtools.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.body;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EventGroupRequirementRoutesTest {
    private static final int STATION_ID = 3;
    private static final int EVENT_GROUP_ID = 70;
    private static final String REQUIREMENTS = PREFIX + "/events/groups/" + EVENT_GROUP_ID + "/document-requirements";
    private static final EventGroup GROUP = new EventGroup(
            EVENT_GROUP_ID,
            STATION_ID,
            "Jugendflamme",
            null,
            null,
            null,
            false,
            RestrictionMode.AND,
            RestrictionMode.AND,
            false,
            null,
            null,
            null);

    private EventRequirementService requirements;
    private EventGroupGuards readGuards;
    private RouteHarness harness;

    @BeforeEach
    void serve() {
        requirements = mock(EventRequirementService.class);
        readGuards = mock(EventGroupGuards.class);
        var eventGroups = mock(EventGroupService.class);
        when(eventGroups.requireEventGroup(STATION_ID, EVENT_GROUP_ID)).thenReturn(GROUP);
        when(readGuards.requireOpen(any(), anyInt())).thenReturn(GROUP);
        when(requirements.forEventGroup(EVENT_GROUP_ID)).thenReturn(List.of());
        when(requirements.setForEventGroup(any(), anyInt(), anyList())).thenReturn(List.of());
        harness = RouteHarness.serving(new EventGroupRequirementRoutes(requirements, eventGroups, readGuards));
    }

    private Response call(String method, String json, StationPermission... permissions) {
        var session = harness.as(TestSessions.member(STATION_ID, permissions));
        return harness.request(client -> switch (method) {
            case "GET" -> client.get(REQUIREMENTS, session);
            default -> client.put(REQUIREMENTS, body(json), session);
        });
    }

    @Test
    void membersReadWhatAGroupTheyMayOpenAsksFor() {
        assertEquals(200, call("GET", null, StationPermission.USER).code());

        when(readGuards.requireOpen(any(), anyInt())).thenThrow(EventRefusal.EVENT_GROUP_NOT_HERE.raise());
        assertEquals(404, call("GET", null, StationPermission.USER).code());
    }

    @Test
    void onlyWhoeverEditsAppointmentsSetsIt() {
        String chosen = "{\"templateIds\": [4, 5]}";
        assertEquals(403, call("PUT", chosen, StationPermission.USER).code());
        verify(requirements, never()).setForEventGroup(any(), anyInt(), anyList());

        assertEquals(200, call("PUT", chosen, StationPermission.EVENT_EDIT).code());
        verify(requirements).setForEventGroup(any(), anyInt(), anyList());
    }
}
