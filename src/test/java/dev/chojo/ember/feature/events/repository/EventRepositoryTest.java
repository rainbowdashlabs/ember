/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.repository;

import dev.chojo.ember.feature.account.entity.Account;
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
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

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
                null,
                false,
                null,
                null,
                null,
                null,
                null);
    }

    /**
     * An event closing inside the window is offered once for that lead time and never again, which is
     * what keeps a sweep running every few minutes from warning every few minutes.
     */
    @Test
    void anEventClosingSoonIsOfferedUntilItsWarningIsRecorded() {
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
                Instant.now().plus(java.time.Duration.ofDays(2)),
                false,
                null,
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
                Instant.now().plus(java.time.Duration.ofDays(30)),
                false,
                null,
                null,
                null,
                null,
                null);
        try {
            var due = eventRepo.findEventsClosingIn(3);
            assertTrue(due.stream().anyMatch(e -> e.eventId() == closing.id()));
            assertTrue(due.stream().noneMatch(e -> e.eventId() == later.id()), "a distant deadline waits");

            eventReminderRepo.markDeadlineWarningSent(closing.id(), 3);
            assertTrue(
                    eventRepo.findEventsClosingIn(3).stream().noneMatch(e -> e.eventId() == closing.id()),
                    "a warning already given is not given again");
            assertTrue(
                    eventRepo.findEventsClosingIn(1).stream().noneMatch(e -> e.eventId() == closing.id()),
                    "and one day out is still two days away");
        } finally {
            eventRepo.delete(closing.id());
            eventRepo.delete(later.id());
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
                null,
                false,
                null,
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
                null,
                true,
                null,
                false,
                null,
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
                null,
                false,
                null,
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
    void createEventWithCloseDaysAndFindThem() {
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
                null,
                false,
                null,
                null,
                null,
                null,
                2);
        try {
            assertEquals(2, event.registrationCloseDays());
            assertTrue(eventRepo.findRecurringEventsWithCloseDays().stream().anyMatch(e -> e.id() == event.id()));
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
                null,
                false,
                null,
                false,
                null,
                null,
                null,
                null));
    }

    @Test
    void findByStationForMemberReturnsVisibleEvents() {
        var event =
                oneTime("Member Visible", Instant.parse("2027-02-15T09:00:00Z"), Instant.parse("2027-02-15T12:00:00Z"));
        try {
            var events = eventRepo.findByStationForMember(station.id(), member.id());
            assertTrue(events.stream().anyMatch(e -> e.id() == event.id()));
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
                null,
                false,
                category.id(),
                null,
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
    void findAutoCancel() {
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
                null,
                false,
                null,
                null,
                5,
                Instant.now().minusSeconds(3600),
                null);
        try {
            assertTrue(eventRepo.findAutoCancel().stream().anyMatch(e -> e.id() == event.id()));
        } finally {
            eventRepo.delete(event.id());
        }
    }

    @Test
    void setThresholdNotified() {
        var event = eventRepo.create(
                station.id(),
                "Threshold Notify Event",
                "desc",
                StationEvent.EventType.ONE_TIME,
                null,
                Instant.parse("2027-10-15T09:00:00Z"),
                Instant.parse("2027-10-15T12:00:00Z"),
                null,
                false,
                null,
                false,
                null,
                null,
                3,
                Instant.now().plusSeconds(86400),
                null);
        try {
            assertTrue(eventRepo.setThresholdNotified(event.id()));
            assertTrue(eventRepo.findById(event.id()).orElseThrow().thresholdNotified());
        } finally {
            eventRepo.delete(event.id());
        }
    }

    @Test
    void markDeadlineNotifiedAndFindExpiredDeadlines() {
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
                Instant.now().minus(1, ChronoUnit.HOURS),
                false,
                null,
                null,
                null,
                null,
                null);
        try {
            eventRegistrationRepo.create(
                    event.id(), member.id(), LocalDate.now().plusDays(1), RegistrationStatus.PENDING, null);
            var expired = eventRepo.findOneTimeEventsWithExpiredDeadline().stream()
                    .filter(e -> e.eventId() == event.id())
                    .findFirst()
                    .orElseThrow();
            assertEquals(station.id(), expired.stationId());
            assertEquals("Expired Deadline", expired.name());
            assertEquals(1, expired.pendingCount());

            eventRepo.markDeadlineNotified(event.id());
            assertFalse(
                    eventRepo.findOneTimeEventsWithExpiredDeadline().stream().anyMatch(e -> e.eventId() == event.id()));
        } finally {
            eventRepo.delete(event.id());
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
                    null,
                    false,
                    null,
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
                null,
                false,
                category.id(),
                null,
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
                null,
                false,
                category.id(),
                null,
                null,
                null,
                null);
        try {
            var futureMatches =
                    eventRepo.searchForPicker(station.id(), "Future", EventRepository.PickerMode.FUTURE, 20);
            var match = futureMatches.stream()
                    .filter(e -> "Future-Picker-Event".equals(e.name()))
                    .findFirst()
                    .orElseThrow();
            assertNotNull(match.eventUid());
            assertNotNull(match.startTime());
            assertEquals("PickerCat", match.categoryName());

            var pastMatches = eventRepo.searchForPicker(station.id(), null, EventRepository.PickerMode.PAST, 20);
            assertTrue(pastMatches.stream().anyMatch(e -> "Past-Picker-Event".equals(e.name())));

            var all = eventRepo.searchForPicker(station.id(), "  ", EventRepository.PickerMode.ALL, 20);
            assertTrue(all.stream().anyMatch(e -> "Future-Picker-Event".equals(e.name())));
            assertTrue(all.stream().anyMatch(e -> "Past-Picker-Event".equals(e.name())));
        } finally {
            eventRepo.delete(future.id());
            eventRepo.delete(past.id());
            eventCategoryRepo.delete(category.id());
        }
    }
}
