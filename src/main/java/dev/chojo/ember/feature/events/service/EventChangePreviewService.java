/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.api.refusal.EventRefusal;
import dev.chojo.ember.feature.events.entity.ChangePreview;
import dev.chojo.ember.feature.events.entity.EditScope;
import dev.chojo.ember.feature.events.entity.EditScopeKind;
import dev.chojo.ember.feature.events.entity.EventDraft;
import dev.chojo.ember.feature.events.entity.OccurrenceRule;
import dev.chojo.ember.feature.events.entity.StationCalendar;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventRegistrationRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Says what saving an appointment would tell, before anything is saved: how many people the change
 * would be told to if the editor chose to tell them, whether the start moves, and how many people
 * holding a place would be told the new time in any case.
 *
 * <p>It reads the appointment as it stands and the draft the editor is about to save, and writes
 * nothing. The dates it looks at are the ones the save would touch: from today on for every date, the
 * one date for only this date, and that date and the ones after it for this and later dates.
 */
@Singleton
public class EventChangePreviewService {
    private final OccurrenceCalendar occurrenceCalendar;
    private final EventAudience audience;
    private final EventRegistrationRepository registrationRepository;
    private final EventChangeNotifier changeNotifier;

    @Inject
    public EventChangePreviewService(
            OccurrenceCalendar occurrenceCalendar,
            EventAudience audience,
            EventRegistrationRepository registrationRepository,
            EventChangeNotifier changeNotifier) {
        this.occurrenceCalendar = occurrenceCalendar;
        this.audience = audience;
        this.registrationRepository = registrationRepository;
        this.changeNotifier = changeNotifier;
    }

    /**
     * What saving the draft to the dates the scope names would tell.
     *
     * @param current the appointment as it stands
     * @param draft   the appointment as the editor is about to save it
     * @param scope   which of its dates the save applies to
     * @return the counts the save dialog shows
     */
    public ChangePreview preview(StationEvent current, EventDraft draft, EditScope scope) {
        var calendar = occurrenceCalendar.forStation(current.stationId());
        EditScopeKind kind = scope.kind();
        if (kind == null) throw EventRefusal.EVENT_SCOPE_NEEDS_A_KIND.raise();
        LocalDate date = kind == EditScopeKind.ALL ? null : scope.date();
        if (kind != EditScopeKind.ALL && date == null) throw EventRefusal.EVENT_SCOPE_NEEDS_A_DATE.raise();

        var after = asSaved(current, draft, kind, date, calendar);
        var effect = changeNotifier.effectOf(current, after, kind == EditScopeKind.THIS ? date : null);
        LocalDate today = calendar.today();
        LocalDate from = date != null ? date : today;
        if (from.isBefore(today) || firstFrom(after, from, calendar) == null) {
            return new ChangePreview(0, false, null, 0, 0);
        }

        List<Integer> holders = holdersKept(current, after, from, kind == EditScopeKind.THIS, calendar);
        List<Integer> told = current.requiresRegistration() ? holders : concerned(current, after, from, calendar);
        List<Integer> moved = effect.startTime() == null ? List.of() : holders;
        var everybody = new LinkedHashSet<>(told);
        everybody.addAll(moved);
        return new ChangePreview(
                told.size(), effect.startTime() != null, effect.startTime(), moved.size(), everybody.size());
    }

    /**
     * The members holding a place on a date the saved appointment still falls on, from the first date
     * the save touches. A place on a date it no longer falls on is withdrawn by the save, and its member
     * hears that instead. A one-off that stays one takes its places to its new day, but only from a day
     * that has not passed.
     */
    private List<Integer> holdersKept(
            StationEvent current, StationEvent after, LocalDate from, boolean onlyThat, StationCalendar calendar) {
        if (!current.isRecurring() && !after.isRecurring()) {
            LocalDate day = calendar.ruleOf(current).map(OccurrenceRule::first).orElse(null);
            if (day == null || day.isBefore(from)) return List.of();
            return registrationRepository.findRegisteredMemberIds(current.id(), day);
        }
        var kept = new ArrayList<Integer>();
        registrationRepository.findHeldDatesFrom(current.id(), from).forEach((member, dates) -> {
            boolean stays = dates.stream()
                    .filter(day -> !onlyThat || day.equals(from))
                    .anyMatch(day -> calendar.occursOn(after, day));
            if (stays) kept.add(member);
        });
        return kept;
    }

    /** Everybody an appointment without registration concerns on the first date the saved one falls on from there. */
    private List<Integer> concerned(
            StationEvent current, StationEvent after, LocalDate from, StationCalendar calendar) {
        LocalDate first = firstFrom(after, from, calendar);
        return first == null ? List.of() : audience.onDate(current, first);
    }

    /** The first date the saved appointment falls on from a day on, none where it has none left. */
    private static @Nullable LocalDate firstFrom(StationEvent after, LocalDate from, StationCalendar calendar) {
        if (after.isRecurring()) return calendar.next(after, from).orElse(null);
        return calendar.ruleOf(after)
                .map(OccurrenceRule::first)
                .filter(day -> !day.isBefore(from))
                .orElse(null);
    }

    /** The appointment as the save would leave it, never written anywhere. */
    private static StationEvent asSaved(
            StationEvent current,
            EventDraft draft,
            EditScopeKind kind,
            @Nullable LocalDate date,
            StationCalendar calendar) {
        var type = kind == EditScopeKind.THIS ? StationEvent.EventType.ONE_TIME : draft.eventType();
        var span = date != null && (kind == EditScopeKind.THIS || draft.isRecurring())
                ? StationEvent.placeOn(draft.startTime(), draft.endTime(), date, calendar.zone())
                : new StationEvent.Span(draft.startTime(), draft.endTime());
        return new StationEvent(
                current.id(),
                current.stationId(),
                draft.name(),
                draft.description(),
                type,
                kind == EditScopeKind.THIS ? null : draft.dayOfWeek(),
                span.start(),
                span.end(),
                draft.templateId(),
                draft.requiresRegistration(),
                draft.registrationDeadline(),
                draft.requiresConfirmation(),
                draft.categoryId(),
                current.restrictionMode(),
                current.viewRestrictionMode(),
                current.restricted(),
                draft.isPublic(),
                draft.registrationLimit(),
                current.cancelled(),
                current.cancelledAt(),
                current.cancelReason(),
                draft.minRegistrations(),
                draft.thresholdDays(),
                draft.registrationCloseDays(),
                draft.repeatUntil(),
                draft.repeatCount());
    }
}
