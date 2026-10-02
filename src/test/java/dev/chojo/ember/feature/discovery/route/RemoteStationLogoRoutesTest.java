/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.discovery.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.refusal.DiscoveryRefusal;
import dev.chojo.ember.api.refusal.GeneralRefusal;
import dev.chojo.ember.feature.discovery.service.RemoteStationLogoService;
import dev.chojo.ember.feature.media.entity.MediaContent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.header;
import static dev.chojo.ember.api.RouteHarness.refusalOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The copy of a remote station's logo, served without signing in, cached like the logos of this
 * instance's own stations.
 */
class RemoteStationLogoRoutesTest {
    private static final UUID STATION = UUID.fromString("7f0c1f5e-3f4c-4b6f-9a51-0d1e2f3a4b5c");
    private static final String FINGERPRINT = "0123456789abcdef0123456789abcdef";
    private static final String LOGO = PREFIX + "/public/discovery/remote/" + FINGERPRINT + "/" + STATION + "/logo";
    private static final byte[] PNG = "a kept copy".getBytes(StandardCharsets.UTF_8);

    private RemoteStationLogoService logos;
    private RouteHarness harness;

    @BeforeEach
    void setup() {
        logos = mock(RemoteStationLogoService.class);
        when(logos.read(any(), any(), anyInt())).thenReturn(Optional.empty());
        harness = RouteHarness.serving(new RemoteStationLogoRoutes(logos));
    }

    @Test
    void theKeptCopyIsServedPubliclyCacheableAtTheSizeAskedFor() {
        when(logos.read(FINGERPRINT, STATION, 128)).thenReturn(Optional.of(new MediaContent(PNG, "image/png")));

        var response = harness.request(client -> client.get(LOGO + "?size=128"));

        assertEquals(200, response.code());
        assertTrue(header(response, "Content-Type").startsWith("image/png"));
        assertEquals("public, max-age=3600", header(response, "Cache-Control"));
        assertNotNull(header(response, "ETag"));
        assertEquals(new String(PNG, StandardCharsets.UTF_8), response.body().string());
    }

    @Test
    void aStationWithoutAKeptCopyIsNotFound() {
        var response = harness.request(client -> client.get(LOGO));

        assertEquals(404, response.code());
        assertEquals(DiscoveryRefusal.REMOTE_LOGO_NOT_HERE, refusalOf(response));
    }

    @Test
    void anAddressNamingNoStationIsRefused() {
        var response = harness.request(
                client -> client.get(PREFIX + "/public/discovery/remote/" + FINGERPRINT + "/nope/logo"));

        assertEquals(GeneralRefusal.ADDRESS_NOT_AN_IDENTIFIER, refusalOf(response));
    }
}
