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
import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.api.refusal.GeneralRefusal;
import dev.chojo.ember.feature.signing.entity.FieldRole;
import dev.chojo.ember.feature.signing.entity.FieldState;
import dev.chojo.ember.feature.signing.entity.ManagedSignatureRequest;
import dev.chojo.ember.feature.signing.entity.RequestState;
import dev.chojo.ember.feature.signing.entity.RequestedSignature;
import dev.chojo.ember.feature.signing.entity.SignatureAsk;
import dev.chojo.ember.feature.signing.entity.SignatureLevel;
import dev.chojo.ember.feature.signing.entity.SignatureRequest;
import dev.chojo.ember.feature.signing.entity.Signer;
import dev.chojo.ember.feature.signing.entity.SignerCapacity;
import dev.chojo.ember.feature.signing.entity.SigningAct;
import dev.chojo.ember.feature.signing.entity.SigningEvidence;
import dev.chojo.ember.feature.signing.entity.StoredEvidence;
import dev.chojo.ember.feature.signing.service.SignatureManagementService;
import dev.chojo.ember.feature.signing.service.SignatureRequestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.json;
import static dev.chojo.ember.api.RouteHarness.refusalOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Asking for signatures and looking after them over HTTP: only a manager of member documents reaches it, a
 * look asks nobody, asking answers the request with its fields, and each management action answers the
 * request as it then stands or the refusal by name.
 */
class SignatureRequestRoutesTest {
    private static final UUID REQUEST_UID = UUID.fromString("7b0d3e0c-9f4e-4f0e-8a51-0c9d6c8f2a11");
    private static final RequestedSignature.Draft GUARDIAN = new RequestedSignature.Draft(
            "guardian1", FieldRole.GUARDIAN, 5, "Gerda Erste", SignerCapacity.GUARDIAN, "Ich bin einverstanden.");

    private SignatureRequestService service;
    private SignatureManagementService management;
    private RouteHarness harness;

