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

@Singleton
public class EventFieldService {
    private final EventFieldRepository repository;

    @Inject
    public EventFieldService(EventFieldRepository repository) {
        this.repository = repository;
    }

    public List<String> findDistinctFieldNames(int stationId) {
        return repository.findDistinctFieldNames(stationId);
    }

    public List<EventField> findByEvent(int eventId) {
        return repository.findByEvent(eventId);
    }

    public void replaceFields(int eventId, List<EventFieldRepository.FieldEntry> fields) {
        repository.replaceFields(eventId, fields);
    }
}
