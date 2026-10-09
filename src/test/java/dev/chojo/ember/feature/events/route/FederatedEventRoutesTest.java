/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.api.refusal.EventRefusal;
import dev.chojo.ember.feature.events.entity.PartnerAgreementOffer;
import dev.chojo.ember.feature.events.entity.PartnerDocumentToSign;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.entity.SharedEvent;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.service.EventFederationService;
import dev.chojo.ember.feature.events.service.PartnerAppointmentSignatures;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.service.FederationService;
import dev.chojo.ember.feature.generator.entity.RequirementSignature;
import dev.chojo.ember.feature.generator.entity.RequirementSignatureState;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.service.StationMemberService;
import io.javalin.testtools.Request;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.body;
import static dev.chojo.ember.api.RouteHarness.json;
import static dev.chojo.ember.api.RouteHarness.refusalOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Registering for a partner's appointment names the partner the station holds, and a member's own
 * registrations include those of the members they look after.
 */
class FederatedEventRoutesTest {
    private static final int STATION = 3;
    private static final UUID PARTNER_UID = UUID.fromString("00000000-0000-0000-0000-000000000099");
    private static final UUID MEMBER_UID = UUID.fromString("00000000-0000-0000-0000-000000000011");
    private static final UUID WARD_UID = UUID.fromString("00000000-0000-0000-0000-000000000012");
    private static final LocalDate DAY = LocalDate.of(2026, 5, 1);
    private static final String REGISTER = PREFIX + "/federated/" + PARTNER_UID + "/events/4/register";
    private static final FederationPartner PARTNER = new FederationPartner(
            7,
            STATION,
            PARTNER_UID,
            null,
            null,
            null,
            FederationPartner.FederationStatus.ACTIVE,
            null,
            Instant.EPOCH,
            Instant.EPOCH,
            null,
            "Wache Nord");

    private EventFederationService events;
    private FederationService federation;
    private StationMemberService members;
    private RouteHarness harness;

    @BeforeEach
    void setup() {
        events = mock(EventFederationService.class);
        federation = mock(FederationService.class);
        members = mock(StationMemberService.class);
        when(federation.findPartnerByRemoteUid(STATION, PARTNER_UID)).thenReturn(Optional.of(PARTNER));
        harness = RouteHarness.serving(
                new FederatedEventRoutes(events, federation, members, PartnerAppointmentSignatures.NONE));
    }

    private Consumer<Request.Builder> registrar() {
        return harness.as(TestSessions.member(STATION, StationPermission.USER, StationPermission.EVENT_REGISTRATION));
    }

    @Test
    void aMemberIsRegisteredWithdrawnRestoredAndConfirmedAtTheNamedPartner() {
        when(events.registerForFederatedEvent(STATION, PARTNER_UID, 4, MEMBER_UID, DAY))
                .thenReturn(RegistrationStatus.ACCEPTED);
        var day = body("{\"eventDate\": \"2026-05-01\"}");

        harness.run((server, client) -> {
            assertEquals(
                    "ACCEPTED",
                    json(client.post(REGISTER, day, registrar())).path("status").asString());
            assertEquals(204, client.delete(REGISTER, day, registrar()).code());
            assertEquals(204, client.post(REGISTER + "/undo", day, registrar()).code());
            assertEquals(
                    204, client.post(REGISTER + "/confirm", day, registrar()).code());
        });

        verify(events).withdrawFederatedRegistration(STATION, PARTNER_UID, 4, MEMBER_UID, DAY);
        verify(events).undoFederatedWithdrawal(STATION, PARTNER_UID, 4, MEMBER_UID, DAY);
        verify(events).confirmOwnFederatedMember(STATION, PARTNER_UID, 4, MEMBER_UID, DAY);
    }

