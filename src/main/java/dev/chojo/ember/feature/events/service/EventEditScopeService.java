/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.api.refusal.EventRefusal;
import dev.chojo.ember.feature.events.entity.DateSpan;
import dev.chojo.ember.feature.events.entity.EditScope;
import dev.chojo.ember.feature.events.entity.EditScopeKind;
import dev.chojo.ember.feature.events.entity.EventDraft;
import dev.chojo.ember.feature.events.entity.EventLinks;
import dev.chojo.ember.feature.events.entity.EventRegistration;
import dev.chojo.ember.feature.events.entity.OccurrenceRule;
import dev.chojo.ember.feature.events.entity.StationCalendar;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventDetachedDateRepository;
import dev.chojo.ember.feature.events.repository.EventHandoverRepository;
import dev.chojo.ember.feature.events.repository.EventRepository;
import dev.chojo.ember.util.sql.Transactions;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

/**
 * Edits part of a series: one date on its own, or one date and every one after it.
 *
 * <p>One date on its own leaves the series and becomes an appointment of its own on that day, set up
 * the way the editor saved it. The series skips the date from then on without calling it off, so
 * nobody is told it is off. One date and the ones after it end the series the day before and go on
 * as a new series from that date. A series that ended after a number of dates keeps that total: the
 * old one gets an end date, the new one the number still left. Edited from its first date that takes
 * place, with nothing of it left before, a series is simply changed as a whole.
 *
 * <p>The new appointment records which appointment and date it took over from, starts with a copy of
 * every setting of the old one, and takes along everything filed against the dates it took over (see
 * {@link EventHandoverRepository}). Comments stay with the old appointment. Places on a date the new
 * appointment no longer falls on are withdrawn as they are for any move.
 *
 * <p>Dates before today are never taken: what happened stays with the appointment it happened on.
 *
 * <p>A date taken out of a series stays out even when the series is later changed as a whole into a
 * pattern that would name it again: the date belongs to the appointment that took it over.
 *
 * <p>Creating the new appointment announces nothing. It is the same occasion the members already
 * know, and whether they are told that it changed is decided where the edit is saved.
 */
@Singleton
public class EventEditScopeService {
    private static final Logger log = LoggerFactory.getLogger(EventEditScopeService.class);

    private final EventRepository eventRepository;
    private final EventCrudService crudService;
    private final EventHandoverRepository handoverRepository;
    private final EventDetachedDateRepository detachedDateRepository;
    private final OccurrenceCalendar occurrenceCalendar;
    private final EventMoveService moveService;

    @Inject
    public EventEditScopeService(
            EventRepository eventRepository,
            EventCrudService crudService,
            EventHandoverRepository handoverRepository,
            EventDetachedDateRepository detachedDateRepository,
            OccurrenceCalendar occurrenceCalendar,
            EventMoveService moveService) {
        this.eventRepository = eventRepository;
        this.crudService = crudService;
        this.handoverRepository = handoverRepository;
        this.detachedDateRepository = detachedDateRepository;
        this.occurrenceCalendar = occurrenceCalendar;
        this.moveService = moveService;
    }

    /**
     * How an appointment is linked to the ones it took dates from and gave dates to.
     *
     * @param eventId the appointment
     * @return its links, none where it was never split
     */
    public EventLinks linksOf(int eventId) {
        return new EventLinks(
                eventRepository.findContinues(eventId).orElse(null), eventRepository.findContinuations(eventId));
    }

    /**
     * Saves an appointment as the editor sent it, to the dates the scope names.
     *
     * @param current the appointment as it stands
     * @param draft   the appointment as the editor saved it
     * @param scope   which of its dates the change applies to
     * @return the appointment the change was saved into: the same one for every date, a new one for
     *     one date or one date and those after it
     */
    public StationEvent save(StationEvent current, EventDraft draft, EditScope scope) {
        EditScopeKind kind = scope.kind();
        if (kind == null) throw EventRefusal.EVENT_SCOPE_NEEDS_A_KIND.raise();
        return switch (kind) {
            case ALL -> changeWhole(current, draft);
            case THIS -> detach(current, requireDate(scope), draft);
            case THIS_AND_LATER -> split(current, requireDate(scope), draft);
        };
    }

