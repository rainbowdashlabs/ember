/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.event.events.EventRegistrationStatusChanged;
import dev.chojo.ember.feature.events.entity.EventField;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventFieldRepository;
import dev.chojo.ember.feature.events.repository.EventRegistrationRepository;
import dev.chojo.ember.feature.events.repository.EventRepository;
import dev.chojo.ember.feature.members.service.MemberNameResolver;
import dev.chojo.ember.feature.question.QuestionValues;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Keeps the registration list in step with the people an appointment's questions name.
 *
 * <p>Somebody named in a question that asks for members is taking part in the appointment, and this
 * is what says so everywhere else: they stand on the list, they are counted, and the appointment
 * reaches their calendar. The place is confirmed the moment they are named, whatever the appointment
 * says about confirming registrations, because being named is the decision and there is nobody left
 * to make it a second time.
 *
 * <p>It is a place they cannot give back from the list, and the way off is out of the question that
 * names them. Where they take their name out and the appointment had to be signed up for, the place
 * is recorded as given up rather than quietly removed, so whoever runs it sees that somebody who was
 * down for it no longer is.
 */
@Singleton
public class EventFieldRegistrationService {
    private static final Logger log = LoggerFactory.getLogger(EventFieldRegistrationService.class);

    /**
     * How far ahead a question answered once for a whole series writes its registrations.
     *
     * <p>Such a question names the same people on every date, and a series may have no last date at
     * all, so something has to bound it. A year is what the subscribed calendar reaches, which makes
     * this exactly as far ahead as anything can see. The daily pass moves the edge along as the days
     * pass, so a member named today is still on next year's dates when next year comes.
     */
    public static final int HORIZON_DAYS = 365;

    private final EventRepository eventRepository;
    private final EventFieldRepository fieldRepository;
    private final EventRegistrationRepository registrationRepository;
    private final EventDateResolver dateResolver;
    private final DomainEventBus eventBus;
    private final MemberNameResolver nameResolver;

    @Inject
    public EventFieldRegistrationService(
            EventRepository eventRepository,
            EventFieldRepository fieldRepository,
            EventRegistrationRepository registrationRepository,
            EventDateResolver dateResolver,
            DomainEventBus eventBus,
            MemberNameResolver nameResolver) {
        this.eventRepository = eventRepository;
        this.fieldRepository = fieldRepository;
        this.registrationRepository = registrationRepository;
        this.dateResolver = dateResolver;
        this.eventBus = eventBus;
        this.nameResolver = nameResolver;
    }

    /**
     * Brings one appointment's registrations back in step with its questions.
     *
     * <p>Safe to call as often as anything likes: it compares what is named against what is written
     * and touches only the difference.
     *
     * @param eventId the appointment to reconcile
     */
    public void reconcile(int eventId) {
        var event = eventRepository.findById(eventId).orElse(null);
        if (event == null) return;

        var memberFields = fieldRepository.findByEvent(eventId).stream()
                .filter(field -> field.fieldType().isMemberField())
                .toList();
        var dateValues = fieldRepository.findDateValues(eventId);
        var dates = datesToReconcile(event, memberFields, dateValues);
        if (dates.isEmpty()) return;

        var named = namedOn(memberFields, dateValues, dates);
        var written = registrationRepository.findFromFieldOn(eventId, dates);

        var confirmMembers = new ArrayList<Integer>();
        var confirmDates = new ArrayList<LocalDate>();
        for (var entry : named.entrySet()) {
            for (int memberId : entry.getValue()) {
                confirmMembers.add(memberId);
                confirmDates.add(entry.getKey());
            }
        }

        var goneMembers = new ArrayList<Integer>();
        var goneDates = new ArrayList<LocalDate>();
        var goneNames = new LinkedHashSet<Integer>();
        for (var registration : written) {
            if (named.getOrDefault(registration.eventDate(), Set.of()).contains(registration.memberId())) continue;
            goneMembers.add(registration.memberId());
            goneDates.add(registration.eventDate());
            goneNames.add(registration.memberId());
        }

        int confirmed = registrationRepository.confirmFromField(eventId, confirmMembers, confirmDates);
        int removed = event.requiresRegistration()
                ? registrationRepository.withdrawFromField(eventId, goneMembers, goneDates)
                : registrationRepository.deleteFromField(eventId, goneMembers, goneDates);

        if (confirmed > 0 || removed > 0) {
            log.info("Questions of event {} confirmed {} and released {} registrations", eventId, confirmed, removed);
        }
        if (event.requiresRegistration()) {
            goneNames.forEach(memberId -> announceFreedPlace(event, memberId));
        }
    }

    /** Reconciles every appointment that asks for members, which is what moves the horizon along. */
    public int reconcileAll() {
        var eventIds = fieldRepository.findEventIdsWithMemberFields();
        for (int eventId : eventIds) {
            try {
                reconcile(eventId);
            } catch (Exception e) {
                log.error("Failed to reconcile the registrations of event {}", eventId, e);
            }
        }
        return eventIds.size();
    }

    /**
     * The dates worth looking at.
     *
     * <p>Three sources, and each of them is a date on which the answer may have changed. A date
     * somebody answered explicitly, whether it is still ahead or long past. Every occurrence inside
     * the horizon, where a question is answered once for the whole series and so names people on all
     * of them. And every date that already carries a place given by a question, so emptying the last
     * question still takes those places away again.
     */
    private Set<LocalDate> datesToReconcile(
            StationEvent event, List<EventField> memberFields, Map<Integer, Map<LocalDate, String>> dateValues) {
        var dates = new TreeSet<LocalDate>();
        for (var field : memberFields) {
            if (!field.config().perDate()) continue;
            dates.addAll(dateValues.getOrDefault(field.id(), Map.of()).keySet());
        }
        boolean anyRepeating =
                memberFields.stream().anyMatch(field -> !field.config().perDate());
        if (anyRepeating) dates.addAll(dateResolver.occurrencesWithin(event, HORIZON_DAYS));
        dates.addAll(registrationRepository.findFromFieldDates(event.id()));
        return dates;
    }

    /** Who each of those dates names, as a set of members per date. */
    private Map<LocalDate, Set<Integer>> namedOn(
            List<EventField> memberFields, Map<Integer, Map<LocalDate, String>> dateValues, Set<LocalDate> dates) {
        var named = new HashMap<LocalDate, Set<Integer>>();
        for (var date : dates) {
            var members = new LinkedHashSet<Integer>();
            for (var field : memberFields) {
                String value = field.config().perDate()
                        ? dateValues.getOrDefault(field.id(), Map.of()).get(date)
                        : field.value();
                members.addAll(QuestionValues.memberIds(value));
            }
            named.put(date, members);
        }
        return named;
    }

    /**
     * Tells whoever runs the appointment that somebody who was down for it is not any more, once per
     * member however many of its dates they came off.
     */
    private void announceFreedPlace(StationEvent event, int memberId) {
        eventBus.publish(new EventRegistrationStatusChanged(
                event.stationId(),
                event.id(),
                event.name(),
                memberId,
                nameResolver.called(memberId),
                RegistrationStatus.WITHDRAWN));
    }
}
