/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.feature.attendance.entity.AttendanceTemplateField;
import dev.chojo.ember.feature.attendance.repository.AttendanceRepository;
import dev.chojo.ember.feature.events.entity.EventFieldDefault;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventFieldDefaultRepository;
import dev.chojo.ember.feature.events.repository.EventRepository;
import dev.chojo.ember.feature.question.Question;
import dev.chojo.ember.feature.question.QuestionCheck;
import io.javalin.http.BadRequestResponse;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Owns the prefilled attendance field values an event contributes, including resolving links to
 * event properties into concrete values.
 */
@Singleton
public class EventFieldDefaultService {
    private static final Logger log = LoggerFactory.getLogger(EventFieldDefaultService.class);

    private final EventFieldDefaultRepository fieldDefaultRepository;
    private final EventRepository eventRepository;
    private final AttendanceRepository attendanceRepository;

    @Inject
    public EventFieldDefaultService(
            EventFieldDefaultRepository fieldDefaultRepository,
            EventRepository eventRepository,
            AttendanceRepository attendanceRepository) {
        this.fieldDefaultRepository = fieldDefaultRepository;
        this.eventRepository = eventRepository;
        this.attendanceRepository = attendanceRepository;
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
        requireAnswerable(eventId, defaults);
        fieldDefaultRepository.replaceForEvent(eventId, defaults);
        log.info("Set field defaults for event {} ({} defaults)", eventId, defaults.size());
    }

    /**
     * Refuses a value the sheet field it fills in would not take.
     *
     * <p>What an appointment writes into a field of the attendance sheet lands there as the answer,
     * so it is measured against that field: a choice has to be one the sheet offers. Only a value
     * typed here is measured; the ones that carry a property of the appointment across, its name or
     * its evening, are whatever the appointment says.
     *
     * @throws BadRequestResponse naming the field and what is wrong with the value
     */
    private void requireAnswerable(int eventId, List<EventFieldDefault> defaults) {
        var questions = sheetQuestions(eventId);
        for (var chosen : defaults) {
            if (!"VALUE".equals(chosen.source())) continue;
            var question = questions.get(chosen.fieldId());
            if (question == null) continue;
            QuestionCheck.answerIfGiven(question, chosen.value()).ifPresent(problem -> {
                throw new BadRequestResponse(problem.message());
            });
        }
    }

    /** The questions the sheet this appointment is taken on asks, by field. */
    private Map<Integer, Question> sheetQuestions(int eventId) {
        return eventRepository
                .findById(eventId)
                .map(StationEvent::templateId)
                .map(sheetId -> attendanceRepository.findTemplateFields(sheetId).stream()
                        .collect(Collectors.toMap(AttendanceTemplateField::id, AttendanceTemplateField::question)))
                .orElse(Map.of());
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
