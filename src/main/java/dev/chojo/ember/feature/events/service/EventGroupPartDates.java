/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.feature.events.entity.EventGroupPart;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventGroupRepository;
import dev.chojo.ember.feature.events.repository.EventRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * The parts of an event group with the one date each takes place on, which is how everything filed for a
 * date (registrations, copies of documents, signatures) names a part. A part is a one-time appointment, so
 * its date is the day it starts on the station's calendar.
 */
@Singleton
public class EventGroupPartDates {
    private final EventGroupRepository eventGroups;
    private final EventRepository events;
    private final OccurrenceCalendar calendar;

    @Inject
    public EventGroupPartDates(EventGroupRepository eventGroups, EventRepository events, OccurrenceCalendar calendar) {
        this.eventGroups = eventGroups;
        this.events = events;
        this.calendar = calendar;
    }

    /**
     * A part of an event group on its date.
     *
     * @param eventId the part
     * @param date    the day it takes place on
     */
    public record PartDate(int eventId, LocalDate date) {}

    /**
     * The event group an appointment is a part of.
     *
     * @param eventId the appointment
     * @return the event group, empty for an appointment of its own
     */
    public Optional<Integer> eventGroupOf(int eventId) {
        return eventGroups.findPart(eventId).map(EventGroupPart::eventGroupId);
    }

    /**
     * Every part of an event group with its date, in date order.
     *
     * @param eventGroupId the event group
     * @return its parts
     */
    public List<PartDate> parts(int eventGroupId) {
        return eventGroups.findParts(eventGroupId).stream()
                .map(part -> dateOf(part.eventId()))
                .flatMap(Optional::stream)
                .sorted((a, b) -> a.date().compareTo(b.date()))
                .toList();
    }

    /**
     * The other parts of the event group an appointment is a part of.
     *
     * @param eventId the appointment
     * @return the other parts with their dates; empty for an appointment of its own
     */
    public List<PartDate> siblings(int eventId) {
        return eventGroupOf(eventId).map(this::parts).orElse(List.of()).stream()
                .filter(part -> part.eventId() != eventId)
                .toList();
    }

    private Optional<PartDate> dateOf(int eventId) {
        return events.findById(eventId)
                .flatMap(event -> dayOf(calendar, event))
                .map(date -> new PartDate(eventId, date));
    }

    /**
     * The one day a part of an event group falls on, as the station's calendar reads it.
     *
     * @param calendar the occurrence calendar
     * @param part     the part
     * @return its day, empty where it has no start
     */
    static Optional<LocalDate> dayOf(OccurrenceCalendar calendar, StationEvent part) {
        return calendar.forStation(part.stationId()).ruleOf(part).map(rule -> rule.first());
    }
}
