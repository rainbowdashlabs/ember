/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.util;

import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.feature.events.repository.EventBreakRepository;
import dev.chojo.ember.feature.events.repository.EventDateCancellationRepository;
import dev.chojo.ember.feature.events.repository.EventDetachedDateRepository;
import dev.chojo.ember.feature.events.repository.EventRegistrationRepository;
import dev.chojo.ember.feature.events.repository.EventRepository;
import dev.chojo.ember.feature.events.repository.RegistrationQueueRepository;
import dev.chojo.ember.feature.events.service.EventCapacity;
import dev.chojo.ember.feature.events.service.EventPlaces;
import dev.chojo.ember.feature.events.service.OccurrenceCalendar;
import dev.chojo.ember.feature.events.service.QueueAdvancer;
import dev.chojo.ember.feature.events.service.QueuePlacement;
import dev.chojo.ember.feature.station.repository.StationRepository;

import java.util.Set;

/** The places of appointment dates and their waiting lists, wired to the test database. */
public final class TestEventPlaces {
    private TestEventPlaces() {}

    /**
     * Places reading and writing the real tables.
     *
     * @param registrations the registrations the fairness score reads
     * @return the places
     */
    public static EventPlaces of(EventRegistrationRepository registrations) {
        return of(registrations, new DomainEventBus(Set.of()));
    }

    /**
     * Places reading and writing the real tables, announcing a partner's member moving up on the given bus.
     *
     * @param registrations the registrations the fairness score reads
     * @param bus           where a partner's member moving up is announced
     * @return the places
     */
    public static EventPlaces of(EventRegistrationRepository registrations, DomainEventBus bus) {
        var queue = new RegistrationQueueRepository();
        var capacity = new EventCapacity(queue);
        var events = new EventRepository();
        var calendar = new OccurrenceCalendar(
                events,
                new EventBreakRepository(),
                new EventDateCancellationRepository(),
                new EventDetachedDateRepository(),
                new StationRepository());
        return new EventPlaces(
                capacity,
                new QueuePlacement(queue, registrations),
                queue,
                new QueueAdvancer(events, capacity, queue, registrations, calendar, bus));
    }
}
