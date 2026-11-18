/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.feature.events.entity.Places;
import dev.chojo.ember.feature.events.repository.RegistrationQueueRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.LocalDate;

/**
 * Answers how many places an appointment date has and how many of them are taken.
 *
 * <p>Only accepted sign-ups take a place, of this station's members and of partners' members alike. A
 * part of an event group without a limit of its own has the places per part its event group gives.
 */
@Singleton
public class EventCapacity {
    private final RegistrationQueueRepository queue;

    /**
     * Creates the capacity reader.
     *
     * @param queue the places and waiting lists
     */
    @Inject
    public EventCapacity(RegistrationQueueRepository queue) {
        this.queue = queue;
    }

    /**
     * The places of an appointment date.
     *
     * @param eventId   the appointment
     * @param eventDate the date
     * @return its places
     */
    public Places of(int eventId, LocalDate eventDate) {
        return queue.places(eventId, eventDate);
    }
}
