/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api;

import dev.chojo.ember.util.Sha256;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.HandlerType;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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

    private static final String BODY = "{\"version\":\"26.13.7\"}";

    private static Map<String, String> headersFor(String path) throws Exception {
        Map<String, String> written = new HashMap<>();
        Context ctx = mock(Context.class);
        when(ctx.method()).thenReturn(HandlerType.GET);
        when(ctx.statusCode()).thenReturn(200);
        when(ctx.path()).thenReturn(path);
        when(ctx.result()).thenReturn(BODY);
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

    /**
     * A waiting-list entry behind its own link carries a name, an address and the appointment somebody
     * was invited to, and it changes the moment they answer. Held for an hour it was both a copy of
     * a family's details sitting in a cache nobody owns and an answer the page could not see it had
     * given.
     */
    @Test
    void anEntryBehindItsOwnLinkIsKeptNowhere() throws Exception {
        var headers = headersFor("/api/v1/public/waiting-list/entry/some-token");

        assertEquals("private, no-store", headers.get("Cache-Control"));
        assertEquals(false, headers.containsKey("ETag"), "there is nothing to revalidate against");
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

    @Test
    void theTagIsTheStartOfTheBodysSha256() throws Exception {
        assertEquals(
                "\"" + Sha256.hexPrefix(BODY, 16) + "\"",
                headersFor("/api/v1/news").get("ETag"));
    }

    /**
     * The tag is written by the cache headers and the {@code 304} is Javalin's: a browser that sends
     * the tag back gets an empty answer, and one that sends another gets the body.
     */
    @Test
    void aTagSentBackIsAnsweredWithNotModified() throws Exception {
        Method apply = ApiServer.class.getDeclaredMethod("applyCacheHeaders", Context.class);
        apply.setAccessible(true);
        Javalin app = Javalin.create(config -> {
                    config.routes.get("/api/v1/news", ctx -> ctx.result(BODY));
                    config.routes.after(ctx -> apply.invoke(null, ctx));
                })
                .start(0);
        try (HttpClient client = HttpClient.newHttpClient()) {
            URI uri = URI.create("http://localhost:" + app.port() + "/api/v1/news");
            var first = client.send(HttpRequest.newBuilder(uri).build(), HttpResponse.BodyHandlers.ofString());
            String tag = first.headers().firstValue("ETag").orElseThrow();

            var repeated = client.send(
                    HttpRequest.newBuilder(uri).header("If-None-Match", tag).build(),
                    HttpResponse.BodyHandlers.ofString());
            var stale = client.send(
                    HttpRequest.newBuilder(uri)
                            .header("If-None-Match", "\"other\"")
                            .build(),
                    HttpResponse.BodyHandlers.ofString());

            assertEquals(BODY, first.body());
            assertEquals(304, repeated.statusCode());
            assertEquals("", repeated.body());
            assertEquals(200, stale.statusCode());
            assertEquals(BODY, stale.body());
        } finally {
            app.stop();
        }
    }
}
