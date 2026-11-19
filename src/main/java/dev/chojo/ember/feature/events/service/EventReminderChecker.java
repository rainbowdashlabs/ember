/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.feature.events.entity.RegistrationDeadline;
import dev.chojo.ember.feature.events.entity.StationCalendar;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventRegistrationRepository;
import dev.chojo.ember.feature.events.repository.EventReminderRepository;
import dev.chojo.ember.feature.events.repository.EventRepository;
import dev.chojo.ember.feature.members.service.MemberNameResolver;
import dev.chojo.ember.feature.notifications.entity.Delivery;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationData.NotificationLink;
import dev.chojo.ember.feature.notifications.entity.NotificationLinks;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.entity.StationAudience;
import dev.chojo.ember.feature.notifications.service.Notifier;
import dev.chojo.ember.feature.storage.service.StationReadOnlyGuard;
import dev.chojo.ember.lifecycle.Schedule;
import dev.chojo.ember.lifecycle.ScheduledTask;
import dev.chojo.ember.lifecycle.TaskSource;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Singleton
public class EventReminderChecker implements TaskSource {
    private static final Logger log = LoggerFactory.getLogger(EventReminderChecker.class);

    private final EventRepository eventRepository;
    private final EventReminderRepository reminderRepository;
    private final EventRegistrationRepository registrationRepository;
    private final Notifier notifier;
    private final EventRestrictionService restrictionService;
    private final StationReadOnlyGuard readOnlyGuard;
    private final OccurrenceCalendar occurrenceCalendar;
    private final EventAudience audience;
    private final ClosingWarnings closingWarnings;

    @Inject
    public EventReminderChecker(
            EventRepository eventRepository,
            EventReminderRepository reminderRepository,
            EventRegistrationRepository registrationRepository,
            Notifier notifier,
            MemberNameResolver memberNameResolver,
            EventRestrictionService restrictionService,
            StationReadOnlyGuard readOnlyGuard,
            OccurrenceCalendar occurrenceCalendar,
            EventAudience audience) {
        this.eventRepository = eventRepository;
        this.reminderRepository = reminderRepository;
        this.registrationRepository = registrationRepository;
        this.notifier = notifier;
        this.restrictionService = restrictionService;
        this.readOnlyGuard = readOnlyGuard;
        this.occurrenceCalendar = occurrenceCalendar;
        this.audience = audience;
        this.closingWarnings = new ClosingWarnings(notifier, memberNameResolver, readOnlyGuard);
    }

