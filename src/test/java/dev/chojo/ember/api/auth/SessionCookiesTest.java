/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api.auth;

import dev.chojo.ember.conf.file.elements.Demo;
import dev.chojo.ember.feature.account.entity.LoginResult;
import io.javalin.http.Context;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** What the session cookie carries on a production instance, and when nothing is written at all. */
class SessionCookiesTest {
    private final HttpServletResponse response = mock(HttpServletResponse.class);
    private final Context ctx = mock(Context.class);

    @Test
    void aProductionInstanceMarksTheCookieSecureAndLetsItLiveAsLongAsTheSession() {
        when(ctx.res()).thenReturn(response);

        production().issue(ctx, "token", Instant.now().plus(2, ChronoUnit.HOURS).plusSeconds(5));

        String cookie = written();
        assertTrue(cookie.startsWith("ember_session=token; Path=/; Max-Age=720"), cookie);
        assertTrue(cookie.endsWith("; HttpOnly; SameSite=Lax; Secure"), cookie);
    }

    @Test
    void clearingWritesAnEmptyCookieThatHasAlreadyRunOut() {
        when(ctx.res()).thenReturn(response);

        production().clear(ctx);

        assertEquals("ember_session=; Path=/; Max-Age=0; HttpOnly; SameSite=Lax; Secure", written());
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

    private String written() {
        var header = ArgumentCaptor.forClass(String.class);
        verify(response).addHeader(eq("Set-Cookie"), header.capture());
        return header.getValue();
    }

    private static SessionCookies production() {
        return new SessionCookies(mock(Demo.class));
    }
}
