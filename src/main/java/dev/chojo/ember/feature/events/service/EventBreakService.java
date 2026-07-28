/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.feature.events.entity.EventBreak;
import dev.chojo.ember.feature.events.repository.EventBreakRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Owns the break periods of a station, during which recurring events do not take place.
 */
@Singleton
public class EventBreakService {
    private static final Logger log = LoggerFactory.getLogger(EventBreakService.class);

    private final EventBreakRepository breakRepository;

    @Inject
    public EventBreakService(EventBreakRepository breakRepository) {
        this.breakRepository = breakRepository;
    }

    /**
     * Retrieves all event breaks for a station.
     *
     * @param stationId the station ID
     * @return the list of breaks
     */
    public List<EventBreak> findByStation(int stationId) {
        return breakRepository.findByStation(stationId);
    }

    /**
     * Finds an event break by its ID.
     *
     * @param id the break ID
     * @return the break, if found
     */
    public Optional<EventBreak> findById(int id) {
        return breakRepository.findById(id);
    }

    /**
     * Checks whether a station is on break on the given date.
     *
     * @param stationId the station ID
     * @param date      the date to check
     * @return true if the date falls into a break period
     */
    public boolean isDateInBreak(int stationId, LocalDate date) {
        return breakRepository.isDateInBreak(stationId, date);
    }

    /**
     * Creates a new event break.
     *
     * @param stationId the station ID
     * @param name      the break name
     * @param startDate the first day of the break
     * @param endDate   the last day of the break
     * @return the created break
     */
    public EventBreak create(int stationId, String name, LocalDate startDate, LocalDate endDate) {
        var eventBreak = breakRepository.create(stationId, name, startDate, endDate);
        log.info("Created event break {} for station {}", eventBreak.id(), stationId);
        return eventBreak;
    }

    /**
     * Updates an event break and returns the refreshed entity if the update was successful.
     *
     * @param id        the break ID
     * @param name      the new break name
     * @param startDate the new start date
     * @param endDate   the new end date
     * @return the updated break, or empty if not found
     */
    public Optional<EventBreak> update(int id, String name, LocalDate startDate, LocalDate endDate) {
        if (breakRepository.update(id, name, startDate, endDate)) {
            log.info("Updated event break {}", id);
            return breakRepository.findById(id);
        }
        log.warn("Cannot update event break: break {} not found", id);
        return Optional.empty();
    }

    /**
     * Deletes an event break by ID.
     *
     * @param id the break ID
     * @return true if the break was deleted
     */
    public boolean delete(int id) {
        if (breakRepository.delete(id)) {
            log.info("Deleted event break {}", id);
            return true;
        }
        log.warn("Cannot delete event break: break {} not found", id);
        return false;
    }
}