    /**
     * One sweep: what has to be warned about, and what has to be reminded of.
     *
     * <p>Which day it is, and which day an appointment falls on, are both questions about the
     * station's calendar. Asked in UTC, an appointment starting at 00:30 in Berlin belongs to the day
     * before, and a reminder set three days ahead goes out four days ahead. The calendar is read once
     * per station per sweep rather than once per event.
     */
    private void check() {
        try {
            warnAboutClosingRegistrations();
            var events = eventRepository.findEventsWithReminders();
            var calendars = new HashMap<Integer, StationCalendar>();

            for (var event : events) {
                if (!readOnlyGuard.isWritable(event.stationId())) continue;
                var calendar = calendars.computeIfAbsent(event.stationId(), occurrenceCalendar::forStation);
                LocalDate today = calendar.today();
                var reminderDays = reminderRepository.findEffectiveDays(event.id());
                var occurrences = computeOccurrences(event, today, reminderDays, calendar);

                for (var occurrence : occurrences) {
                    for (int daysBefore : reminderDays) {
                        LocalDate reminderDate = occurrence.minusDays(daysBefore);
                        if (!today.equals(reminderDate)) continue;
                        if (reminderRepository.isSent(event.id(), occurrence, daysBefore)) continue;

                        var targetIds = audience.onDate(event, occurrence);
                        if (!targetIds.isEmpty()) {
                            notifier.notify(
                                    StationAudience.members(targetIds),
                                    NotificationType.EVENT_REMINDER,
                                    NotificationData.of(
                                            new NotificationParams.EventReminder(event.name(), daysBefore, occurrence),
                                            NotificationLinks.eventDate(event.id(), occurrence)),
                                    Delivery.EVERY_TIME);
                            log.info(
                                    "Sent {} reminder(s) for event '{}' (id={}) on {} - {} days before",
                                    targetIds.size(),
                                    event.name(),
                                    event.id(),
                                    occurrence,
                                    daysBefore);
                        }
                        reminderRepository.markSent(event.id(), occurrence, daysBefore);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error checking event reminders", e);
        }
    }

    /**
     * The days this event takes place on that are worth reminding about: from today up to the
     * furthest reminder, with the dates the station's breaks take out and the dates called off left
     * out. Nobody is reminded to come to something that is not happening.
     *
     * @param calendar the station's calendar, which decides the day an appointment belongs to
     */
    private static List<LocalDate> computeOccurrences(
            StationEvent event, LocalDate today, List<Integer> reminderDays, StationCalendar calendar) {
        int maxDays = reminderDays.stream().mapToInt(Integer::intValue).max().orElse(0);
        return calendar.between(event, today, today.plusDays(maxDays)).stream()
                .filter(date -> calendar.takesPlaceOn(event, date))
                .toList();
    }

    /**
     * Warns whoever still owes an answer that registration for an appointment is about to close.
     *
     * <p>Eligibility is asked without any permissions, so nobody is reminded merely because they could
     * override the restriction: somebody the appointment is closed to has nothing to answer.
     */
    private void warnAboutClosingRegistrations() {
        var now = Instant.now();
        var candidates = datesClosingBetween(now, now.plus(Duration.ofDays(ClosingWarnings.MAX_LEAD_DAYS)));
        var sent = new HashSet<>(reminderRepository.findDeadlineWarningsSent(
                candidates.stream().map(ClosingDate::eventId).distinct().toList()));
        closingWarnings.warn(new ClosingWarnings.Closable() {
            @Override
            public List<ClosingWarnings.Closing> closingIn(int daysBefore) {
                var limit = now.plus(Duration.ofDays(daysBefore));
                return candidates.stream()
                        .filter(date -> !date.closes().isAfter(limit))
                        .filter(date -> !sent.contains(
                                new EventReminderRepository.DeadlineWarning(date.eventId(), date.date(), daysBefore)))
                        .map(ClosingDate::closing)
                        .toList();
            }

            @Override
            public List<Integer> unansweredMemberIds(ClosingWarnings.Closing closing) {
                return registrationRepository.findUnansweredMemberIds(
                        closing.id(), closing.stationId(), Objects.requireNonNull(closing.date()));
            }

            @Override
            public boolean meantFor(ClosingWarnings.Closing closing, int memberId) {
                return restrictionService.canRegister(closing.id(), memberId, Set.of());
            }

            @Override
            public void markWarned(ClosingWarnings.Closing closing, int daysBefore) {
                var date = Objects.requireNonNull(closing.date());
                reminderRepository.markDeadlineWarningSent(closing.id(), date, daysBefore);
                sent.add(new EventReminderRepository.DeadlineWarning(closing.id(), date, daysBefore));
            }

            @Override
            public NotificationLink linkTo(int id) {
                return NotificationLinks.event(id);
            }
        });
    }

    /**
     * The dates whose sign-up closes after one instant and no later than another, once per sweep for
     * every lead time.
     *
     * <p>A relative deadline closes each date on its own, so every date close enough to close in time
     * is asked. A fixed deadline closes every date at once; only the next date is warned about, since
     * the warning is about answering before it closes. A date called off or taken out by a break is
     * left out.
     *
     * @param from  the earliest close, excluded
     * @param until the latest close, included
     * @return one entry per appointment and date
     */
    private List<ClosingDate> datesClosingBetween(Instant from, Instant until) {
        var calendars = new HashMap<Integer, StationCalendar>();
        var closing = new ArrayList<ClosingDate>();
        for (var event : eventRepository.findWithDeadline()) {
            var calendar = calendars.computeIfAbsent(event.stationId(), occurrenceCalendar::forStation);
            for (var date : candidateDates(event, calendar, until)) {
                var closes = event.registrationClosesOn(date, calendar.zone());
                if (closes.isEmpty()
                        || !closes.get().isAfter(from)
                        || closes.get().isAfter(until)) continue;
                closing.add(new ClosingDate(
                        new ClosingWarnings.Closing(
                                event.id(), event.stationId(), event.name(), date, event.closesPerDate()),
                        closes.get()));
            }
        }
        return closing;
    }

    /**
     * One date of an appointment whose sign-up closes soon.
     *
     * @param closing what the warning is about
     * @param closes  when that date closes
     */
    private record ClosingDate(ClosingWarnings.Closing closing, Instant closes) {
        int eventId() {
            return closing.id();
        }

        LocalDate date() {
            return Objects.requireNonNull(closing.date());
        }
    }

    private static List<LocalDate> candidateDates(StationEvent event, StationCalendar calendar, Instant limit) {
        var today = calendar.today();
        var deadline = event.registrationDeadline();
        if (deadline.kind() == RegistrationDeadline.DeadlineKind.FIXED) {
            return calendar.next(event, today)
                    .filter(date -> calendar.takesPlaceOn(event, date))
                    .map(List::of)
                    .orElse(List.of());
        }
        long spanDays = Duration.ofMinutes(Objects.requireNonNullElse(deadline.offsetMinutes(), 0))
                        .toDays()
                + 1;
        var last = limit.atZone(calendar.zone()).toLocalDate().plusDays(spanDays);
        return calendar.between(event, today, last).stream()
                .filter(date -> calendar.takesPlaceOn(event, date))
                .toList();
    }

    @Override
    public List<ScheduledTask> scheduledTasks() {
        return List.of(new ScheduledTask(
                "event-reminder-check",
                Schedule.fixedDelay(Duration.ofMinutes(5), Duration.ofMinutes(30)),
                this::check));
    }
}
