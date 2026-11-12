/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.api.refusal.EventRefusal;
import dev.chojo.ember.feature.events.entity.ChangeNotice;
import dev.chojo.ember.feature.events.entity.EventChangePart;
import dev.chojo.ember.feature.events.entity.EventLink;
import dev.chojo.ember.feature.events.entity.StationCalendar;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventRegistrationRepository;
import dev.chojo.ember.feature.events.repository.EventRepository;
import dev.chojo.ember.feature.members.service.MemberNameResolver;
import dev.chojo.ember.feature.notifications.entity.Delivery;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
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
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Tells the people an appointment concerns that it changed, once the change is saved.
 *
 * <p>Two things can be told. A start that moved on a date somebody holds a place on is always told to
 * everybody holding a place on the dates the change touches. The change itself is told only where the
 * editor chose to, to everybody the appointment concerns on those dates (see {@link EventAudience}).
 * Somebody in both hears it once: one message naming the new start, the changed parts and the note.
 * Somebody in only one of them gets that one.
 *
 * <p>The dates a change touches are the appointment's dates from today on. A change to an appointment
 * that lies wholly in the past tells nobody.
 *
 * <p>Each member is named with the first date they hold a place on from the first touched date on,
 * and linked to it; without a place, with the first touched date.
 *
 * <p>Which parts changed comes from the editor, except the time and the date, which are read from the
 * save itself so a client cannot leave a move out. A notice that names no part at all, because
 * nothing about the appointment changed, is not sent.
 */
@Singleton
public class EventChangeNotifier implements TaskSource {
    private static final Logger log = LoggerFactory.getLogger(EventChangeNotifier.class);
    private static final DateTimeFormatter TIME_OF_DAY = DateTimeFormatter.ofPattern("HH:mm");

    /** How long the editor has to send the notice of an appointment that took over dates. */
    public static final Duration GRACE = Duration.ofMinutes(10);

    private final OccurrenceCalendar occurrenceCalendar;
    private final EventAudience audience;
    private final EventRegistrationRepository registrationRepository;
    private final EventRepository eventRepository;
    private final MemberNameResolver nameResolver;
    private final Notifier notifier;
    private final StationReadOnlyGuard readOnlyGuard;
    private final FederatedEventChangeService federatedChanges;

    @Inject
    public EventChangeNotifier(
            OccurrenceCalendar occurrenceCalendar,
            EventAudience audience,
            EventRegistrationRepository registrationRepository,
            EventRepository eventRepository,
            MemberNameResolver nameResolver,
            Notifier notifier,
            StationReadOnlyGuard readOnlyGuard,
            FederatedEventChangeService federatedChanges) {
        this.readOnlyGuard = readOnlyGuard;
        this.federatedChanges = federatedChanges;
        this.occurrenceCalendar = occurrenceCalendar;
        this.audience = audience;
        this.registrationRepository = registrationRepository;
        this.eventRepository = eventRepository;
        this.nameResolver = nameResolver;
        this.notifier = notifier;
    }

    /**
     * Refuses a notice whose note is too long, before anything is saved.
     *
     * @param notice the notice, or null for none
     */
    public void requireValid(@Nullable ChangeNotice notice) {
        if (notice == null || !notice.tell()) return;
        String note = notice.trimmedNote();
        if (note != null && note.length() > ChangeNotice.NOTE_LIMIT) throw EventRefusal.EVENT_NOTE_TOO_LONG.raise();
    }

    /**
     * What a save did to an appointment, as far as the people it concerns are told about it.
     *
     * @param before the appointment as it stood, or the one it took over from
     * @param after  the appointment as it now stands
     * @param from   the date the appointment took over from, or null where it was changed in place
     * @return the effect
     */
    public ChangeEffect effectOf(StationEvent before, StationEvent after, @Nullable LocalDate from) {
        var calendar = occurrenceCalendar.forStation(after.stationId());
        var parts = EnumSet.noneOf(EventChangePart.class);
        boolean moved = startMoved(before, after, calendar.zone());
        if (moved) parts.add(EventChangePart.TIME);
        if (datesMoved(before, after, from, calendar.zone())) parts.add(EventChangePart.DATE);
        LocalDate first = firstDate(after, calendar);
        String startTime = moved ? after.startTime().atZone(calendar.zone()).format(TIME_OF_DAY) : null;
        return new ChangeEffect(first, parts, startTime);
    }

