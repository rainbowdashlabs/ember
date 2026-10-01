/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.api.refusal.EventRefusal;
import dev.chojo.ember.api.refusal.Refusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.events.entity.CancellationCause;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.handler.EventCancelledHandler;
import dev.chojo.ember.feature.events.handler.EventDateRestoredHandler;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.notifications.entity.Delivery;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.entity.StationAudience;
import dev.chojo.ember.feature.notifications.service.Notifier;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Calling off one date, a whole series, and bringing a date back, with the members who hear about it.
 */
class EventCancellationServiceTest extends RepositoryTestBase {
    private static Station station;
    private static StationMember manager;
    private static StationMember onTheDate;
    private static StationMember onTheNextDate;

    private Notifier notifications;
    private EventCancellationService service;
    private EventOccurrenceService occurrences;
    private StationEvent event;
    private LocalDate today;
    private ZoneId zone;

    @BeforeAll
    static void setup() {
        station = stationRepo.create("Cancellation Service Station");
        manager = member("cancel-manager@test.com");
        onTheDate = member("cancel-on-date@test.com");
        onTheNextDate = member("cancel-next-date@test.com");
    }

    private static StationMember member(String email) {
        Account account = accountRepo.create(email, "Cancel", "Service");
        return stationMemberRepo.create(station.id(), account.id());
    }

    @BeforeEach
    void wire() {
        notifications = mock(Notifier.class);
        var bus = new DomainEventBus(Set.of(
                new EventCancelledHandler(notifications, eventRegistrationRepo, occurrenceCalendar),
                new EventDateRestoredHandler(notifications, eventRegistrationRepo)));
        var services = newEventServices(bus);
        service = services.cancellation();
        occurrences = services.occurrence();
        var calendar = occurrenceCalendar.forStation(station.id());
        today = calendar.today();
        zone = calendar.zone();
    }

    @AfterEach
    void removeEvent() {
        if (event != null) eventRepo.delete(event.id());
        event = null;
    }

    /** A weekly series that started a month ago and whose next date is the day after tomorrow. */
    private StationEvent weekly() {
        LocalDate first = firstDate().minusWeeks(4);
        event = eventRepo.create(
                station.id(),
                "Übung",
                "",
                StationEvent.EventType.RECURRING,
                first.getDayOfWeek().getValue(),
                first.atTime(18, 0).atZone(zone).toInstant(),
                first.atTime(20, 0).atZone(zone).toInstant(),
                null,
                true,
                null,
                false,
                null,
                null,
                null,
                null,
                null);
        return event;
    }

    private StationEvent oneOff() {
        LocalDate day = today.plusDays(3);
        event = eventRepo.create(
                station.id(),
                "Fest",
                "",
                StationEvent.EventType.ONE_TIME,
                null,
                day.atTime(18, 0).atZone(zone).toInstant(),
                day.atTime(20, 0).atZone(zone).toInstant(),
                null,
                true,
                null,
                false,
                null,
                null,
                null,
                null,
                null);
        return event;
    }

    private LocalDate firstDate() {
        return today.plusDays(2);
    }

    /** Only the date called off is off, and only the members on it hear about it. */
    @Test
    void cancellingOneDateOfASeriesTellsOnlyThatDatesMembers() {
        weekly();
        eventRegistrationRepo.create(event.id(), onTheDate.id(), firstDate(), RegistrationStatus.ACCEPTED, null);
        eventRegistrationRepo.create(
                event.id(), onTheNextDate.id(), firstDate().plusWeeks(1), RegistrationStatus.ACCEPTED, null);

        service.cancelDate(event, firstDate(), "Sturm", manager.id());

        assertTrue(eventDateCancellationRepo.isCancelled(event.id(), firstDate()));
        assertFalse(
                eventDateCancellationRepo.isCancelled(event.id(), firstDate().plusWeeks(1)));
        assertFalse(eventRepo.findById(event.id()).orElseThrow().cancelled(), "the series itself goes on");
        assertEquals(
                RegistrationStatus.ACCEPTED,
                eventRegistrationRepo
                        .findByEventAndDate(event.id(), firstDate())
                        .getFirst()
                        .status(),
                "places on a cancelled date are kept");
        verify(notifications)
                .notify(
                        eq(StationAudience.household(Set.of(onTheDate.id()))),
                        eq(NotificationType.EVENT_CANCELLED),
                        argThat((NotificationData data) ->
                                data.params() instanceof NotificationParams.EventCancelled params
                                        && "Sturm".equals(params.reason())
                                        && firstDate().equals(params.eventDate())),
                        eq(Delivery.EVERY_TIME));
        var notice = service.findCancelledDates(event.id()).getFirst();
        assertEquals(firstDate(), notice.date());
        assertEquals(CancellationCause.MANUAL, notice.cause());
    }