    /**
     * A partner's appointment without registrations lists the documents it offers and takes one on for the
     * member named; one that takes registrations refuses, since its agreement is signed on registering.
     */
    @Test
    void theAgreementOfAnAppointmentWithoutRegistrationsIsOfferedAndTakenOn() {
        var signatures = mock(PartnerAppointmentSignatures.class);
        harness = RouteHarness.serving(new FederatedEventRoutes(events, federation, members, signatures));
        var offer = new PartnerAgreementOffer(8, "Einverständnis");
        when(signatures.offers(any(), eq(PARTNER_UID), eq(4), eq(DAY))).thenReturn(List.of(offer));
        var toSign = new PartnerDocumentToSign(
                "Einverständnis",
                12,
                "Kim",
                new RequirementSignature(
                        8, 12, UUID.randomUUID(), RequirementSignatureState.OPEN, List.of(), false, null));
        when(signatures.offered(any(), eq(PARTNER_UID), eq(4), eq(DAY), eq(WARD_UID)))
                .thenReturn(List.of(toSign));
        when(events.getFederatedEvent(STATION, PARTNER_UID, 4)).thenReturn(detail(false));
        when(events.getFederatedEvent(STATION, PARTNER_UID, 5)).thenReturn(detail(true));
        var forWard = body("{\"eventDate\": \"2026-05-01\", \"memberId\": \"" + WARD_UID + "\"}");
        String events4 = PREFIX + "/federated/" + PARTNER_UID + "/events/4";

        harness.run((server, client) -> {
            assertEquals(
                    "Einverständnis",
                    json(client.get(events4 + "/agreements?date=2026-05-01", registrar()))
                            .path(0)
                            .path("title")
                            .asString());
            assertEquals(
                    "Kim",
                    json(client.post(events4 + "/agreement", forWard, registrar()))
                            .path(0)
                            .path("memberName")
                            .asString());
            assertEquals(
                    EventRefusal.AGREEMENT_SIGNED_ON_REGISTERING,
                    refusalOf(client.post(
                            PREFIX + "/federated/" + PARTNER_UID + "/events/5/agreement", forWard, registrar())));
        });
    }

    private static RemoteEventRoutes.RemoteEventDetail detail(boolean requiresRegistration) {
        var event = new SharedEvent(
                4,
                "Tag der offenen Tür",
                "",
                StationEvent.EventType.ONE_TIME,
                0,
                "",
                "",
                requiresRegistration,
                true,
                null,
                null);
        return new RemoteEventRoutes.RemoteEventDetail(event, List.of(), null);
    }

    @Test
    void aStationThatIsNoPartnerIsNotAsked() {
        var stranger = UUID.fromString("00000000-0000-0000-0000-000000000098");

        var answer = harness.request(client -> client.post(
                PREFIX + "/federated/" + stranger + "/events/4/register",
                body("{\"eventDate\": \"2026-05-01\"}"),
                registrar()));

        assertEquals(EventRefusal.PARTNER_NOT_HERE, refusalOf(answer));
        verifyNoInteractions(events);
    }

    @Test
    void theOwnRegistrationsIncludeThoseOfTheMembersLookedAfter() {
        var ward = new StationMember(
                12, STATION, WARD_UID, null, false, null, "Kim", StationUserType.MEMBER, LocalDate.EPOCH);
        when(members.findManaged(TestSessions.MEMBER_ID)).thenReturn(List.of(ward));
        when(events.findMyRegistrations(STATION, List.of(MEMBER_UID, WARD_UID)))
                .thenReturn(List.of(new RemoteEventRoutes.RemoteMemberRegistration(
                        4, WARD_UID.toString(), "2026-05-01", RegistrationStatus.ACCEPTED, 7)));

        var answer = harness.request(client -> client.get(PREFIX + "/federated/my-registrations", registrar()));

        assertEquals(
                WARD_UID.toString(), json(answer).path(0).path("remoteMemberId").asString());
        verify(events).findMyRegistrations(STATION, List.of(MEMBER_UID, WARD_UID));
    }
}
