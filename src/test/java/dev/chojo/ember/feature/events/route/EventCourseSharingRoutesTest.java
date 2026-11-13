/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.events.entity.EventCourseShare;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.service.EventCoursePartnerService;
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

class EventCourseSharingRoutesTest {
    private static final int STATION_ID = 3;
    private static final int COURSE_ID = 70;
    private static final String COURSE = PREFIX + "/events/courses/" + COURSE_ID;

    private EventCoursePartnerService partners;
    private RouteHarness harness;

    @BeforeEach
    void serve() {
        partners = mock(EventCoursePartnerService.class);
        when(partners.findShare(STATION_ID, COURSE_ID))
                .thenReturn(Optional.of(new EventCourseShare(COURSE_ID, ShareScope.SPECIFIC, List.of(4))));
        when(partners.signUps(STATION_ID, COURSE_ID)).thenReturn(List.of());
        harness = RouteHarness.serving(new EventCourseSharingRoutes(partners));
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
    void onlyWhoeverSharesAppointmentsSharesACourse() {
        String share = "{\"scope\": \"SPECIFIC\", \"partnerIds\": [4]}";
        assertEquals(
                403,
                call("PUT", COURSE + "/federation", share, StationPermission.EVENT_EDIT)
                        .code());
        verify(partners, never()).share(anyInt(), anyInt(), any(), anyList());

        assertEquals(
                200,
                call(
                                "PUT",
                                COURSE + "/federation",
                                share,
                                StationPermission.EVENT_EDIT,
                                StationPermission.EVENTS_FEDERATE)
                        .code());
        verify(partners).share(STATION_ID, COURSE_ID, ShareScope.SPECIFIC, List.of(4));

        var read = json(call("GET", COURSE + "/federation", null, StationPermission.EVENTS_FEDERATE));
        assertEquals("SPECIFIC", read.get("scope").asString());
        assertEquals(
                204,
                call("DELETE", COURSE + "/federation", null, StationPermission.EVENTS_FEDERATE)
                        .code());
        verify(partners).unshare(STATION_ID, COURSE_ID);
    }

    @Test
    void onlyWhoeverHandlesRegistrationsSeesAndDecidesPartnerSignUps() {
        assertEquals(
                403,
                call("GET", COURSE + "/partner-sign-ups", null, StationPermission.USER)
                        .code());
        assertEquals(
                200,
                call("GET", COURSE + "/partner-sign-ups", null, StationPermission.EVENT_REGISTRATION)
                        .code());

        String accept = "{\"status\": \"ACCEPTED\"}";
        assertEquals(
                204,
                call("PUT", COURSE + "/partner-sign-ups/9/status", accept, StationPermission.EVENT_REGISTRATION)
                        .code());
        verify(partners).decide(STATION_ID, COURSE_ID, 9, RegistrationStatus.ACCEPTED);
    }
}
