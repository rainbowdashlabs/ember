/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.feature.events.entity.EventField;
import dev.chojo.ember.feature.events.repository.EventFieldRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;

/**
 * Service for managing custom fields attached to station events.
 */
@Singleton
public class EventFieldService {
    private final EventFieldRepository repository;

    @Inject
    public EventFieldService(EventFieldRepository repository) {
        this.repository = repository;
    }

    /**
     * Retrieves all distinct field names used across events of a station.
     *
     * @param stationId the station ID
     * @return the sorted list of unique field names
     */
    public List<String> findDistinctFieldNames(int stationId) {
        return repository.findDistinctFieldNames(stationId);
    }

    /**
     * Retrieves all fields for a given event.
     *
     * @param eventId the event ID
     * @return the list of event fields
     */
    public List<EventField> findByEvent(int eventId) {
        return repository.findByEvent(eventId);
    }

    /**
     * Replaces all fields for an event with the given entries.
     *
     * @param eventId the event ID
     * @param fields  the new field entries
     */
    public void replaceFields(int eventId, List<EventFieldRepository.FieldEntry> fields) {
        repository.replaceFields(eventId, fields);
    }
}
