/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.signing.entity.FieldRole;
import dev.chojo.ember.feature.signing.entity.FieldState;
import dev.chojo.ember.feature.signing.entity.RequestState;
import dev.chojo.ember.feature.signing.entity.RequestedSignature;
import dev.chojo.ember.feature.signing.entity.SignatureAsk;
import dev.chojo.ember.feature.signing.entity.SignatureRequest;
import dev.chojo.ember.feature.signing.entity.SignerCapacity;
import dev.chojo.ember.feature.signing.service.SignatureRequestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.json;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Asking for signatures over HTTP: only a manager of member documents reaches it, a look asks nobody, and
 * asking answers the request with its fields.
 */
class SignatureRequestRoutesTest {
    private static final UUID REQUEST_UID = UUID.fromString("7b0d3e0c-9f4e-4f0e-8a51-0c9d6c8f2a11");
    private static final RequestedSignature.Draft GUARDIAN = new RequestedSignature.Draft(
            "guardian1", FieldRole.GUARDIAN, 5, "Gerda Erste", SignerCapacity.GUARDIAN, "Ich bin einverstanden.");

    private SignatureRequestService service;
    private RouteHarness harness;

    @BeforeEach
    void setup() {
        service = mock(SignatureRequestService.class);
        when(service.requireOwnedAsk(any(StationSession.class), eq(11)))
                .thenReturn(SignatureAsk.notYet(List.of(GUARDIAN)));
        when(service.requireOwnedThenRequest(any(StationSession.class), eq(11)))
                .thenReturn(
                        new SignatureAsk(request(), List.of(new SignatureAsk.AskedField(GUARDIAN, FieldState.OPEN))));
        harness = RouteHarness.serving(new SignatureRequestRoutes(service));
    }

    private static SignatureRequest request() {
        return new SignatureRequest(
                1,
                REQUEST_UID,
                3,
                11,
                20,
                7,
                "Kim Kind",
                "a".repeat(64),
                RequestState.OPEN,
                null,
                48,
                null,
                Instant.EPOCH,
                2,
                null,
                true);
    }

    @Test
    void aManagerLooksFirstAndThenAsks() {
        harness.run((server, client) -> {
            var manager = harness.as(TestSessions.member(3, StationPermission.DOCUMENT_EDIT_MEMBER));

            var look = client.get(PREFIX + "/signing/generations/11", manager);
            assertEquals(200, look.code());
            var looked = json(look);
            assertTrue(looked.path("request").isNull());
            var field = looked.path("fields").get(0);
            assertEquals("guardian1", field.path("fieldName").asString());
            assertEquals("Gerda Erste", field.path("signerName").asString());
            assertEquals("Ich bin einverstanden.", field.path("statement").asString());
            assertTrue(field.path("state").isNull());

            var asked = client.post(PREFIX + "/signing/generations/11/request", null, manager);
            assertEquals(201, asked.code());
            var answer = json(asked);
            assertEquals(
                    REQUEST_UID.toString(), answer.path("request").path("uid").asString());
            assertEquals(48, answer.path("request").path("retentionMonths").asInt());
            assertTrue(answer.path("request").path("copyAttached").asBoolean());
            assertEquals("OPEN", answer.path("fields").get(0).path("state").asString());
        });

        verify(service).requireOwnedThenRequest(any(StationSession.class), eq(11));
    }

    @Test
    void aMemberWithoutTheDocumentRightAsksNothing() {
        harness.run((server, client) -> {
            var member = harness.as(TestSessions.member(3, StationPermission.LOGIN));

            assertEquals(
                    403, client.get(PREFIX + "/signing/generations/11", member).code());
            assertEquals(
                    403,
                    client.post(PREFIX + "/signing/generations/11/request", null, member)
                            .code());
        });

        verify(service, never()).requireOwnedAsk(any(StationSession.class), eq(11));
        verify(service, never()).requireOwnedThenRequest(any(StationSession.class), eq(11));
    }
}
