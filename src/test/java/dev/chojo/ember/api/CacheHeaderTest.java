/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api;

import io.javalin.http.Context;
import io.javalin.http.HandlerType;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * How long a browser is allowed to keep an answer.
 *
 * <p>The one that matters here is the public configuration. It names the version that is running,
 * which is the single thing a deployment changes, and while it was held for an hour every
 * deployment looked as though it had not happened.
 */
class CacheHeaderTest {

    private static Map<String, String> headersFor(String path) throws Exception {
        Map<String, String> written = new HashMap<>();
        Context ctx = mock(Context.class);
        when(ctx.method()).thenReturn(HandlerType.GET);
        when(ctx.statusCode()).thenReturn(200);
        when(ctx.path()).thenReturn(path);
        when(ctx.result()).thenReturn("{\"version\":\"26.13.7\"}");
        when(ctx.header(any(String.class))).thenReturn(null);
        when(ctx.header(any(String.class), any(String.class))).thenAnswer(invocation -> {
            written.put(invocation.getArgument(0), invocation.getArgument(1));
            return ctx;
        });
        when(ctx.status(anyInt())).thenReturn(ctx);

        Method apply = ApiServer.class.getDeclaredMethod("applyCacheHeaders", Context.class);
        apply.setAccessible(true);
        apply.invoke(null, ctx);
        return written;
    }

    @Test
    void theRunningVersionIsAskedForEveryTime() throws Exception {
        var headers = headersFor("/api/v1/public/config");

        assertEquals("public, no-cache", headers.get("Cache-Control"));
        assertEquals(true, headers.containsKey("ETag"), "and the tag is written, so asking again is cheap");
    }

    @Test
    void everythingElseThatIsPublicIsStillHeldForAnHour() throws Exception {
        assertEquals(
                "public, max-age=3600",
                headersFor("/api/v1/public/kb/some-station/article").get("Cache-Control"));
    }

    @Test
    void aPageFileIsHeldForAYearBecauseItsNameCarriesItsContent() throws Exception {
        assertEquals(
                "public, max-age=31536000, immutable",
                headersFor("/api/v1/public/pages/files/abc123.png").get("Cache-Control"));
    }

    @Test
    void anythingBehindASessionIsRevalidated() throws Exception {
        assertEquals("private, no-cache", headersFor("/api/v1/news").get("Cache-Control"));
    }
}
