/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api;

import dev.chojo.ember.conf.file.elements.Demo;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import io.javalin.Javalin;
import io.javalin.config.RoutesConfig;
import io.javalin.http.HttpStatus;
import io.javalin.testtools.JavalinTest;
import io.javalin.testtools.Response;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static dev.chojo.ember.api.RouteHarness.header;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The headers every response carries, judged on an application that installs nothing but the policy.
 */
class ResponseHeaderPolicyTest {
    private static final String PREFIX = "/api/v1";

    private final StationRepository stations = mock(StationRepository.class);

    private static Demo demo(boolean dev) {
        Demo demo = mock(Demo.class);
        when(demo.dev()).thenReturn(dev);
        return demo;
    }

    private Response get(boolean dev, String scheme, String path, Consumer<RoutesConfig> routes) {
        var policy = new ResponseHeaderPolicy(demo(dev), stations);
        Javalin app = Javalin.create(config -> {
            config.contextResolver.scheme = ctx -> scheme;
            policy.install(config.routes);
            routes.accept(config.routes);
        });
        var answered = new AtomicReference<Response>();
        JavalinTest.test(app, (server, client) -> answered.set(client.get(path)));
        return answered.get();
    }

    private Response get(String path) {
        return get(false, "http", path, routes -> routes.get(path, ctx -> ctx.result("body")));
    }

    @Test
    void everyResponseIsHardenedInTheBrowser() {
        var answer = get(PREFIX + "/news");

        assertEquals("nosniff", header(answer, "X-Content-Type-Options"));
        assertEquals("SAMEORIGIN", header(answer, "X-Frame-Options"));
        assertEquals("frame-ancestors 'self'", header(answer, "Content-Security-Policy"));
        assertEquals("strict-origin-when-cross-origin", header(answer, "Referrer-Policy"));
        assertNull(header(answer, "Strict-Transport-Security"), "plain HTTP is never pinned");
    }

    @Test
    void aStricterReferrerPolicyARouteChoseIsKept() {
        String path = PREFIX + "/feed";
        var answer = get(
                false,
                "http",
                path,
                routes -> routes.get(path, ctx -> ctx.header("Referrer-Policy", "no-referrer")
                        .result("feed")));

        assertEquals("no-referrer", header(answer, "Referrer-Policy"));
    }

    @Test
    void transportSecurityIsSentOverHttpsOnly() {
        String path = PREFIX + "/news";
        var answer = get(false, "https", path, routes -> routes.get(path, ctx -> ctx.result("body")));

        assertEquals("max-age=31536000", header(answer, "Strict-Transport-Security"));
    }

    @Test
    void aDevelopmentInstanceNeverSendsTransportSecurity() {
        String path = PREFIX + "/news";
        var answer = get(true, "https", path, routes -> routes.get(path, ctx -> ctx.result("body")));

        assertNull(header(answer, "Strict-Transport-Security"));
    }

    @Test
    void aSharedLinkIsKeptNowhereAndLeavesNoReferrer() {
        var answer = get(PREFIX + "/public/shared/some-token");

        assertEquals("private, no-store", header(answer, "Cache-Control"));
        assertEquals("no-referrer", header(answer, "Referrer-Policy"));
        assertEquals("noindex", header(answer, "X-Robots-Tag"));
    }

    @Test
    void aPictureBehindASessionIsHeldBriefly() {
        assertEquals("private, max-age=300", header(get(PREFIX + "/session/avatar"), "Cache-Control"));
    }

    @Test
    void aPathThatMerelyContainsAPictureWordIsNotTakenForOne() {
        assertEquals("private, no-cache", header(get(PREFIX + "/auth/logout"), "Cache-Control"));
    }

    @Test
    void theDemoDataIsHeldForAMinute() {
        assertEquals("public, max-age=60", header(get(PREFIX + "/demo/status"), "Cache-Control"));
    }

    @Test
    void anErrorIsNeverCached() {
        String path = PREFIX + "/news";
        var answer = get(
                false,
                "http",
                path,
                routes ->
                        routes.get(path, ctx -> ctx.status(HttpStatus.NOT_FOUND).result("gone")));

        assertNull(header(answer, "Cache-Control"));
        assertNull(header(answer, "ETag"));
    }

    @Test
    void anAnswerWithoutABodyGetsNoTag() {
        String path = PREFIX + "/news";
        var answer = get(false, "http", path, routes -> routes.get(path, ctx -> ctx.status(HttpStatus.OK)));

        assertEquals("private, no-cache", header(answer, "Cache-Control"));
        assertNull(header(answer, "ETag"));
    }

    @Test
    void aRemoteAnswerNamesTheStationThatServedIt() {
        UUID uid = UUID.fromString("00000000-0000-0000-0000-000000000007");
        Station station = mock(Station.class);
        when(station.name()).thenReturn("Nordwache");
        when(station.uid()).thenReturn(uid);
        when(stations.findById(7)).thenReturn(Optional.of(station));
        FederationSession partner = mock(FederationSession.class);
        when(partner.stationId()).thenReturn(7);

        String path = PREFIX + "/remote/quiz/catalogs";
        var answer = get(false, "http", path, routes -> {
            routes.before(ctx -> ctx.attribute(FederationSession.ATTR_FEDERATION_SESSION, partner));
            routes.get(path, ctx -> ctx.result("[]"));
        });

        assertEquals("Nordwache", header(answer, FederationHeaders.HEADER_STATION_NAME));
        assertEquals(uid.toString(), header(answer, FederationHeaders.HEADER_STATION_ID));
    }

    @Test
    void aRemoteAnswerWithoutAPartnerNamesNoStation() {
        var answer = get(PREFIX + "/remote/quiz/catalogs");

        assertNull(header(answer, FederationHeaders.HEADER_STATION_ID));
    }
}
