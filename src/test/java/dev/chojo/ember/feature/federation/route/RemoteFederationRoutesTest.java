/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.route;

import dev.chojo.ember.api.FederationSession;
import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.refusal.FederationRefusal;
import dev.chojo.ember.feature.events.service.EventFederationService;
import dev.chojo.ember.feature.federation.entity.ChangeType;
import dev.chojo.ember.feature.federation.entity.ContentType;
import dev.chojo.ember.feature.federation.entity.FederationChangeLog;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.service.FederationEnrollmentService;
import dev.chojo.ember.feature.federation.service.FederationService;
import dev.chojo.ember.feature.federation.service.RemoteUrlValidator;
import dev.chojo.ember.feature.storage.service.StationReadOnlyGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.body;
import static dev.chojo.ember.api.RouteHarness.json;
import static dev.chojo.ember.api.RouteHarness.refusalOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A partner registers its webhook and polls for changes through the service, signed as the
 * partnership it arrives on.
 */
class RemoteFederationRoutesTest {
    private static final UUID ASKING = UUID.fromString("00000000-0000-0000-0000-000000000099");
    private static final FederationPartner PARTNER = new FederationPartner(
            7,
            3,
            ASKING,
            null,
            null,
            null,
            FederationPartner.FederationStatus.ACTIVE,
            null,
            Instant.EPOCH,
            Instant.EPOCH,
            "https://asking.example",
            "Asking");

    private FederationService federation;
    private RemoteUrlValidator urls;
    private RouteHarness harness;

    @BeforeEach
    void setup() {
        federation = mock(FederationService.class);
        urls = mock(RemoteUrlValidator.class);
        harness = RouteHarness.serving(new RemoteFederationRoutes(
                federation,
                mock(FederationEnrollmentService.class),
                mock(EventFederationService.class),
                urls,
                mock(StationReadOnlyGuard.class)));
    }

    @Test
    void anAllowedWebhookIsRememberedForThePartnership() {
        when(urls.isAllowed("https://asking.example/hook")).thenReturn(true);
        var partner = harness.asPartner(new FederationSession(PARTNER, ASKING));

        var answer = harness.request(client -> client.post(
                PREFIX + "/remote/webhook/register",
                body("{\"webhookUrl\": \"https://asking.example/hook\"}"),
                partner));

        assertEquals(200, answer.code());
        verify(federation).registerWebhook(7, "https://asking.example/hook");
    }

    @Test
    void aWebhookThisInstanceWillNotCallIsRefused() {
        var partner = harness.asPartner(new FederationSession(PARTNER, ASKING));

        var answer = harness.request(client -> client.post(
                PREFIX + "/remote/webhook/register", body("{\"webhookUrl\": \"http://10.0.0.1/\"}"), partner));

        assertEquals(FederationRefusal.WEBHOOK_ADDRESS_NOT_ALLOWED, refusalOf(answer));
        verify(federation, never()).registerWebhook(any(Integer.class), any());
    }

    @Test
    void aPollAnswersTheChangesSinceTheMomentGiven() {
        var since = Instant.parse("2026-01-01T00:00:00Z");
        when(federation.syncChanges(PARTNER, since))
                .thenReturn(List.of(new FederationChangeLog(1, 3, ContentType.KB, 5, ChangeType.UPDATED, since)));
        var partner = harness.asPartner(new FederationSession(PARTNER, ASKING));

        var answer = harness.request(
                client -> client.get(PREFIX + "/remote/sync/metadata?since=2026-01-01T00:00:00Z", partner));

        assertEquals(5, json(answer).path(0).path("contentId").asInt());
    }
}