    @Test
    void aOneOffIsCancelledByItsDate() {
        oneOff();
        LocalDate day = today.plusDays(3);

        service.cancelDate(event, day, null, null);

        assertTrue(occurrenceCalendar.forStation(station.id()).cancelledAltogether(event));
        assertRefused(
                EventRefusal.ONE_TIME_EVENT_CANCELLED_AS_SERIES,
                () -> service.cancelSeries(station.id(), event.id(), null));
    }

    @Test
    void aDateTheAppointmentDoesNotFallOnOrThatIsPastOrOffAlreadyIsRefused() {
        weekly();
        assertRefused(
                EventRefusal.DATE_TO_CANCEL_NOT_A_DATE_OF_THE_EVENT,
                () -> service.cancelDate(event, firstDate().plusDays(1), null, null));
        assertRefused(
                EventRefusal.DATE_TO_CANCEL_IN_THE_PAST,
                () -> service.cancelDate(event, firstDate().minusWeeks(1), null, null));
        var breakRow = eventBreakRepo.create(
                station.id(), "Ferien", firstDate().plusWeeks(2), firstDate().plusWeeks(2));
        assertRefused(
                EventRefusal.DATE_TO_CANCEL_NOT_A_DATE_OF_THE_EVENT,
                () -> service.cancelDate(event, firstDate().plusWeeks(2), null, null));
        eventBreakRepo.delete(breakRow.id());

        service.cancelDate(event, firstDate(), null, null);
        assertRefused(EventRefusal.DATE_ALREADY_CANCELLED, () -> service.cancelDate(event, firstDate(), null, null));
    }

    /** A date brought back takes place again, and whoever kept their place is told. */
    @Test
    void aRestoredDateTakesPlaceAgainAndTellsItsMembers() {
        weekly();
        eventRegistrationRepo.create(event.id(), onTheDate.id(), firstDate(), RegistrationStatus.ACCEPTED, null);
        service.cancelDate(event, firstDate(), null, null);

        service.restoreDate(event, firstDate());

        assertFalse(eventDateCancellationRepo.isCancelled(event.id(), firstDate()));
        assertTrue(service.findCancelledDates(event.id()).isEmpty());
        verify(notifications)
                .notify(
                        eq(StationAudience.household(Set.of(onTheDate.id()))),
                        eq(NotificationType.EVENT_DATE_RESTORED),
                        any(),
                        eq(Delivery.EVERY_TIME));
        assertRefused(EventRefusal.DATE_TO_RESTORE_NOT_CANCELLED, () -> service.restoreDate(event, firstDate()));
    }

    @Test
    void aPastDateIsNotRestored() {
        weekly();
        LocalDate past = firstDate().minusWeeks(1);
        eventDateCancellationRepo.cancel(event.id(), past, CancellationCause.MANUAL, null, null);

        assertRefused(EventRefusal.DATE_TO_RESTORE_IN_THE_PAST, () -> service.restoreDate(event, past));
    }

    /** A series called off as a whole tells every place still to come and stays off for good. */
    @Test
    void aSeriesCancelStillWorksAndIsFinal() {
        weekly();
        eventRegistrationRepo.create(event.id(), onTheDate.id(), firstDate(), RegistrationStatus.ACCEPTED, null);
        eventRegistrationRepo.create(
                event.id(), onTheNextDate.id(), firstDate().plusWeeks(1), RegistrationStatus.PENDING, null);

        service.cancelSeries(station.id(), event.id(), "Aufgelöst");

        var cancelled = eventRepo.findById(event.id()).orElseThrow();
        assertTrue(cancelled.cancelled());
        assertEquals("Aufgelöst", cancelled.cancelReason());
        verify(notifications)
                .notify(
                        eq(StationAudience.household(Set.of(onTheDate.id(), onTheNextDate.id()))),
                        eq(NotificationType.EVENT_CANCELLED),
                        any(),
                        eq(Delivery.EVERY_TIME));
        assertRefused(
                EventRefusal.SERIES_ALREADY_CANCELLED, () -> service.cancelSeries(station.id(), event.id(), null));
        assertRefused(
                EventRefusal.DATE_ALREADY_CANCELLED, () -> service.cancelDate(cancelled, firstDate(), null, null));
        assertRefused(
                EventRefusal.DATE_OF_CANCELLED_SERIES_NOT_RESTORED, () -> service.restoreDate(cancelled, firstDate()));
    }

