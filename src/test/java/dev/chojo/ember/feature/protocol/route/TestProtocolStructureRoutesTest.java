/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.protocol.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.protocol.entity.TestProtocol;
import dev.chojo.ember.feature.protocol.service.TestProtocolEvaluationService;
import dev.chojo.ember.feature.protocol.service.TestProtocolExaminerService;
import dev.chojo.ember.feature.protocol.service.TestProtocolGuards;
import dev.chojo.ember.feature.protocol.service.TestProtocolPdfService;
import dev.chojo.ember.feature.protocol.service.TestProtocolRunService;
import dev.chojo.ember.feature.protocol.service.TestProtocolService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.body;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Changing and sorting the sections and points of a protocol, over HTTP.
 */
class TestProtocolStructureRoutesTest {
    private static final int STATION = 3;
    private static final TestProtocol PROTOCOL = new TestProtocol(1, STATION, "Knoten", "", 70, null, null);

    private TestProtocolService protocols;
    private RouteHarness harness;

    @BeforeEach
    void setup() {
        protocols = mock(TestProtocolService.class);
        when(protocols.findProtocol(1)).thenReturn(Optional.of(PROTOCOL));
        when(protocols.findSectionStation(2)).thenReturn(Optional.of(STATION));
        when(protocols.findItemStation(7)).thenReturn(Optional.of(STATION));
        harness = RouteHarness.serving(new TestProtocolRoutes(
                protocols,
                new TestProtocolGuards(protocols),
                mock(TestProtocolPdfService.class),
                mock(TestProtocolRunService.class),
                mock(TestProtocolEvaluationService.class),
                mock(TestProtocolExaminerService.class)));
    }

    @Test
    void theSectionsAndPointsOfAProtocolOfTheStationAreSorted() {
        harness.run((server, client) -> {
            var configurer = harness.as(TestSessions.member(STATION, StationPermission.PROTOCOL_CONFIGURE));
            assertEquals(
                    204,
                    client.put(PREFIX + "/protocols/1/sections/order", body("{\"ids\": [3, 2]}"), configurer)
                            .code());
            assertEquals(
                    204,
                    client.put(PREFIX + "/protocols/sections/2/items/order", body("{\"ids\": [8, 7]}"), configurer)
                            .code());
        });

        verify(protocols).reorderSections(1, List.of(3, 2));
        verify(protocols).reorderItems(2, List.of(8, 7));
    }

    @Test
    void aProtocolOfAnotherStationIsNotSorted() {
        harness.run((server, client) -> {
            var foreign = harness.as(TestSessions.member(9, StationPermission.PROTOCOL_CONFIGURE));
            assertEquals(
                    404,
                    client.put(PREFIX + "/protocols/1/sections/order", body("{\"ids\": [3, 2]}"), foreign)
                            .code());
        });

        verify(protocols, never()).reorderSections(anyInt(), any());
    }

    /** A change that names no place leaves the section or point where it was sorted to. */
    @Test
    void aChangeWithoutAPlaceKeepsThePlace() {
        harness.run((server, client) -> {
            var configurer = harness.as(TestSessions.member(STATION, StationPermission.PROTOCOL_CONFIGURE));
            assertEquals(
                    204,
                    client.put(PREFIX + "/protocols/sections/2", body("{\"name\": \"Leinen\"}"), configurer)
                            .code());
            assertEquals(
                    204,
                    client.put(PREFIX + "/protocols/items/7", body("{\"label\": \"Palstek\"}"), configurer)
                            .code());
        });

        verify(protocols).updateSection(2, "Leinen", "", null, null, null);
        verify(protocols).updateItem(7, "Palstek", "", 1.0, false, null);
    }
}
