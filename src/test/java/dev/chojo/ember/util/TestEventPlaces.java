/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.util;

import dev.chojo.ember.feature.events.repository.EventRegistrationRepository;
import dev.chojo.ember.feature.events.repository.RegistrationQueueRepository;
import dev.chojo.ember.feature.events.service.EventCapacity;
import dev.chojo.ember.feature.events.service.EventPlaces;
import dev.chojo.ember.feature.events.service.QueuePlacement;

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
        return new EventPlaces(new EventCapacity(queue), new QueuePlacement(queue, registrations), queue);
    }
}
