/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.feature.events.repository.EventRepository;
import dev.chojo.ember.feature.storage.service.StationReadOnlyGuard;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Background scheduler that periodically checks for events whose registration threshold date
 * has passed without meeting the minimum number of accepted registrations. Such events are
 * automatically cancelled with an appropriate reason.
 */
@Singleton
public class EventThresholdChecker {
    private static final Logger log = LoggerFactory.getLogger(EventThresholdChecker.class);

    private final EventRepository eventRepository;
    private final EventCrudService crudService;
    private final StationReadOnlyGuard readOnlyGuard;

    @Inject
    public EventThresholdChecker(
            EventRepository eventRepository, EventCrudService crudService, StationReadOnlyGuard readOnlyGuard) {
        this.eventRepository = eventRepository;
        this.crudService = crudService;
        this.readOnlyGuard = readOnlyGuard;
        var scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            var t = new Thread(r, "event-threshold-checker");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleWithFixedDelay(this::check, 5, 30, TimeUnit.MINUTES);
    }

    private void check() {
        try {
            var events = eventRepository.findAutoCancel();
            for (var event : events) {
                if (!readOnlyGuard.isWritable(event.stationId())) continue;
                log.info("Auto-cancelling event {} (id={}) — threshold not met", event.name(), event.id());
                crudService.cancelEvent(
                        event.stationId(),
                        event.id(),
                        "Mindestanzahl von " + event.minRegistrations() + " Anmeldungen nicht erreicht");
            }
        } catch (Exception e) {
            log.error("Error checking event thresholds", e);
        }
    }
}
