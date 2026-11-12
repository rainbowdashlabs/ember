/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.feature.events.entity.EventChangePart;
import dev.chojo.ember.feature.events.entity.EventDraft;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventFederationRepository;
import dev.chojo.ember.feature.events.repository.EventHandoverRepository;
import dev.chojo.ember.feature.federation.FederationTestTransport;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.entity.ShareScope;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.federation.service.FederationHttpClient;
import dev.chojo.ember.feature.federation.service.FederationService;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.entity.StationAudience;
import dev.chojo.ember.feature.notifications.service.Notifier;
import dev.chojo.ember.feature.signing.repository.PartnerSigningRepository;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import dev.chojo.ember.util.TestFederationServices;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** A shared appointment that changes tells the partner stations whose members hold a place on it. */
class FederatedEventChangeServiceTest extends RepositoryTestBase {

    private static FederationRepository federationRepo;
    private static EventFederationRepository eventFederationRepo;
    private static FederationTestTransport transport;
    private static Station host;
    private static Station guest;
    private static StationMember guestMember;
    private static FederationPartner guestAtHost;
    private static final List<Integer> accounts = new ArrayList<>();

    private final List<Integer> events = new ArrayList<>();
    private final PartnerSigningRepository signups = new PartnerSigningRepository();
    private Notifier notifications;
    private FederatedEventChangeService service;
    private LocalDate today;
    private ZoneId zone;

    @BeforeAll
    static void setup() {
        federationRepo = new FederationRepository();
        eventFederationRepo = new EventFederationRepository();
        FederationService federationService = TestFederationServices.of(federationRepo, stationRepo);
        var httpClient = mock(FederationHttpClient.class);
        when(httpClient.canSign(anyInt())).thenReturn(true);
        transport = new FederationTestTransport(httpClient, federationRepo, stationRepo);
        host = stationRepo.create("Federated Change Host");
        guest = stationRepo.create("Federated Change Guest");
        var keyPair = federationService.generateKeyPair();
        federationService.acceptInvite(host.id(), guest.id(), federationService.encodePublicKey(keyPair), null, null);
        guestAtHost = federationRepo
                .findPartnerByStationAndRemoteUid(host.id(), guest.uid())
                .orElseThrow();
        var account = accountRepo.create("guest@federated-change.test", "Gina", "Guest");
        accounts.add(account.id());
        guestMember = stationMemberRepo.create(guest.id(), account.id());
    }

    @AfterAll
    static void cleanup() {
        for (var partner : federationRepo.findPartners(host.id())) federationRepo.deletePartner(partner.id());
        for (var partner : federationRepo.findPartners(guest.id())) federationRepo.deletePartner(partner.id());
        stationRepo.delete(host.id());
        stationRepo.delete(guest.id());
        accounts.forEach(accountRepo::delete);
    }

    @BeforeEach
    void wire() {
        notifications = mock(Notifier.class);
        service = new FederatedEventChangeService(
                eventFederationRepo,
                federationRepo,
                transport.transport(),
                stationMemberRepo,
                memberNameResolver,
                notifications,
                signups);
        transport.serve(service);
        today = occurrenceCalendar.forStation(host.id()).today();
        zone = occurrenceCalendar.zoneOf(host.id());
    }

    @AfterEach
    void removeEvents() {
        events.forEach(eventRepo::delete);
        events.clear();
    }

    private StationEvent weekly() {
        LocalDate first = today.minusWeeks(2).with(TemporalAdjusters.nextOrSame(DayOfWeek.TUESDAY));
        Instant start = first.atTime(18, 0).atZone(zone).toInstant();
        var event = eventRepo.create(
                host.id(),
                "Gemeinsame Übung",
                null,
                StationEvent.EventType.RECURRING,
                DayOfWeek.TUESDAY.getValue(),
                start,
                start.plus(2, ChronoUnit.HOURS),
                null,
                true,
                null,
                false,
                null,
                null,
                null,
                null,
                null);
        events.add(event.id());
        return event;
    }

    private LocalDate nextTuesday() {
        return today.plusDays(1).with(TemporalAdjusters.nextOrSame(DayOfWeek.TUESDAY));
    }

    /** Records at the guest that it signed its member up for this date, as signing up there does. */
    private void signedUpAtGuest(StationEvent event, LocalDate date) {
        signups.recordSignup(guest.id(), guestMember.id(), host.uid(), event.id(), date);
    }

    private void verifyGuestTold(NotificationType type) {
        verify(notifications)
                .notify(
                        eq(StationAudience.household(List.of(guestMember.id()))),
                        eq(type),
                        any(NotificationData.class),
                        any());
    }

