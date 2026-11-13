/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.events.entity.EventGroupShare;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.service.EventGroupPartnerService;
import dev.chojo.ember.feature.federation.entity.ShareScope;
import io.javalin.testtools.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.body;
import static dev.chojo.ember.api.RouteHarness.json;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EventGroupSharingRoutesTest {
    private static final int STATION_ID = 3;
    private static final int EVENT_GROUP_ID = 70;
    private static final String EVENT_GROUP = PREFIX + "/events/groups/" + EVENT_GROUP_ID;

    private EventGroupPartnerService partners;
    private RouteHarness harness;

    @BeforeEach
    void serve() {
        partners = mock(EventGroupPartnerService.class);
        when(partners.findShare(STATION_ID, EVENT_GROUP_ID))
                .thenReturn(Optional.of(new EventGroupShare(EVENT_GROUP_ID, ShareScope.SPECIFIC, List.of(4))));
        when(partners.signUps(STATION_ID, EVENT_GROUP_ID)).thenReturn(List.of());
        harness = RouteHarness.serving(new EventGroupSharingRoutes(partners));
    }

    private Response call(String method, String path, String json, StationPermission... permissions) {
        var session = harness.as(TestSessions.member(STATION_ID, permissions));
        return harness.request(client -> switch (method) {
            case "GET" -> client.get(path, session);
            case "PUT" -> client.put(path, body(json), session);
            default -> client.delete(path, null, session);
        });
    }

    @Test
    void onlyWhoeverSharesAppointmentsSharesAnEventGroup() {
        String share = "{\"scope\": \"SPECIFIC\", \"partnerIds\": [4]}";
        assertEquals(
                403,
                call("PUT", EVENT_GROUP + "/federation", share, StationPermission.EVENT_EDIT)
                        .code());
        verify(partners, never()).share(anyInt(), anyInt(), any(), anyList());

        assertEquals(
                200,
                call(
                                "PUT",
                                EVENT_GROUP + "/federation",
                                share,
                                StationPermission.EVENT_EDIT,
                                StationPermission.EVENTS_FEDERATE)
                        .code());
        verify(partners).share(STATION_ID, EVENT_GROUP_ID, ShareScope.SPECIFIC, List.of(4));

        var read = json(call("GET", EVENT_GROUP + "/federation", null, StationPermission.EVENTS_FEDERATE));
        assertEquals("SPECIFIC", read.get("scope").asString());
        assertEquals(
                204,
                call("DELETE", EVENT_GROUP + "/federation", null, StationPermission.EVENTS_FEDERATE)
                        .code());
        verify(partners).unshare(STATION_ID, EVENT_GROUP_ID);
    }

    @Test
    void onlyWhoeverHandlesRegistrationsSeesAndDecidesPartnerSignUps() {
        assertEquals(
                403,
                call("GET", EVENT_GROUP + "/partner-sign-ups", null, StationPermission.USER)
                        .code());
        assertEquals(
                200,
                call("GET", EVENT_GROUP + "/partner-sign-ups", null, StationPermission.EVENT_REGISTRATION)
                        .code());

        String accept = "{\"status\": \"ACCEPTED\"}";
        assertEquals(
                204,
                call("PUT", EVENT_GROUP + "/partner-sign-ups/9/status", accept, StationPermission.EVENT_REGISTRATION)
                        .code());
        verify(partners).decide(STATION_ID, EVENT_GROUP_ID, 9, RegistrationStatus.ACCEPTED);
    }
}
