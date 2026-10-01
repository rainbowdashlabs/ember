/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api;

import dev.chojo.ember.util.Sha256;
import io.javalin.testtools.Response;
import org.junit.jupiter.api.Test;

import static dev.chojo.ember.api.RouteHarness.header;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * How long a browser is allowed to keep an answer.
 *
 * <p>The one that matters here is the public configuration. It names the version that is running,
 * which is the single thing a deployment changes, and while it was held for an hour every
 * deployment looked as though it had not happened.
 */
class CacheHeaderTest {

    private static final String BODY = "{\"version\":\"26.13.7\"}";

    /** A route answering every address the cases ask about, so what is judged is the address alone. */
    private static final Routes ANSWERING = (routes, prefix) -> {
        routes.get(prefix + "/public/config", ctx -> ctx.json(new Version("26.13.7")));
        routes.get(prefix + "/public/kb/{station}/{article}", ctx -> ctx.json(new Version("26.13.7")));
        routes.get(prefix + "/public/waiting-list/entry/{token}", ctx -> ctx.json(new Version("26.13.7")));
        routes.get(prefix + "/public/pages/files/{file}", ctx -> ctx.result(new byte[] {1}));
        routes.get(prefix + "/news", ctx -> ctx.result(BODY));
    };

    private record Version(String version) {}

    private static Response get(String path) {
        return RouteHarness.serving(ANSWERING).request(client -> client.get(path));
    }

    @Test
    void theRunningVersionIsAskedForEveryTime() {
        var answer = get("/api/v1/public/config");

        assertEquals("public, no-cache", header(answer, "Cache-Control"));
        assertNotNull(header(answer, "ETag"), "and the tag is written, so asking again is cheap");
    }

    @Test
    void everythingElseThatIsPublicIsStillHeldForAnHour() {
        assertEquals("public, max-age=3600", header(get("/api/v1/public/kb/some-station/article"), "Cache-Control"));
    }

    /**
     * A waiting-list entry behind its own link carries a name, an address and the appointment somebody
     * was invited to, and it changes the moment they answer. Held for an hour it was both a copy of
     * a family's details sitting in a cache nobody owns and an answer the page could not see it had
     * given.
     */
    @Test
    void anEntryBehindItsOwnLinkIsKeptNowhere() {
        var answer = get("/api/v1/public/waiting-list/entry/some-token");

        assertEquals("private, no-store", header(answer, "Cache-Control"));
        assertNull(header(answer, "ETag"), "there is nothing to revalidate against");
    }

    @Test
    void aPageFileIsHeldForAYearBecauseItsNameCarriesItsContent() {
        assertEquals(
                "public, max-age=31536000, immutable",
                header(get("/api/v1/public/pages/files/abc123.png"), "Cache-Control"));
    }

    @Test
    void anythingBehindASessionIsRevalidated() {
        assertEquals("private, no-cache", header(get("/api/v1/news"), "Cache-Control"));
    }

    @Test
    void theTagIsTheStartOfTheBodysSha256() {
        assertEquals("\"" + Sha256.hexPrefix(BODY, 16) + "\"", header(get("/api/v1/news"), "ETag"));
    }

    /**
     * The tag is written by the cache headers and the {@code 304} is Javalin's: a browser that sends
     * the tag back gets an empty answer, and one that sends another gets the body.
     */
    @Test
    void aTagSentBackIsAnsweredWithNotModified() {
        RouteHarness.serving(ANSWERING).run((server, client) -> {
            var first = client.get("/api/v1/news");
            String tag = header(first, "ETag");
            var repeated = client.get("/api/v1/news", request -> request.header("If-None-Match", tag));
            var stale = client.get("/api/v1/news", request -> request.header("If-None-Match", "\"other\""));

            assertEquals(BODY, first.body().string());
            assertEquals(304, repeated.code());
            assertEquals("", repeated.body().string());
            assertEquals(200, stale.code());
            assertEquals(BODY, stale.body().string());
        });
    }
}