    private static LocalDate requireDate(EditScope scope) {
        LocalDate date = scope.date();
        if (date == null) throw EventRefusal.EVENT_SCOPE_NEEDS_A_DATE.raise();
        return date;
    }

    /**
     * Takes one date out of a series and makes it an appointment of its own, set up as the editor
     * saved it, at the time of day the draft names.
     *
     * @param series the series
     * @param date   the date, today or later, that the series falls on
     * @param draft  the appointment as the editor saved it; how it repeats is ignored
     * @return the new appointment
     */
    public StationEvent detach(StationEvent series, LocalDate date, EventDraft draft) {
        var calendar = requireOpenDate(series, date);
        var span = StationEvent.placeOn(draft.startTime(), draft.endTime(), date, calendar.zone());
        return told(Transactions.call(() -> {
            var created = createFrom(series, draft, StationEvent.EventType.ONE_TIME, null, span, null, null, date);
            handOver(series, created, DateSpan.only(date), calendar.zone());
            detachedDateRepository.detach(series.id(), date);
            log.info("Event {} gave its date {} to event {}", series.id(), date, created.id());
            return settled(created);
        }));
    }

    /**
     * Ends a series the day before a date and goes on from that date as a new series, set up as the
     * editor saved it.
     *
     * @param series the series
     * @param from   the first date the new series takes over, today or later, that the series falls on
     * @param draft  the series as the editor saved it; its end counts from the first date of the old one
     * @return the new series, or the series itself where no date of it is left before the date
     */
    public StationEvent split(StationEvent series, LocalDate from, EventDraft draft) {
        var calendar = requireOpenDate(series, from);
        var rule = calendar.ruleOf(series).orElseThrow(EventRefusal.EVENT_SCOPE_DATE_NOT_AN_OCCURRENCE::raise);
        if (calendar.previous(series, from).isEmpty()) return changeWhole(series, draft);
        if (draft.repeatUntil() != null && draft.repeatCount() != null) {
            throw EventRefusal.EVENT_SERIES_END_GIVEN_TWICE.raise();
        }

        Integer count = remainingCount(draft.repeatCount(), datesBefore(rule, from));
        LocalDate until = draft.repeatUntil();
        if (until != null && until.isBefore(from)) throw EventRefusal.EVENT_SERIES_ENDS_BEFORE_IT_STARTS.raise();
        var span = draft.isRecurring()
                ? StationEvent.placeOn(draft.startTime(), draft.endTime(), from, calendar.zone())
                : new StationEvent.Span(draft.startTime(), draft.endTime());
        return told(Transactions.call(() -> {
            var created = draft.isRecurring()
                    ? createFrom(series, draft, draft.eventType(), draft.dayOfWeek(), span, until, count, from)
                    : createFrom(series, draft, draft.eventType(), null, span, null, null, from);
            handOver(series, created, DateSpan.onwardFrom(from), calendar.zone());
            eventRepository.updateRepeatEnd(series.id(), from.minusDays(1), null);
            log.info("Event {} ends before {} and goes on as event {}", series.id(), from, created.id());
            return settled(created);
        }));
    }

    private StationCalendar requireOpenDate(StationEvent series, LocalDate date) {
        if (!series.isRecurring()) throw EventRefusal.EVENT_SCOPE_NEEDS_A_SERIES.raise();
        var calendar = occurrenceCalendar.forStation(series.stationId());
        if (date.isBefore(calendar.today())) throw EventRefusal.EVENT_SCOPE_DATE_PASSED.raise();
        if (!calendar.occursOn(series, date)) throw EventRefusal.EVENT_SCOPE_DATE_NOT_AN_OCCURRENCE.raise();
        return calendar;
    }