    @Test
    void aNoticeReachesThePartnersMembersThroughTheirStation() {
        var event = weekly();
        eventFederationRepo.createRegistration(
                event.id(),
                guestAtHost.id(),
                guestMember.uid(),
                nextTuesday().plusWeeks(1),
                RegistrationStatus.ACCEPTED);
        eventFederationRepo.createRegistration(
                event.id(),
                guestAtHost.id(),
                guestMember.uid(),
                nextTuesday().minusWeeks(1),
                RegistrationStatus.ACCEPTED);
        signedUpAtGuest(event, nextTuesday().plusWeeks(1));

        service.tellPartners(event, nextTuesday(), "DESCRIPTION", "  Helm  ", null, true);

        verify(notifications)
                .notify(
                        eq(StationAudience.household(List.of(guestMember.id()))),
                        eq(NotificationType.EVENT_CHANGED),
                        argThat((NotificationData data) -> data.params()
                                        instanceof NotificationParams.EventChangeNotice notice
                                && notice.eventDate().equals(nextTuesday().plusWeeks(1))
                                && notice.parts().equals(List.of(EventChangePart.DESCRIPTION))
                                && "Helm".equals(notice.note())
                                && data.link().route().equals("federated-event-detail")),
                        any());
    }

    @Test
    void withoutANoticeOnlyAMovedStartIsToldAndNothingElse() {
        var event = weekly();
        eventFederationRepo.createRegistration(
                event.id(), guestAtHost.id(), guestMember.uid(), nextTuesday(), RegistrationStatus.PENDING);
        signedUpAtGuest(event, nextTuesday());

        service.tellPartners(event, nextTuesday(), "", null, null, false);
        verify(notifications, never()).notify(any(), any(), any(), any());

        service.tellPartners(event, nextTuesday(), "TIME", null, "19:00", false);
        verifyGuestTold(NotificationType.EVENT_MOVED);
    }

    /** A partner is believed about this station's members only where this station signed them up. */
    @Test
    void aMemberTheGuestNeverSignedUpHearsNothing() {
        var event = weekly();
        eventFederationRepo.createRegistration(
                event.id(), guestAtHost.id(), guestMember.uid(), nextTuesday(), RegistrationStatus.ACCEPTED);

        service.tellPartners(event, nextTuesday(), "DESCRIPTION", "Hallo", null, true);

        verify(notifications, never()).notify(any(), any(), any(), any());
    }

    @Test
    void aWithdrawnPartnerPlaceHearsNothing() {
        var event = weekly();
        eventFederationRepo.createRegistration(
                event.id(), guestAtHost.id(), guestMember.uid(), nextTuesday(), RegistrationStatus.WITHDRAWN);

        service.tellPartners(event, nextTuesday(), "TIME", null, "19:00", true);

        verify(notifications, never()).notify(any(), any(), any(), any());
    }

    /** A shared series that goes on as a new one is shared the same way, and partners' places go along. */
    @Test
    void aSplitSharesTheNewSeriesAndTakesThePartnersPlaces() {
        var event = weekly();
        var share = eventFederationRepo.setShare(event.id(), ShareScope.SPECIFIC);
        eventFederationRepo.setShareTargets(share.id(), List.of(guestAtHost.id()));
        eventFederationRepo.setPartnerPlaces(event.id(), guestAtHost.id(), 3, true);
        LocalDate from = nextTuesday().plusWeeks(1);
        var before = eventFederationRepo.createRegistration(
                event.id(), guestAtHost.id(), guestMember.uid(), nextTuesday(), RegistrationStatus.ACCEPTED);
        var after = eventFederationRepo.createRegistration(
                event.id(), guestAtHost.id(), guestMember.uid(), from, RegistrationStatus.ACCEPTED);
        signedUpAtGuest(event, nextTuesday());
        signedUpAtGuest(event, from);
        var moveService = new EventMoveService(
                eventRegistrationRepo,
                eventFieldRepo,
                eventReminderRepo,
                eventDateCancellationRepo,
                occurrenceCalendar,
                memberNameResolver,
                notifications);
        var scope = new EventEditScopeService(
                eventRepo,
                newEventServices(new DomainEventBus(Set.of())).crud(),
                new EventHandoverRepository(),
                eventDetachedDateRepo,
                occurrenceCalendar,
                moveService,
                service);
        var draft = new EventDraft(
                event.name(),
                null,
                event.eventType(),
                event.dayOfWeek(),
                event.startTime(),
                event.endTime(),
                null,
                true,
                null,
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);

        var continued = scope.split(event, from, draft);
        events.add(continued.id());

        var copied = eventFederationRepo.findShareByEvent(continued.id()).orElseThrow();
        assertEquals(ShareScope.SPECIFIC, copied.scope());
        assertEquals(List.of(guestAtHost.id()), eventFederationRepo.findShareTargets(copied.id()));
        assertEquals(
                3,
                eventFederationRepo
                        .findPartnerPlaces(continued.id(), guestAtHost.id())
                        .slotBudget());
        assertEquals(
                event.id(),
                eventFederationRepo
                        .findRegistrationById(before.id())
                        .orElseThrow()
                        .eventId());
        assertEquals(
                continued.id(),
                eventFederationRepo
                        .findRegistrationById(after.id())
                        .orElseThrow()
                        .eventId());
        assertTrue(eventFederationRepo.findShareByEvent(event.id()).isPresent(), "the old series stays shared");
        assertTrue(
                signups.signedUp(guestMember.id(), host.uid(), continued.id(), from), "the guest follows the handover");
        assertTrue(signups.signedUp(guestMember.id(), host.uid(), event.id(), nextTuesday()), "earlier dates stay");
    }
}
