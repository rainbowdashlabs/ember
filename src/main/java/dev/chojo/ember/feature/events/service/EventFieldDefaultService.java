/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.feature.events.entity.EventFieldDefault;
import dev.chojo.ember.feature.events.repository.EventFieldDefaultRepository;
import dev.chojo.ember.feature.events.repository.EventRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Owns the prefilled attendance field values an event contributes, including resolving links to
 * event properties into concrete values.
 */
@Singleton
public class EventFieldDefaultService {
    private static final Logger log = LoggerFactory.getLogger(EventFieldDefaultService.class);

    private final EventFieldDefaultRepository fieldDefaultRepository;
    private final EventRepository eventRepository;

    @Inject
    public EventFieldDefaultService(
            EventFieldDefaultRepository fieldDefaultRepository, EventRepository eventRepository) {
        this.fieldDefaultRepository = fieldDefaultRepository;
        this.eventRepository = eventRepository;
    }

    /**
     * Retrieves all field default configurations for an event.
     *
     * @param eventId the event ID
     * @return the list of field defaults
     */
    public List<EventFieldDefault> findByEvent(int eventId) {
        return fieldDefaultRepository.findByEvent(eventId);
    }

    /**
     * Replaces all field defaults for an event.
     *
     * @param eventId  the event ID
     * @param defaults the new field default configurations
     */
    public void setForEvent(int eventId, List<EventFieldDefault> defaults) {
        fieldDefaultRepository.replaceForEvent(eventId, defaults);
        log.info("Set field defaults for event {} ({} defaults)", eventId, defaults.size());
    }

    /**
     * Resolves field defaults for an event into concrete values by replacing event property links.
     */
    public Map<Integer, String> resolve(int eventId) {
        var event = eventRepository.findById(eventId).orElse(null);
        if (event == null) return Map.of();

        var defaults = fieldDefaultRepository.findByEvent(eventId);
        var result = new HashMap<Integer, String>();
        for (var def : defaults) {
            String resolved =
                    switch (def.source()) {
                        case "VALUE" -> def.value();
                        case "EVENT_NAME" -> event.name();
                        case "EVENT_DESCRIPTION" -> event.description();
                        case "EVENT_START_TIME" -> event.startTime() != null ? "\"" + event.startTime() + "\"" : null;
                        case "EVENT_END_TIME" -> event.endTime() != null ? "\"" + event.endTime() + "\"" : null;
                        default -> null;
                    };
            if (resolved != null) {
                result.put(def.fieldId(), resolved);
            }
        }
        return result;
    }
}
