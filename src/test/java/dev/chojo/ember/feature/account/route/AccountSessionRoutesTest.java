/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.account.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.auth.SessionCookies;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.auth.TokenHasher;
import dev.chojo.ember.feature.account.service.AuthService;
import org.junit.jupiter.api.Test;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Ending one session hands the reader's own account to the service, which ends only sessions of
 * that account.
 */
class AccountSessionRoutesTest {
    private final AuthService auth = mock(AuthService.class);
    private final RouteHarness harness = RouteHarness.serving(
            new AccountSessionRoutes(auth, TokenHasher.forTesting("pepper"), mock(SessionCookies.class)));

    @Test
    void aSessionIsEndedForTheReadersAccount() {
        var reader = harness.as(TestSessions.member(3, StationPermission.LOGIN));

        var response = harness.request(client -> client.delete(PREFIX + "/session/active/5", null, reader));

        assertEquals(204, response.code());
        verify(auth).invalidateSession(5, TestSessions.ACCOUNT_ID);
    }
}
