/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api.auth;

import dev.chojo.ember.api.refusal.MemberRefusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.auth.TokenHasher;
import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.conf.file.elements.Demo;
import io.javalin.http.Context;
import io.javalin.http.HandlerType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A change under a session needs the token derived from it, a read never does, and a sign-in only
 * works from this site's own origin.
 */
class CsrfGuardTest {
    private final Api api = mock(Api.class);
    private final Demo demo = mock(Demo.class);
    private final Context ctx = mock(Context.class);
    private CsrfGuard guard;

    @BeforeEach
    void setUp() {
        when(api.allowedOrigins()).thenReturn(List.of("https://ember.example.org/"));
        when(api.demoUrl()).thenReturn("https://demo.example.org");
        guard = new CsrfGuard(TokenHasher.forTesting("csrf-test-pepper"), api, demo);
    }

    @Test
    void theTokenFollowsTheSessionAndRevealsNothingOfIt() {
        String token = guard.tokenFor("session");

        assertEquals(token, guard.tokenFor("session"));
        assertNotEquals(token, guard.tokenFor("another session"));
        assertFalse(token.contains("session"));
        assertNotEquals(TokenHasher.forTesting("csrf-test-pepper").hash("session"), token);
    }

    @Test
    void aReadNeedsNoToken() {
        for (HandlerType method : List.of(HandlerType.GET, HandlerType.HEAD, HandlerType.OPTIONS)) {
            when(ctx.method()).thenReturn(method);
            assertTrue(guard.permits(ctx, "session"), method.name());
        }
    }

    @Test
    void aChangeNeedsTheMatchingToken() {
        for (HandlerType method : List.of(HandlerType.POST, HandlerType.PUT, HandlerType.PATCH, HandlerType.DELETE)) {
            when(ctx.method()).thenReturn(method);
            when(ctx.header(CsrfGuard.HEADER)).thenReturn(null);
            assertFalse(guard.permits(ctx, "session"), method.name());

            when(ctx.header(CsrfGuard.HEADER)).thenReturn(guard.tokenFor("another session"));
            assertFalse(guard.permits(ctx, "session"), method.name());

            when(ctx.header(CsrfGuard.HEADER)).thenReturn(guard.tokenFor("session"));
            assertTrue(guard.permits(ctx, "session"), method.name());
        }
    }

    @Test
    void aChangeWithoutTheTokenIsRefusedByName() {
        when(ctx.method()).thenReturn(HandlerType.POST);

        var refused = assertThrows(RefusalResponse.class, () -> guard.require(ctx, "session"));

        assertEquals(MemberRefusal.REQUEST_NOT_FROM_THIS_PAGE, refused.refusal());
    }

    @Test
    void aSignInFromTheSiteItselfOrWithoutAnOriginGoesAhead() {
        when(ctx.header("Origin")).thenReturn(null);
        assertDoesNotThrow(() -> guard.requireOwnOrigin(ctx));

        when(ctx.header("Origin")).thenReturn("https://EMBER.example.org");
        assertDoesNotThrow(() -> guard.requireOwnOrigin(ctx));
    }

    @Test
    void aSignInFromAnotherSiteIsRefused() {
        when(ctx.header("Origin")).thenReturn("https://attacker.example.net");
        var refused = assertThrows(RefusalResponse.class, () -> guard.requireOwnOrigin(ctx));
        assertEquals(MemberRefusal.SIGN_IN_FROM_ANOTHER_SITE, refused.refusal());

        when(ctx.header("Origin")).thenReturn("null");
        assertThrows(RefusalResponse.class, () -> guard.requireOwnOrigin(ctx));
    }

    @Test
    void theDemoAddressCountsOnADemoAndAnyOriginOnADevInstance() {
        when(ctx.header("Origin")).thenReturn("https://demo.example.org");
        assertThrows(RefusalResponse.class, () -> guard.requireOwnOrigin(ctx));

        when(demo.enabled()).thenReturn(true);
        assertDoesNotThrow(() -> guard.requireOwnOrigin(ctx));

        when(demo.dev()).thenReturn(true);
        when(ctx.header("Origin")).thenReturn("http://192.168.1.20:3000");
        assertDoesNotThrow(() -> guard.requireOwnOrigin(ctx));
    }
}
