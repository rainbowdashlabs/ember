/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.media.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.refusal.MediaLibraryRefusal;
import dev.chojo.ember.feature.media.entity.MediaContent;
import dev.chojo.ember.feature.media.service.PublicMediaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.header;
import static dev.chojo.ember.api.RouteHarness.refusalOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PublicMediaRoutesTest {
    private PublicMediaService media;
    private RouteHarness harness;

    @BeforeEach
    void setup() {
        media = mock(PublicMediaService.class);
        harness = RouteHarness.serving(new PublicMediaRoutes(media));
    }

    @Test
    void aStationsPictureIsServedInlineFromTheStationTheAddressNames() {
        when(media.resolveStation("wache-nord")).thenReturn(4);
        when(media.read(eq(4), eq("abc"), eq("320"), any()))
                .thenReturn(new MediaContent(new byte[] {1, 2, 3}, "image/webp"));

        harness.run((server, client) -> {
            var served = client.get(PREFIX + "/public/media/wache-nord/abc?w=320");
            assertEquals(200, served.code());
            assertTrue(header(served, "Content-Type").startsWith("image/webp"));
            assertTrue(header(served, "Content-Disposition").startsWith("inline"));
            assertEquals("Accept", header(served, "Vary"));
            assertEquals(
                    200,
                    client.get(PREFIX + "/public/pages/wache-nord/files/abc?w=320")
                            .code());
        });
    }

    @Test
    void aFileThatIsNotSafeToShowIsServedAsADownload() {
        when(media.readInstance(eq("abc"), isNull(), any()))
                .thenReturn(new MediaContent("<svg/>".getBytes(), "image/svg+xml"));

        harness.run((server, client) -> {
            var served = client.get(PREFIX + "/public/media/instance/abc");
            assertTrue(header(served, "Content-Disposition").startsWith("attachment"));
        });
    }

    @Test
    void aStationThatIsNotHereIsRefused() {
        when(media.resolveStation("gone")).thenThrow(MediaLibraryRefusal.STATION_NOT_HERE_BEHIND_PUBLIC_FILE.raise());

        harness.run((server, client) -> assertEquals(
                MediaLibraryRefusal.STATION_NOT_HERE_BEHIND_PUBLIC_FILE,
                refusalOf(client.get(PREFIX + "/public/media/gone/abc"))));
    }
}
