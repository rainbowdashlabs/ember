/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.twofactor.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.api.refusal.BodyRefusal;
import dev.chojo.ember.api.refusal.TwoFactorRefusal;
import dev.chojo.ember.feature.twofactor.entity.TwoFactorPolicy;
import dev.chojo.ember.feature.twofactor.service.TwoFactorAdminService;
import dev.chojo.ember.feature.twofactor.service.TwoFactorAdminService.AuditResponse;
import dev.chojo.ember.feature.twofactor.service.TwoFactorPolicyService;
import dev.chojo.ember.feature.twofactor.service.TwoFactorService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.body;
import static dev.chojo.ember.api.RouteHarness.json;
import static dev.chojo.ember.api.RouteHarness.refusalOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The second-factor administration that leaves its decisions to the admin service, over HTTP.
 */
class TwoFactorAdminRoutesTest {
    private final TwoFactorAdminService admin = mock(TwoFactorAdminService.class);
    private final TwoFactorPolicyService policies = mock(TwoFactorPolicyService.class);
    private final RouteHarness harness =
            RouteHarness.serving(new TwoFactorAdminRoutes(policies, mock(TwoFactorService.class), admin));

    @Test
    void aRuleNamesItsMemberTypeAndAnswersItsScope() {
        when(policies.setInstancePolicy(eq(StationUserType.TEAM), eq(true), eq((short) 7), any()))
                .thenReturn(new TwoFactorPolicy(
                        4,
                        TwoFactorPolicy.PolicyScope.INSTANCE,
                        null,
                        StationUserType.TEAM,
                        true,
                        (short) 7,
                        null,
                        Instant.EPOCH));

        harness.run((server, client) -> {
            var administrator = harness.as(TestSessions.administrator());
            String path = PREFIX + "/admin/2fa/policies";
            var saved = json(client.put(path, body("{\"userType\": \"TEAM\", \"required\": true}"), administrator));
            assertEquals("INSTANCE", saved.path("scope").asString());
            assertEquals("TEAM", saved.path("userType").asString());
            assertEquals(
                    BodyRefusal.BODY_DOES_NOT_MATCH,
                    refusalOf(
                            client.put(path, body("{\"userType\": \"VISITOR\", \"required\": true}"), administrator)));
        });
    }

    @Test
    void aStationAdministratorResetsForTheirStation() {
        var stationAdmin = TestSessions.member(3, StationPermission.STATION_ADMINISTRATOR);
        doThrow(TwoFactorRefusal.MEMBER_NOT_YOURS_TO_RESET.raise())
                .when(admin)
                .resetForStation(eq(3), eq(43), anyInt(), any(), any());

        harness.run((server, client) -> {
            assertEquals(
                    200,
                    client.post(PREFIX + "/station/accounts/42/2fa/reset", null, harness.as(stationAdmin))
                            .code());
            assertEquals(
                    TwoFactorRefusal.MEMBER_NOT_YOURS_TO_RESET,
                    refusalOf(client.post(PREFIX + "/station/accounts/43/2fa/reset", null, harness.as(stationAdmin))));
        });

        verify(admin).resetForStation(eq(3), eq(42), eq(TestSessions.ACCOUNT_ID), any(), any());
    }

    @Test
    void accountsAndTheAuditLogAreReadThroughTheService() {
        when(admin.searchAccounts("tom", null, 5)).thenReturn(List.of());
        when(admin.audit(42, 10, 2)).thenReturn(new AuditResponse(List.of()));
        when(admin.audit(null, 50, 0)).thenReturn(new AuditResponse(List.of()));

        harness.run((server, client) -> {
            var administrator = harness.as(TestSessions.administrator());
            assertEquals(
                    200,
                    client.get(PREFIX + "/admin/accounts/search?q=tom&limit=5", administrator)
                            .code());
            assertEquals(
                    200,
                    client.get(PREFIX + "/admin/2fa/audit?accountId=42&limit=10&offset=2", administrator)
                            .code());
            assertEquals(
                    200, client.get(PREFIX + "/admin/2fa/audit", administrator).code());
        });

        verify(admin).searchAccounts("tom", null, 5);
        verify(admin).audit(42, 10, 2);
        verify(admin).audit(null, 50, 0);
    }
}
