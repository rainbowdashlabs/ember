/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.repository;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.content.entity.BlockAudience;
import dev.chojo.ember.feature.events.entity.CancellationCause;
import dev.chojo.ember.feature.events.entity.PickerMode;
import dev.chojo.ember.feature.events.entity.RegistrationDeadline;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventRepositoryTest extends RepositoryTestBase {
    private static Station station;
    private static Account account;
    private static StationMember member;

    @BeforeAll
    static void setup() {
        station = stationRepo.create("Event Station");
        account = accountRepo.create("event@test.com", "Event", "User");
        member = stationMemberRepo.create(station.id(), account.id());
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    private static StationEvent oneTime(String name, Instant start, Instant end) {
        return eventRepo.create(
                station.id(),
                name,
                "desc",
                StationEvent.EventType.ONE_TIME,
                null,
                start,
                end,
                null,
                false,
                RegistrationDeadline.none(),
                false,
                null,
                null,
                null,
                null);
    }

    /**
     * Every appointment taking sign-ups with a deadline can close, however far away; an appointment
     * without a deadline or without sign-ups cannot.
     */
    @Test
    void theAppointmentsWithADeadlineAreThoseThatCanClose() {
        var closing = eventRepo.create(
                station.id(),
                "Bald zu",
                "desc",
                StationEvent.EventType.ONE_TIME,
                null,
                Instant.now().plus(java.time.Duration.ofDays(10)),
                Instant.now().plus(java.time.Duration.ofDays(10)).plusSeconds(3600),
                null,
                true,
                RegistrationDeadline.fixed(Instant.now().plus(java.time.Duration.ofDays(2))),
                false,
                null,
                null,
                null,
                null);
        var later = eventRepo.create(
                station.id(),
                "Noch lange",
                "desc",
                StationEvent.EventType.ONE_TIME,
                null,
                Instant.now().plus(java.time.Duration.ofDays(40)),
                Instant.now().plus(java.time.Duration.ofDays(40)).plusSeconds(3600),
                null,
                true,
                RegistrationDeadline.fixed(Instant.now().plus(java.time.Duration.ofDays(30))),
                false,
                null,
                null,
                null,
                null);
        var open = eventRepo.create(
                station.id(),
                "Ohne Schluss",
                "desc",
                StationEvent.EventType.ONE_TIME,
                null,
                Instant.now().plus(java.time.Duration.ofDays(10)),
                Instant.now().plus(java.time.Duration.ofDays(10)).plusSeconds(3600),
                null,
                true,
                RegistrationDeadline.none(),
                false,
                null,
                null,
                null,
                null);
        try {
            var closable =
                    eventRepo.findWithDeadline().stream().map(StationEvent::id).toList();
            assertTrue(closable.contains(closing.id()));
            assertTrue(closable.contains(later.id()), "a distant deadline is the checker's to weigh");
            assertFalse(closable.contains(open.id()), "no deadline closes nothing");
        } finally {
            eventRepo.delete(closing.id());
            eventRepo.delete(later.id());
            eventRepo.delete(open.id());
        }
    }

    /** A one-time appointment whose date was called off has nobody owing an answer. */
    @Test
    void aCancelledOneOffAwaitsNoAnswers() {
        Instant start = Instant.now().plus(java.time.Duration.ofDays(10));
        var called = eventRepo.create(
                station.id(),
                "Abgesagt",
                "desc",
                StationEvent.EventType.ONE_TIME,
                null,
                start,
                start.plusSeconds(3600),
                null,
                true,
                RegistrationDeadline.fixed(Instant.now().plus(java.time.Duration.ofDays(2))),
                false,
                null,
                null,
                null,
                null);
        try {
            assertTrue(awaitingAnswers().find(List.of(member.id())).stream().anyMatch(a -> a.eventId() == called.id()));

            var day = occurrenceCalendar.dateInView(called).orElseThrow();
            eventDateCancellationRepo.cancel(called.id(), day, CancellationCause.MANUAL, null, null);

            assertTrue(
                    awaitingAnswers().find(List.of(member.id())).stream().noneMatch(a -> a.eventId() == called.id()));
        } finally {
            eventRepo.delete(called.id());
        }
    }

    /**
     * Declining a registration event is an answer, and so is giving up a place: neither leaves the event
     * waiting for an answer, which is what kept the warnings coming for people who were not interested.
     */
    @Test
    void aDeclinedOrGivenUpRegistrationAwaitsNoAnswer() {
        Instant start = Instant.now().plus(java.time.Duration.ofDays(10));
        var asked = eventRepo.create(
                station.id(),
                "Anmeldung",
                "desc",
                StationEvent.EventType.ONE_TIME,
                null,
                start,
                start.plusSeconds(3600),
                null,
                true,
                RegistrationDeadline.fixed(Instant.now().plus(java.time.Duration.ofDays(2))),
                false,
                null,
                null,
                null,
                null);
        try {
            var day = occurrenceCalendar.dateInView(asked).orElseThrow();
            assertTrue(
                    awaitingAnswers().find(List.of(member.id())).stream().anyMatch(a -> a.eventId() == asked.id()),
                    "nobody has answered yet");

            var answer = eventRegistrationRepo.create(asked.id(), member.id(), day, RegistrationStatus.DECLINED, null);
            assertTrue(
                    awaitingAnswers().find(List.of(member.id())).stream().noneMatch(a -> a.eventId() == asked.id()),
                    "a decline is an answer");

            eventRegistrationRepo.recordAnswer(answer.id(), RegistrationStatus.ACCEPTED);
            eventRegistrationRepo.recordAnswer(answer.id(), RegistrationStatus.WITHDRAWN);
            assertTrue(
                    awaitingAnswers().find(List.of(member.id())).stream().noneMatch(a -> a.eventId() == asked.id()),
                    "and so is a place given up");
        } finally {
            eventRepo.delete(asked.id());
        }
    }

    @Test
    void createReadUpdateDelete() {
        var event = eventRepo.create(
                station.id(),
                "Fire Drill",
                "Annual drill",
                StationEvent.EventType.ONE_TIME,
                null,
                Instant.parse("2026-06-15T09:00:00Z"),
                Instant.parse("2026-06-15T12:00:00Z"),
                null,
                false,
                RegistrationDeadline.none(),
                false,
                null,
                null,
                null,
                null);
        assertEquals("Fire Drill", event.name());
        assertEquals(StationEvent.EventType.ONE_TIME, event.eventType());

        assertEquals("Fire Drill", eventRepo.findById(event.id()).orElseThrow().name());
        assertTrue(eventRepo.findByStation(station.id()).stream().anyMatch(e -> e.id() == event.id()));

        assertTrue(eventRepo.update(
                event.id(),
                "Updated Drill",
                "Updated desc",
                StationEvent.EventType.ONE_TIME,
                null,
                Instant.parse("2026-07-01T10:00:00Z"),
                Instant.parse("2026-07-01T13:00:00Z"),
                null,
                true,
                RegistrationDeadline.none(),
                true,
                null,
                false,
                null,
                null,
                null));
        var updated = eventRepo.findById(event.id()).orElseThrow();
        assertEquals("Updated Drill", updated.name());
        assertEquals("Updated desc", updated.description());
        assertTrue(updated.requiresRegistration());
        assertTrue(updated.requiresConfirmation());

        assertTrue(eventRepo.delete(event.id()));
        assertTrue(eventRepo.findById(event.id()).isEmpty());
    }

    @Test
    void createRecurringEvent() {
        var event = eventRepo.create(
                station.id(),
                "Weekly Meeting",
                "Team sync",
                StationEvent.EventType.RECURRING,
                1,
                Instant.parse("2026-06-16T14:00:00Z"),
                Instant.parse("2026-06-16T15:00:00Z"),
                null,
                false,
                RegistrationDeadline.none(),
                false,
                null,
                null,
                null,
                null);
        try {
            assertEquals(StationEvent.EventType.RECURRING, event.eventType());
            assertEquals(1, event.dayOfWeek());
        } finally {
            eventRepo.delete(event.id());
        }
    }

    @Test
    void aRelativeDeadlineIsStoredAndReadBack() {
        var event = eventRepo.create(
                station.id(),
                "Close Days Event",
                "desc",
                StationEvent.EventType.RECURRING,
                3,
                Instant.parse("2026-06-15T09:00:00Z"),
                Instant.parse("2026-06-15T12:00:00Z"),
                null,
                true,
                RegistrationDeadline.relative(2880, RegistrationDeadline.DeadlineAnchor.MIDNIGHT),
                false,
                null,
                null,
                null,
                null);
        try {
            var read = eventRepo.findById(event.id()).orElseThrow();
            assertEquals(
                    RegistrationDeadline.relative(2880, RegistrationDeadline.DeadlineAnchor.MIDNIGHT),
                    read.registrationDeadline());
            assertTrue(eventRepo.findWithDeadline().stream().anyMatch(e -> e.id() == event.id()));
        } finally {
            eventRepo.delete(event.id());
        }
    }

    @Test
    void findByIdAndDeleteMissEmpty() {
        assertTrue(eventRepo.findById(99999).isEmpty());
        assertFalse(eventRepo.delete(99999));
    }

    @Test
    void updateNonExistentEvent() {
        assertFalse(eventRepo.update(
                99999,
                "X",
                "X",
                StationEvent.EventType.ONE_TIME,
                null,
                Instant.now(),
                Instant.now(),
                null,
                false,
                RegistrationDeadline.none(),
                false,
                null,
                false,
                null,
                null,
                null));
    }

    @Test
    void findByStationForMemberReturnsVisibleEvents() {
        var event =
                oneTime("Member Visible", Instant.parse("2027-02-15T09:00:00Z"), Instant.parse("2027-02-15T12:00:00Z"));
        try {
            var events = eventRepo.findByStationForMember(station.id(), member.id(), false);
            assertTrue(events.stream().anyMatch(e -> e.id() == event.id()));
        } finally {
            eventRepo.delete(event.id());
        }
    }

    @Test
    void searchForPickerOffersInternalEventsToMembersOnly() {
        var event = oneTime(
                "Internal-Picker-Event",
                Instant.now().plus(40, ChronoUnit.DAYS),
                Instant.now().plus(41, ChronoUnit.DAYS));
        try {
            var members = eventRepo.searchForPicker(
                    station.id(), BlockAudience.MEMBERS, "internal-picker", PickerMode.FUTURE, 20);
            assertTrue(members.stream().anyMatch(e -> "Internal-Picker-Event".equals(e.name())));

            var publicOnly = eventRepo.searchForPicker(
                    station.id(), BlockAudience.PUBLIC, "internal-picker", PickerMode.FUTURE, 20);
            assertTrue(publicOnly.isEmpty());
        } finally {
            eventRepo.delete(event.id());
        }
    }

    @Test
    void findFilteredWithoutFilters() {
        var event =
                oneTime("Filtered Event", Instant.parse("2027-01-15T09:00:00Z"), Instant.parse("2027-01-15T12:00:00Z"));
        try {
            var results = eventRepo.findFiltered(station.id(), null, null, null);
            assertTrue(results.stream().anyMatch(e -> e.id() == event.id()));
        } finally {
            eventRepo.delete(event.id());
        }
    }

    @Test
    void findFilteredByCategoryMemberAndRegistration() {
        var category = eventCategoryRepo.create(station.id(), "FilterCat", 0, null);
        var event = eventRepo.create(
                station.id(),
                "Cat Filtered",
                "desc",
                StationEvent.EventType.ONE_TIME,
                null,
                Instant.parse("2027-03-15T09:00:00Z"),
                Instant.parse("2027-03-15T12:00:00Z"),
                null,
                true,
                RegistrationDeadline.none(),
                false,
                category.id(),
                null,
                null,
                null);
        try {
            var byCategory = eventRepo.findFiltered(station.id(), null, category.id(), null);
            assertTrue(byCategory.stream().anyMatch(e -> e.id() == event.id()));
            assertTrue(byCategory.stream().allMatch(e -> e.categoryId() != null && e.categoryId() == category.id()));

            var byRegistration = eventRepo.findFiltered(station.id(), null, null, true);
            assertTrue(byRegistration.stream().anyMatch(e -> e.id() == event.id()));
            assertTrue(byRegistration.stream().allMatch(StationEvent::requiresRegistration));

            assertNotNull(eventRepo.findFiltered(station.id(), member.id(), category.id(), true));
        } finally {
            eventRepo.delete(event.id());
            eventCategoryRepo.delete(category.id());
        }
    }

    @Test
    void updateRestrictionMode() {
        var event = oneTime(
                "Restriction Mode", Instant.parse("2027-04-15T09:00:00Z"), Instant.parse("2027-04-15T12:00:00Z"));
        try {
            assertTrue(eventRepo.updateRestrictionMode(event.id(), RestrictionMode.OR));
            assertTrue(eventRepo.updateRestrictionMode(event.id(), RestrictionMode.AND));
            assertFalse(eventRepo.updateRestrictionMode(99999, RestrictionMode.OR));
        } finally {
            eventRepo.delete(event.id());
        }
    }

    @Test
    void cancelEvent() {
        var event = oneTime("Cancel Me", Instant.parse("2027-08-15T09:00:00Z"), Instant.parse("2027-08-15T12:00:00Z"));
        try {
            assertTrue(eventRepo.cancelEvent(event.id(), "Testing cancellation"));
            var cancelled = eventRepo.findById(event.id()).orElseThrow();
            assertTrue(cancelled.cancelled());
            assertEquals("Testing cancellation", cancelled.cancelReason());
            assertNotNull(cancelled.cancelledAt());
        } finally {
            eventRepo.delete(event.id());
        }
        assertFalse(eventRepo.cancelEvent(99999, "reason"));
    }

    @Test
    void findThresholdCandidates() {
        var event = eventRepo.create(
                station.id(),
                "Auto Cancel Event",
                "desc",
                StationEvent.EventType.ONE_TIME,
                null,
                Instant.parse("2027-09-15T09:00:00Z"),
                Instant.parse("2027-09-15T12:00:00Z"),
                null,
                true,
                RegistrationDeadline.none(),
                false,
                null,
                null,
                5,
                1000);
        try {
            assertTrue(eventRepo.findThresholdCandidates().stream().anyMatch(e -> e.id() == event.id()));
            eventRepo.cancelEvent(event.id(), null);
            assertFalse(
                    eventRepo.findThresholdCandidates().stream().anyMatch(e -> e.id() == event.id()),
                    "a series called off as a whole has no date left to check");
        } finally {
            eventRepo.delete(event.id());
        }
    }

    @Test
    void theDaysBeforeEachDateAreKept() {
        var event = eventRepo.create(
                station.id(),
                "Threshold Days Event",
                "desc",
                StationEvent.EventType.RECURRING,
                3,
                Instant.parse("2027-10-13T09:00:00Z"),
                Instant.parse("2027-10-13T12:00:00Z"),
                null,
                false,
                RegistrationDeadline.none(),
                false,
                null,
                null,
                3,
                2);
        try {
            var read = eventRepo.findById(event.id()).orElseThrow();
            assertEquals(3, read.minRegistrations());
            assertEquals(2, read.thresholdDays());
        } finally {
            eventRepo.delete(event.id());
        }
    }

    /**
     * A date with sign-ups waiting is offered with the close its passed deadline was handled for, so the
     * caller can tell a handled close from one that moved since, and a fixed deadline handled for the
     * instant it still has is not offered at all. The record is per date and the latest close replaces
     * the earlier one.
     */
    @Test
    void aPendingDateCarriesTheCloseItWasHandledFor() {
        var closes = Instant.now().minus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);
        var event = eventRepo.create(
                station.id(),
                "Expired Deadline",
                "desc",
                StationEvent.EventType.ONE_TIME,
                null,
                Instant.now().plus(1, ChronoUnit.DAYS),
                Instant.now().plus(1, ChronoUnit.DAYS).plus(2, ChronoUnit.HOURS),
                null,
                true,
                RegistrationDeadline.fixed(closes),
                false,
                null,
                null,
                null,
                null);
        LocalDate date = LocalDate.now().plusDays(1);
        try {
            eventRegistrationRepo.create(event.id(), member.id(), date, RegistrationStatus.PENDING, null);
            assertEquals(List.of(new EventRepository.PendingDate(event.id(), date, null, 1)), pendingOf(event));

            eventRepo.markDeadlineExpired(event.id(), date.plusDays(7), closes);
            assertEquals(
                    List.of(new EventRepository.PendingDate(event.id(), date, null, 1)),
                    pendingOf(event),
                    "another date's record leaves this one unhandled");

            var earlier = closes.minus(1, ChronoUnit.DAYS);
            eventRepo.markDeadlineExpired(event.id(), date, earlier);
            assertEquals(
                    List.of(new EventRepository.PendingDate(event.id(), date, earlier, 1)),
                    pendingOf(event),
                    "handled for a close the fixed deadline no longer has");

            eventRepo.markDeadlineExpired(event.id(), date, closes);
            assertEquals(List.of(), pendingOf(event), "handled for the fixed deadline it still has");
        } finally {
            eventRepo.delete(event.id());
        }
    }

    private List<EventRepository.PendingDate> pendingOf(StationEvent event) {
        return eventRepo.findPendingDatesWithDeadline().stream()
                .filter(pending -> pending.eventId() == event.id())
                .toList();
    }

    private StationEvent withDeadline(
            String name, StationEvent.EventType type, Instant start, RegistrationDeadline deadline) {
        return eventRepo.create(
                station.id(),
                name,
                "desc",
                type,
                type == StationEvent.EventType.ONE_TIME
                        ? null
                        : start.atZone(ZoneOffset.UTC).getDayOfWeek().getValue(),
                start,
                start.plus(2, ChronoUnit.HOURS),
                null,
                true,
                deadline,
                false,
                null,
                null,
                null,
                null);
    }

    /** A one-time appointment already begun and a series already ended have nothing left to close. */
    @Test
    void whatIsOverCannotClose() {
        var deadline = RegistrationDeadline.relative(24 * 60, RegistrationDeadline.DeadlineAnchor.EXACT);
        var past = withDeadline(
                "Vorbei", StationEvent.EventType.ONE_TIME, Instant.now().minus(1, ChronoUnit.DAYS), deadline);
        var ended = withDeadline(
                "Beendet", StationEvent.EventType.RECURRING, Instant.now().minus(30, ChronoUnit.DAYS), deadline);
        var running = withDeadline(
                "Läuft", StationEvent.EventType.RECURRING, Instant.now().minus(30, ChronoUnit.DAYS), deadline);
        try {
            query("UPDATE station_event SET repeat_until = current_date - 1 WHERE id = :id;")
                    .single(call().bind("id", ended.id()))
                    .update();
            query("UPDATE station_event SET repeat_until = current_date + 30 WHERE id = :id;")
                    .single(call().bind("id", running.id()))
                    .update();

            var closable =
                    eventRepo.findWithDeadline().stream().map(StationEvent::id).toList();
            assertFalse(closable.contains(past.id()), "a one-time appointment already begun");
            assertFalse(closable.contains(ended.id()), "a series whose last day has passed");
            assertTrue(closable.contains(running.id()), "a series still running");
        } finally {
            eventRepo.delete(past.id());
            eventRepo.delete(ended.id());
            eventRepo.delete(running.id());
        }
    }

    /**
     * Only dates whose deadline may have passed are offered: a fixed deadline once its instant is
     * behind, a relative one up to its span plus two days ahead, and nothing more than a week back.
     */
    @Test
    void onlyDatesWhoseDeadlineMayHavePassedAreOffered() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        var ahead = withDeadline(
                "Fest, noch offen",
                StationEvent.EventType.RECURRING,
                Instant.now().minus(30, ChronoUnit.DAYS),
                RegistrationDeadline.fixed(Instant.now().plus(1, ChronoUnit.DAYS)));
        var relative = withDeadline(
                "Relativ",
                StationEvent.EventType.RECURRING,
                Instant.now().minus(30, ChronoUnit.DAYS),
                RegistrationDeadline.relative(2 * 24 * 60, RegistrationDeadline.DeadlineAnchor.EXACT));
        try {
            eventRegistrationRepo.create(ahead.id(), member.id(), today.plusDays(1), RegistrationStatus.PENDING, null);
            eventRegistrationRepo.create(
                    relative.id(), member.id(), today.plusDays(3), RegistrationStatus.PENDING, null);
            eventRegistrationRepo.create(
                    relative.id(), member.id(), today.plusDays(10), RegistrationStatus.PENDING, null);
            eventRegistrationRepo.create(
                    relative.id(), member.id(), today.minusDays(8), RegistrationStatus.PENDING, null);

            var offered = eventRepo.findPendingDatesWithDeadline();
            assertTrue(
                    offered.stream().noneMatch(date -> date.eventId() == ahead.id()), "a fixed deadline still ahead");
            assertEquals(
                    List.of(today.plusDays(3)),
                    offered.stream()
                            .filter(date -> date.eventId() == relative.id())
                            .map(EventRepository.PendingDate::date)
                            .toList(),
                    "inside the span plus two days, neither beyond it nor more than a week back");
        } finally {
            eventRepo.delete(ahead.id());
            eventRepo.delete(relative.id());
        }
    }

    /** A relative deadline of a one-time appointment asks for an answer until its computed close. */
    @Test
    void anAnswerIsAwaitedUntilTheRelativeDeadline() {
        var deadline = RegistrationDeadline.relative(2 * 24 * 60, RegistrationDeadline.DeadlineAnchor.EXACT);
        var open = withDeadline(
                "Noch offen", StationEvent.EventType.ONE_TIME, Instant.now().plus(10, ChronoUnit.DAYS), deadline);
        var closed = withDeadline(
                "Schon zu", StationEvent.EventType.ONE_TIME, Instant.now().plus(1, ChronoUnit.DAYS), deadline);
        try {
            var awaiting = awaitingAnswers().find(List.of(member.id()));
            var listed = awaiting.stream()
                    .filter(answer -> answer.eventId() == open.id())
                    .findFirst()
                    .orElseThrow();
            assertEquals(open.startTime().minus(2, ChronoUnit.DAYS), listed.registrationClosesAt());
            assertTrue(awaiting.stream().noneMatch(answer -> answer.eventId() == closed.id()));
        } finally {
            eventRepo.delete(open.id());
            eventRepo.delete(closed.id());
        }
    }

    @Test
    void findEventsWithReminders() {
        var event = oneTime(
                "ReminderListEvent", Instant.parse("2028-09-15T09:00:00Z"), Instant.parse("2028-09-15T12:00:00Z"));
        try {
            eventReminderRepo.replace(event.id(), List.of(1, 3));
            var result = eventRepo.findEventsWithReminders();
            assertEquals(
                    1,
                    result.stream().filter(e -> e.id() == event.id()).count(),
                    "EXISTS-based query should not duplicate the event row");
        } finally {
            eventRepo.delete(event.id());
        }
    }

    @Test
    void existsForStation() {
        var emptyStation = stationRepo.create("Empty For Exists");
        try {
            assertFalse(eventRepo.existsForStation(emptyStation.id()));
            var event = oneTime(
                    "Exists Probe", Instant.parse("2028-08-15T09:00:00Z"), Instant.parse("2028-08-15T12:00:00Z"));
            try {
                assertTrue(eventRepo.existsForStation(station.id()));
            } finally {
                eventRepo.delete(event.id());
            }
        } finally {
            stationRepo.delete(emptyStation.id());
        }
    }

    @Test
    void findMaxEventUpdatedAtIsEpochWithoutEventsAndAdvancesOnCreate() {
        var freshStation = stationRepo.create("Fresh Max Tracking");
        try {
            assertEquals(Instant.EPOCH, eventRepo.findMaxEventUpdatedAt(freshStation.id()));
            var event = eventRepo.create(
                    freshStation.id(),
                    "Tracked",
                    "",
                    StationEvent.EventType.ONE_TIME,
                    null,
                    Instant.parse("2027-10-10T09:00:00Z"),
                    Instant.parse("2027-10-10T11:00:00Z"),
                    null,
                    true,
                    RegistrationDeadline.none(),
                    false,
                    null,
                    null,
                    null,
                    null);
            assertTrue(
                    eventRepo.findMaxEventUpdatedAt(freshStation.id()).isAfter(Instant.EPOCH),
                    "create should bump the max updated_at");
            eventRepo.delete(event.id());
        } finally {
            stationRepo.delete(freshStation.id());
        }
    }

    @Test
    void findPublicUidsByIdsAndFindByPublicUid() {
        assertTrue(eventRepo.findPublicUidsByIds(station.id(), List.of()).isEmpty());
        var event =
                oneTime("PublicUidEvent", Instant.parse("2028-07-15T09:00:00Z"), Instant.parse("2028-07-15T12:00:00Z"));
        try {
            var map = eventRepo.findPublicUidsByIds(station.id(), List.of(event.id()));
            assertTrue(map.containsKey(event.id()));
            var uid = map.get(event.id());
            assertNotNull(uid);
            var byUid = eventRepo.findByPublicUid(station.id(), uid);
            assertTrue(byUid.isPresent());
            assertEquals(event.id(), byUid.orElseThrow().id());
            assertTrue(
                    eventRepo.findByPublicUid(station.id(), UUID.randomUUID()).isEmpty());
        } finally {
            eventRepo.delete(event.id());
        }
    }

    @Test
    void searchForPicker() {
        var category = eventCategoryRepo.create(station.id(), "PickerCat", 0, null);
        eventCategoryRepo.update(category.id(), "PickerCat", 0, null, true, null);
        var future = eventRepo.create(
                station.id(),
                "Future-Picker-Event",
                "desc",
                StationEvent.EventType.ONE_TIME,
                null,
                Instant.now().plus(30, ChronoUnit.DAYS),
                Instant.now().plus(30, ChronoUnit.DAYS).plusSeconds(3600),
                null,
                false,
                RegistrationDeadline.none(),
                false,
                category.id(),
                null,
                null,
                null);
        var past = eventRepo.create(
                station.id(),
                "Past-Picker-Event",
                "desc",
                StationEvent.EventType.ONE_TIME,
                null,
                Instant.now().minus(60, ChronoUnit.DAYS),
                Instant.now().minus(60, ChronoUnit.DAYS).plusSeconds(3600),
                null,
                false,
                RegistrationDeadline.none(),
                false,
                category.id(),
                null,
                null,
                null);
        try {
            var futureMatches =
                    eventRepo.searchForPicker(station.id(), BlockAudience.PUBLIC, "Future", PickerMode.FUTURE, 20);
            var match = futureMatches.stream()
                    .filter(e -> "Future-Picker-Event".equals(e.name()))
                    .findFirst()
                    .orElseThrow();
            assertNotNull(match.eventUid());
            assertNotNull(match.startTime());
            assertEquals("PickerCat", match.categoryName());

            var pastMatches = eventRepo.searchForPicker(station.id(), BlockAudience.PUBLIC, null, PickerMode.PAST, 20);
            assertTrue(pastMatches.stream().anyMatch(e -> "Past-Picker-Event".equals(e.name())));

            var all = eventRepo.searchForPicker(station.id(), BlockAudience.PUBLIC, "  ", PickerMode.ALL, 20);
            assertTrue(all.stream().anyMatch(e -> "Future-Picker-Event".equals(e.name())));
            assertTrue(all.stream().anyMatch(e -> "Past-Picker-Event".equals(e.name())));
        } finally {
            eventRepo.delete(future.id());
            eventRepo.delete(past.id());
            eventCategoryRepo.delete(category.id());
        }
    }
}
