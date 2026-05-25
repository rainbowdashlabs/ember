/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.event.events.RegistrationDeadlineExpired;
import dev.chojo.ember.feature.events.repository.EventRepository;
import dev.chojo.ember.feature.station.repository.StationRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Periodically checks for events whose registration deadline has just passed
 * and still have pending registrations. Notifies event managers.
 */
@Singleton
public class RegistrationDeadlineChecker {
    private static final Logger log = LoggerFactory.getLogger(RegistrationDeadlineChecker.class);

    private final EventRepository eventRepository;
    private final StationRepository stationRepository;
    private final DomainEventBus eventBus;

    @Inject
    public RegistrationDeadlineChecker(
            EventRepository eventRepository, StationRepository stationRepository, DomainEventBus eventBus) {
        this.eventRepository = eventRepository;
        this.stationRepository = stationRepository;
        this.eventBus = eventBus;

        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            var t = new Thread(r, "registration-deadline-checker");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleWithFixedDelay(this::check, 5, 5, TimeUnit.MINUTES);
    }

    private void check() {
        try {
            var now = Instant.now();
            var stations = stationRepository.findAll();
            for (var station : stations) {
                var events = eventRepository.findByStation(station.id());
                for (var event : events) {
                    if (!event.requiresRegistration()) continue;
                    if (event.registrationDeadline() == null) continue;
                    if (event.registrationDeadline().isAfter(now)) continue;

                    if (isDeadlineNotified(event.id())) continue;

                    // Deadline has passed — check for pending registrations
                    var pending = eventRepository.findPendingRegistrations(event.id());
                    if (!pending.isEmpty()) {
                        eventRepository.markDeadlineNotified(event.id());
                        eventBus.publish(new RegistrationDeadlineExpired(
                                station.id(), event.id(), event.name(), pending.size()));
                        log.info(
                                "Registration deadline expired for event '{}' (id={}) with {} pending registrations",
                                event.name(),
                                event.id(),
                                pending.size());
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error checking registration deadlines", e);
        }
    }

    private boolean isDeadlineNotified(int eventId) {
        return de.chojo.sadu.queries.api.query.Query.query(
                        "SELECT deadline_notified FROM station_event WHERE id = :id;")
                .single(de.chojo.sadu.queries.api.call.Call.of().bind("id", eventId))
                .map(row -> row.getBoolean("deadline_notified"))
                .first()
                .orElse(false);
    }
}
