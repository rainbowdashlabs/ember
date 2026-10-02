/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api.auth;

import dev.chojo.ember.auth.TokenHasher;
import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.conf.file.elements.Demo;
import dev.chojo.ember.feature.account.entity.LoginResult;
import io.javalin.http.Context;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** What the session cookies carry on a production instance, and when nothing is written at all. */
class SessionCookiesTest {
    private final HttpServletResponse response = mock(HttpServletResponse.class);
    private final Context ctx = mock(Context.class);
    private final CsrfGuard csrfGuard =
            new CsrfGuard(TokenHasher.forTesting("cookie-test-pepper"), mock(Api.class), mock(Demo.class));

    @Test
    void aProductionInstanceMarksBothCookiesSecureAndLetsThemLiveAsLongAsTheSession() {
        when(ctx.res()).thenReturn(response);

        production().issue(ctx, "token", Instant.now().plus(2, ChronoUnit.HOURS).plusSeconds(5));

        List<String> cookies = written();
        assertTrue(cookies.get(0).startsWith("ember_session=token; Path=/; Max-Age=720"), cookies.get(0));
        assertTrue(cookies.get(0).endsWith("; HttpOnly; SameSite=Lax; Secure"), cookies.get(0));
        assertTrue(cookies.get(1).startsWith("ember_csrf=" + csrfGuard.tokenFor("token") + "; Path=/; Max-Age=720"));
        assertTrue(cookies.get(1).endsWith("; SameSite=Lax; Secure"), cookies.get(1));
        assertTrue(!cookies.get(1).contains("HttpOnly"), "the page has to read the second one");
    }

    @Test
    void clearingWritesEmptyCookiesThatHaveAlreadyRunOut() {
        when(ctx.res()).thenReturn(response);

        production().clear(ctx);

        assertEquals(
                List.of(
                        "ember_session=; Path=/; Max-Age=0; HttpOnly; SameSite=Lax; Secure",
                        "ember_csrf=; Path=/; Max-Age=0; SameSite=Lax; Secure"),
                written());
    }

    @Test
    void onlyAFinishedSignInReachesTheBrowser() {
        when(ctx.res()).thenReturn(response);
        var cookies = production();

        cookies.issue(ctx, (LoginResult) null);
        cookies.issue(ctx, LoginResult.failure("no"));
        cookies.issue(ctx, LoginResult.passwordChangeRequired("step", Instant.now()));
        cookies.issue(ctx, LoginResult.addressRequired("step", Instant.now()));
        cookies.issue(ctx, LoginResult.twoFactorRequired("step", Instant.now()));

        verify(response, never()).addHeader(anyString(), anyString());
    }

    @Test
    void theTokenIsReadFromTheCookieAndABlankOneCountsAsNone() {
        when(ctx.cookie(SessionCookies.SESSION_COOKIE)).thenReturn("token");
        assertEquals("token", SessionCookies.token(ctx).orElseThrow());

        when(ctx.cookie(SessionCookies.SESSION_COOKIE)).thenReturn(" ");
        assertTrue(SessionCookies.token(ctx).isEmpty());
    }

    private List<String> written() {
        var header = ArgumentCaptor.forClass(String.class);
        verify(response, times(2)).addHeader(eq("Set-Cookie"), header.capture());
        return header.getAllValues();
    }

    private SessionCookies production() {
        return new SessionCookies(mock(Demo.class), csrfGuard);
    }
}
