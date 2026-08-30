/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.attendance.entity;

import dev.chojo.ember.feature.attendance.entity.AttendanceEntry.AttendanceStatus;
import dev.chojo.ember.feature.attendance.entity.AttendanceEntry.EntrySource;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The times a member's line on an attendance sheet carries.
 *
 * <p>The sheet, the report and the exported document all show the same two times, and each of them
 * used to work them out for itself. What they agree on is checked here, because the case that gets
 * it wrong is the quiet one: somebody who was not there, handed the session's hours.
 */
class AttendanceEntryShownTimesTest {

    private static final Instant SESSION_START = Instant.parse("2026-09-02T18:00:00Z");
    private static final Instant SESSION_END = Instant.parse("2026-09-02T20:00:00Z");
    private static final Instant OWN_ARRIVAL = Instant.parse("2026-09-02T18:30:00Z");
    private static final Instant OWN_DEPARTURE = Instant.parse("2026-09-02T19:15:00Z");

    private static AttendanceEntry entry(AttendanceStatus status, Instant checkIn, Instant checkOut) {
        return new AttendanceEntry(1, 1, 1, status, checkIn, checkOut, EntrySource.EXPECTED);
    }

    @Test
    void presentWithoutTimesStandsForTheWholeSession() {
        var present = entry(AttendanceStatus.PRESENT, null, null);

        assertEquals(SESSION_START, present.shownCheckIn(SESSION_START));
        assertEquals(SESSION_END, present.shownCheckOut(SESSION_END));
    }

    @Test
    void ownTimesBeatTheSession() {
        var present = entry(AttendanceStatus.PRESENT, OWN_ARRIVAL, OWN_DEPARTURE);

        assertEquals(OWN_ARRIVAL, present.shownCheckIn(SESSION_START));
        assertEquals(OWN_DEPARTURE, present.shownCheckOut(SESSION_END));
    }

    /** One half written down does not invent the other. */
    @Test
    void oneOwnTimeLeavesTheOtherOnTheSession() {
        var present = entry(AttendanceStatus.PRESENT, OWN_ARRIVAL, null);

        assertEquals(OWN_ARRIVAL, present.shownCheckIn(SESSION_START));
        assertEquals(SESSION_END, present.shownCheckOut(SESSION_END));
    }

    @Test
    void nobodyAbsentIsGivenTheSessionsHours() {
        for (var status :
                new AttendanceStatus[] {AttendanceStatus.ABSENT, AttendanceStatus.DECLINED, AttendanceStatus.UNCONFIRMED
                }) {
            var away = entry(status, null, null);

            assertNull(away.shownCheckIn(SESSION_START), "check-in for " + status);
            assertNull(away.shownCheckOut(SESSION_END), "check-out for " + status);
        }
    }

    /** A time somebody wrote down stays on the sheet, whatever the status became afterwards. */
    @Test
    void aWrittenTimeSurvivesBeingMarkedAway() {
        var away = entry(AttendanceStatus.ABSENT, OWN_ARRIVAL, OWN_DEPARTURE);

        assertEquals(OWN_ARRIVAL, away.shownCheckIn(SESSION_START));
        assertEquals(OWN_DEPARTURE, away.shownCheckOut(SESSION_END));
    }

    /** A session that never had times of its own has none to lend. */
    @Test
    void aSessionWithoutTimesLendsNothing() {
        var present = entry(AttendanceStatus.PRESENT, null, null);

        assertNull(present.shownCheckIn(null));
        assertNull(present.shownCheckOut(null));
    }
}
