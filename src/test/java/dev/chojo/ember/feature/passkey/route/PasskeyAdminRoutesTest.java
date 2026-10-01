/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.passkey.route;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.conf.file.elements.PasskeySettings;
import dev.chojo.ember.feature.passkey.entity.PasswordlessReport;
import dev.chojo.ember.feature.passkey.entity.ResidueEntry;
import dev.chojo.ember.feature.passkey.service.PasskeyAdminService;
import dev.chojo.ember.feature.passkey.service.PasskeyAdminService.BulkRetireResult;
import dev.chojo.ember.feature.passkey.service.PasskeyAdminService.RetireOutcome;
import dev.chojo.ember.feature.passkey.service.PasskeyAdminService.SetModeResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.body;
import static dev.chojo.ember.api.RouteHarness.json;
import static dev.chojo.ember.api.RouteHarness.refusalOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The operator's report and the retiring of passwords, over HTTP.
 */
class PasskeyAdminRoutesTest {
    private final PasskeyAdminService admin = mock(PasskeyAdminService.class);
    private final RouteHarness harness = RouteHarness.serving(new PasskeyAdminRoutes(admin));

    @Test
    void theReportAndTheResidueAreAnsweredAsTheServiceHasThem() {
        when(admin.passwordlessReport()).thenReturn(new PasswordlessReport(4, 3, 2, 1));
        when(admin.residue()).thenReturn(List.of(new ResidueEntry(7, "Mara", "Nager", null, true, false)));

        harness.run((server, client) -> {
            var administrator = harness.as(TestSessions.administrator());
            var report = json(client.get(PREFIX + "/admin/config/auth/passkeys/report", administrator));
            assertEquals(2, report.path("reachableOnlyByQr").asInt());
            var residue = json(client.get(PREFIX + "/admin/config/auth/passkeys/residue", administrator));
            assertTrue(residue.get(0).path("reachable").asBoolean());
            assertEquals("Nager", residue.get(0).path("lastName").asString());
        });
    }

    @Test
    void retiringAPasswordSaysWhatBecameOfIt() {
        when(admin.retirePassword(eq(7), any(), any(), any())).thenReturn(RetireOutcome.RETIRED);
        when(admin.retirePassword(eq(8), any(), any(), any())).thenReturn(RetireOutcome.NO_PASSWORD);
        when(admin.retirePassword(eq(9), any(), any(), any())).thenReturn(RetireOutcome.NO_TRIED_PASSKEY);
        when(admin.retireAllEligible(any(), any(), any())).thenReturn(new BulkRetireResult(3, 1));

        harness.run((server, client) -> {
            var administrator = harness.as(TestSessions.administrator());
            var retired = client.post(PREFIX + "/admin/accounts/7/password/retire", null, administrator);
            assertEquals("Password retired", json(retired).path("message").asString());
            assertEquals(
                    Refusal.ACCOUNT_HOLDS_NO_PASSWORD_ON_RETIRE,
                    refusalOf(client.post(PREFIX + "/admin/accounts/8/password/retire", null, administrator)));
            assertEquals(
                    Refusal.NO_TRIED_PASSKEY_ON_RETIRE,
                    refusalOf(client.post(PREFIX + "/admin/accounts/9/password/retire", null, administrator)));
            var all = client.post(PREFIX + "/admin/config/auth/passkeys/retire-all", null, administrator);
            assertEquals(3, json(all).path("retired").asInt());
        });
    }

    @Test
    void theModeIsOneOfTheModesTheInstanceHas() {
        when(admin.setMode(PasskeySettings.Mode.PASSWORDLESS))
                .thenReturn(new SetModeResult(SetModeResult.Outcome.NO_MAIL_PROOF, 0));

        harness.run((server, client) -> {
            var administrator = harness.as(TestSessions.administrator());
            String path = PREFIX + "/admin/config/auth/passkeys";
            assertEquals(
                    Refusal.PASSWORDLESS_NEEDS_WORKING_MAIL,
                    refusalOf(client.put(path, body("{\"mode\": \"PASSWORDLESS\"}"), administrator)));
            assertEquals(Refusal.PASSKEY_MODE_UNKNOWN, refusalOf(client.put(path, body("{}"), administrator)));
            assertEquals(
                    Refusal.BODY_DOES_NOT_MATCH,
                    refusalOf(client.put(path, body("{\"mode\": \"SOMETIMES\"}"), administrator)));
        });
    }
}
