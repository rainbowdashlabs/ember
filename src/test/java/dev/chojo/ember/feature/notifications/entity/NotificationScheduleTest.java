/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.notifications.entity;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * When a station is written to.
 *
 * <p>The number this used to be was a length of time, and a length of time only ever says "no more
 * often than": a station wanting its mail before the working day got it whenever the server had last
 * been restarted. These are the moments themselves, so the cases worth holding are the ones about a
 * day rolling over and about a station that has been quiet.
 */
class NotificationScheduleTest {

    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    private static final Duration FLOOR = Duration.ofMinutes(60);
    private static final List<LocalTime> MORNING_AND_AFTERNOON = List.of(LocalTime.of(7, 0), LocalTime.of(14, 0));

    private static Instant berlin(String isoLocal) {
        return java.time.LocalDateTime.parse(isoLocal).atZone(BERLIN).toInstant();
    }

    @Test
    void nothingGoesOutBeforeTheFirstTimeAsked() {
        Instant waiting = berlin("2026-09-18T05:30");
        Instant now = berlin("2026-09-18T06:45");

        assertFalse(NotificationSchedule.isDue(MORNING_AND_AFTERNOON, null, waiting, BERLIN, FLOOR, now));
    }

    @Test
    void theMorningMailGoesOutAtSeven() {
        Instant waiting = berlin("2026-09-18T05:30");
        Instant now = berlin("2026-09-18T07:01");

        assertTrue(NotificationSchedule.isDue(MORNING_AND_AFTERNOON, null, waiting, BERLIN, FLOOR, now));
    }

    /** Having been written to at seven, nothing more goes out until the afternoon comes round. */
    @Test
    void theAfternoonIsWaitedFor() {
        Instant sent = berlin("2026-09-18T07:00");
        Instant waiting = berlin("2026-09-18T08:15");

        assertFalse(NotificationSchedule.isDue(
                MORNING_AND_AFTERNOON, sent, waiting, BERLIN, FLOOR, berlin("2026-09-18T11:00")));
        assertTrue(NotificationSchedule.isDue(
                MORNING_AND_AFTERNOON, sent, waiting, BERLIN, FLOOR, berlin("2026-09-18T14:02")));
    }

    /**
     * What arrives in the evening waits for the morning, which is what one mail a day means.
     *
     * <p>Midnight passing is not a time the station asked for, so nothing goes out on it.
     */
    @Test
    void theEveningWaitsForTheMorning() {
        var morningOnly = List.of(LocalTime.of(7, 0));
        Instant sent = berlin("2026-09-17T07:00");
        Instant waiting = berlin("2026-09-17T20:00");

        assertFalse(NotificationSchedule.isDue(morningOnly, sent, waiting, BERLIN, FLOOR, berlin("2026-09-18T01:00")));
        assertTrue(NotificationSchedule.isDue(morningOnly, sent, waiting, BERLIN, FLOOR, berlin("2026-09-18T07:00")));
    }

    /**
     * A time missed while the server was down is still sent once it is back.
     *
     * <p>Yesterday's times are looked at as well as today's, so an installation that was off over the
     * hour a station asked for writes to it when it returns rather than holding everything for a
     * further day.
     */
    @Test
    void aTimeMissedWhileNobodyWasListeningIsCaughtUp() {
        var morningOnly = List.of(LocalTime.of(7, 0));
        Instant sent = berlin("2026-09-15T07:00");
        Instant waiting = berlin("2026-09-15T08:00");

        assertTrue(NotificationSchedule.isDue(morningOnly, sent, waiting, BERLIN, FLOOR, berlin("2026-09-17T06:00")));
    }

    @Test
    void hourlyIsTheSameRuleSaidTwentyFourTimes() {
        var hourly = NotificationSchedule.everyHour();
        assertEquals(24, hourly.size());

        Instant sent = berlin("2026-09-18T09:00");
        Instant waiting = berlin("2026-09-18T09:20");

        assertFalse(NotificationSchedule.isDue(hourly, sent, waiting, BERLIN, FLOOR, berlin("2026-09-18T09:45")));
        assertTrue(NotificationSchedule.isDue(hourly, sent, waiting, BERLIN, FLOOR, berlin("2026-09-18T10:01")));
    }

    /**
     * The operator is accountable for what the installation sends, so their number is a floor under
     * every station's choice and not only the default for a station that has made none.
     */
    @Test
    void theOperatorsFloorHoldsWhateverTheStationAsksFor() {
        var hourly = NotificationSchedule.everyHour();
        Instant sent = berlin("2026-09-18T09:00");
        Instant waiting = berlin("2026-09-18T09:20");

        assertFalse(NotificationSchedule.isDue(
                hourly, sent, waiting, BERLIN, Duration.ofHours(6), berlin("2026-09-18T10:01")));
        assertTrue(NotificationSchedule.isDue(
                hourly, sent, waiting, BERLIN, Duration.ofHours(6), berlin("2026-09-18T15:01")));
    }

    /**
     * A station that has asked for nothing is written to exactly the way every station was before
     * any of this existed: on the next look, and thereafter no more often than the operator allows.
     */
    @Test
    void askingForNothingKeepsTheOperatorsNumber() {
        Instant waiting = berlin("2026-09-18T09:00");

        assertTrue(NotificationSchedule.isDue(List.of(), null, waiting, BERLIN, FLOOR, berlin("2026-09-18T09:05")));
        assertTrue(NotificationSchedule.isDue(null, null, waiting, BERLIN, FLOOR, berlin("2026-09-18T09:05")));

        Instant sent = berlin("2026-09-18T09:05");
        assertFalse(NotificationSchedule.isDue(List.of(), sent, waiting, BERLIN, FLOOR, berlin("2026-09-18T09:40")));
        assertTrue(NotificationSchedule.isDue(List.of(), sent, waiting, BERLIN, FLOOR, berlin("2026-09-18T10:06")));
    }

    /** A station quiet for a week is written to at the hour it asked for, not the moment it wakes. */
    @Test
    void aQuietStationIsStillWrittenToAtTheHourItAskedFor() {
        Instant sent = berlin("2026-09-10T07:00");
        Instant waiting = berlin("2026-09-18T06:00");

        assertFalse(NotificationSchedule.isDue(
                MORNING_AND_AFTERNOON, sent, waiting, BERLIN, FLOOR, berlin("2026-09-18T06:30")));
        assertTrue(NotificationSchedule.isDue(
                MORNING_AND_AFTERNOON, sent, waiting, BERLIN, FLOOR, berlin("2026-09-18T07:00")));
    }
}
