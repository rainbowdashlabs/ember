/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.feature.signing.entity.LockedSigningKey;
import dev.chojo.ember.feature.signing.entity.SigningKeyKind;
import dev.chojo.ember.feature.signing.entity.SigningKeyRecoveryEntry;
import dev.chojo.ember.feature.signing.entity.SigningKeyStatus;
import dev.chojo.ember.feature.signing.service.SigningKeyRecovery;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.body;
import static dev.chojo.ember.api.RouteHarness.json;
import static dev.chojo.ember.api.RouteHarness.refusalOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The administrator's signing key routes over HTTP: only an instance administrator sees the keys and
 * gives them up, and giving up hands the serial numbers they confirmed to the recovery as sent.
 */
class SigningKeyAdminRoutesTest {
    private static final Instant NOW = Instant.parse("2026-10-08T10:00:00Z");

    private final SigningKeyRecovery recovery = mock(SigningKeyRecovery.class);
    private final RouteHarness harness = RouteHarness.serving(new SigningKeyAdminRoutes(recovery));

    @Test
    void anAdministratorSeesTheKeysThatNoLongerOpen() {
        when(recovery.status())
                .thenReturn(new SigningKeyStatus(
                        List.of(new LockedSigningKey(SigningKeyKind.AUTHORITY, "ab12", "AB:12", true, null, NOW)),
                        0,
                        List.of()));

        harness.run((server, client) -> {
            var response = client.get(PREFIX + "/admin/signing/keys", harness.as(TestSessions.administrator()));
            assertEquals(200, response.code());
            var locked = json(response).path("locked").get(0);
            assertEquals("AUTHORITY", locked.path("kind").asString());
            assertEquals("ab12", locked.path("serialNumber").asString());
        });
    }

    @Test
    void onlyAnAdministratorReachesTheKeys() {
        harness.run((server, client) -> {
            var member = harness.as(TestSessions.member(3, StationPermission.STATION_ADMINISTRATOR));
            assertEquals(403, client.get(PREFIX + "/admin/signing/keys", member).code());
            assertEquals(
                    403,
                    client.post(PREFIX + "/admin/signing/keys/recover", body("{\"serialNumbers\":[\"ab12\"]}"), member)
                            .code());
        });
        verify(recovery, never()).recover(anyInt(), anyList());
    }

    @Test
    void givingUpHandsOnTheConfirmedSerialNumbers() {
        when(recovery.recover(TestSessions.ACCOUNT_ID, List.of("ab12", "cd34")))
                .thenReturn(new SigningKeyRecoveryEntry(1, NOW, "Ada Admin", List.of("ab12"), List.of("cd34")));

        harness.run((server, client) -> {
            var response = client.post(
                    PREFIX + "/admin/signing/keys/recover",
                    body("{\"serialNumbers\":[\"ab12\",\"cd34\"]}"),
                    harness.as(TestSessions.administrator()));
            assertEquals(200, response.code());
            assertEquals("Ada Admin", json(response).path("recoveredBy").asString());
        });
    }

    @Test
    void aRecoveryWithoutSerialNumbersConfirmsNone() {
        when(recovery.recover(TestSessions.ACCOUNT_ID, List.of()))
                .thenThrow(DocumentRefusal.SIGNING_KEYS_CHANGED.raise());

        harness.run((server, client) -> {
            var response = client.post(
                    PREFIX + "/admin/signing/keys/recover", body("{}"), harness.as(TestSessions.administrator()));
            assertEquals(DocumentRefusal.SIGNING_KEYS_CHANGED, refusalOf(response));
        });
    }
}
