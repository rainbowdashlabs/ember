/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventRegistrationRepository;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.LocalDate;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Who an appointment concerns on its dates, which is who hears about it: a reminder about one date,
 * or word that the appointment changed.
 *
 * <p>Where the appointment is signed up for, that is whoever holds a place, pending or accepted, on
 * one of the dates, and holding one already means they were allowed to take it. A place on another
 * date of a series is not a reason to hear about these. Where it is not signed up for, it is everybody
 * who may know the appointment exists, minus whoever said they are not coming. Nobody hears about
 * something they cannot see, and visibility is asked without permissions so that being able to
 * override the restriction is not itself a reason to hear about it.
 */
@Singleton
public class EventAudience {
    private final EventRegistrationRepository registrationRepository;
    private final StationMemberRepository stationMemberRepository;
    private final EventRestrictionService restrictionService;
    private final OccurrenceCalendar occurrenceCalendar;

    @Inject
    public EventAudience(
            EventRegistrationRepository registrationRepository,
            StationMemberRepository stationMemberRepository,
            EventRestrictionService restrictionService,
            OccurrenceCalendar occurrenceCalendar) {
        this.registrationRepository = registrationRepository;
        this.stationMemberRepository = stationMemberRepository;
        this.restrictionService = restrictionService;
        this.occurrenceCalendar = occurrenceCalendar;
    }

    /**
     * Who the appointment concerns on one date.
     *
     * @param event the appointment
     * @param date  the date
     * @return the members, each once
     */
    public List<Integer> onDate(StationEvent event, LocalDate date) {
        if (event.requiresRegistration()) return registrationRepository.findRegisteredMemberIds(event.id(), date);
        return everybodyComingTo(event, date);
    }

    /**
     * Who the appointment concerns on any of these dates, each member once.
     *
     * <p>Without registration, a decline counts for the first of the dates that is one of the
     * appointment's: that is the one the member answered for, and the dates after it have not been
     * asked about yet. A date the appointment does not fall on has no answers and is passed over.
     *
     * @param event the appointment
     * @param dates the dates
     * @return the members, each once; nobody where none of the dates is one of the appointment's
     */
    public List<Integer> onDates(StationEvent event, Collection<LocalDate> dates) {
        if (dates.isEmpty()) return List.of();
        if (event.requiresRegistration()) {
            return registrationRepository.findRegisteredMemberIdsOn(event.id(), dates);
        }
        var calendar = occurrenceCalendar.forStation(event.stationId());
        return dates.stream()
                .filter(date -> calendar.occursOn(event, date))
                .min(LocalDate::compareTo)
                .map(first -> everybodyComingTo(event, first))
                .orElse(List.of());
    }

    /**
     * Who the appointment concerns from one date on: every place held on that date or later, or
     * without registration everybody who may see it and did not decline the first of its dates from
     * there on.
     *
     * <p>A place on a date that was called off still counts: the member holds it, and the date can be
     * brought back.
     *
     * @param event the appointment
     * @param from  the first date
     * @return the members, each once; without registration nobody where no date is left from there on
     */
    public List<Integer> fromDate(StationEvent event, LocalDate from) {
        if (event.requiresRegistration()) return registrationRepository.findRegisteredMemberIdsFrom(event.id(), from);
        return occurrenceCalendar
                .forStation(event.stationId())
                .next(event, from)
                .map(first -> everybodyComingTo(event, first))
                .orElse(List.of());
    }

    private List<Integer> everybodyComingTo(StationEvent event, LocalDate date) {
        Set<Integer> declined = new HashSet<>(registrationRepository.findNotAttendingMemberIds(event.id(), date));
        return stationMemberRepository.findByStation(event.stationId()).stream()
                .map(StationMember::id)
                .filter(id -> !declined.contains(id))
                .filter(id -> restrictionService.canView(event.id(), id, Set.of()))
                .toList();
    }
}
