/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api.auth;

import dev.chojo.ember.api.AccessManager;
import dev.chojo.ember.api.ApiServer;
import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.RefusalResponse;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.conf.file.elements.Auth;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import io.javalin.http.Context;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The gate admits a live session, keeps it alive and renews its cookie once half its lifetime is
 * spent, and clears a cookie that names nothing.
 */
class SessionGateTest {
    private final AccessManager accessManager = mock(AccessManager.class);
    private final AccountRepository accounts = mock(AccountRepository.class);
    private final SessionCookies cookies = mock(SessionCookies.class);
    private final CsrfGuard csrfGuard = mock(CsrfGuard.class);
    private final Context ctx = mock(Context.class);
    private SessionGate gate;

    @BeforeEach
    void setUp() {
        Auth auth = mock(Auth.class);
        when(auth.sessionMinutes(false)).thenReturn(60);
        when(auth.sessionMinutes(true)).thenReturn(43200);
        when(ctx.userAgent()).thenReturn("agent");
        when(ctx.header("CF-IPCountry")).thenReturn("DE");
        when(csrfGuard.permits(any(), anyString())).thenReturn(true);
        gate = new SessionGate(accessManager, accounts, auth, cookies, csrfGuard);
    }

    @Test
    void aChangeWithoutTheTokenStopsBeforeTheSessionIsUsed() {
        when(accessManager.resolveUserSession("live", null, null)).thenReturn(Optional.of(mock(UserSession.class)));
        doThrow(Refusal.REQUEST_NOT_FROM_THIS_PAGE.raise()).when(csrfGuard).require(ctx, "live");

        assertThrows(RefusalResponse.class, () -> gate.admit(ctx, "live", null, null));

        verify(accounts, never()).touchSession(anyString(), any(), any());
    }

    @Test
    void aPublicRouteGoesWithoutTheSessionWhenTheRequestMayNotUseIt() {
        when(csrfGuard.permits(ctx, "live")).thenReturn(false);

        gate.attach(ctx, "live", null);

        verify(accessManager, never()).resolveUserSession("live", null);
    }

    @Test
    void aTokenNamingNoLiveSessionIsClearedAndRefused() {
        when(accessManager.resolveUserSession("dead", null, null)).thenReturn(Optional.empty());

        assertTrue(gate.admit(ctx, "dead", null, null).isEmpty());

        verify(cookies).clear(ctx);
        verify(accounts, never()).touchSession(anyString(), any(), any());
    }

    @Test
    void aLiveSessionIsTouchedAndLeftAloneWhileFresh() {
        UserSession session = mock(UserSession.class);
        when(accessManager.resolveUserSession("live", null, null)).thenReturn(Optional.of(session));
        when(accounts.renewSession("live", 60, 43200)).thenReturn(Optional.empty());

        assertSame(session, gate.admit(ctx, "live", null, null).orElseThrow());

        verify(accounts).touchSession("live", "agent", "DE");
        verify(cookies, never()).issue(any(Context.class), anyString(), any(Instant.class));
    }

    @Test
    void aRenewedSessionGetsItsCookieWrittenAgain() {
        UserSession session = mock(UserSession.class);
        Instant renewed = Instant.now().plus(1, ChronoUnit.HOURS);
        when(accessManager.resolveUserSession("half-spent", null, null)).thenReturn(Optional.of(session));
        when(accounts.renewSession(eq("half-spent"), anyInt(), anyInt())).thenReturn(Optional.of(renewed));

        gate.admit(ctx, "half-spent", null, null);

        verify(cookies).issue(ctx, "half-spent", renewed);
    }

    @Test
    void aPublicRouteGetsTheSessionAttachedWhenThereIsOne() {
        UserSession session = mock(UserSession.class);
        when(accessManager.resolveUserSession("live", null)).thenReturn(Optional.of(session));
        when(accessManager.resolveUserSession("dead", null)).thenReturn(Optional.empty());

        gate.attach(ctx, "live", null);
        gate.attach(ctx, "dead", null);

        verify(ctx).attribute(ApiServer.ATTR_SESSION, session);
        verify(cookies, never()).clear(ctx);
    }
}
