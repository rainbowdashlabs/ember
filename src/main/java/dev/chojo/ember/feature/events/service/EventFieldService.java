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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Singleton
public class EventFieldService {
    private static final Logger log = LoggerFactory.getLogger(EventFieldService.class);

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

    public Map<Integer, List<EventField>> findOverviewFieldsByEvents(List<Integer> eventIds) {
        var allFields = repository.findOverviewFieldsByEvents(eventIds);
        var result = new LinkedHashMap<Integer, List<EventField>>();
        for (var field : allFields) {
            result.computeIfAbsent(field.eventId(), _ -> new ArrayList<>()).add(field);
        }
        return result;
    }

    public void replaceFields(int eventId, List<EventFieldRepository.FieldEntry> fields) {
        repository.replaceFields(eventId, fields);
        log.info("Replaced {} fields for event {}", fields.size(), eventId);
    }
}
