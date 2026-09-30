/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.feature.events.entity.StationCalendar;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventRegistrationRepository;
import dev.chojo.ember.feature.events.repository.EventReminderRepository;
import dev.chojo.ember.feature.events.repository.EventRepository;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.members.service.MemberNameResolver;
import dev.chojo.ember.feature.notifications.entity.Delivery;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationLinks;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.entity.StationAudience;
import dev.chojo.ember.feature.notifications.service.Notifier;
import dev.chojo.ember.feature.storage.service.StationReadOnlyGuard;
import dev.chojo.ember.lifecycle.DelegatingTask;
import dev.chojo.ember.lifecycle.Schedule;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Singleton
public class EventReminderChecker {
    private static final Logger log = LoggerFactory.getLogger(EventReminderChecker.class);

    private final EventRepository eventRepository;
    private final EventReminderRepository reminderRepository;
    private final EventRegistrationRepository registrationRepository;
    private final StationMemberRepository stationMemberRepository;
    private final Notifier notifier;
    private final MemberNameResolver memberNameResolver;
    private final EventRestrictionService restrictionService;
    private final StationReadOnlyGuard readOnlyGuard;
    private final OccurrenceCalendar occurrenceCalendar;

    @Inject
    public EventReminderChecker(
            EventRepository eventRepository,
            EventReminderRepository reminderRepository,
            EventRegistrationRepository registrationRepository,
            StationMemberRepository stationMemberRepository,
            Notifier notifier,
            MemberNameResolver memberNameResolver,
            EventRestrictionService restrictionService,
            StationReadOnlyGuard readOnlyGuard,
            OccurrenceCalendar occurrenceCalendar) {
        this.eventRepository = eventRepository;
        this.reminderRepository = reminderRepository;
        this.registrationRepository = registrationRepository;
        this.stationMemberRepository = stationMemberRepository;
        this.notifier = notifier;
        this.memberNameResolver = memberNameResolver;
        this.restrictionService = restrictionService;
        this.readOnlyGuard = readOnlyGuard;
        this.occurrenceCalendar = occurrenceCalendar;
    }

    private static final int[] CLOSING_WARNINGS = {3, 1};

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
                var reminderDays = reminderRepository.findDays(event.id());
                var occurrences = computeOccurrences(event, today, reminderDays, calendar);

                for (var occurrence : occurrences) {
                    for (int daysBefore : reminderDays) {
                        LocalDate reminderDate = occurrence.minusDays(daysBefore);
                        if (!today.equals(reminderDate)) continue;
                        if (reminderRepository.isSent(event.id(), occurrence, daysBefore)) continue;

                        var targetIds = resolveTargetMembers(event, occurrence);
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
     * Warns whoever still owes an answer that registration is about to close.
     *
     * <p>Three days out and one day out, each sent once per event: the sweep runs every half hour, and a
     * warning that arrived every half hour would be worse than none.
     *
     * <p>Only people the event is actually open to are warned. Eligibility is asked without any
     * permissions, so nobody is reminded merely because they could override the restriction: somebody the
     * event is closed to has nothing to answer.
     *
     * <p>The warning goes to everyone who could answer, which is the member and whoever looks after them.
     * A household where a guardian and two children are all still unanswered therefore hears three times,
     * once about each person, because the guardian has to know which of them it is about.
     */
    private void warnAboutClosingRegistrations() {
        for (int daysBefore : CLOSING_WARNINGS) {
            for (var event : eventRepository.findEventsClosingIn(daysBefore)) {
                if (!readOnlyGuard.isWritable(event.stationId())) continue;

                int warned = 0;
                for (int memberId :
                        registrationRepository.findUnansweredMemberIds(event.eventId(), event.stationId())) {
                    if (!restrictionService.canRegister(event.eventId(), memberId, Set.of())) continue;
                    warned += warnAbout(event, memberId, daysBefore) ? 1 : 0;
                }
                reminderRepository.markDeadlineWarningSent(event.eventId(), daysBefore);
                log.info(
                        "Warned {} member(s) that registration for '{}' (id={}) closes in {} day(s)",
                        warned,
                        event.name(),
                        event.eventId(),
                        daysBefore);
            }
        }
    }

    /**
     * Tells one member, and everyone who answers for them, that their answer is still missing.
     *
     * @return whether anybody was told
     */
    private boolean warnAbout(EventRepository.ClosingEvent event, int memberId, int daysBefore) {
        int told = notifier.notify(
                StationAudience.household(List.of(memberId)),
                NotificationType.REGISTRATION_CLOSING,
                NotificationData.of(
                        new NotificationParams.RegistrationClosing(
                                event.name(), daysBefore, memberNameResolver.called(memberId)),
                        NotificationLinks.event(event.eventId())),
                Delivery.EVERY_TIME);
        return told > 0;
    }

    /**
     * Who a reminder about one date goes to.
     *
     * <p>Where the appointment is signed up for, that is whoever holds a place on that very date, and
     * holding one already means they were allowed to take it. A place on another date of a repeating
     * appointment is not a reason to be reminded of this one. Where it is not, it is everybody who may know the
     * appointment exists, minus whoever has said they are not coming. Nobody is reminded of something
     * they cannot see, and visibility is asked without permissions so that being able to override the
     * restriction is not itself a reason to hear about it.
     */
    private List<Integer> resolveTargetMembers(StationEvent event, LocalDate eventDate) {
        if (event.requiresRegistration()) {
            return registrationRepository.findRegisteredMemberIds(event.id(), eventDate);
        }
        var allMembers = stationMemberRepository.findByStation(event.stationId());
        var declinedIds = new HashSet<>(registrationRepository.findNotAttendingMemberIds(event.id(), eventDate));
        return allMembers.stream()
                .map(StationMember::id)
                .filter(id -> !declinedIds.contains(id))
                .filter(id -> restrictionService.canView(event.id(), id, Set.of()))
                .toList();
    }

    /** Sends the due reminders and closing warnings, every thirty minutes. */
    @Singleton
    public static final class Task extends DelegatingTask {
        @Inject
        Task(EventReminderChecker checker) {
            super(
                    "event-reminder-check",
                    Schedule.fixedDelay(Duration.ofMinutes(5), Duration.ofMinutes(30)),
                    checker::check);
        }
    }
}
