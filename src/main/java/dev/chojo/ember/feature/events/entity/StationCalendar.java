/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Which dates the appointments of one station fall on: their repetition, read on the station's
 * clock, with the station's breaks taken out.
 *
 * <p>This is the one answer to that question. The lists, the reminders, the registration deadlines,
 * the registration itself, the exports and the calendar feed all ask it here, so a date one of them
 * offers is a date every other one accepts.
 *
 * <p>A break suspends a series and leaves a one-off appointment standing: somebody who put a single
 * date into the holidays meant that date.
 *
 * @param zone   the station's clock
 * @param breaks the periods the station does not meet in
 */
public record StationCalendar(ZoneId zone, List<EventBreak> breaks) {

    /** Today on the station's clock. */
    public LocalDate today() {
        return LocalDate.now(zone);
    }

    /** The repetition of an appointment on this station's clock, empty where it falls on no date at all. */
    public Optional<OccurrenceRule> ruleOf(StationEvent event) {
        return OccurrenceRule.of(event, zone);
    }

    /** Whether an appointment takes place on this date. */
    public boolean occursOn(StationEvent event, LocalDate date) {
        return check(event, date) == DateCheck.OCCURRENCE;
    }

    /**
     * Whether an appointment takes place on this date, and if not, why not.
     *
     * @param event the appointment
     * @param date  the date asked about
     * @return what that date is to the appointment
     */
    public DateCheck check(StationEvent event, LocalDate date) {
        boolean fits = ruleOf(event).map(rule -> rule.matches(date)).orElse(false);
        if (!fits) return DateCheck.NOT_AN_OCCURRENCE;
        return suspends(event, date) ? DateCheck.IN_A_BREAK : DateCheck.OCCURRENCE;
    }

    /** The first date on or after this one that the appointment takes place on, where one is left. */
    public Optional<LocalDate> next(StationEvent event, LocalDate from) {
        return ruleOf(event).flatMap(rule -> next(event, rule, from));
    }

    private Optional<LocalDate> next(StationEvent event, OccurrenceRule rule, LocalDate from) {
        Optional<LocalDate> candidate = rule.onOrAfter(from);
        while (candidate.isPresent() && suspends(event, candidate.get())) {
            candidate = rule.onOrAfter(candidate.get().plusDays(1));
        }
        return candidate;
    }

    /** The last date strictly before this one that the appointment took place on, where it had one. */
    public Optional<LocalDate> previous(StationEvent event, LocalDate before) {
        var rule = ruleOf(event);
        if (rule.isEmpty()) return Optional.empty();
        Optional<LocalDate> candidate = rule.get().before(before);
        while (candidate.isPresent() && suspends(event, candidate.get())) {
            candidate = rule.get().before(candidate.get());
        }
        return candidate;
    }

    /** Every date from one day to another, both included, that the appointment takes place on. */
    public List<LocalDate> between(StationEvent event, LocalDate from, LocalDate to) {
        var rule = ruleOf(event);
        if (rule.isEmpty()) return List.of();
        var dates = new ArrayList<LocalDate>();
        Optional<LocalDate> date = next(event, rule.get(), from);
        while (date.isPresent() && !date.get().isAfter(to)) {
            dates.add(date.get());
            date = next(event, rule.get(), date.get().plusDays(1));
        }
        return dates;
    }

    /**
     * The date a screen about the appointment is read for: the next one, today counting as next,
     * and the last one it had once none is left.
     */
    public Optional<LocalDate> dateInView(StationEvent event) {
        LocalDate today = today();
        return next(event, today).or(() -> previous(event, today));
    }

    /**
     * The dates the repetition names that a break takes out, which a calendar is told as exceptions
     * to the rule.
     *
     * @param event the appointment
     * @return those dates in order, none for a one-off appointment
     */
    public List<LocalDate> suspendedDates(StationEvent event) {
        var rule = ruleOf(event);
        if (rule.isEmpty() || !event.isRecurring()) return List.of();
        var dates = new ArrayList<LocalDate>();
        for (var suspension : breaks) {
            if (suspension.startDate() == null || suspension.endDate() == null) continue;
            Optional<LocalDate> date = rule.get().onOrAfter(suspension.startDate());
            while (date.isPresent() && !date.get().isAfter(suspension.endDate())) {
                dates.add(date.get());
                date = rule.get().onOrAfter(date.get().plusDays(1));
            }
        }
        return dates.stream().distinct().sorted().toList();
    }

    private boolean suspends(StationEvent event, LocalDate date) {
        return event.isRecurring() && EventBreak.coversAny(breaks, date);
    }

    /** What a date is to an appointment. */
    public enum DateCheck {
        /** The appointment takes place on it. */
        OCCURRENCE,
        /** The repetition does not name it, or it lies outside the series. */
        NOT_AN_OCCURRENCE,
        /** The repetition names it, and a break of the station takes it out. */
        IN_A_BREAK
    }
}