    /**
     * Who would be told what, without telling anybody.
     *
     * @param after  the appointment as it now stands
     * @param effect what the save did
     * @param notice what the editor chose
     * @return the two audiences
     */
    public Audiences audiencesOf(StationEvent after, ChangeEffect effect, @Nullable ChangeNotice notice) {
        LocalDate first = effect.firstDate();
        if (first == null) return new Audiences(List.of(), List.of());
        List<Integer> moved = effect.startTime() == null ? List.of() : placeHolders(after, first);
        List<Integer> told = tellsChange(effect, notice) ? concerned(after, first) : List.of();
        return new Audiences(moved, told);
    }

    /**
     * Tells the people an appointment changed in place concerns, once every part of the save landed.
     *
     * @param before the appointment as it stood
     * @param after  the appointment as it now stands
     * @param notice what the editor chose, or null for nothing beyond a moved start
     */
    public void tellChanged(StationEvent before, StationEvent after, @Nullable ChangeNotice notice) {
        tell(after, effectOf(before, after, null), notice);
    }

    /**
     * Tells the people an appointment that took over dates of another one concerns, once every part of
     * the save landed. The move is read against the appointment it took over from.
     *
     * <p>Told once: the first call tells, a repeated one finds nothing left to tell. Where the editor
     * never sends it, {@link #scheduledTasks()} tells about a moved start on its own after
     * {@link #GRACE}, since a moved start is told whatever the editor chose.
     *
     * @param event  the appointment that took over
     * @param notice what the editor chose, or null for nothing beyond a moved start
     */
    public void tellContinued(StationEvent event, @Nullable ChangeNotice notice) {
        requireValid(notice);
        var link = eventRepository.findContinues(event.id());
        if (link.isEmpty()) throw EventRefusal.EVENT_NOTICE_NOT_A_CONTINUATION.raise();
        if (!eventRepository.claimChangeNotice(event.id())) return;
        var before = eventRepository.findById(link.get().eventId()).orElse(event);
        tell(event, effectOf(before, event, link.get().date()), notice);
    }

    /** Tells about a moved start where the editor sent no notice for an appointment that took over dates. */
    private void tellOverdue() {
        for (int id : eventRepository.findChangeNoticesOverdue(GRACE)) {
            try {
                tellOverdue(id);
            } catch (RuntimeException e) {
                log.error("Could not tell about the change of event {}", id, e);
            }
        }
    }

    private void tellOverdue(int id) {
        var event = eventRepository.findById(id).orElse(null);
        if (event == null || !readOnlyGuard.isWritable(event.stationId())) return;
        if (!eventRepository.claimChangeNotice(id)) return;
        var link = eventRepository.findContinues(id);
        var before =
                link.map(EventLink::eventId).flatMap(eventRepository::findById).orElse(event);
        tell(event, effectOf(before, event, link.map(EventLink::date).orElse(null)), ChangeNotice.SILENT);
    }

    @Override
    public List<ScheduledTask> scheduledTasks() {
        return List.of(new ScheduledTask(
                "event-change-notice-overdue",
                Schedule.fixedDelay(Duration.ofMinutes(2), Duration.ofMinutes(5)),
                this::tellOverdue));
    }

    private void tell(StationEvent after, ChangeEffect effect, @Nullable ChangeNotice notice) {
        LocalDate first = effect.firstDate();
        if (first == null) return;
        var audiences = audiencesOf(after, effect, notice);
        var everybody = new LinkedHashSet<Integer>(audiences.moved());
        everybody.addAll(audiences.told());
        Set<Integer> told = Set.copyOf(audiences.told());
        var heldFrom = after.isRecurring()
                ? registrationRepository.findFirstHeldDatesFrom(after.id(), first)
                : Map.<Integer, LocalDate>of();
        for (int memberId : everybody) {
            String name = nameResolver.called(memberId);
            LocalDate date = heldFrom.getOrDefault(memberId, first);
            var link = NotificationLinks.eventDate(after.id(), date);
            if (told.contains(memberId)) {
                var notified = Objects.requireNonNull(notice, "a change is told only where one was chosen");
                var parts = EnumSet.noneOf(EventChangePart.class);
                parts.addAll(effect.parts());
                parts.addAll(notified.parts());
                send(
                        memberId,
                        NotificationType.EVENT_CHANGED,
                        NotificationParams.EventChangeNotice.of(
                                after.name(), date, parts, notified.trimmedNote(), effect.startTime(), name),
                        link);
            } else {
                send(
                        memberId,
                        NotificationType.EVENT_MOVED,
                        new NotificationParams.EventMoved(
                                after.name(), date, Objects.requireNonNull(effect.startTime()), name),
                        link);
            }
        }
        tellPartners(after, first, effect, notice);
        log.info(
                "Event {} changed: told {} about a moved start and {} about the change",
                after.id(),
                audiences.moved().size(),
                audiences.told().size());
    }

