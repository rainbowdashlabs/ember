/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.account.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.auth.SessionCookies;
import dev.chojo.ember.api.refusal.MemberRefusal;
import dev.chojo.ember.conf.file.elements.Demo;
import dev.chojo.ember.feature.account.entity.LoginResult;
import dev.chojo.ember.feature.account.service.AuthRateLimiter;
import dev.chojo.ember.feature.account.service.AuthService;
import dev.chojo.ember.feature.account.service.AuthService.AddressOutcome;
import dev.chojo.ember.feature.passkey.service.PasskeyModeService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.Arrays;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.body;
import static dev.chojo.ember.api.RouteHarness.refusalOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * How the address setup answers what became of an attempt, and how a refused sign-in is named.
 */
class AuthRoutesTest {

    @Test
    void everyAddressSetupThatFailedIsRefusedOnItsOwnTerms() {
        var refusals = Arrays.stream(AddressOutcome.values())
                .filter(outcome -> outcome != AddressOutcome.OK)
                .map(AuthRoutes::addressSetupRefusal)
                .toList();

        assertEquals(
                AddressOutcome.values().length - 1, refusals.stream().distinct().count());
        refusals.forEach(refusal -> assertEquals(4, refusal.status().getCode() / 100, refusal.name()));
    }

    @Test
    void anAddressThatWasSetIsNoRefusal() {
        assertThrows(IllegalArgumentException.class, () -> AuthRoutes.addressSetupRefusal(AddressOutcome.OK));
    }

    @Test
    void anExpiredOneTimePasswordIsRefusedWithItsOwnCodeAndAnyOtherFailureWithTheUsualOne() {
        var auth = mock(AuthService.class);
        when(auth.login(eq("lena@test.com"), any(), any(), any(), any(), anyBoolean()))
                .thenReturn(LoginResult.oneTimePasswordHasExpired());
        when(auth.login(eq("tom@test.com"), any(), any(), any(), any(), anyBoolean()))
                .thenReturn(LoginResult.failure("Invalid email or password"));
        var harness = RouteHarness.serving(new AuthRoutes(
                auth,
                new AuthRateLimiter(Clock.systemUTC()),
                new Demo(),
                mock(PasskeyModeService.class),
                mock(SessionCookies.class)));

        harness.run((server, client) -> {
            var expired = client.post(
                    PREFIX + "/auth/login", body("{\"identifier\": \"lena@test.com\", \"password\": \"k7mq-x2pd\"}"));
            assertEquals(MemberRefusal.ONE_TIME_PASSWORD_EXPIRED, refusalOf(expired));
            var wrong = client.post(
                    PREFIX + "/auth/login", body("{\"identifier\": \"tom@test.com\", \"password\": \"wrong\"}"));
            assertEquals(MemberRefusal.SIGN_IN_REFUSED, refusalOf(wrong));
        });
    }
}
