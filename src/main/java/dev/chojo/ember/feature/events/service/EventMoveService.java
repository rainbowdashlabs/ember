/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.feature.events.entity.EventRegistration;
import dev.chojo.ember.feature.events.entity.OccurrenceRule;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.entity.StationCalendar;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventDateCancellationRepository;
import dev.chojo.ember.feature.events.repository.EventFieldRepository;
import dev.chojo.ember.feature.events.repository.EventRegistrationRepository;
import dev.chojo.ember.feature.events.repository.EventReminderRepository;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.members.service.MemberNameResolver;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationLinks;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.service.NotificationService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;

/**
 * What moving an appointment does to everything filed against its dates.
 *
 * <p>Registrations, the answers a question carries per date and the record of reminders already sent
 * are all filed against the day an occasion falls on. Moving the appointment used to leave all of it
 * where it was, so a member stayed signed up for a Tuesday a series now held on Wednesdays no longer
 * has, and whatever still pointed at that Tuesday kept pointing at it.
 *
 * <p>A one-off appointment is one occasion whichever day it falls on, so what is filed against it
 * moves along to the new day, and everybody holding a place is told the new time. A series is many
 * occasions, and a date it leaves is an occasion that no longer happens: the places still held on it
 * are withdrawn, their members and whoever looks after them are told, with the next date the series
 * now falls on, and the answers, reminder records and cancellation filed against it go. The same holds for a series
 * whose end moved closer and for a one-off appointment that became a series or the other way round.
 * Dates the appointment still falls on are left exactly as they are, and so is a place already given
 * up or turned down, which says the member is not coming either way.
 *
 * <p>Dates before today are never touched. They are what happened, and an appointment moved today
 * does not change who was there last week.
 */
@Singleton
public class EventMoveService {
    private static final Logger log = LoggerFactory.getLogger(EventMoveService.class);
    private static final DateTimeFormatter TIME_OF_DAY = DateTimeFormatter.ofPattern("HH:mm");

    private final EventRegistrationRepository registrationRepository;
    private final EventFieldRepository fieldRepository;
    private final EventReminderRepository reminderRepository;
    private final EventDateCancellationRepository cancellationRepository;
    private final OccurrenceCalendar occurrenceCalendar;
    private final StationMemberRepository memberRepository;
    private final MemberNameResolver nameResolver;
    private final NotificationService notificationService;

    @Inject
    public EventMoveService(
            EventRegistrationRepository registrationRepository,
            EventFieldRepository fieldRepository,
            EventReminderRepository reminderRepository,
            EventDateCancellationRepository cancellationRepository,
            OccurrenceCalendar occurrenceCalendar,
            StationMemberRepository memberRepository,
            MemberNameResolver nameResolver,
            NotificationService notificationService) {
        this.registrationRepository = registrationRepository;
        this.fieldRepository = fieldRepository;
        this.reminderRepository = reminderRepository;
        this.cancellationRepository = cancellationRepository;
        this.occurrenceCalendar = occurrenceCalendar;
        this.memberRepository = memberRepository;
        this.nameResolver = nameResolver;
        this.notificationService = notificationService;
    }

    /**
     * Brings what is filed against an appointment's dates in line with the dates it now falls on.
     *
     * @param before the appointment as it stood
     * @param after  the appointment as it now stands
     */
    public void followMove(StationEvent before, StationEvent after) {
        var calendar = occurrenceCalendar.forStation(after.stationId());
        LocalDate today = calendar.today();
        if (!before.isRecurring() && !after.isRecurring()) carryOneOffDate(before, after, calendar, today);
        withdrawDatesGone(after, calendar, today);
    }

    /**
     * Moves the registrations and answers of a one-off appointment to its new day, and tells everybody
     * holding a place when it now starts.
     *
     * <p>Told whenever the start moved, on the same day or to another one, because the time is what
     * somebody who is coming has to know. A change to the end alone tells nobody.
     */
    private void carryOneOffDate(StationEvent before, StationEvent after, StationCalendar calendar, LocalDate today) {
        LocalDate from = calendar.ruleOf(before).map(OccurrenceRule::first).orElse(null);
        LocalDate to = calendar.ruleOf(after).map(OccurrenceRule::first).orElse(null);
        if (from == null || to == null || from.isBefore(today)) return;
        if (!from.equals(to)) {
            int moved = registrationRepository.moveDate(after.id(), from, to);
            fieldRepository.moveDateValues(after.id(), from, to);
            cancellationRepository.moveDate(after.id(), from, to);
            log.info("Event {} moved from {} to {}, taking {} registrations along", after.id(), from, to, moved);
        }
        if (Objects.equals(before.startTime(), after.startTime())) return;

        String startTime = after.startTime().atZone(calendar.zone()).format(TIME_OF_DAY);
        for (var registration : registrationRepository.findByEventAndDate(after.id(), to)) {
            if (!holdsPlace(registration)) continue;
            tell(
                    registration.memberId(),
                    NotificationType.EVENT_MOVED,
                    new NotificationParams.EventMoved(
                            after.name(), to, startTime, nameResolver.called(registration.memberId())),
                    NotificationLinks.eventDate(after.id(), to));
        }
    }

    /**
     * Withdraws the places on dates from today on that the appointment no longer falls on, and drops
     * what else is filed against those dates.
     *
     * <p>The dates looked at are the ones something is actually filed against, so however far ahead
     * somebody signed up, that date is checked, and nothing is asked about dates nobody used.
     */
    private void withdrawDatesGone(StationEvent after, StationCalendar calendar, LocalDate today) {
        var filed = new TreeSet<LocalDate>();
        filed.addAll(registrationRepository.findDatesFrom(after.id(), today));
        filed.addAll(fieldRepository.findDateValueDatesFrom(after.id(), today));
        filed.addAll(reminderRepository.findSentDatesFrom(after.id(), today));
        List<LocalDate> gone =
                filed.stream().filter(date -> !calendar.occursOn(after, date)).toList();
        if (gone.isEmpty()) return;

        var withdrawn = registrationRepository.withdrawOnDates(after.id(), gone);
        fieldRepository.deleteDateValuesOn(after.id(), gone);
        reminderRepository.deleteSentOn(after.id(), gone);
        cancellationRepository.deleteOn(after.id(), gone);
        log.info("Event {} no longer falls on {}, withdrew {} registrations there", after.id(), gone, withdrawn.size());

        for (var registration : withdrawn) {
            LocalDate nextDate = calendar.next(after, registration.eventDate()).orElse(null);
            tell(
                    registration.memberId(),
                    NotificationType.EVENT_DATE_DROPPED,
                    new NotificationParams.EventDateDropped(
                            after.name(),
                            registration.eventDate(),
                            nameResolver.called(registration.memberId()),
                            nextDate),
                    nextDate != null
                            ? NotificationLinks.eventDate(after.id(), nextDate)
                            : NotificationLinks.event(after.id()));
        }
    }

    private static boolean holdsPlace(EventRegistration registration) {
        return registration.status() == RegistrationStatus.PENDING
                || registration.status() == RegistrationStatus.ACCEPTED;
    }

    /**
     * Tells a member, and everyone who answers for them, the way every other word about a
     * registration reaches a household. The member is named in the message, because a guardian has
     * to know which of their children it is about.
     */
    private void tell(
            int memberId, NotificationType type, NotificationParams params, NotificationData.NotificationLink link) {
        var audience = new HashSet<Integer>();
        audience.add(memberId);
        memberRepository.findManagers(memberId).stream().map(StationMember::id).forEach(audience::add);
        notificationService.notifyMembers(audience, type, NotificationData.of(params, link));
    }
}
