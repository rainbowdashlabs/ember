/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.EventRefusal;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.route.EventVisibility;
import dev.chojo.ember.feature.generator.entity.RequirementSignature;
import dev.chojo.ember.feature.generator.entity.RequirementSignatureState;
import dev.chojo.ember.feature.signing.entity.AgreementSigner;
import dev.chojo.ember.feature.signing.entity.DocumentAgreement;
import dev.chojo.ember.feature.signing.entity.RequestState;
import dev.chojo.ember.feature.signing.entity.SignatureWithdrawal;
import dev.chojo.ember.feature.signing.entity.SignerCapacity;
import dev.chojo.ember.feature.signing.entity.SigningCircumstances;
import dev.chojo.ember.feature.signing.service.AgreementOffers;
import dev.chojo.ember.feature.signing.service.AgreementSigners;
import dev.chojo.ember.feature.signing.service.SignatureWithdrawals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The agreement routes over HTTP: signing an appointment's agreement from its page, the signers for whoever
 * runs it, and withdrawing an agreement, each handing the request to its service and answering what it
 * returned, or its refusal.
 */
class AgreementRoutesTest {
    private static final int STATION_ID = 3;
    private static final int EVENT_ID = 9;
    private static final LocalDate DAY = LocalDate.parse("2026-10-10");
    private static final UUID REQUEST = UUID.fromString("0b9f5c1e-8f6d-4a39-9d55-2c1b7f3d4e10");
    private static final Instant AT = Instant.parse("2026-10-09T10:00:00Z");

    private AgreementOffers offers;
    private AgreementSigners signers;
    private SignatureWithdrawals withdrawals;
    private StationEvent event;
    private RouteHarness harness;

    @BeforeEach
    void setup() {
        offers = mock(AgreementOffers.class);
        signers = mock(AgreementSigners.class);
        withdrawals = mock(SignatureWithdrawals.class);
        var visibility = mock(EventVisibility.class);
        event = mock(StationEvent.class);
        when(event.id()).thenReturn(EVENT_ID);
        when(visibility.requireVisibleEvent(any(), eq(EVENT_ID))).thenReturn(event);
        harness = RouteHarness.serving(new AgreementRoutes(offers, signers, withdrawals, visibility));
    }

    @Test
    void anAgreementIsOfferedForTheDateAndMember() {
        var offered = new RequirementSignature(8, 11, REQUEST, RequirementSignatureState.OPEN, List.of(), false, null);
        when(offers.offer(any(), eq(event), eq(DAY), eq(8), eq(11))).thenReturn(offered);

        var response = harness.request(client -> client.post(
                RouteHarness.PREFIX
                        + "/events/%d/documents-to-bring/8/members/11/agreement?date=%s".formatted(EVENT_ID, DAY),
                RouteHarness.body("{}"),
                harness.as(member(StationPermission.LOGIN))));

        assertEquals(200, response.code());
        assertEquals(
                REQUEST, RouteHarness.read(response, RequirementSignature.class).requestUid());
    }

    @Test
    void aRefusedOfferAnswersItsRefusal() {
        when(offers.offer(any(), any(), any(), anyInt(), anyInt()))
                .thenThrow(EventRefusal.AGREEMENT_SIGNED_ON_REGISTERING.raise());

        var response = harness.request(client -> client.post(
                RouteHarness.PREFIX
                        + "/events/%d/documents-to-bring/8/members/11/agreement?date=%s".formatted(EVENT_ID, DAY),
                RouteHarness.body("{}"),
                harness.as(member(StationPermission.LOGIN))));

        assertEquals(EventRefusal.AGREEMENT_SIGNED_ON_REGISTERING, RouteHarness.refusalOf(response));
    }

    @Test
    void whoeverRunsTheAppointmentReadsTheSigners() {
        when(signers.of(EVENT_ID, DAY))
                .thenReturn(List.of(new AgreementSigner(
                        11, null, "Lena", 8, "Einverständnis", RequirementSignatureState.SIGNED, AT, null, false)));

        var response = harness.request(client -> client.get(
                RouteHarness.PREFIX + "/events/%d/agreement-signers?date=%s".formatted(EVENT_ID, DAY),
                harness.as(member(StationPermission.EVENT_REGISTRATION))));

        assertEquals(200, response.code());
        assertEquals("Lena", RouteHarness.json(response).get(0).get("name").asText());
    }

    @Test
    void anAgreementIsWithdrawnWithTheReasonAndWhereItCameFrom() {
        when(withdrawals.requireOwnedThenWithdraw(any(), eq(REQUEST), eq("Krank"), any(SigningCircumstances.class)))
                .thenReturn(new SignatureWithdrawal(
                        1, 2, 11, 11, "Lena", SignerCapacity.ACCOUNT_HOLDER, "Krank", AT, null, null, null));

        var response = harness.request(client -> client.post(
                RouteHarness.PREFIX + "/signing/requests/%s/withdrawal".formatted(REQUEST),
                RouteHarness.body("{\"reason\": \"Krank\"}"),
                harness.as(member(StationPermission.LOGIN))));

        assertEquals(200, response.code());
        assertEquals(
                AT.toString(), RouteHarness.json(response).get("withdrawnAt").asText());
        verify(withdrawals).requireOwnedThenWithdraw(any(), eq(REQUEST), eq("Krank"), any(SigningCircumstances.class));
    }

    @Test
    void theAgreementsOfADocumentAreListed() {
        when(withdrawals.requireOwnedAgreements(any(), eq(40)))
                .thenReturn(List.of(new DocumentAgreement(REQUEST, RequestState.COMPLETE, true, null, null)));

        var response = harness.request(client -> client.get(
                RouteHarness.PREFIX + "/signing/documents/40/agreements", harness.as(member(StationPermission.LOGIN))));

        assertEquals(200, response.code());
        assertEquals(
                REQUEST.toString(),
                RouteHarness.json(response).get(0).get("requestUid").asText());
    }

    private static UserSession member(StationPermission permission) {
        return TestSessions.member(STATION_ID, StationPermission.USER, permission);
    }
}
