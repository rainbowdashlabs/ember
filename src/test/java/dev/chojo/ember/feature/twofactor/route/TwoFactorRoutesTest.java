/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.twofactor.route;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.auth.SessionCookies;
import dev.chojo.ember.conf.file.elements.Demo;
import dev.chojo.ember.feature.account.entity.LoginResult;
import dev.chojo.ember.feature.account.service.AuthRateLimiter;
import dev.chojo.ember.feature.account.service.AuthService;
import dev.chojo.ember.feature.twofactor.service.TrustedDeviceService;
import dev.chojo.ember.feature.twofactor.service.TwoFactorAuditService;
import dev.chojo.ember.feature.twofactor.service.TwoFactorService;
import dev.chojo.ember.feature.twofactor.service.TwoFactorSignInService;
import dev.chojo.ember.feature.twofactor.service.WebAuthnService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.body;
import static dev.chojo.ember.api.RouteHarness.refusalOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Finishing a sign-in with a second factor over HTTP: the waiting sign-in and the code are left to
 * the sign-in service, and a session is only handed out once it accepted them.
 */
class TwoFactorRoutesTest {
    private static final int ACCOUNT = 7;

    private final TwoFactorSignInService signIn = mock(TwoFactorSignInService.class);
    private final AuthService auth = mock(AuthService.class);
    private final WebAuthnService webAuthn = mock(WebAuthnService.class);
    private final AuthRateLimiter rateLimiter = mock(AuthRateLimiter.class);
    private final SessionCookies cookies = mock(SessionCookies.class);
    private final RouteHarness harness = RouteHarness.serving(new TwoFactorRoutes(
            mock(TwoFactorService.class),
            mock(TwoFactorAuditService.class),
            signIn,
            auth,
            webAuthn,
            mock(Demo.class),
            mock(TrustedDeviceService.class),
            rateLimiter,
            cookies));

    @BeforeEach
    void aWaitingSignIn() {
        when(signIn.waitingAccount(eq("pre-auth"), any())).thenReturn(ACCOUNT);
        when(rateLimiter.tryTwoFactor(anyString(), anyInt())).thenReturn(Optional.empty());
        when(auth.createVerifiedSessionForAccount(eq(ACCOUNT), any(), any(), any(), anyBoolean()))
                .thenReturn(mock(LoginResult.class));
    }

    @Test
    void theRightCodeEarnsASession() {
        var response = harness.request(client -> client.post(PREFIX + "/auth/2fa", body("""
                {"preAuthToken":"pre-auth","factor":"TOTP","proof":"123456"}""")));

        assertEquals(200, response.code());
        verify(signIn)
                .verify(
                        eq(ACCOUNT),
                        eq("pre-auth"),
                        argThat(attempt -> attempt.factor().equals("TOTP")
                                && attempt.proof().equals("123456")));
        verify(cookies).issue(any(), any());
    }

    @Test
    void aWrongCodeEarnsNoSession() {
        doThrow(Refusal.TWO_FACTOR_CODE_WRONG.raise()).when(signIn).verify(eq(ACCOUNT), eq("pre-auth"), any());

        var response = harness.request(client -> client.post(PREFIX + "/auth/2fa", body("""
                {"preAuthToken":"pre-auth","factor":"TOTP","proof":"000000"}""")));

        assertEquals(Refusal.TWO_FACTOR_CODE_WRONG, refusalOf(response));
        verify(auth, never()).createVerifiedSessionForAccount(anyInt(), any(), any(), any(), anyBoolean());
    }

    @Test
    void aSecurityKeyFinishesTheWaitingSignIn() {
        when(webAuthn.finishAssertion(ACCOUNT, "challenge", "{}")).thenReturn(true);

        var response = harness.request(client -> client.post(PREFIX + "/auth/2fa/webauthn/finish", body("""
                {"preAuthToken":"pre-auth","challengeToken":"challenge","credentialJson":"{}"}""")));

        assertEquals(200, response.code());
        verify(signIn).finish("pre-auth");
    }

    @Test
    void aSecurityKeyForNoWaitingSignInIsRefused() {
        when(signIn.waitingAccount("gone", Refusal.SIGN_IN_NOT_WAITING_ON_A_KEY))
                .thenThrow(Refusal.SIGN_IN_NOT_WAITING_ON_A_KEY.raise());

        var response = harness.request(client -> client.post(PREFIX + "/auth/2fa/webauthn/begin", body("""
                {"preAuthToken":"gone"}""")));

        assertEquals(Refusal.SIGN_IN_NOT_WAITING_ON_A_KEY, refusalOf(response));
    }
}
