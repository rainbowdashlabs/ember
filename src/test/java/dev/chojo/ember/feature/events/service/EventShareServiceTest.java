/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.api.refusal.EventRefusal;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.feature.events.entity.EventGroupDetails;
import dev.chojo.ember.feature.events.entity.RegistrationDeadline;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventGroupRepository;
import dev.chojo.ember.feature.federation.entity.ShareScope;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import dev.chojo.ember.feature.restriction.RestrictionSelection;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** How this station shares its own appointments, what each partner may decide, and the names partners' members go by. */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EventShareServiceTest extends EventFederationTestBase {
    @Test
    @Order(1)
    void setShareAllPartners() {
        var share = shareService.setShare(eventId, ShareScope.ALL_PARTNERS, List.of());
        assertNotNull(share);
        assertEquals(eventId, share.eventId());
        assertEquals(ShareScope.ALL_PARTNERS, share.scope());
    }

    @Test
    @Order(2)
    void findShareByEvent() {
        var found = shareService.findShareByEvent(eventId);
        assertTrue(found.isPresent());
        assertEquals(ShareScope.ALL_PARTNERS, found.get().scope());
    }

    @Test
    @Order(3)
    void setShareSpecificWithTargets() {
        var share = shareService.setShare(eventId, ShareScope.SPECIFIC, List.of(partnerId));
        assertNotNull(share);
        assertEquals(ShareScope.SPECIFIC, share.scope());

        var targets = shareService.findShareTargets(share.id());
        assertTrue(targets.contains(partnerId));
    }

    @Test
    @Order(4)
    void findSharedEventIds() {
        var sharedIds = shareService.findSharedEventIds(partnerId, stationA.id());
        assertTrue(sharedIds.contains(eventId));
    }

    @Test
    @Order(5)
    void findSharedEventIdsAllPartners() {
        shareService.setShare(eventId, ShareScope.ALL_PARTNERS, List.of());
        var sharedIds = shareService.findSharedEventIds(partnerId, stationA.id());
        assertTrue(sharedIds.contains(eventId));
    }

    /**
     * An appointment not every member here may know about is withdrawn from the partners, and the
     * order the two settings were made in does not matter. The audience names groups of this station,
     * which mean nothing at the partner, so a shared one would stand open to everybody there.
     */
    @Test
    @Order(5)
    void restrictingVisibilityWithdrawsAShare() {
        shareService.setShare(eventId, ShareScope.ALL_PARTNERS, List.of());
        var eventRestrictions = newEventServices(new DomainEventBus(Set.of())).restriction();
        eventRestrictions.setViewRestrictions(
                eventId,
                new RestrictionSelection(
                        List.of(StationUserType.GUARDIAN), List.of(), List.of(), List.of(), RestrictionMode.AND));

        assertFalse(shareService.findSharedEventIds(partnerId, stationA.id()).contains(eventId));

        eventRestrictions.setViewRestrictions(eventId, RestrictionSelection.empty());
        assertTrue(shareService.findSharedEventIds(partnerId, stationA.id()).contains(eventId));
    }

    @Test
    @Order(6)
    void removeShare() {
        shareService.removeShare(eventId);
        var found = shareService.findShareByEvent(eventId);
        assertTrue(found.isEmpty());
    }

    @Test
    @Order(7)
    void findShareByEventMissing() {
        assertTrue(shareService.findShareByEvent(999999).isEmpty());
    }

    /**
     * The three arrangements, and the default being the one the product always had.
     *
     * <p>Nobody has to configure anything for the host to decide: that is what holds where nothing is
     * said, and it is what every shared appointment did before any of this existed.
     */
    @Test
    @Order(19)
    void aPartnerDecidesOnlyWhereTheHostSaidSo() {
        shareService.setPartnerPlaces(eventId, partnerId, null, false);
        assertFalse(
                shareService.partnerPlaces(eventId, partnerId).partnerConfirms(),
                "saying nothing leaves the decision with the station holding the appointment");
        assertNull(
                shareService.partnerPlaces(eventId, partnerId).slotBudget(),
                "and leaves it uncapped, which is what no arrangement means");

        shareService.setPartnerPlaces(eventId, partnerId, null, true);
        assertTrue(
                shareService.partnerPlaces(eventId, partnerId).partnerConfirms(), "the partner may be given the pen");
        assertNull(shareService.partnerPlaces(eventId, partnerId).slotBudget(), "with no cap on how many come");

        shareService.setPartnerPlaces(eventId, partnerId, 2, true);
        assertEquals(2, shareService.partnerPlaces(eventId, partnerId).slotBudget(), "or with one");

        shareService.setPartnerPlaces(eventId, partnerId, 3, true);
        assertEquals(
                1,
                shareService.partnerPlaces(eventId).size(),
                "the screen that arranges this reads every partner's arrangement for the appointment");

        shareService.setPartnerPlaces(eventId, partnerId, null, false);
        assertFalse(
                shareService.partnerPlaces(eventId, partnerId).partnerConfirms(),
                "and the host can take it back, which leaves no arrangement rather than a false one");
        assertTrue(
                shareService.partnerPlaces(eventId).isEmpty(),
                "taking it back leaves no row, so nothing reads as an arrangement that is not one");
    }

    @Test
    @Order(20)
    void cacheAndGetName() {
        shareService.cacheName(partnerId, REMOTE_MEMBER_1, "Alice Smith");
        var cached = shareService.getCachedName(partnerId, REMOTE_MEMBER_1);
        assertTrue(cached.isPresent());
        assertEquals("Alice Smith", cached.get());
    }

    @Test
    @Order(21)
    void getCachedNameMissing() {
        var cached = shareService.getCachedName(partnerId, UUID.randomUUID());
        assertTrue(cached.isEmpty());
    }

    @Test
    @Order(22)
    void cacheNameUpdatesExisting() {
        shareService.cacheName(partnerId, REMOTE_MEMBER_1, "Alice Updated");
        var cached = shareService.getCachedName(partnerId, REMOTE_MEMBER_1);
        assertTrue(cached.isPresent());
        assertEquals("Alice Updated", cached.get());
    }

    @Test
    @Order(23)
    void invalidateName() {
        UUID toInvalidate = UUID.fromString("00000000-0000-0000-0000-000000000098");
        shareService.cacheName(partnerId, toInvalidate, "Bob");
        shareService.invalidateName(partnerId, toInvalidate);
        assertTrue(shareService.getCachedName(partnerId, toInvalidate).isEmpty());
    }

    /**
     * A part of an event group is shared with its event group and not on its own, and nothing is
     * written for it.
     */
    @Test
    @Order(8)
    void aPartOfAnEventGroupIsNotSharedOnItsOwn() {
        var start = Instant.now().plus(3, ChronoUnit.DAYS);
        var part = eventRepo.create(
                stationA.id(),
                "Kurstag",
                null,
                StationEvent.EventType.ONE_TIME,
                null,
                start,
                start.plus(2, ChronoUnit.HOURS),
                null,
                true,
                RegistrationDeadline.none(),
                false,
                null,
                null,
                null,
                null);
        var eventGroupRepo = new EventGroupRepository();
        var eventGroup =
                eventGroupRepo.create(stationA.id(), new EventGroupDetails("Lehrgang", null, null, null, false, null));
        eventGroupRepo.claimPart(
                part.id(),
                eventGroupRepo.createSet(eventGroup.id(), "Block", 1, 0).id());
        try {
            assertRefused(
                    EventRefusal.EVENT_GROUP_PART_SHARED_WITH_EVENT_GROUP,
                    () -> shareService.share(
                            crudService.findById(part.id()).orElseThrow(), ShareScope.ALL_PARTNERS, List.of()));
            assertTrue(shareService.findShareByEvent(part.id()).isEmpty());
        } finally {
            eventRepo.delete(part.id());
        }
    }

    /**
     * An appointment not every member here may know about is not handed over: its audience names groups
     * of this station, which mean nothing at the partner.
     */
    @Test
    @Order(8)
    void aRestrictedAppointmentIsNotShared() {
        var eventRestrictions = newEventServices(new DomainEventBus(Set.of())).restriction();
        eventRestrictions.setViewRestrictions(
                eventId,
                new RestrictionSelection(
                        List.of(StationUserType.GUARDIAN), List.of(), List.of(), List.of(), RestrictionMode.AND));
        try {
            assertRefused(
                    EventRefusal.RESTRICTED_EVENT_NOT_SHARED,
                    () -> shareService.share(
                            crudService.findById(eventId).orElseThrow(), ShareScope.ALL_PARTNERS, List.of()));
            assertTrue(shareService.findShareByEvent(eventId).isEmpty());
        } finally {
            eventRestrictions.setViewRestrictions(eventId, RestrictionSelection.empty());
        }
    }

    /** An appointment asking for a document partners could not sign alike is not handed over. */
    @Test
    @Order(8)
    void anAppointmentWithADocumentPartnersCannotSignIsNotShared() {
        doThrow(DocumentRefusal.PARTNER_AGREEMENT_NAMES_A_PERSON.raise())
                .when(requirements)
                .requireShareable(eventId);
        try {
            assertRefused(
                    DocumentRefusal.PARTNER_AGREEMENT_NAMES_A_PERSON,
                    () -> shareService.share(
                            crudService.findById(eventId).orElseThrow(), ShareScope.ALL_PARTNERS, List.of()));
            assertTrue(shareService.findShareByEvent(eventId).isEmpty());
        } finally {
            reset(requirements);
        }
    }

    /** An appointment that may be shared is shared with the partners named. */
    @Test
    @Order(9)
    void aShareableAppointmentIsSharedWithThePartnersNamed() {
        var share = shareService.share(
                crudService.findById(eventId).orElseThrow(), ShareScope.SPECIFIC, List.of(partnerId));
        try {
            verify(requirements).requireShareable(eventId);
            assertEquals(List.of(partnerId), shareService.findShareTargets(share.id()));
        } finally {
            shareService.removeShare(eventId);
        }
    }
}
