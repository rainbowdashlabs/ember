/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.repository;

import dev.chojo.ember.feature.events.entity.RegistrationDeadline;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventReminderRepositoryTest extends RepositoryTestBase {
    private static Station station;

    @BeforeAll
    static void setup() {
        station = stationRepo.create("Event Reminder Station");
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
    }

    private static StationEvent event(String name, Instant start) {
        return eventRepo.create(
                station.id(),
                name,
                "desc",
                StationEvent.EventType.ONE_TIME,
                null,
                start,
                start.plusSeconds(7200),
                null,
                false,
                RegistrationDeadline.none(),
                false,
                null,
                null,
                null,
                null);
    }

    /** The run-out warning is recorded per date and lead time, so a sweep every half hour warns once. */
    @Test
    void aDeadlineWarningIsRecordedOncePerDateAndLeadTime() {
        var created = event("Closing Event", Instant.parse("2028-04-01T09:00:00Z"));
        var other = event("Other Event", Instant.parse("2028-04-01T09:00:00Z"));
        LocalDate date = LocalDate.of(2028, 4, 1);
        Instant earlier = Instant.parse("2028-03-29T09:00:00Z");
        Instant closes = Instant.parse("2028-03-30T09:00:00Z");
        try {
            assertEquals(List.of(), eventReminderRepo.findDeadlineWarningsSent(List.of()));
            assertEquals(List.of(), eventReminderRepo.findDeadlineWarningsSent(List.of(created.id())));

            eventReminderRepo.markDeadlineWarningSent(created.id(), date, 3, earlier);
            eventReminderRepo.markDeadlineWarningSent(created.id(), date, 3, closes);
            eventReminderRepo.markDeadlineWarningSent(created.id(), date.plusWeeks(1), 1, closes);
            eventReminderRepo.markDeadlineWarningSent(other.id(), date, 3, closes);

            assertEquals(
                    Set.of(
                            new EventReminderRepository.DeadlineWarning(created.id(), date, 3, closes),
                            new EventReminderRepository.DeadlineWarning(created.id(), date.plusWeeks(1), 1, closes)),
                    Set.copyOf(eventReminderRepo.findDeadlineWarningsSent(List.of(created.id()))),
                    "per date and lead time, the later close replacing the earlier, only the appointments asked for");
        } finally {
            eventRepo.delete(created.id());
            eventRepo.delete(other.id());
        }
    }

    @Test
    void replaceAndFindReminderDays() {
        var created = event("Reminder Event", Instant.parse("2028-03-15T09:00:00Z"));
        try {
            eventReminderRepo.replace(created.id(), List.of(1, 3, 7));
            assertEquals(List.of(1, 3, 7), eventReminderRepo.findDays(created.id()));

            eventReminderRepo.replace(created.id(), List.of(2));
            assertEquals(List.of(2), eventReminderRepo.findDays(created.id()));

            eventReminderRepo.replace(created.id(), List.of());
            assertTrue(eventReminderRepo.findDays(created.id()).isEmpty());
        } finally {
            eventRepo.delete(created.id());
        }
    }

    @Test
    void replaceDropsDuplicateDays() {
        var created = event("DupeReminder", Instant.parse("2028-04-15T09:00:00Z"));
        try {
            eventReminderRepo.replace(created.id(), List.of(3, 3, 7));
            var days = eventReminderRepo.findDays(created.id());
            assertEquals(2, days.size());
            assertTrue(days.contains(3));
            assertTrue(days.contains(7));
        } finally {
            eventRepo.delete(created.id());
        }
    }

    @Test
    void findDaysForUnknownEventIsEmpty() {
        assertTrue(eventReminderRepo.findDays(99999).isEmpty());
    }

    @Test
    void markSentIsScopedToDateAndLeadTime() {
        var created = event("ReminderSent Event", Instant.parse("2028-05-15T09:00:00Z"));
        try {
            LocalDate eventDate = LocalDate.of(2028, 5, 15);
            assertFalse(eventReminderRepo.isSent(created.id(), eventDate, 3));

            eventReminderRepo.markSent(created.id(), eventDate, 3);
            assertTrue(eventReminderRepo.isSent(created.id(), eventDate, 3));
            assertFalse(eventReminderRepo.isSent(created.id(), eventDate, 1));
            assertFalse(eventReminderRepo.isSent(created.id(), eventDate.plusDays(1), 3));

            assertDoesNotThrow(() -> eventReminderRepo.markSent(created.id(), eventDate, 3));
        } finally {
            eventRepo.delete(created.id());
        }
    }
}
