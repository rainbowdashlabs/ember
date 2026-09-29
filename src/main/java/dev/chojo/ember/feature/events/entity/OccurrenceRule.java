/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import dev.chojo.ember.feature.events.entity.StationEvent.EventType;
import org.jspecify.annotations.Nullable;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.Optional;

/**
 * The dates one appointment falls on by its repetition alone, before any break is taken out.
 *
 * <p>The meaning is the one a calendar gives an RFC 5545 rule, because the calendar feed hands the
 * same appointment to other calendars as exactly such a rule and the two have to agree. The series
 * starts at its first date, which is the first day on or after the configured start that fits the
 * pattern, and nothing falls before it. A quarterly appointment comes round every third month
 * counted from that first date, not in the months a calendar year opens its quarters with. A
 * monthly or quarterly one falls on the configured weekday within the first seven days of its
 * month, and a yearly one on the day and month of its first date, which skips the years a 29
 * February does not exist in.
 *
 * <p>A weekday-based series that names no weekday takes the one of its start, which is what a
 * calendar does with a rule that names none.
 *
 * @param type    how the appointment repeats
 * @param first   the first date it falls on
 * @param weekday the weekday a weekly, monthly or quarterly series falls on
 * @param last    the last date it falls on, or null where the series has no end
 */
public record OccurrenceRule(
        EventType type,
        LocalDate first,
        DayOfWeek weekday,
        @Nullable LocalDate last) {

    private static final DateTimeFormatter UNTIL = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final int YEARS_BETWEEN_LEAP_DAYS = 8;

    /**
     * The rule of an appointment, read on the station's clock.
     *
     * <p>An end given as a number of times is the date of that many occurrences, counted from the
     * first; an end given as a day is the last occurrence on or before it. Breaks count towards the
     * number, the way an excluded date still counts in a calendar.
     *
     * @param event the appointment
     * @param zone  the station's clock, which says which day its start falls on
     * @return the rule, empty where the appointment has no start or its end comes before its first date
     */
    public static Optional<OccurrenceRule> of(StationEvent event, ZoneId zone) {
        if (event.startTime() == null) return Optional.empty();
        LocalDate start = event.startTime().atZone(zone).toLocalDate();
        DayOfWeek weekday = event.dayOfWeek() != null ? DayOfWeek.of(event.dayOfWeek()) : start.getDayOfWeek();
        var anchored = new OccurrenceRule(event.eventType(), start, weekday, null);
        LocalDate first = anchored.patternOnOrAfter(start);
        var open = new OccurrenceRule(event.eventType(), first, weekday, null);
        if (event.eventType() == EventType.ONE_TIME) return Optional.of(open.endingOn(first));

        if (event.repeatUntil() != null) {
            if (event.repeatUntil().isBefore(first)) return Optional.empty();
            return Optional.of(
                    open.endingOn(open.patternBefore(event.repeatUntil().plusDays(1))));
        }
        if (event.repeatCount() != null) return Optional.of(open.endingOn(open.nth(event.repeatCount())));
        return Optional.of(open);
    }

    private OccurrenceRule endingOn(LocalDate date) {
        return new OccurrenceRule(type, first, weekday, date);
    }

    /** Whether the appointment falls on this date by its repetition. */
    public boolean matches(LocalDate date) {
        return withinBounds(date) && fitsPattern(date);
    }

    /** The first date on or after this one that the appointment falls on, where one is left. */
    public Optional<LocalDate> onOrAfter(LocalDate date) {
        LocalDate from = date.isBefore(first) ? first : date;
        LocalDate candidate = patternOnOrAfter(from);
        if (candidate.isBefore(from) || (last != null && candidate.isAfter(last))) return Optional.empty();
        return Optional.of(candidate);
    }

    /** The last date strictly before this one that the appointment fell on, where it had one. */
    public Optional<LocalDate> before(LocalDate date) {
        LocalDate ceiling = last != null && date.isAfter(last) ? last.plusDays(1) : date;
        if (!ceiling.isAfter(first)) return Optional.empty();
        return Optional.of(patternBefore(ceiling));
    }

    /**
     * The rule in the wording a calendar reads, without the start it is anchored on.
     *
     * @return the rule, empty for an appointment that happens once
     */
    public Optional<String> calendarRule() {
        String day = weekday.name().substring(0, 2);
        String rule =
                switch (type) {
                    case ONE_TIME -> null;
                    case RECURRING -> "FREQ=WEEKLY;BYDAY=" + day;
                    case MONTHLY_FIRST -> "FREQ=MONTHLY;BYDAY=1" + day;
                    case QUARTERLY -> "FREQ=MONTHLY;INTERVAL=3;BYDAY=1" + day;
                    case YEARLY -> "FREQ=YEARLY";
                };
        if (rule == null) return Optional.empty();
        return Optional.of(last == null ? rule : rule + ";UNTIL=" + last.format(UNTIL) + "T235959Z");
    }

    private boolean withinBounds(LocalDate date) {
        return !date.isBefore(first) && (last == null || !date.isAfter(last));
    }

    private boolean fitsPattern(LocalDate date) {
        return switch (type) {
            case ONE_TIME -> date.equals(first);
            case RECURRING -> date.getDayOfWeek() == weekday;
            case MONTHLY_FIRST -> date.equals(firstWeekdayOf(YearMonth.from(date)));
            case QUARTERLY -> inQuarterMonth(YearMonth.from(date)) && date.equals(firstWeekdayOf(YearMonth.from(date)));
            case YEARLY -> MonthDay.from(date).equals(MonthDay.from(first));
        };
    }

    /** The occurrence counted this many from the first, the first being number one. */
    private LocalDate nth(int count) {
        LocalDate date = first;
        for (int i = 1; i < count; i++) date = patternOnOrAfter(date.plusDays(1));
        return date;
    }

    private LocalDate patternOnOrAfter(LocalDate date) {
        return switch (type) {
            case ONE_TIME -> first;
            case RECURRING -> date.with(TemporalAdjusters.nextOrSame(weekday));
            case MONTHLY_FIRST -> firstWeekdayOnOrAfter(date, 1);
            case QUARTERLY -> firstWeekdayOnOrAfter(date, 3);
            case YEARLY -> yearlyOnOrAfter(date);
        };
    }

    private LocalDate patternBefore(LocalDate date) {
        return switch (type) {
            case ONE_TIME -> first;
            case RECURRING -> date.minusDays(1).with(TemporalAdjusters.previousOrSame(weekday));
            case MONTHLY_FIRST -> firstWeekdayBefore(date, 1);
            case QUARTERLY -> firstWeekdayBefore(date, 3);
            case YEARLY -> yearlyBefore(date);
        };
    }

    private LocalDate firstWeekdayOnOrAfter(LocalDate date, int step) {
        YearMonth month = alignedForward(YearMonth.from(date), step);
        LocalDate candidate = firstWeekdayOf(month);
        return candidate.isBefore(date) ? firstWeekdayOf(month.plusMonths(step)) : candidate;
    }

    private LocalDate firstWeekdayBefore(LocalDate date, int step) {
        YearMonth month = alignedBackward(YearMonth.from(date), step);
        LocalDate candidate = firstWeekdayOf(month);
        return candidate.isBefore(date) ? candidate : firstWeekdayOf(month.minusMonths(step));
    }

    private YearMonth alignedForward(YearMonth month, int step) {
        return month.plusMonths(Math.floorMod(-monthsFromFirst(month), step));
    }

    private YearMonth alignedBackward(YearMonth month, int step) {
        return month.minusMonths(Math.floorMod(monthsFromFirst(month), step));
    }

    private boolean inQuarterMonth(YearMonth month) {
        return Math.floorMod(monthsFromFirst(month), 3) == 0;
    }

    private long monthsFromFirst(YearMonth month) {
        return ChronoUnit.MONTHS.between(YearMonth.from(first), month);
    }

    private LocalDate firstWeekdayOf(YearMonth month) {
        return month.atDay(1).with(TemporalAdjusters.nextOrSame(weekday));
    }

    private LocalDate yearlyOnOrAfter(LocalDate date) {
        MonthDay day = MonthDay.from(first);
        for (int year = date.getYear(); ; year++) {
            if (!day.isValidYear(year)) continue;
            LocalDate candidate = day.atYear(year);
            if (!candidate.isBefore(date)) return candidate;
        }
    }

    private LocalDate yearlyBefore(LocalDate date) {
        MonthDay day = MonthDay.from(first);
        for (int year = date.getYear(); year > date.getYear() - YEARS_BETWEEN_LEAP_DAYS - 1; year--) {
            if (!day.isValidYear(year)) continue;
            LocalDate candidate = day.atYear(year);
            if (candidate.isBefore(date)) return candidate;
        }
        return first;
    }
}