    /** Tells the partner stations whose members hold a place, which tell those members themselves. */
    private void tellPartners(StationEvent after, LocalDate first, ChangeEffect effect, @Nullable ChangeNotice notice) {
        boolean told = tellsChange(effect, notice);
        var parts = EnumSet.noneOf(EventChangePart.class);
        parts.addAll(effect.parts());
        if (told) parts.addAll(Objects.requireNonNull(notice).parts());
        String joined = NotificationParams.EventChangeNotice.of(
                        after.name(), first, parts, null, effect.startTime(), "")
                .changedParts();
        federatedChanges.tellPartners(
                after,
                first,
                joined,
                told ? Objects.requireNonNull(notice).trimmedNote() : null,
                effect.startTime(),
                told);
    }

    private static boolean tellsChange(ChangeEffect effect, @Nullable ChangeNotice notice) {
        if (notice == null || !notice.tell()) return false;
        return !effect.parts().isEmpty() || !notice.parts().isEmpty();
    }

    /** Everybody holding a place on the appointment's dates from the first one on. */
    private List<Integer> placeHolders(StationEvent event, LocalDate first) {
        if (!event.isRecurring()) return registrationRepository.findRegisteredMemberIds(event.id(), first);
        return registrationRepository.findRegisteredMemberIdsFrom(event.id(), first);
    }

    /** Everybody the appointment concerns on its dates from the first one on. */
    private List<Integer> concerned(StationEvent event, LocalDate first) {
        if (!event.isRecurring()) return audience.onDate(event, first);
        return audience.fromDate(event, first);
    }

    /** The first date the change touches: the appointment's next date from today, none for one that is past. */
    private static @Nullable LocalDate firstDate(StationEvent event, StationCalendar calendar) {
        LocalDate today = calendar.today();
        if (!event.isRecurring()) {
            return calendar.ruleOf(event)
                    .map(rule -> rule.first())
                    .filter(date -> !date.isBefore(today))
                    .orElse(null);
        }
        return calendar.next(event, today).orElse(null);
    }

    /**
     * Whether the start moved: to another moment for a one-off that stays one, and to another time of
     * day for anything that repeats or stops or starts repeating.
     */
    private static boolean startMoved(StationEvent before, StationEvent after, ZoneId zone) {
        if (!before.isRecurring() && !after.isRecurring())
            return !before.startTime().equals(after.startTime());
        return !before.startTime()
                .atZone(zone)
                .toLocalTime()
                .equals(after.startTime().atZone(zone).toLocalTime());
    }

    /**
     * Whether the dates moved: a one-off on another day, or a series on other days.
     *
     * @param from the date the appointment took over from, which a date taken out of a series keeps
     */
    private static boolean datesMoved(StationEvent before, StationEvent after, @Nullable LocalDate from, ZoneId zone) {
        if (!after.isRecurring()) {
            LocalDate day = after.startTime().atZone(zone).toLocalDate();
            if (from != null) return !day.equals(from);
            return before.isRecurring()
                    || !day.equals(before.startTime().atZone(zone).toLocalDate());
        }
        return !before.isRecurring()
                || before.eventType() != after.eventType()
                || !Objects.equals(before.dayOfWeek(), after.dayOfWeek());
    }

    private void send(
            int memberId, NotificationType type, NotificationParams params, NotificationData.NotificationLink link) {
        notifier.notify(
                StationAudience.household(List.of(memberId)),
                type,
                NotificationData.of(params, link),
                Delivery.EVERY_TIME);
    }

    /**
     * What a save did to an appointment that the people it concerns are told.
     *
     * @param firstDate the first date the change touches, or null where none is left from today on
     * @param parts     the parts read from the save itself: the time, the date
     * @param startTime the time of day it now starts at where the start moved, or null
     */
    public record ChangeEffect(
            @Nullable LocalDate firstDate,
            Set<EventChangePart> parts,
            @Nullable String startTime) {}

    /**
     * Who is told what.
     *
     * @param moved the members told about a moved start, holding a place on a touched date
     * @param told  the members told about the change, where the editor chose to tell them
     */
    public record Audiences(List<Integer> moved, List<Integer> told) {}
}
