/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.api.refusal.EventRefusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.feature.events.entity.AppointmentOwner;
import dev.chojo.ember.feature.events.entity.CancellationCause;
import dev.chojo.ember.feature.events.entity.EventFederationRegistration;
import dev.chojo.ember.feature.events.entity.EventGroupDetails;
import dev.chojo.ember.feature.events.entity.QueueEntry;
import dev.chojo.ember.feature.events.entity.RegistrationDeadline;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.entity.SharedEvent;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventGroupRepository;
import dev.chojo.ember.feature.events.repository.RegistrationQueueRepository;
import dev.chojo.ember.feature.events.route.RemoteEventRoutes;
import dev.chojo.ember.feature.federation.entity.ShareScope;
import dev.chojo.ember.feature.media.entity.MediaContent;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** What this station serves partners about its shared appointments, and the registrations their members hold here. */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EventPartnerRegistrationServiceTest extends EventFederationTestBase {
    @Test
    @Order(10)
    void registerFederated() {
        var reg = hostService.registerFederated(eventId, partnerId, REMOTE_MEMBER_1, LocalDate.of(2026, 7, 1));
        assertNotNull(reg);
        assertEquals(eventId, reg.eventId());
        assertEquals(partnerId, reg.partnerId());
        assertEquals(REMOTE_MEMBER_1, reg.remoteMemberId());
        assertEquals(LocalDate.of(2026, 7, 1), reg.eventDate());
        assertNotNull(reg.status());
    }

    @Test
    @Order(11)
    void findRegistrationById() {
        var reg = hostService.registerFederated(eventId, partnerId, REMOTE_MEMBER_2, LocalDate.of(2026, 7, 2));
        var found = hostService.findRegistrationById(reg.id());
        assertTrue(found.isPresent());
        assertEquals(reg.id(), found.get().id());
        assertEquals(REMOTE_MEMBER_2, found.get().remoteMemberId());
    }

    @Test
    @Order(12)
    void findRegistrationByIdMissing() {
        assertTrue(hostService.findRegistrationById(999999).isEmpty());
    }

    @Test
    @Order(13)
    void updateRegistrationStatus() {
        var reg = hostService.registerFederated(eventId, partnerId, REMOTE_MEMBER_3, LocalDate.of(2026, 7, 3));
        boolean updated = hostService.updateRegistrationStatus(reg.id(), RegistrationStatus.ACCEPTED);
        assertTrue(updated);
        var found = hostService.findRegistrationById(reg.id()).orElseThrow();
        assertEquals(RegistrationStatus.ACCEPTED, found.status());
    }

    @Test
    @Order(14)
    void findRegistrations() {
        LocalDate date = LocalDate.of(2026, 7, 1);
        var regs = hostService.findRegistrations(eventId, date);
        assertFalse(regs.isEmpty());
        assertTrue(regs.stream().anyMatch(r -> r.remoteMemberId().equals(REMOTE_MEMBER_1)));
    }

    @Test
    @Order(15)
    void findRegistrationsByPartner() {
        var regs = hostService.findRegistrationsByPartner(partnerId);
        assertFalse(regs.isEmpty());
        assertTrue(regs.stream().anyMatch(r -> r.remoteMemberId().equals(REMOTE_MEMBER_1)));
    }

    @Test
    @Order(16)
    void findRegistrationsNullDate() {
        var regs = hostService.findRegistrations(eventId, null);
        assertNotNull(regs);
        assertFalse(regs.isEmpty());
    }

    @Test
    @Order(16)
    void findRegistrationsByRemoteMember() {
        var regs = hostService.findRegistrationsByRemoteMember(REMOTE_MEMBER_1);
        assertNotNull(regs);
        assertFalse(regs.isEmpty());
        assertTrue(regs.stream().allMatch(r -> r.remoteMemberId().equals(REMOTE_MEMBER_1)));
    }

    @Test
    @Order(17)
    void withdrawRegistration() {
        UUID toWithdraw = UUID.fromString("00000000-0000-0000-0000-000000000099");
        LocalDate day = LocalDate.of(2026, 8, 1);
        hostService.registerFederated(eventId, partnerId, toWithdraw, day);

        assertTrue(hostService.withdrawRegistration(eventId, partnerId, toWithdraw, day));
        var withdrawn = hostService.findRegistrations(eventId, day).stream()
                .filter(reg -> reg.remoteMemberId().equals(toWithdraw))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "the row stays, or the host cannot tell somebody who left from somebody who never answered"));
        assertEquals(RegistrationStatus.WITHDRAWN, withdrawn.status());

        assertTrue(
                hostService.undoWithdrawal(eventId, partnerId, toWithdraw, day),
                "and a partner may ask for it back while the window is open");
        assertTrue(
                hostService
                                .registerFederated(eventId, partnerId, toWithdraw, day)
                                .id()
                        > 0,
                "registering again after a withdrawal answers rather than colliding with the old row");
    }

    /**
     * A visitor is asked about the date the way a member of this station is: a date the series does
     * not fall on and a date that was called off are both refused, and the dates beside a cancelled
     * one still take visitors.
     */
    @Test
    @Order(19)
    void aVisitorCannotRegisterForACancelledDate() {
        var calendar = occurrenceCalendar.forStation(stationA.id());
        LocalDate next = calendar.today().plusDays(2);
        var weekly = eventRepo.create(
                stationA.id(),
                "Weekly Visitors",
                "desc",
                StationEvent.EventType.RECURRING,
                next.getDayOfWeek().getValue(),
                next.minusWeeks(2).atTime(18, 0).atZone(calendar.zone()).toInstant(),
                next.minusWeeks(2).atTime(20, 0).atZone(calendar.zone()).toInstant(),
                null,
                true,
                RegistrationDeadline.none(),
                false,
                null,
                null,
                null,
                null);
        UUID visitor = UUID.fromString("00000000-0000-0000-0000-0000000000d1");
        try {
            eventDateCancellationRepo.cancel(weekly.id(), next, CancellationCause.MANUAL, null, null);

            var cancelled = assertThrows(
                    RefusalResponse.class, () -> hostService.registerFederated(weekly.id(), partnerId, visitor, next));
            assertEquals(EventRefusal.REGISTRATION_DAY_CANCELLED, cancelled.refusal());
            var notADate = assertThrows(
                    RefusalResponse.class,
                    () -> hostService.registerFederated(weekly.id(), partnerId, visitor, next.plusDays(1)));
            assertEquals(EventRefusal.REGISTRATION_DAY_NOT_AN_OCCURRENCE, notADate.refusal());
            assertEquals(
                    next.plusWeeks(1),
                    hostService
                            .registerFederated(weekly.id(), partnerId, visitor, next.plusWeeks(1))
                            .eventDate());
        } finally {
            eventRepo.delete(weekly.id());
        }
    }

    /**
     * A relative deadline closes each date on its own for a visitor too: the date whose deadline passed
     * is refused, the next date still takes them.
     */
    @Test
    @Order(19)
    void aVisitorMeetsTheDeadlineOfTheDateTheyAskFor() {
        var calendar = occurrenceCalendar.forStation(stationA.id());
        LocalDate near = calendar.today().plusDays(1);
        var weekly = eventRepo.create(
                stationA.id(),
                "Weekly Deadline",
                "desc",
                StationEvent.EventType.RECURRING,
                near.getDayOfWeek().getValue(),
                near.minusWeeks(2).atTime(18, 0).atZone(calendar.zone()).toInstant(),
                near.minusWeeks(2).atTime(20, 0).atZone(calendar.zone()).toInstant(),
                null,
                true,
                RegistrationDeadline.relative(3 * 24 * 60, RegistrationDeadline.DeadlineAnchor.EXACT),
                false,
                null,
                null,
                null,
                null);
        UUID visitor = UUID.fromString("00000000-0000-0000-0000-0000000000d2");
        try {
            var closed = assertThrows(
                    RefusalResponse.class, () -> hostService.registerFederated(weekly.id(), partnerId, visitor, near));
            assertEquals(EventRefusal.REGISTRATION_CLOSED_TO_PARTNER, closed.refusal());
            assertEquals(
                    near.plusWeeks(1),
                    hostService
                            .registerFederated(weekly.id(), partnerId, visitor, near.plusWeeks(1))
                            .eventDate());
        } finally {
            eventRepo.delete(weekly.id());
        }
    }

    /**
     * One partner's member on one date, which is what a partner names when it confirms or takes back.
     * A date nobody answered for is empty rather than somebody else's row.
     */
    @Test
    @Order(19)
    void aRegistrationIsFoundByThePartnerAndTheDayItIsFor() {
        LocalDate day = LocalDate.of(2026, 9, 11);
        UUID who = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
        hostService.registerFederated(eventId, partnerId, who, day);

        assertTrue(hostService.findRegistration(eventId, partnerId, who, day).isPresent());
        assertTrue(
                hostService
                        .findRegistration(eventId, partnerId, who, day.plusDays(1))
                        .isEmpty(),
                "another day is another answer, or none");
    }

    /**
     * A budget is spent in the statement that grants the place.
     *
     * <p>Counting first and writing after is exactly the race this has to survive: two people at the
     * partner pressing confirm in the same moment would both see room and both take the last place.
     */
    @Test
    @Order(19)
    void aBudgetCannotBeOverspent() {
        LocalDate day = LocalDate.of(2026, 9, 9);
        UUID first = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
        UUID second = UUID.fromString("00000000-0000-0000-0000-0000000000a2");
        givePlaces(eventId, partnerId, 1, true);
        var one = hostService.registerFederated(eventId, partnerId, first, day);
        var two = hostService.registerFederated(eventId, partnerId, second, day);

        assertTrue(
                hostService.acceptWithinBudget(one.id(), eventId, partnerId, day),
                "the one place the partner was given is theirs to fill");
        assertFalse(
                hostService.acceptWithinBudget(two.id(), eventId, partnerId, day),
                "and the second is refused rather than quietly granted");

        var counted = hostService.countPartnerPlaces(eventId, partnerId, day);
        assertEquals(1, counted.taken());
        assertEquals(1, counted.budget());

        givePlaces(eventId, partnerId, null, false);
    }

    /**
     * Lowering a budget below what is already taken never un-invites anybody.
     *
     * <p>Withdrawing a place already granted is a conversation between two stations, not a number
     * quietly going down. The places stand and no more may be added.
     */
    @Test
    @Order(19)
    void loweringABudgetLeavesThePlacesAlreadyGiven() {
        LocalDate day = LocalDate.of(2026, 9, 10);
        UUID held = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
        UUID wanted = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
        givePlaces(eventId, partnerId, 2, true);
        var standing = hostService.registerFederated(eventId, partnerId, held, day);
        assertTrue(hostService.acceptWithinBudget(standing.id(), eventId, partnerId, day));

        givePlaces(eventId, partnerId, 1, true);
        assertEquals(
                1,
                hostService.countPartnerPlaces(eventId, partnerId, day).taken(),
                "the place already given still stands");

        var next = hostService.registerFederated(eventId, partnerId, wanted, day);
        assertFalse(
                hostService.acceptWithinBudget(next.id(), eventId, partnerId, day),
                "and the room that is gone is gone");

        givePlaces(eventId, partnerId, null, false);
    }

    /**
     * A visitor meets the same door a member does, wherever their station is kept.
     *
     * <p>The remote route asked this and the same-instance path did not, so two stations sharing an
     * instance could sign up for an appointment that had been called off, or one whose list closed
     * weeks ago. The question belongs where both paths pass, not on one of them.
     */
    @Test
    @Order(33)
    void aVisitorIsTurnedAwayFromAnAppointmentThatTakesNoRegistrations() {
        Instant start = Instant.now().plus(3, ChronoUnit.DAYS);
        var openHouse = eventRepo.create(
                stationA.id(),
                "Kein Anmeldung",
                "desc",
                StationEvent.EventType.ONE_TIME,
                null,
                start,
                start.plus(2, ChronoUnit.HOURS),
                null,
                false,
                RegistrationDeadline.none(),
                false,
                null,
                null,
                null,
                null);
        try {
            assertThrows(
                    RefusalResponse.class,
                    () -> hostService.registerFederated(
                            openHouse.id(), partnerId, REMOTE_MEMBER_3, LocalDate.of(2026, 7, 9)),
                    "an appointment with no list has none for a visitor either");
        } finally {
            eventRepo.delete(openHouse.id());
        }
    }

    @Test
    @Order(97)
    void whatOneStationShowsAnother() {
        var event = new SharedEvent(
                1, "Name", "Description", StationEvent.EventType.RECURRING, 3, "09:00", "11:00", false, true, null, 8);
        assertEquals(1, event.id());
        assertEquals("Name", event.name());
        assertEquals("Description", event.description());
        assertEquals(StationEvent.EventType.RECURRING, event.eventType());
        assertEquals(3, event.dayOfWeek());
        assertEquals("09:00", event.startTime());
        assertEquals("11:00", event.endTime());
        assertFalse(event.requiresRegistration());
        assertTrue(event.requiresConfirmation());
        assertEquals(8, event.repeatCount());
    }

    /** A series that ends says so to a partner as well, or it repeats for ever on their screen. */
    @Test
    @Order(98)
    void aSharedSeriesCarriesItsEnd() {
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        var series = crudService.create(
                stationA.id(),
                "Geteilte Reihe",
                "desc",
                StationEvent.EventType.RECURRING,
                start.atZone(ZoneOffset.UTC).getDayOfWeek().getValue(),
                start,
                start.plus(2, ChronoUnit.HOURS),
                null,
                false,
                RegistrationDeadline.none(),
                false,
                null,
                null,
                null,
                null);

        var ends = crudService.setRepeatEnd(series.id(), null, 4).orElseThrow();
        var shared = SharedEvent.of(ends);

        assertEquals(4, shared.repeatCount());
        assertNull(shared.repeatUntil());
    }

    /**
     * A partner station is handed the open files of a shared event and never what it keeps back.
     *
     * <p>Internal means kept back from the room, and a partner is further out than the room. The
     * list omits such a file, and asking for it by its own id is refused at the source rather than
     * answered differently from the list.
     */
    @Test
    @Order(99)
    void aPartnerIsHandedTheOpenFilesAndNeverTheInternalOnes() {
        var stationFile = mediaFileRepo.create(
                null, stationA.id(), UUID.randomUUID().toString(), "laufzettel.pdf", "application/pdf", 12);
        var kept = mediaFileRepo.create(
                null, stationA.id(), UUID.randomUUID().toString(), "einsatzplan.pdf", "application/pdf", 12);
        when(media.findFile(stationFile.id())).thenReturn(Optional.of(stationFile));
        when(media.findFile(kept.id())).thenReturn(Optional.of(kept));
        when(media.read(stationA.id(), stationFile.contentHash()))
                .thenReturn(Optional.of(
                        new MediaContent("Laufzettel".getBytes(StandardCharsets.UTF_8), "application/pdf")));

        var open = attachmentService.attach(
                AppointmentOwner.EVENT, eventId, stationA.id(), stationFile.id(), "Laufzettel", false);
        var internal = attachmentService.attach(
                AppointmentOwner.EVENT, eventId, stationA.id(), kept.id(), "Einsatzplan", true);
        shareService.setShare(eventId, ShareScope.ALL_PARTNERS, List.of());

        try {
            var listed = clientService.listFederatedAttachments(stationB.id(), stationA.uid(), eventId);
            assertEquals(1, listed.size(), "the partner is handed one of the two");
            assertEquals("Laufzettel", listed.getFirst().name());

            var handed = clientService.getFederatedAttachment(stationB.id(), stationA.uid(), eventId, open.id());
            assertEquals(open.id(), handed.attachmentId());
            assertEquals(
                    "Laufzettel",
                    new String(Base64.getDecoder().decode(handed.base64()), StandardCharsets.UTF_8),
                    "the bytes travel as the channel can carry them");

            assertThrows(
                    Exception.class,
                    () -> clientService.getFederatedAttachment(stationB.id(), stationA.uid(), eventId, internal.id()),
                    "what is kept back is refused even when its id is named");

            shareService.removeShare(eventId);
            assertThrows(
                    Exception.class,
                    () -> clientService.listFederatedAttachments(stationB.id(), stationA.uid(), eventId),
                    "an event that is no longer shared hands over nothing");
        } finally {
            attachmentService.detach(AppointmentOwner.EVENT, open.id());
            attachmentService.detach(AppointmentOwner.EVENT, internal.id());
        }
    }

    /**
     * A partner on this instance goes through the same doors as one elsewhere: it signs up, gives the
     * place back, takes the withdrawal back and confirms its own member only where it was handed that
     * decision, and every refusal names what was refused.
     */
    @Test
    @Order(94)
    void aPartnerHereSignsUpAndChangesItsMindThroughTheSameDoors() {
        shareService.setShare(eventId, ShareScope.ALL_PARTNERS, List.of());
        var member = UUID.randomUUID();
        var day = LocalDate.now().plusDays(1);
        var asking = partnerOf(stationB, stationA);
        int serving = partnerOf(stationA, stationB).id();

        assertNotNull(clientService.registerForFederatedEvent(
                stationB.id(), stationA.uid(), eventId, member, day, List.of()));
        transport.assertParity(
                asking, RemoteEventRoutes.LIST_REGISTRATIONS.at(eventId), null, EventFederationRegistration.class);
        transport.assertParity(
                asking,
                RemoteEventRoutes.LIST_MEMBER_REGISTRATIONS.at(member),
                null,
                RemoteEventRoutes.RemoteMemberRegistration.class);

        clientService.withdrawFederatedRegistration(stationB.id(), stationA.uid(), eventId, member, day);
        clientService.undoFederatedWithdrawal(stationB.id(), stationA.uid(), eventId, member, day);
        assertTrue(hostService
                .findRegistration(eventId, serving, member, day)
                .orElseThrow()
                .isStanding());

        assertRefused(
                EventRefusal.PARTNER_DOES_NOT_CONFIRM_ITS_OWN,
                () -> clientService.confirmOwnFederatedMember(stationB.id(), stationA.uid(), eventId, member, day));
        givePlaces(eventId, serving, 1, true);
        var second = UUID.randomUUID();
        assertEquals(
                RegistrationStatus.PENDING,
                clientService.registerForFederatedEvent(
                        stationB.id(), stationA.uid(), eventId, second, day, List.of()));
        assertRefused(
                EventRefusal.NO_PLACES_LEFT_FOR_PARTNER,
                () -> clientService.confirmOwnFederatedMember(stationB.id(), stationA.uid(), eventId, second, day));
        givePlaces(eventId, serving, 2, true);
        assertDoesNotThrow(
                () -> clientService.confirmOwnFederatedMember(stationB.id(), stationA.uid(), eventId, second, day));
        givePlaces(eventId, serving, null, false);

        var stranger = UUID.randomUUID();
        clientService.withdrawFederatedRegistration(stationB.id(), stationA.uid(), eventId, stranger, day);
        assertRefused(
                EventRefusal.PARTNER_WITHDRAWAL_NO_LONGER_UNDONE,
                () -> clientService.undoFederatedWithdrawal(stationB.id(), stationA.uid(), eventId, stranger, day));
    }

    /** A part of an event group takes places through the event group; a partner cannot take or give one up on its own. */
    @Test
    @Order(96)
    void aPartnerActsOnAPartOfAnEventGroupOnlyThroughTheEventGroup() {
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
        var set = eventGroupRepo.createSet(eventGroup.id(), "Block", 1, 0);
        eventGroupRepo.claimPart(part.id(), set.id());
        shareService.setShare(part.id(), ShareScope.ALL_PARTNERS, List.of());
        var day = start.atZone(occurrenceCalendar.zoneOf(stationA.id())).toLocalDate();

        assertRefused(
                EventRefusal.REGISTRATION_THROUGH_EVENT_GROUP,
                () -> hostService.registerFederated(part.id(), partnerId, REMOTE_MEMBER_3, day));
        assertRefused(
                EventRefusal.REGISTRATION_THROUGH_EVENT_GROUP,
                () -> clientService.withdrawFederatedRegistration(
                        stationB.id(), stationA.uid(), part.id(), REMOTE_MEMBER_3, day));
        assertRefused(
                EventRefusal.REGISTRATION_THROUGH_EVENT_GROUP,
                () -> clientService.undoFederatedWithdrawal(
                        stationB.id(), stationA.uid(), part.id(), REMOTE_MEMBER_3, day));
    }

    @Test
    void aPartnersMemberWaitsForAFullDateAndMovesUpWhenAPlaceFallsFree() {
        var event = onePlace();
        var day = dayOf(event);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        try {
            assertEquals(
                    RegistrationStatus.ACCEPTED,
                    hostService
                            .registerFederated(event.id(), partnerId, first, day)
                            .status());
            assertEquals(
                    RegistrationStatus.QUEUED,
                    hostService
                            .registerFederated(event.id(), partnerId, second, day)
                            .status());
            assertEquals(
                    RegistrationStatus.QUEUED,
                    hostService
                            .registerFederated(event.id(), partnerId, second, day)
                            .status(),
                    "signing up again keeps the place in line");

            hostService.withdrawRegistration(event.id(), partnerId, first, day);

            assertEquals(RegistrationStatus.ACCEPTED, statusOf(event, second));
        } finally {
            eventRepo.delete(event.id());
        }
    }

    @Test
    void acceptingAWaitingPartnerMemberBeyondTheLimitHasToBeSaidAndTurningDownMovesTheNextUp() {
        var event = onePlace();
        var day = dayOf(event);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        try {
            var held = hostService.registerFederated(event.id(), partnerId, first, day);
            var waiting = hostService.registerFederated(event.id(), partnerId, second, day);

            var refusal = assertThrows(
                    RefusalResponse.class,
                    () -> hostService.acceptWithinBudget(waiting.id(), event.id(), partnerId, day, false));
            assertEquals(EventRefusal.REGISTRATION_FULL, refusal.refusal());

            hostService.updateRegistrationStatus(held.id(), RegistrationStatus.DENIED);
            assertEquals(RegistrationStatus.ACCEPTED, statusOf(event, second));

            var third = hostService.registerFederated(event.id(), partnerId, UUID.randomUUID(), day);
            assertTrue(hostService.acceptWithinBudget(third.id(), event.id(), partnerId, day, true));
        } finally {
            eventRepo.delete(event.id());
        }
    }

    @Test
    void takingBackAGivenUpSeatThatWasFilledPutsThePartnersMemberFirstInLine() {
        var event = onePlace();
        var day = dayOf(event);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID third = UUID.randomUUID();
        try {
            hostService.registerFederated(event.id(), partnerId, first, day);
            hostService.registerFederated(event.id(), partnerId, second, day);
            hostService.registerFederated(event.id(), partnerId, third, day);
            hostService.withdrawRegistration(event.id(), partnerId, first, day);

            assertTrue(hostService.undoWithdrawal(event.id(), partnerId, first, day));

            assertEquals(RegistrationStatus.ACCEPTED, statusOf(event, second));
            assertEquals(RegistrationStatus.QUEUED, statusOf(event, first));
            var line = new RegistrationQueueRepository().entries(RegistrationQueueRepository.ofDate(event.id(), day));
            assertEquals(
                    hostService
                            .findRegistration(event.id(), partnerId, first, day)
                            .orElseThrow()
                            .id(),
                    line.getFirst().partnerId());
        } finally {
            eventRepo.delete(event.id());
        }
    }

    @Test
    void membersHereAndPartnersMembersWaitInOneLine() {
        var event = onePlace();
        var day = dayOf(event);
        var registrations = newEventServices(new DomainEventBus(Set.of())).registration();
        var anna = stationMemberRepo.create(
                stationA.id(),
                accountRepo.create("line-anna@test.com", "Anna", "Line").id());
        var ben = stationMemberRepo.create(
                stationA.id(),
                accountRepo.create("line-ben@test.com", "Ben", "Line").id());
        UUID visitor = UUID.randomUUID();
        try {
            var held = registrations.register(event.id(), anna.id(), day, true, null);
            assertEquals(
                    RegistrationStatus.QUEUED,
                    hostService
                            .registerFederated(event.id(), partnerId, visitor, day)
                            .status());
            var waiting = registrations.register(event.id(), ben.id(), day, true, null);
            assertEquals(RegistrationStatus.QUEUED, waiting.status());

            registrations.withdraw(held.id());
            assertEquals(RegistrationStatus.ACCEPTED, statusOf(event, visitor), "the partner's member was first");

            hostService.withdrawRegistration(event.id(), partnerId, visitor, day);
            assertEquals(
                    RegistrationStatus.ACCEPTED,
                    eventRegistrationRepo.findById(waiting.id()).orElseThrow().status());
        } finally {
            eventRepo.delete(event.id());
        }
    }

    @Test
    void takingBackLeavingTheLinePutsThePartnersMemberBackInLine() {
        var event = onePlace();
        var day = dayOf(event);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        try {
            hostService.registerFederated(event.id(), partnerId, first, day);
            hostService.registerFederated(event.id(), partnerId, second, day);
            hostService.withdrawRegistration(event.id(), partnerId, second, day);

            assertTrue(hostService.undoWithdrawal(event.id(), partnerId, second, day));

            assertEquals(RegistrationStatus.QUEUED, statusOf(event, second));
            assertEquals(
                    1,
                    new RegistrationQueueRepository()
                            .entries(RegistrationQueueRepository.ofDate(event.id(), day))
                            .size());
        } finally {
            eventRepo.delete(event.id());
        }
    }

    /**
     * Whoever runs the appointment accepts or turns down a partner's member, and only where the partner
     * did not take that decision: otherwise neither a decision nor the waiting list is the host's.
     */
    @Test
    void theHostDecidesAboutAPartnersMemberOnlyWhereThePartnerDoesNot() {
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        var event = eventRepo.create(
                stationA.id(),
                "Zur Entscheidung",
                "desc",
                StationEvent.EventType.ONE_TIME,
                null,
                start,
                start.plus(2, ChronoUnit.HOURS),
                null,
                true,
                RegistrationDeadline.none(),
                true,
                null,
                2,
                null,
                null);
        var day = dayOf(event);
        try {
            var one = hostService.registerFederated(event.id(), partnerId, UUID.randomUUID(), day);
            var two = hostService.registerFederated(event.id(), partnerId, UUID.randomUUID(), day);
            assertRefused(
                    EventRefusal.REGISTRATION_DECISION_UNKNOWN,
                    () -> hostService.decide(one.id(), RegistrationStatus.QUEUED, false));
            assertRefused(
                    EventRefusal.PARTNER_REGISTRATION_NOT_HERE_ON_DECISION,
                    () -> hostService.decide(Integer.MAX_VALUE, RegistrationStatus.ACCEPTED, false));

            hostService.decide(one.id(), RegistrationStatus.ACCEPTED, false);
            assertEquals(RegistrationStatus.ACCEPTED, statusOf(event, one.remoteMemberId()));
            hostService.decide(one.id(), RegistrationStatus.DENIED, false);
            assertEquals(RegistrationStatus.DENIED, statusOf(event, one.remoteMemberId()));

            givePlaces(event.id(), partnerId, null, true);
            assertRefused(
                    EventRefusal.PARTNER_DECIDES_ITS_OWN,
                    () -> hostService.decide(two.id(), RegistrationStatus.ACCEPTED, false));
            assertRefused(
                    EventRefusal.PARTNER_DECIDES_ITS_OWN,
                    () -> hostService.decide(two.id(), RegistrationStatus.DENIED, false));
            assertRefused(EventRefusal.PARTNER_DECIDES_ITS_OWN, () -> hostService.moveToQueue(List.of(two.id())));
            assertEquals(RegistrationStatus.PENDING, statusOf(event, two.remoteMemberId()));
        } finally {
            eventRepo.delete(event.id());
        }
    }

    @Test
    void whoeverRunsTheAppointmentPutsPartnersMembersOnTheListAndReordersIt() {
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        var event = eventRepo.create(
                stationA.id(),
                "Mit Entscheidung",
                "desc",
                StationEvent.EventType.ONE_TIME,
                null,
                start,
                start.plus(2, ChronoUnit.HOURS),
                null,
                true,
                RegistrationDeadline.none(),
                true,
                null,
                1,
                null,
                null);
        var day = dayOf(event);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        try {
            var one = hostService.registerFederated(event.id(), partnerId, first, day);
            var two = hostService.registerFederated(event.id(), partnerId, second, day);
            assertEquals(RegistrationStatus.PENDING, one.status());

            hostService.moveToQueue(List.of(two.id(), one.id()));
            hostService.moveInQueue(two.id(), 1);

            var line = new RegistrationQueueRepository().entries(RegistrationQueueRepository.ofDate(event.id(), day));
            assertEquals(
                    List.of(two.id(), one.id()),
                    line.stream().map(QueueEntry::partnerId).toList());
            assertRefused(
                    EventRefusal.REGISTRATION_NOT_PENDING_FOR_QUEUE, () -> hostService.moveToQueue(List.of(one.id())));
            assertRefused(
                    EventRefusal.REGISTRATION_NOT_QUEUED,
                    () -> hostService.moveInQueue(
                            hostService
                                    .registerFederated(event.id(), partnerId, UUID.randomUUID(), day)
                                    .id(),
                            1));
        } finally {
            eventRepo.delete(event.id());
        }
    }

    @Test
    void aPartnerConfirmingItsOwnOnAFullDatePutsThemOnTheWaitingList() {
        var event = onePlace();
        var day = dayOf(event);
        int serving = partnerOf(stationA, stationB).id();
        var registrations = newEventServices(new DomainEventBus(Set.of())).registration();
        var held = stationMemberRepo.create(
                stationA.id(),
                accountRepo.create("full-held@test.com", "Voll", "Platz").id());
        UUID member = UUID.randomUUID();
        try {
            shareService.setShare(event.id(), ShareScope.ALL_PARTNERS, List.of());
            givePlaces(event.id(), serving, 2, true);
            clientService.registerForFederatedEvent(stationB.id(), stationA.uid(), event.id(), member, day, List.of());
            registrations.register(event.id(), held.id(), day, true, null);

            clientService.confirmOwnFederatedMember(stationB.id(), stationA.uid(), event.id(), member, day);

            assertEquals(
                    RegistrationStatus.QUEUED,
                    hostService
                            .findRegistration(event.id(), serving, member, day)
                            .orElseThrow()
                            .status());
        } finally {
            eventRepo.delete(event.id());
        }
    }

    @Test
    void aPartnerWaitsOnlyWithinItsPlacesAndAMemberWithoutAPlaceLeftIsPassedOver() {
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        var event = eventRepo.create(
                stationA.id(),
                "Zwei Stellen",
                "desc",
                StationEvent.EventType.ONE_TIME,
                null,
                start,
                start.plus(2, ChronoUnit.HOURS),
                null,
                true,
                RegistrationDeadline.none(),
                false,
                null,
                2,
                null,
                null);
        var day = dayOf(event);
        int serving = partnerOf(stationA, stationB).id();
        var registrations = newEventServices(new DomainEventBus(Set.of())).registration();
        var local = stationMemberRepo.create(
                stationA.id(),
                accountRepo.create("budget-local@test.com", "Bud", "Get").id());
        var behind = stationMemberRepo.create(
                stationA.id(),
                accountRepo.create("budget-behind@test.com", "Hin", "Ten").id());
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID third = UUID.randomUUID();
        try {
            shareService.setShare(event.id(), ShareScope.ALL_PARTNERS, List.of());
            givePlaces(event.id(), serving, 2, true);
            for (var member : List.of(first, second, third)) {
                clientService.registerForFederatedEvent(
                        stationB.id(), stationA.uid(), event.id(), member, day, List.of());
            }
            clientService.confirmOwnFederatedMember(stationB.id(), stationA.uid(), event.id(), first, day);
            var held = registrations.register(event.id(), local.id(), day, true, null);
            clientService.confirmOwnFederatedMember(stationB.id(), stationA.uid(), event.id(), second, day);
            assertEquals(
                    RegistrationStatus.QUEUED,
                    hostService
                            .findRegistration(event.id(), serving, second, day)
                            .orElseThrow()
                            .status());

            assertRefused(
                    EventRefusal.NO_PLACES_LEFT_FOR_PARTNER,
                    () -> clientService.confirmOwnFederatedMember(
                            stationB.id(), stationA.uid(), event.id(), third, day));

            givePlaces(event.id(), serving, 1, true);
            var waitingHere = registrations.register(event.id(), behind.id(), day, true, null);
            registrations.withdraw(held.id());
            assertEquals(
                    RegistrationStatus.QUEUED,
                    hostService
                            .findRegistration(event.id(), serving, second, day)
                            .orElseThrow()
                            .status(),
                    "the partner's own places are spent");
            assertEquals(
                    RegistrationStatus.ACCEPTED,
                    eventRegistrationRepo
                            .findById(waitingHere.id())
                            .orElseThrow()
                            .status());

            assertEquals(
                    RegistrationStatus.QUEUED,
                    hostService
                            .registerFederated(event.id(), serving, second, day)
                            .status(),
                    "signing up again changes nothing");
            clientService.confirmOwnFederatedMember(stationB.id(), stationA.uid(), event.id(), second, day);
            registrations.withdraw(waitingHere.id());
            var late = stationMemberRepo.create(
                    stationA.id(),
                    accountRepo.create("budget-late@test.com", "Sp", "Aet").id());
            assertEquals(
                    RegistrationStatus.ACCEPTED,
                    registrations
                            .register(event.id(), late.id(), day, true, null)
                            .status(),
                    "a free place is not kept from a newcomer by somebody who cannot move up");
        } finally {
            eventRepo.delete(event.id());
        }
    }
}
