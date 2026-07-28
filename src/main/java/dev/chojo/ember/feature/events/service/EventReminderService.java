/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.feature.events.repository.EventReminderRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Owns how many days ahead of an occurrence its registered members are reminded.
 */
@Singleton
public class EventReminderService {
    private static final Logger log = LoggerFactory.getLogger(EventReminderService.class);

    private final EventReminderRepository reminderRepository;

    @Inject
    public EventReminderService(EventReminderRepository reminderRepository) {
        this.reminderRepository = reminderRepository;
    }

    /**
     * Returns the configured reminder lead times of an event.
     *
     * @param eventId the event ID
     * @return the days before the occurrence a reminder is sent
     */
    public List<Integer> findDays(int eventId) {
        return reminderRepository.findDays(eventId);
    }

    /**
     * Replaces the reminder lead times of an event.
     *
     * @param eventId    the event ID
     * @param daysBefore the days before the occurrence a reminder is sent
     */
    public void setDays(int eventId, List<Integer> daysBefore) {
        reminderRepository.replace(eventId, daysBefore);
        log.info("Set reminders for event {} ({} days)", eventId, daysBefore.size());
    }
}