    @BeforeEach
    void setup() {
        service = mock(SignatureRequestService.class);
        when(service.requireOwnedAsk(any(StationSession.class), eq(11)))
                .thenReturn(SignatureAsk.notYet(List.of(GUARDIAN)));
        when(service.requireOwnedThenRequest(any(StationSession.class), eq(11)))
                .thenReturn(
                        new SignatureAsk(request(), List.of(new SignatureAsk.AskedField(GUARDIAN, FieldState.OPEN))));
        management = mock(SignatureManagementService.class);
        harness = RouteHarness.serving(new SignatureRequestRoutes(service, management));
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

    private static ManagedSignatureRequest managed(SignatureRequest request) {
        var nobody = new RequestedSignature(
                31,
                1,
                "guardian2",
                FieldRole.GUARDIAN,
                7,
                null,
                null,
                SignerCapacity.GUARDIAN,
                "Ich bin einverstanden.",
                FieldState.OPEN,
                null,
                null,
                null,
                null);
        var signedField = new RequestedSignature(
                30,
                1,
                "guardian1",
                FieldRole.GUARDIAN,
                7,
                5,
                "Gerda Erste",
                SignerCapacity.GUARDIAN,
                "Ich bin einverstanden.",
                FieldState.SIGNED,
                Instant.EPOCH,
                5,
                "Gerda Erste",
                null);
        var act = new SigningAct(
                REQUEST_UID,
                Signer.guardian(9, 7),
                "Gerda Erste",
                "Kim Kind",
                "guardian1",
                "Ich bin einverstanden.",
                new byte[32],
                List.of(),
                new byte[32],
                Instant.EPOCH,
                null,
                null);
        var evidence = new StoredEvidence(
                40,
                30,
                SignatureLevel.SIMPLE,
                new SigningEvidence.TotpUnbound(act),
                5,
                7,
                null,
                "b".repeat(64),
                Instant.EPOCH);
        return new ManagedSignatureRequest(
                request,
                "Einverständnis",
                null,
                List.of(
                        new ManagedSignatureRequest.ManagedField(signedField, false, evidence),
                        new ManagedSignatureRequest.ManagedField(nobody, true, null)),
                List.of(new ManagedSignatureRequest.Correction(12, "Einverständnis", Instant.EPOCH)));
    }

    /** The manager reads each field with its signer, state, whether anybody can sign it, and the act. */
    @Test
    void aManagerReadsTheRequestWithEachFieldAndItsAct() {
        when(management.requireOwnedView(any(StationSession.class), eq(REQUEST_UID)))
                .thenReturn(managed(request()));
        harness.run((server, client) -> {
            var manager = harness.as(TestSessions.member(3, StationPermission.DOCUMENT_EDIT_MEMBER));

            var response = client.get(PREFIX + "/signing/requests/" + REQUEST_UID, manager);

            assertEquals(200, response.code());
            var read = json(response);
            assertEquals(REQUEST_UID.toString(), read.path("uid").asString());
            assertEquals("Einverständnis", read.path("documentTitle").asString());
            assertEquals(20, read.path("documentId").asInt());
            var signedField = read.path("fields").get(0);
            assertEquals("SIGNED", signedField.path("state").asString());
            assertFalse(signedField.path("nobodyCanSign").asBoolean());
            var act = signedField.path("act");
            assertEquals("Gerda Erste", act.path("signerName").asString());
            assertEquals("TOTP", act.path("proof").asString());
            assertFalse(act.path("bound").asBoolean());
            assertTrue(act.path("userVerified").isNull());
            assertTrue(act.path("sealed").asBoolean());
            var nobody = read.path("fields").get(1);
            assertTrue(nobody.path("nobodyCanSign").asBoolean());
            assertTrue(nobody.path("act").isNull());
            assertTrue(nobody.path("signerName").isNull());
            assertEquals(
                    12, read.path("corrections").get(0).path("generationId").asInt());
        });
    }

    /** Each action settles through the service and answers the request as it now stands. */
    @Test
    void aManagerSettlesFieldsWithdrawsAndAsksAnew() {
        var answer = managed(request());
        when(management.requireOwnedThenConfirmOnPaper(any(StationSession.class), eq(REQUEST_UID), eq("guardian2")))
                .thenReturn(answer);
        when(management.requireOwnedThenWaive(any(StationSession.class), eq(REQUEST_UID), eq("guardian2")))
                .thenReturn(answer);
        when(management.requireOwnedThenWithdrawField(any(StationSession.class), eq(REQUEST_UID), eq("guardian2")))
                .thenReturn(answer);
        when(management.requireOwnedThenWithdraw(any(StationSession.class), eq(REQUEST_UID)))
                .thenReturn(answer);
        when(management.requireOwnedThenRectify(any(StationSession.class), eq(REQUEST_UID), eq(12)))
                .thenReturn(answer);
        harness.run((server, client) -> {
            var manager = harness.as(TestSessions.member(3, StationPermission.DOCUMENT_EDIT_MEMBER));
            String field = PREFIX + "/signing/requests/" + REQUEST_UID + "/fields/guardian2";

            assertEquals(200, client.post(field + "/paper", null, manager).code());
            assertEquals(200, client.post(field + "/waive", null, manager).code());
            assertEquals(200, client.post(field + "/withdraw", null, manager).code());
            assertEquals(
                    200,
                    client.post(PREFIX + "/signing/requests/" + REQUEST_UID + "/withdraw", null, manager)
                            .code());
            var rectified = client.post(
                    PREFIX + "/signing/requests/" + REQUEST_UID + "/rectify",
                    RouteHarness.body("{\"generationId\": 12}"),
                    manager);
            assertEquals(201, rectified.code());
            assertEquals(REQUEST_UID.toString(), json(rectified).path("uid").asString());
        });

        verify(management).requireOwnedThenConfirmOnPaper(any(StationSession.class), eq(REQUEST_UID), eq("guardian2"));
        verify(management).requireOwnedThenWaive(any(StationSession.class), eq(REQUEST_UID), eq("guardian2"));
        verify(management).requireOwnedThenWithdrawField(any(StationSession.class), eq(REQUEST_UID), eq("guardian2"));
        verify(management).requireOwnedThenWithdraw(any(StationSession.class), eq(REQUEST_UID));
        verify(management).requireOwnedThenRectify(any(StationSession.class), eq(REQUEST_UID), eq(12));
    }

    /** What the services refuse reaches the manager under its own code and status. */
    @Test
    void refusalsReachTheManagerByName() {
        when(management.requireOwnedThenRectify(any(StationSession.class), eq(REQUEST_UID), isNull()))
                .thenThrow(DocumentRefusal.SIGNING_CORRECTION_NOT_NAMED.raise());
        when(management.requireOwnedThenWaive(any(StationSession.class), eq(REQUEST_UID), eq("guardian1")))
                .thenThrow(DocumentRefusal.SIGNING_FIELD_NOT_OPEN.raise());
        when(management.requireOwnedView(any(StationSession.class), eq(REQUEST_UID)))
                .thenThrow(DocumentRefusal.SIGNING_REQUEST_NOT_FOUND.raise());
        harness.run((server, client) -> {
            var manager = harness.as(TestSessions.member(3, StationPermission.DOCUMENT_EDIT_MEMBER));

            var unnamed = client.post(
                    PREFIX + "/signing/requests/" + REQUEST_UID + "/rectify", RouteHarness.body("{}"), manager);
            assertEquals(400, unnamed.code());
            assertEquals(DocumentRefusal.SIGNING_CORRECTION_NOT_NAMED, refusalOf(unnamed));

            var settled =
                    client.post(PREFIX + "/signing/requests/" + REQUEST_UID + "/fields/guardian1/waive", null, manager);
            assertEquals(409, settled.code());
            assertEquals(DocumentRefusal.SIGNING_FIELD_NOT_OPEN, refusalOf(settled));

            var elsewhere = client.get(PREFIX + "/signing/requests/" + REQUEST_UID, manager);
            assertEquals(404, elsewhere.code());
            assertEquals(DocumentRefusal.SIGNING_REQUEST_NOT_FOUND, refusalOf(elsewhere));

            var malformed = client.get(PREFIX + "/signing/requests/not-a-uid", manager);
            assertEquals(GeneralRefusal.ADDRESS_NOT_AN_IDENTIFIER, refusalOf(malformed));
        });
    }

    /** Without the right to change member documents no management route is reached. */
    @Test
    void aMemberWithoutTheDocumentRightManagesNothing() {
        harness.run((server, client) -> {
            var member = harness.as(TestSessions.member(3, StationPermission.LOGIN));
            String request = PREFIX + "/signing/requests/" + REQUEST_UID;

            assertEquals(403, client.get(request, member).code());
            for (String path : List.of(
                    "/withdraw",
                    "/rectify",
                    "/fields/guardian1/paper",
                    "/fields/guardian1/waive",
                    "/fields/guardian1/withdraw")) {
                assertEquals(403, client.post(request + path, null, member).code(), path);
            }
        });

        verifyNoInteractions(management);
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
