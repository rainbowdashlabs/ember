/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.transport;

import dev.chojo.ember.api.RefusalResponse;
import dev.chojo.ember.feature.federation.contract.FederationEndpoint;
import dev.chojo.ember.feature.federation.contract.FederationRequest;
import dev.chojo.ember.feature.federation.contract.FederationSurface;
import io.javalin.http.Context;
import org.junit.jupiter.api.Test;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PathParamsTest {

    private static final FederationEndpoint ENDPOINT =
            FederationEndpoint.get(FederationSurface.NEWS_SHARE, "/remote/things/{station}/{id}/{word}", String.class);

    @Test
    void aRequestBuiltHereReadsAsTheRouteReadsIt() {
        UUID station = UUID.randomUUID();
        var request = ENDPOINT.at(station, 7, URLEncoder.encode("Funk Gerät", StandardCharsets.UTF_8))
                .query("since", "2026-01-01T00:00:00Z")
                .query("flag", "a&b");

        var params = PathParams.of(request);

        assertEquals(station, params.uuid("station"));
        assertEquals(7, params.integer("id"));
        assertEquals("Funk Gerät", params.text("word"));
        assertEquals(Optional.of("2026-01-01T00:00:00Z"), params.query("since"));
        assertEquals(Optional.of("a&b"), params.query("flag"));
        assertEquals(Optional.empty(), params.query("missing"));

        var ctx = mock(Context.class);
        when(ctx.pathParam("station")).thenReturn(station.toString());
        when(ctx.pathParam("id")).thenReturn("7");
        when(ctx.pathParam("word")).thenReturn(URLEncoder.encode("Funk Gerät", StandardCharsets.UTF_8));
        when(ctx.queryParamMap())
                .thenReturn(
                        Map.of("since", List.of("2026-01-01T00:00:00Z"), "flag", List.of("a&b"), "none", List.of()));
        assertEquals(params, PathParams.of(ctx, ENDPOINT));
    }

    @Test
    void anAddressThatNamesNothingIsRefused() {
        var params = new PathParams(Map.of("id", "seven", "station", "not-a-uuid"), Map.of());
        assertThrows(RefusalResponse.class, () -> params.integer("id"));
        assertThrows(RefusalResponse.class, () -> params.uuid("station"));
        assertThrows(IllegalArgumentException.class, () -> params.text("absent"));
    }

    @Test
    void aQueryWithoutAValueReadsAsEmptyText() {
        var request = FederationEndpoint.get(FederationSurface.NEWS_SHARE, "/remote/list", String.class)
                .at()
                .query("a", "1");
        var bare = new FederationRequest(request.endpoint(), request.path() + "&flag&&b=2");
        var params = PathParams.of(bare);
        assertEquals(Optional.of(""), params.query("flag"));
        assertEquals(Optional.of("2"), params.query("b"));
        assertEquals(PathParams.NONE.path(), params.path());
    }
}
