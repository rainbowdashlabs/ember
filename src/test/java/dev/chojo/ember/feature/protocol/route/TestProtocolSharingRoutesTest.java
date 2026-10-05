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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Sharing a protocol with the station's federation partners, over HTTP.
 */
class TestProtocolSharingRoutesTest {
    private static final int STATION = 3;
    private static final TestProtocol PROTOCOL = new TestProtocol(1, STATION, "Knoten", "", 70, null, null);

    private TestProtocolService protocols;
    private RouteHarness harness;

    @BeforeEach
    void setup() {
        protocols = mock(TestProtocolService.class);
        when(protocols.findProtocol(1)).thenReturn(Optional.of(PROTOCOL));
        when(protocols.findSharedProtocolIds(STATION)).thenReturn(List.of(1));
        harness = RouteHarness.serving(new TestProtocolRoutes(
                protocols,
                new TestProtocolGuards(protocols),
                mock(TestProtocolPdfService.class),
                mock(TestProtocolRunService.class),
                mock(TestProtocolEvaluationService.class),
                mock(TestProtocolExaminerService.class)));
    }

    @Test
    void aSharerSharesAProtocolOfTheStation() {
        harness.run((server, client) -> {
            var sharer = harness.as(TestSessions.member(STATION, StationPermission.PROTOCOL_SHARE));
            assertEquals(
                    204,
                    client.put(PREFIX + "/protocols/1/sharing", body("{\"shared\": true}"), sharer)
                            .code());
            var listed = client.get(PREFIX + "/protocols/sharing", sharer);
            assertEquals(200, listed.code());
            assertTrue(listed.body().string().contains("\"protocolIds\":[1]"));
        });

        verify(protocols).setShared(STATION, 1, true);
    }

    @Test
    void sharingNeedsTheRightToShare() {
        harness.run((server, client) -> {
            var configurer = harness.as(TestSessions.member(STATION, StationPermission.PROTOCOL_CONFIGURE));
            assertEquals(
                    403,
                    client.put(PREFIX + "/protocols/1/sharing", body("{\"shared\": true}"), configurer)
                            .code());
        });

        verify(protocols, never()).setShared(anyInt(), anyInt(), anyBoolean());
    }

    @Test
    void aProtocolOfAnotherStationIsNotShared() {
        harness.run((server, client) -> {
            var foreign = harness.as(TestSessions.member(9, StationPermission.PROTOCOL_SHARE));
            assertEquals(
                    404,
                    client.put(PREFIX + "/protocols/1/sharing", body("{\"shared\": true}"), foreign)
                            .code());
        });

        verify(protocols, never()).setShared(anyInt(), anyInt(), anyBoolean());
    }
}
