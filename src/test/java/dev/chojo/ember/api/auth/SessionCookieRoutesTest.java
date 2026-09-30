/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api.auth;

import dev.chojo.ember.api.Jackson3Mapper;
import dev.chojo.ember.conf.file.elements.Demo;
import dev.chojo.ember.feature.account.entity.LoginResult;
import dev.chojo.ember.feature.account.route.AuthRoutes;
import dev.chojo.ember.feature.account.service.AuthRateLimiter;
import dev.chojo.ember.feature.account.service.AuthService;
import dev.chojo.ember.feature.passkey.service.PasskeyModeService;
import io.javalin.Javalin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Serves real requests to prove that a sign-in puts its session into the cookie and never into the
 * body, that a step still owed puts nothing there, and that signing out ends the session the cookie
 * names and clears the cookie.
 */
class SessionCookieRoutesTest {
    private final AuthService authService = mock(AuthService.class);
    private Javalin app;

    @BeforeEach
    void start() {
        Demo demo = mock(Demo.class);
        when(demo.dev()).thenReturn(true);
        var routes = new AuthRoutes(
                authService,
                mock(AuthRateLimiter.class),
                demo,
                mock(PasskeyModeService.class),
                new SessionCookies(demo));
        app = Javalin.create(config -> {
            config.jsonMapper(new Jackson3Mapper(JsonMapper.builder().build()));
            routes.register(config.routes, "/api/v1");
        });
        app.start(0);
    }

    @AfterEach
    void stop() {
        app.stop();
    }

    @Test
    void signInPutsTheSessionIntoAnHttpOnlyCookie() throws Exception {
        when(authService.login(anyString(), anyString(), any(), any(), any(), anyBoolean()))
                .thenReturn(LoginResult.success("session-token", Instant.now().plus(1, ChronoUnit.HOURS)));

        var response = post("/api/v1/auth/login", """
                {"identifier":"someone@example.org","password":"secret"}""", null);

        assertEquals(200, response.statusCode());
        String cookie = sessionCookie(response);
        assertTrue(cookie.startsWith("ember_session=session-token;"), cookie);
        assertTrue(cookie.contains("HttpOnly"), cookie);
        assertTrue(cookie.contains("SameSite=Lax"), cookie);
        assertFalse(cookie.contains("Secure"), "a dev instance is reached over plain HTTP");
        assertFalse(response.body().contains("session-token"), response.body());
        assertTrue(response.body().contains("\"token\":null"), response.body());
    }

    @Test
    void aSecondFactorStillOwedPutsNothingIntoTheBrowser() throws Exception {
        when(authService.login(anyString(), anyString(), any(), any(), any(), anyBoolean()))
                .thenReturn(
                        LoginResult.twoFactorRequired("pre-auth", Instant.now().plus(5, ChronoUnit.MINUTES)));

        var response = post("/api/v1/auth/login", """
                {"identifier":"someone@example.org","password":"secret"}""", null);

        assertEquals(200, response.statusCode());
        assertTrue(response.headers().allValues("Set-Cookie").isEmpty());
        assertTrue(response.body().contains("pre-auth"), response.body());
    }

    @Test
    void demoSignInSetsTheCookieToo() throws Exception {
        when(authService.loginAsDemo(anyString(), any(), any()))
                .thenReturn(
                        LoginResult.success("demo@example.org", Instant.now().plus(1, ChronoUnit.DAYS)));

        var response = post("/api/v1/demo/login", """
                {"email":"demo@example.org"}""", null);

        assertTrue(sessionCookie(response).startsWith("ember_session=demo@example.org;"));
    }

    @Test
    void signOutEndsTheSessionTheCookieNamesAndClearsIt() throws Exception {
        var response = post("/api/v1/auth/logout", "", "ember_session=session-token");

        assertEquals(200, response.statusCode());
        verify(authService).logout("session-token");
        String cookie = sessionCookie(response);
        assertTrue(cookie.startsWith("ember_session=;"), cookie);
        assertTrue(cookie.contains("Max-Age=0"), cookie);
    }

    @Test
    void signOutWithoutASessionStillAnswers() throws Exception {
        var response = post("/api/v1/auth/logout", "", null);

        assertEquals(200, response.statusCode());
        verify(authService, never()).logout(anyString());
    }

    @Test
    void theRenewalEndpointIsGone() throws Exception {
        var response = post("/api/v1/auth/refresh", "{}", "ember_session=session-token");

        assertEquals(404, response.statusCode());
    }

    private static String sessionCookie(HttpResponse<String> response) {
        List<String> cookies = response.headers().allValues("Set-Cookie");
        return cookies.stream()
                .filter(c -> c.startsWith(SessionCookies.SESSION_COOKIE + "="))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no session cookie in " + cookies));
    }

    private HttpResponse<String> post(String path, String body, String cookie) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + app.port() + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (cookie != null) request.header("Cookie", cookie);
        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        }
    }
}
