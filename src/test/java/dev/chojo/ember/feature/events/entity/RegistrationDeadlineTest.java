/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import dev.chojo.ember.feature.events.entity.RegistrationDeadline.DeadlineAnchor;
import dev.chojo.ember.feature.events.entity.RegistrationDeadline.DeadlineKind;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * When sign-up closes for a date, on the station's clock, across both changes of Berlin's daylight
 * saving time in 2026 (29 March forward, 25 October back).
 */
class RegistrationDeadlineTest {

    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    private static final int WEEK = 7 * 24 * 60;

    private static Instant berlin(String localDateTime) {
        return LocalDateTime.parse(localDateTime).atZone(BERLIN).toInstant();
    }

    private static LocalDateTime local(Optional<Instant> instant) {
        return LocalDateTime.ofInstant(instant.orElseThrow(), BERLIN);
    }

    @Test
    void sevenDaysAtMidnightClosesOnTheSaturdayAWeekEarlierAt178Hours() {
        var start = berlin("2026-11-14T10:00");

        var closes =
                RegistrationDeadline.relative(WEEK, DeadlineAnchor.MIDNIGHT).closesAt(start, BERLIN);

        assertEquals(LocalDateTime.parse("2026-11-07T00:00"), local(closes));
        assertEquals(Duration.ofHours(178), Duration.between(closes.orElseThrow(), start));
    }

    @Test
    void wholeDaysAtMidnightCountCalendarDaysAcrossTheSpringChange() {
        var start = berlin("2026-04-04T00:30");

        var closes =
                RegistrationDeadline.relative(WEEK, DeadlineAnchor.MIDNIGHT).closesAt(start, BERLIN);

        assertEquals(LocalDateTime.parse("2026-03-28T00:00"), local(closes));
    }

    @Test
    void wholeDaysAtMidnightCountCalendarDaysAcrossTheAutumnChange() {
        var start = berlin("2026-10-31T10:00");

        var closes =
                RegistrationDeadline.relative(WEEK, DeadlineAnchor.MIDNIGHT).closesAt(start, BERLIN);

        assertEquals(LocalDateTime.parse("2026-10-24T00:00"), local(closes));
        assertEquals(Duration.ofHours(179), Duration.between(closes.orElseThrow(), start));
    }

    @Test
    void aSpanOfHoursAtMidnightGoesDownToTheDayItLandsOn() {
        var start = berlin("2026-03-30T10:00");

        var closes =
                RegistrationDeadline.relative(36 * 60, DeadlineAnchor.MIDNIGHT).closesAt(start, BERLIN);

        assertEquals(LocalDateTime.parse("2026-03-28T00:00"), local(closes));
    }

    @Test
    void noDaysAtMidnightClosesAtTheStartOfTheDay() {
        var start = berlin("2026-10-25T10:00");

        var closes = RegistrationDeadline.relative(0, DeadlineAnchor.MIDNIGHT).closesAt(start, BERLIN);

        assertEquals(LocalDateTime.parse("2026-10-25T00:00"), local(closes));
    }

    @Test
    void exactIsTheSpanInRealTimeAcrossTheSpringChange() {
        var start = berlin("2026-04-04T10:00");

        var closes = RegistrationDeadline.relative(WEEK, DeadlineAnchor.EXACT).closesAt(start, BERLIN);

        assertEquals(start.minus(Duration.ofHours(168)), closes.orElseThrow());
        assertEquals(LocalDateTime.parse("2026-03-28T09:00"), local(closes));
    }

    @Test
    void exactIsTheSpanInRealTimeAcrossTheAutumnChange() {
        var start = berlin("2026-10-31T10:00");

        var closes = RegistrationDeadline.relative(WEEK, DeadlineAnchor.EXACT).closesAt(start, BERLIN);

        assertEquals(start.minus(Duration.ofHours(168)), closes.orElseThrow());
        assertEquals(LocalDateTime.parse("2026-10-24T11:00"), local(closes));
    }

    @Test
    void aFixedDeadlineIgnoresTheStartAndNoneNeverCloses() {
        var at = berlin("2026-05-01T12:00");
        var fixed = RegistrationDeadline.fixed(at);

        assertEquals(Optional.of(at), fixed.closesAt(berlin("2026-05-10T10:00"), BERLIN));
        assertEquals(Optional.of(at), fixed.closesAt(berlin("2026-06-10T10:00"), BERLIN));
        assertEquals(Optional.empty(), RegistrationDeadline.none().closesAt(at, BERLIN));
    }

