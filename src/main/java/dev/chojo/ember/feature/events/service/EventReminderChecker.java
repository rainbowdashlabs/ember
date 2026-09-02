/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventRegistrationRepository;
import dev.chojo.ember.feature.events.repository.EventReminderRepository;
import dev.chojo.ember.feature.events.repository.EventRepository;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.members.service.MemberNameResolver;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationLinks;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.service.NotificationService;
import dev.chojo.ember.feature.storage.service.StationReadOnlyGuard;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Singleton
public class EventReminderChecker {
    private static final Logger log = LoggerFactory.getLogger(EventReminderChecker.class);

    private final EventRepository eventRepository;
    private final EventReminderRepository reminderRepository;
    private final EventRegistrationRepository registrationRepository;
    private final StationMemberRepository stationMemberRepository;
    private final NotificationService notificationService;
    private final MemberNameResolver memberNameResolver;
    private final EventRestrictionService restrictionService;
    private final StationReadOnlyGuard readOnlyGuard;

    @Inject
    public EventReminderChecker(
            EventRepository eventRepository,
            EventReminderRepository reminderRepository,
            EventRegistrationRepository registrationRepository,
            StationMemberRepository stationMemberRepository,
            NotificationService notificationService,
            MemberNameResolver memberNameResolver,
            EventRestrictionService restrictionService,
            StationReadOnlyGuard readOnlyGuard) {
        this.eventRepository = eventRepository;
        this.reminderRepository = reminderRepository;
        this.registrationRepository = registrationRepository;
        this.stationMemberRepository = stationMemberRepository;
        this.notificationService = notificationService;
        this.memberNameResolver = memberNameResolver;
        this.restrictionService = restrictionService;
        this.readOnlyGuard = readOnlyGuard;
        var scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            var t = new Thread(r, "event-reminder-checker");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleWithFixedDelay(this::check, 5, 30, TimeUnit.MINUTES);
    }

    private static final int[] CLOSING_WARNINGS = {3, 1};

    private void check() {
        try {
            warnAboutClosingRegistrations();
            var events = eventRepository.findEventsWithReminders();
            LocalDate today = LocalDate.now(ZoneOffset.UTC);

            for (var event : events) {
                if (!readOnlyGuard.isWritable(event.stationId())) continue;
                var reminderDays = reminderRepository.findDays(event.id());
                var occurrences = computeOccurrences(event, today, reminderDays);

                for (var occurrence : occurrences) {
                    for (int daysBefore : reminderDays) {
                        LocalDate reminderDate = occurrence.minusDays(daysBefore);
                        if (!today.equals(reminderDate)) continue;
                        if (reminderRepository.isSent(event.id(), occurrence, daysBefore)) continue;

                        var targetIds = resolveTargetMembers(event, occurrence);
                        if (!targetIds.isEmpty()) {
                            notificationService.notifyMembers(
                                    targetIds,
                                    NotificationType.EVENT_REMINDER,
                                    NotificationData.of(
                                            new NotificationParams.EventReminder(event.name(), daysBefore, occurrence),
                                            NotificationLinks.eventDate(event.id(), occurrence)));
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

    private List<LocalDate> computeOccurrences(StationEvent event, LocalDate today, List<Integer> reminderDays) {
        int maxDays = reminderDays.stream().mapToInt(Integer::intValue).max().orElse(0);
        var result = new ArrayList<LocalDate>();

        if (event.eventType() == StationEvent.EventType.ONE_TIME) {
            if (event.startTime() != null) {
                LocalDate eventDate = event.startTime().atZone(ZoneOffset.UTC).toLocalDate();
                if (!eventDate.isBefore(today)) {
                    result.add(eventDate);
                }
            }
            return result;
        }

        if (event.dayOfWeek() == null) return result;

        for (int d = 0; d <= maxDays; d++) {
            LocalDate date = today.plusDays(d);
            if (event.occursOn(date)) result.add(date);
        }
        return result;
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

    /** Tells one member, and everyone who answers for them, that their answer is still missing. */
    private boolean warnAbout(EventRepository.ClosingEvent event, int memberId, int daysBefore) {
        var member = stationMemberRepository.findById(memberId).orElse(null);
        if (member == null) return false;

        var audience = new HashSet<Integer>();
        audience.add(memberId);
        for (StationMember manager : stationMemberRepository.findManagers(memberId)) {
            audience.add(manager.id());
        }

        notificationService.notifyMembers(
                audience,
                NotificationType.REGISTRATION_CLOSING,
                NotificationData.of(
                        new NotificationParams.RegistrationClosing(
                                event.name(), daysBefore, memberNameResolver.resolveLocal(member.id())),
                        NotificationLinks.event(event.eventId())));
        return true;
    }

    /**
     * Who a reminder about one date goes to.
     *
     * <p>Where the appointment is signed up for, that is whoever holds a place, and holding one
     * already means they were allowed to take it. Where it is not, it is everybody who may know the
     * appointment exists, minus whoever has said they are not coming. Nobody is reminded of something
     * they cannot see, and visibility is asked without permissions so that being able to override the
     * restriction is not itself a reason to hear about it.
     */
    private List<Integer> resolveTargetMembers(StationEvent event, LocalDate eventDate) {
        if (event.requiresRegistration()) {
            return registrationRepository.findRegisteredMemberIds(event.id());
        }
        var allMembers = stationMemberRepository.findByStation(event.stationId());
        var declinedIds = new HashSet<>(registrationRepository.findDeclinedMemberIds(event.id(), eventDate));
        return allMembers.stream()
                .map(StationMember::id)
                .filter(id -> !declinedIds.contains(id))
                .filter(id -> restrictionService.canView(event.id(), id, Set.of()))
                .toList();
    }
}
