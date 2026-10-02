/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.account.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.auth.InstancePermission;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StepUpCategory;
import dev.chojo.ember.feature.account.entity.IssuedOneTimePassword;
import dev.chojo.ember.feature.account.service.AccountOverviewService;
import dev.chojo.ember.feature.account.service.AccountOverviewService.AccountOverviewPage;
import dev.chojo.ember.feature.account.service.OneTimePasswordService;
import io.javalin.router.JavalinDefaultRoutingApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.body;
import static dev.chojo.ember.api.RouteHarness.json;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The instance administrator's account list and the one-time password issued from it, over HTTP.
 */
class AccountAdminRoutesTest {
    private AccountOverviewService overview;
    private OneTimePasswordService oneTimePasswords;
    private RouteHarness harness;

    @BeforeEach
    void setup() {
        overview = mock(AccountOverviewService.class);
        oneTimePasswords = mock(OneTimePasswordService.class);
        harness = RouteHarness.serving(new AccountAdminRoutes(overview, oneTimePasswords));
    }

    @Test
    void theListIsAskedForTheSearchAndThePageItNames() {
        when(overview.list("lena", 2, 10)).thenReturn(new AccountOverviewPage(List.of(), 21, 2, 10));
        when(overview.list(null, 0, AccountOverviewService.DEFAULT_SIZE))
                .thenReturn(new AccountOverviewPage(List.of(), 3, 0, AccountOverviewService.DEFAULT_SIZE));

        harness.run((server, client) -> {
            var page = client.get(
                    PREFIX + "/admin/accounts?q=lena&page=2&size=10", harness.as(TestSessions.administrator()));
            assertEquals(21, json(page).path("total").asInt());
            var first = client.get(PREFIX + "/admin/accounts", harness.as(TestSessions.administrator()));
            assertEquals(3, json(first).path("total").asInt());
            var stationAdministrator = client.get(
                    PREFIX + "/admin/accounts",
                    harness.as(TestSessions.member(3, StationPermission.STATION_ADMINISTRATOR)));
            assertEquals(403, stationAdministrator.code(), "a station's administration does not see every account");
        });
    }

    @Test
    void anInstanceAdministratorIsHandedTheOneTimePassword() {
        when(oneTimePasswords.issueForInstance(eq(TestSessions.ACCOUNT_ID), eq(42), any(), any()))
                .thenReturn(new IssuedOneTimePassword(42, "Lena Weber", "lena", "k7mq-x2pd-9wtr-hb4z", Instant.EPOCH));

        var answer = harness.request(client -> client.post(
                PREFIX + "/admin/accounts/42/one-time-password", body("{}"), harness.as(TestSessions.administrator())));

        assertEquals("lena", json(answer).path("loginName").asString());
        verify(oneTimePasswords).issueForInstance(eq(TestSessions.ACCOUNT_ID), eq(42), any(), any());
    }

    @Test
    void theOneTimePasswordAsksForAFreshSecondFactor() {
        var router = mock(JavalinDefaultRoutingApi.class);

        new AccountAdminRoutes(overview, oneTimePasswords).register(router, PREFIX);

        verify(router)
                .post(
                        eq(PREFIX + "/admin/accounts/{id}/one-time-password"),
                        any(),
                        eq(InstancePermission.ADMINISTRATOR),
                        eq(StepUpCategory.ACCOUNT_SECURITY));
    }
}