    /**
     * Changes the appointment as a whole, the way it always was: the change and its end are saved one
     * after the other, and what follows a move runs after each, so whoever is told about it is told
     * about what was saved.
     */
    private StationEvent changeWhole(StationEvent series, EventDraft draft) {
        var changed = crudService
                .update(
                        series.id(),
                        draft.name(),
                        draft.description(),
                        draft.eventType(),
                        draft.dayOfWeek(),
                        draft.startTime(),
                        draft.endTime(),
                        draft.templateId(),
                        draft.requiresRegistration(),
                        draft.registrationDeadline(),
                        draft.requiresConfirmation(),
                        draft.categoryId(),
                        draft.isPublic(),
                        draft.registrationLimit(),
                        draft.minRegistrations(),
                        draft.thresholdDays(),
                        draft.registrationCloseDays())
                .orElseThrow(EventRefusal.EVENT_NOT_HERE_ON_CHANGE::raise);
        return crudService
                .setRepeatEnd(series.id(), draft.repeatUntil(), draft.repeatCount())
                .orElse(changed);
    }

    /**
     * How many dates a series counted to an end still has to go once some of them were given away.
     *
     * @return the dates left, or null where the series ends on a day or not at all
     */
    private static @Nullable Integer remainingCount(@Nullable Integer total, int given) {
        if (total == null) return null;
        int left = total - given;
        if (left < 1) throw EventRefusal.EVENT_SERIES_COUNT_BELOW_ONE.raise();
        return left;
    }

    /** How many dates the repetition names before a date, breaks included, the way a count is read. */
    private static int datesBefore(OccurrenceRule rule, LocalDate date) {
        int count = 0;
        Optional<LocalDate> candidate = Optional.of(rule.first());
        while (candidate.isPresent() && candidate.get().isBefore(date)) {
            count++;
            candidate = rule.onOrAfter(candidate.get().plusDays(1));
        }
        return count;
    }

    private StationEvent createFrom(
            StationEvent source,
            EventDraft draft,
            StationEvent.EventType eventType,
            @Nullable Integer dayOfWeek,
            StationEvent.Span span,
            @Nullable LocalDate until,
            @Nullable Integer count,
            LocalDate takenFrom) {
        var created = crudService.createWithoutEvent(
                source.stationId(),
                draft.name(),
                draft.description(),
                eventType,
                dayOfWeek,
                span.start(),
                span.end(),
                draft.templateId(),
                draft.requiresRegistration(),
                draft.registrationDeadline(),
                draft.requiresConfirmation(),
                draft.categoryId(),
                draft.registrationLimit(),
                draft.minRegistrations(),
                draft.thresholdDays(),
                draft.registrationCloseDays());
        eventRepository.updatePublic(created.id(), draft.isPublic());
        if (until != null || count != null) eventRepository.updateRepeatEnd(created.id(), until, count);
        handoverRepository.copyConfiguration(source.id(), created.id());
        eventRepository.setContinues(created.id(), source.id(), takenFrom);
        eventRepository.markChangeNoticeDue(created.id());
        return created;
    }

    private void handOver(StationEvent source, StationEvent target, DateSpan dates, ZoneId zone) {
        handoverRepository.moveDates(source.id(), target.id(), dates);
        Instant from = dates.from().atStartOfDay(zone).toInstant();
        LocalDate last = dates.until();
        Instant until =
                last == null ? null : last.plusDays(1).atStartOfDay(zone).toInstant();
        handoverRepository.moveSheets(source.id(), target.id(), from, until);
    }

    private Settled settled(StationEvent created) {
        var current = eventRepository.findById(created.id()).orElse(created);
        return new Settled(current, moveService.settle(current));
    }

    /** Tells whoever lost a place once the edit is saved, so nobody hears of one that was rolled back. */
    private StationEvent told(Settled settled) {
        moveService.tellDropped(settled.event(), settled.withdrawn());
        return settled.event();
    }

    /**
     * The appointment an edit was saved into, and the places it withdrew on dates it does not fall on.
     *
     * @param event     the appointment
     * @param withdrawn the places withdrawn, whose members are still to be told
     */
    private record Settled(StationEvent event, List<EventRegistration> withdrawn) {}
}