    @Test
    void aSeriesOfAnotherStationIsNotHere() {
        weekly();
        assertRefused(EventRefusal.EVENT_NOT_HERE_ON_CANCELLATION, () -> service.cancelSeries(-1, event.id(), null));
        assertRefused(EventRefusal.EVENT_NOT_HERE_ON_CANCELLATION, () -> service.cancelSeries(station.id(), -1, null));
    }

    /** The check calls a date off once and leaves a date alone that a manager brought back. */
    @Test
    void theCheckCallsADateOffOnceAndNeverOverrulesARestore() {
        weekly();

        assertTrue(service.cancelForTooFewRegistrations(event, firstDate()));
        assertFalse(service.cancelForTooFewRegistrations(event, firstDate()));
        service.restoreDate(event, firstDate());
        assertFalse(service.cancelForTooFewRegistrations(event, firstDate()));

        assertFalse(eventDateCancellationRepo.isCancelled(event.id(), firstDate()));
        verify(notifications, never()).notify(any(), eq(NotificationType.EVENT_DATE_DROPPED), any(), any());
    }

    /** A date called off stays on the lists and says why, and the dates beside it say nothing. */
    @Test
    void aCancelledDateStaysOnTheListsAndSaysWhy() {
        weekly();
        service.cancelDate(event, firstDate(), "Sturm", null);
        var query = new EventOccurrenceService.OccurrenceQuery(null, null, "Übung", null, null, 2, 0);

        var upcoming = occurrences.findUpcomingOccurrences(station.id(), null, query);

        assertEquals(firstDate(), upcoming.getFirst().date());
        assertEquals("Sturm", upcoming.getFirst().cancellation().reason());
        assertEquals(firstDate().plusWeeks(1), upcoming.get(1).date());
        assertEquals(null, upcoming.get(1).cancellation());

        var page = occurrences.findEventsPage(
                station.id(),
                null,
                new EventOccurrenceService.EventPageQuery(EventOccurrenceService.EventState.CURRENT, null, query));
        var row = page.stream()
                .filter(dated -> dated.event().id() == event.id())
                .findFirst()
                .orElseThrow();
        assertEquals(firstDate(), row.nextDate());
        assertEquals(CancellationCause.MANUAL, row.cancellation().cause());
        assertFalse(row.event().seriesCancelled());
    }

    /** The station's calendar is told the dates off of the appointments the reader sees, and no others. */
    @Test
    void theCancelledDatesAreListedForTheAppointmentsSeen() {
        weekly();
        service.cancelDate(event, firstDate(), null, null);

        var seen = service.findCancelledDates(station.id(), List.of(event.id()));
        assertEquals(1, seen.size());
        assertEquals(event.id(), seen.getFirst().eventId());
        assertEquals(firstDate(), seen.getFirst().cancellation().date());
        assertTrue(service.findCancelledDates(station.id(), List.of()).isEmpty());
    }

    /** Every date of a series called off as a whole says so, with the reason given for the series. */
    @Test
    void everyDateOfACancelledSeriesSaysSo() {
        weekly();
        service.cancelSeries(station.id(), event.id(), "Aufgelöst");
        var query = new EventOccurrenceService.OccurrenceQuery(null, null, "Übung", null, null, 2, 0);

        var upcoming = occurrences.findUpcomingOccurrences(station.id(), null, query);

        assertTrue(upcoming.stream()
                .allMatch(o -> "Aufgelöst".equals(o.cancellation().reason())));
        assertTrue(upcoming.getFirst().event().seriesCancelled());
    }

    private static void assertRefused(Refusal refusal, Runnable call) {
        var thrown = assertThrows(RefusalResponse.class, call::run);
        assertEquals(refusal, thrown.refusal());
    }
}
