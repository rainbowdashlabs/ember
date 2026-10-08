/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.legal.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.legal.entity.DocumentVersion;
import dev.chojo.ember.feature.legal.entity.DocumentVersions;
import dev.chojo.ember.feature.legal.service.ConsentService;
import dev.chojo.ember.feature.legal.service.ConsentStatusService;
import dev.chojo.ember.feature.legal.service.ConsentStatusService.ConsentChangesResponse;
import dev.chojo.ember.feature.legal.service.ConsentStatusService.ConsentStatusResponse;
import org.junit.jupiter.api.Test;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.json;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConsentRoutesTest {
    @Test
    void theStatusAndTheChangesAreTheSignedInAccountsOwn() {
        var status = mock(ConsentStatusService.class);
        when(status.status(TestSessions.ACCOUNT_ID))
                .thenReturn(new ConsentStatusResponse(true, true, "c", "p", "t", null, "p", "t", "c"));
        when(status.changes(TestSessions.ACCOUNT_ID, "en"))
                .thenReturn(
                        new ConsentChangesResponse(true, false, false, "+", null, "<p/>", null, null, "p", "t", "c"));
        when(status.changes(TestSessions.ACCOUNT_ID, "de"))
                .thenReturn(
                        new ConsentChangesResponse(false, false, true, null, null, null, null, "<c/>", "p", "t", "c"));
        var harness = RouteHarness.serving(new ConsentRoutes(mock(ConsentService.class), status));

        harness.run((server, client) -> {
            var member = harness.as(TestSessions.member(3, StationPermission.LOGIN));
            assertTrue(json(client.get(PREFIX + "/session/consent", member))
                    .path("current")
                    .asBoolean());
            assertTrue(json(client.get(PREFIX + "/session/consent/changes?lang=en", member))
                    .path("privacyChanged")
                    .asBoolean());
            assertEquals(
                    false,
                    json(client.get(PREFIX + "/session/consent/changes", member))
                            .path("privacyChanged")
                            .asBoolean());
            assertTrue(json(client.get(PREFIX + "/session/consent/changes", member))
                    .path("consentChanged")
                    .asBoolean());
        });
    }

    @Test
    void theLegalVersionsNameTheLegacyVersionsStillAccepted() {
        var consents = mock(ConsentService.class);
        when(consents.getCurrentVersions())
                .thenReturn(new DocumentVersions(
                        new DocumentVersion("p", "p-legacy"),
                        new DocumentVersion("t", "t-legacy"),
                        new DocumentVersion("c", "c-legacy")));
        var harness = RouteHarness.serving(new ConsentRoutes(consents, mock(ConsentStatusService.class)));

        harness.run((server, client) -> {
            var versions = json(client.get(PREFIX + "/public/legal-versions"));
            assertEquals("c", versions.path("consentVersion").asString());
            assertEquals("c-legacy", versions.path("legacyConsentVersion").asString());
            assertEquals("p-legacy", versions.path("legacyPrivacyVersion").asString());
            assertEquals("t-legacy", versions.path("legacyTosVersion").asString());
        });
    }
}