    @Test
    void closedFromTheInstantItClosesOn() {
        var at = berlin("2026-05-01T12:00");
        var series = weekly(RegistrationDeadline.fixed(at));
        var date = LocalDate.of(2026, 5, 6);

        assertFalse(series.registrationClosedOn(date, BERLIN, at.minusSeconds(1)));
        assertTrue(series.registrationClosedOn(date, BERLIN, at));
        assertFalse(
                weekly(RegistrationDeadline.none()).registrationClosedOn(date, BERLIN, at.plus(Duration.ofDays(365))));
    }

    @Test
    void onlyARuleThatCanBePlacedIsValid() {
        assertTrue(RegistrationDeadline.none().isValid());
        assertTrue(RegistrationDeadline.fixed(Instant.now()).isValid());
        assertTrue(RegistrationDeadline.relative(0, DeadlineAnchor.EXACT).isValid());
        assertFalse(RegistrationDeadline.relative(-1, DeadlineAnchor.MIDNIGHT).isValid());
        assertFalse(new RegistrationDeadline(DeadlineKind.FIXED, null, null, null).isValid());
        assertFalse(new RegistrationDeadline(DeadlineKind.RELATIVE, null, null, DeadlineAnchor.EXACT).isValid());
    }

    @Test
    void theRuleKeepsOnlyWhatItsKindUses() {
        assertEquals(RegistrationDeadline.none(), RegistrationDeadline.fixed(null));
        assertEquals(
                RegistrationDeadline.none(), new RegistrationDeadline(null, Instant.now(), 60, DeadlineAnchor.EXACT));

        var fixed = new RegistrationDeadline(DeadlineKind.FIXED, Instant.EPOCH, 60, DeadlineAnchor.MIDNIGHT);
        assertNull(fixed.offsetMinutes());
        assertNull(fixed.anchor());

        var relative = new RegistrationDeadline(DeadlineKind.RELATIVE, Instant.EPOCH, 60, null);
        assertNull(relative.at());
        assertEquals(DeadlineAnchor.EXACT, relative.anchor());
    }

    @Test
    void aFixedDeadlineClosesEveryDateOfASeriesAtOnce() {
        var at = berlin("2026-05-01T12:00");
        var series = weekly(RegistrationDeadline.fixed(at));
        var first = LocalDate.of(2026, 5, 6);
        var second = first.plusWeeks(1);

        assertEquals(Optional.of(at), series.registrationClosesOn(first, BERLIN));
        assertEquals(Optional.of(at), series.registrationClosesOn(second, BERLIN));
        assertTrue(series.registrationOpenOn(second, BERLIN, at.minusSeconds(1)));
        assertFalse(series.registrationOpenOn(first, BERLIN, at));
        assertFalse(series.registrationOpenOn(second, BERLIN, at));
    }

    @Test
    void aRelativeDeadlineClosesEachDateOfASeriesOnItsOwn() {
        var series = weekly(RegistrationDeadline.relative(2 * 24 * 60, DeadlineAnchor.MIDNIGHT));
        var first = LocalDate.of(2026, 5, 6);
        var second = first.plusWeeks(1);
        var between = berlin("2026-05-05T12:00");

        assertEquals(LocalDateTime.parse("2026-05-04T00:00"), local(series.registrationClosesOn(first, BERLIN)));
        assertEquals(LocalDateTime.parse("2026-05-11T00:00"), local(series.registrationClosesOn(second, BERLIN)));
        assertNotEquals(series.registrationClosesOn(first, BERLIN), series.registrationClosesOn(second, BERLIN));
        assertFalse(series.registrationOpenOn(first, BERLIN, between));
        assertTrue(series.registrationOpenOn(second, BERLIN, between));
    }

    @Test
    void anAppointmentWithoutADeadlineStaysOpen() {
        var series = weekly(null);

        assertEquals(RegistrationDeadline.none(), series.registrationDeadline());
        assertEquals(Optional.empty(), series.registrationClosesOn(LocalDate.of(2026, 5, 6), BERLIN));
        assertTrue(series.registrationOpenOn(LocalDate.of(2026, 5, 6), BERLIN, berlin("2027-01-01T00:00")));
    }

