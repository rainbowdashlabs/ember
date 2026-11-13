/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.EventRefusal;
import dev.chojo.ember.feature.events.entity.EventGroup;
import dev.chojo.ember.feature.events.service.EventGroupService;
import dev.chojo.ember.feature.generator.entity.RequirementSignatureState;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import dev.chojo.ember.feature.signing.entity.AgreementSigner;
import dev.chojo.ember.feature.signing.service.EventGroupAgreementSigners;
import io.javalin.testtools.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.json;
import static dev.chojo.ember.api.RouteHarness.refusalOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Who signed an event group's documents over HTTP: whoever handles registrations reads it, for an event
 * group of their own station only.
 */
class EventGroupAgreementRoutesTest {
    private static final int STATION_ID = 3;
    private static final String SIGNERS = PREFIX + "/events/groups/%d/agreement-signers";

    private EventGroupAgreementSigners signers;
    private RouteHarness harness;

    @BeforeEach
    void serve() {
        var eventGroups = mock(EventGroupService.class);
        when(eventGroups.requireEventGroup(STATION_ID, 70))
                .thenReturn(new EventGroup(
                        70,
                        STATION_ID,
                        "Jugendflamme 1",
                        null,
                        null,
                        null,
                        false,
                        RestrictionMode.AND,
                        RestrictionMode.AND,
                        false,
                        null,
                        null,
                        null,
                        null,
                        null));
        when(eventGroups.requireEventGroup(STATION_ID, 71)).thenThrow(EventRefusal.EVENT_GROUP_NOT_HERE.raise());
        signers = mock(EventGroupAgreementSigners.class);
        when(signers.of(70))
                .thenReturn(List.of(new AgreementSigner(
                        11,
                        null,
                        "Lena Schmidt",
                        8,
                        "Einverständnis",
                        RequirementSignatureState.SIGNED,
                        Instant.parse("2026-10-07T10:00:00Z"),
                        null,
                        false)));
        harness = RouteHarness.serving(new EventGroupAgreementRoutes(eventGroups, signers));
    }

    private Response get(int eventGroupId, StationPermission... permissions) {
        return harness.request(client ->
                client.get(SIGNERS.formatted(eventGroupId), harness.as(TestSessions.member(STATION_ID, permissions))));
    }

    @Test
    void whoeverHandlesRegistrationsReadsTheSigners() {
        var listed = json(get(70, StationPermission.EVENT_REGISTRATION));

        assertEquals(1, listed.size());
        assertEquals("Lena Schmidt", listed.get(0).path("name").asString());
    }

    @Test
    void nobodyElseReadsThemAndAnotherStationsEventGroupStaysAbsent() {
        assertEquals(403, get(70, StationPermission.EVENT_EDIT).code());
        assertEquals(EventRefusal.EVENT_GROUP_NOT_HERE, refusalOf(get(71, StationPermission.EVENT_REGISTRATION)));
        verify(signers, never()).of(anyInt());
    }
}
