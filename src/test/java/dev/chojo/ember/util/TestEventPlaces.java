/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.util;

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
                new QueueAdvancer(events, capacity, queue, registrations, calendar));
    }
}