    @Test
    void anEventGroupIsClosedFromTheInstantItsDeadlineNames() {
        var closes = berlin("2026-05-01T12:00");
        var eventGroup = new EventGroup(
                1,
                1,
                new EventGroupDetails("Lehrgang", null, null, RegistrationDeadline.fixed(closes), false, null),
                RestrictionMode.AND,
                RestrictionMode.AND,
                false,
                null,
                null,
                closes);

        assertFalse(eventGroup.deadlinePassed(closes.minusSeconds(1)));
        assertTrue(eventGroup.deadlinePassed(closes));
    }

    @Test
    void theCloseInViewIsTheOneOfTheNextDate() {
        var calendar = new StationCalendar(BERLIN, List.of());
        var start = berlin("2030-05-01T20:00");
        var series = weekly(RegistrationDeadline.relative(2 * 24 * 60, DeadlineAnchor.MIDNIGHT));
        var later = new StationEvent(
                series.id(),
                series.stationId(),
                series.name(),
                null,
                StationEvent.EventType.RECURRING,
                start.atZone(BERLIN).getDayOfWeek().getValue(),
                start,
                start.plus(Duration.ofHours(2)),
                null,
                true,
                series.registrationDeadline(),
                false,
                null,
                RestrictionMode.AND,
                RestrictionMode.AND,
                false,
                null,
                null,
                false,
                null,
                null,
                null,
                null,
                null,
                null);

        assertEquals(LocalDateTime.parse("2030-04-29T00:00"), local(calendar.closesInView(later)));
        assertEquals(Optional.empty(), calendar.closesInView(weekly(RegistrationDeadline.none())));
    }

    @Test
    void theMinimumIsCheckedAtMidnightTheDaysBeforeAcrossTheSpringChange() {
        var series = withThreshold(weekly(RegistrationDeadline.none()), 3);
        var date = LocalDate.of(2026, 4, 1);

        assertEquals(LocalDateTime.parse("2026-03-29T00:00"), local(series.thresholdOn(date, BERLIN)));
    }

    @Test
    void theMinimumIsCheckedAtMidnightTheDaysBeforeAcrossTheAutumnChange() {
        var series = withThreshold(weekly(RegistrationDeadline.none()), 4);
        var date = LocalDate.of(2026, 10, 28);

        assertEquals(LocalDateTime.parse("2026-10-24T00:00"), local(series.thresholdOn(date, BERLIN)));
    }

    @Test
    void theMinimumIsDueFromMidnightNotFromTheTimeOfDayOfTheStart() {
        var start = berlin("2026-05-09T09:00");
        var moment = RegistrationDeadline.daysBeforeAtMidnight(2)
                .closesAt(start, BERLIN)
                .orElseThrow();

        assertEquals(LocalDateTime.parse("2026-05-07T00:00"), local(Optional.of(moment)));
        assertTrue(berlin("2026-05-06T23:00").isBefore(moment), "an hour before midnight it is not due");
        assertFalse(berlin("2026-05-07T01:00").isBefore(moment), "an hour after midnight it is");
    }

    @Test
    void anAppointmentWithoutAMinimumCheckHasNoMoment() {
        assertEquals(
                Optional.empty(), weekly(RegistrationDeadline.none()).thresholdOn(LocalDate.of(2026, 5, 6), BERLIN));
    }

    private static StationEvent withThreshold(StationEvent event, int days) {
        return new StationEvent(
                event.id(),
                event.stationId(),
                event.name(),
                event.description(),
                event.eventType(),
                event.dayOfWeek(),
                event.startTime(),
                event.endTime(),
                event.templateId(),
                event.requiresRegistration(),
                event.registrationDeadline(),
                event.requiresConfirmation(),
                event.categoryId(),
                event.restrictionMode(),
                event.viewRestrictionMode(),
                event.restricted(),
                event.isPublic(),
                event.registrationLimit(),
                event.cancelled(),
                event.cancelledAt(),
                event.cancelReason(),
                2,
                days,
                event.repeatUntil(),
                event.repeatCount(),
                event.queueOrder());
    }

    /** A weekly series on Wednesdays at eight in the evening, Berlin, taking sign-ups. */
    private static StationEvent weekly(RegistrationDeadline deadline) {
        var start = berlin("2026-05-06T20:00");
        return new StationEvent(
                1,
                1,
                "Übung",
                null,
                StationEvent.EventType.RECURRING,
                3,
                start,
                start.plus(Duration.ofHours(2)),
                null,
                true,
                deadline,
                false,
                null,
                RestrictionMode.AND,
                RestrictionMode.AND,
                false,
                null,
                null,
                false,
                null,
                null,
                null,
                null,
                null,
                null);
    }
}
