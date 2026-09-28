/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import dev.chojo.ember.feature.members.entity.ExpirySettings;
import dev.chojo.ember.feature.members.entity.ProfileFieldConfig;
import dev.chojo.ember.feature.members.entity.SentExpiryReminder;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which reminder one expiry date owes on one day.
 */
class ExpiryReminderScheduleTest {
    private static final ZoneId UTC = ZoneOffset.UTC;
    private static final LocalDate EXPIRES = LocalDate.of(2026, 3, 31);

    private static ExpirySettings settings(String json) {
        return ProfileFieldConfig.parse(json).expiry();
    }

    private static SentExpiryReminder sent(LocalDate due, LocalDate on) {
        return new SentExpiryReminder(
                1, EXPIRES, due, on.atStartOfDay(UTC).plusHours(8).toInstant());
    }

    @Test
    void nothingIsOwedBeforeTheFirstReminderDay() {
        var owed = ExpiryReminderSchedule.due(EXPIRES, settings("{}"), LocalDate.of(2026, 2, 28), List.of(), UTC);

        assertTrue(owed.isEmpty());
    }

    @Test
    void aReminderIsOwedOnItsDay() {
        var owed = ExpiryReminderSchedule.due(EXPIRES, settings("{}"), LocalDate.of(2026, 3, 1), List.of(), UTC)
                .orElseThrow();

        assertEquals(LocalDate.of(2026, 3, 1), owed.remindOn());
        assertEquals(List.of(LocalDate.of(2026, 3, 1)), owed.done());
    }

    @Test
    void aReminderDoneWithIsNotOwedAgain() {
        var today = LocalDate.of(2026, 3, 1);
        var owed = ExpiryReminderSchedule.due(EXPIRES, settings("{}"), today, List.of(sent(today, today)), UTC);

        assertTrue(owed.isEmpty());
    }

    /** A sweep that missed days sends the nearest reminder and records the earlier ones beside it. */
    @Test
    void aLateSweepSendsOnlyTheNearestAndRecordsTheRest() {
        var owed = ExpiryReminderSchedule.due(EXPIRES, settings("{}"), LocalDate.of(2026, 4, 3), List.of(), UTC)
                .orElseThrow();

        assertEquals(LocalDate.of(2026, 4, 1), owed.remindOn(), "the first day after the last valid one");
        assertEquals(
                List.of(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 24), LocalDate.of(2026, 4, 1)), owed.done());
    }

    /** A reminder day added later reaches a date already inside it, even after a nearer one went out. */
    @Test
    void aNewReminderDayReachesADateAlreadyInsideIt() {
        var today = LocalDate.of(2026, 3, 25);
        var owed = ExpiryReminderSchedule.due(
                        EXPIRES,
                        settings("{\"reminderDays\":[30,7]}"),
                        today,
                        List.of(sent(LocalDate.of(2026, 3, 24), LocalDate.of(2026, 3, 24))),
                        UTC)
                .orElseThrow();

        assertEquals(LocalDate.of(2026, 3, 1), owed.remindOn());
    }

    @Test
    void theFirstDayAfterTheDateIsAlwaysOwed() {
        var owed = ExpiryReminderSchedule.due(
                        EXPIRES, settings("{\"reminderDays\":[]}"), LocalDate.of(2026, 4, 1), List.of(), UTC)
                .orElseThrow();

        assertEquals(LocalDate.of(2026, 4, 1), owed.remindOn());
    }

    @Test
    void withoutARepeatNothingIsOwedAfterTheExpiredReminder() {
        var afterwards = LocalDate.of(2026, 5, 1);
        var owed = ExpiryReminderSchedule.due(
                EXPIRES,
                settings("{\"reminderDays\":[]}"),
                afterwards,
                List.of(sent(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 1))),
                UTC);

        assertTrue(owed.isEmpty());
    }

    /** A repeat counts its gap from the day the last reminder about the passed date went out. */
    @Test
    void aRepeatIsOwedTheGapAfterTheLastReminderWentOut() {
        var repeating = settings("{\"reminderDays\":[],\"repeatEveryDays\":7}");
        var late = List.of(sent(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 3)));

        assertTrue(ExpiryReminderSchedule.due(EXPIRES, repeating, LocalDate.of(2026, 4, 9), late, UTC)
                .isEmpty());
        var owed = ExpiryReminderSchedule.due(EXPIRES, repeating, LocalDate.of(2026, 4, 10), late, UTC)
                .orElseThrow();
        assertEquals(LocalDate.of(2026, 4, 10), owed.remindOn());
    }

    /** Reminders before the date say nothing about when a repeat is owed. */
    @Test
    void aRepeatWaitsForTheExpiredReminder() {
        var repeating = settings("{\"reminderDays\":[7],\"repeatEveryDays\":1}");
        var before = List.of(sent(LocalDate.of(2026, 3, 24), LocalDate.of(2026, 3, 24)));

        var owed = ExpiryReminderSchedule.due(EXPIRES, repeating, LocalDate.of(2026, 3, 26), before, UTC);

        assertTrue(owed.isEmpty());
    }
}
