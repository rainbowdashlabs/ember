/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.api.refusal.EventRefusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.feature.attendance.entity.AttendanceTemplateField;
import dev.chojo.ember.feature.attendance.repository.AttendanceRepository;
import dev.chojo.ember.feature.attendance.service.AttendanceTemplateGuards;
import dev.chojo.ember.feature.events.entity.AppointmentOwner;
import dev.chojo.ember.feature.events.entity.EventFieldDefault;
import dev.chojo.ember.feature.events.entity.EventTemplate;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventFieldDefaultRepository;
import dev.chojo.ember.feature.events.repository.EventRepository;
import dev.chojo.ember.feature.events.repository.EventTemplateRepository;
import dev.chojo.ember.feature.question.Question;
import dev.chojo.ember.feature.question.QuestionCheck;
import dev.chojo.ember.util.sql.Transactions;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Owns the prefilled attendance field values an event contributes, and the ones a template holds for
 * the events made from it, including resolving links to event properties into concrete values.
 */
@Singleton
public class EventFieldDefaultService {
    private static final Logger log = LoggerFactory.getLogger(EventFieldDefaultService.class);

    private final EventFieldDefaultRepository fieldDefaultRepository;
    private final EventRepository eventRepository;
    private final EventTemplateRepository templateRepository;
    private final AttendanceRepository attendanceRepository;
    private final AttendanceTemplateGuards attendanceTemplateGuards;

    @Inject
    public EventFieldDefaultService(
            EventFieldDefaultRepository fieldDefaultRepository,
            EventRepository eventRepository,
            EventTemplateRepository templateRepository,
            AttendanceRepository attendanceRepository,
            AttendanceTemplateGuards attendanceTemplateGuards) {
        this.fieldDefaultRepository = fieldDefaultRepository;
        this.eventRepository = eventRepository;
        this.templateRepository = templateRepository;
        this.attendanceRepository = attendanceRepository;
        this.attendanceTemplateGuards = attendanceTemplateGuards;
    }

    /**
     * Retrieves all field default configurations of an event or a template.
     *
     * @param owner   whose values these are
     * @param ownerId the event or template
     * @return the list of field defaults
     */
    public List<EventFieldDefault> find(AppointmentOwner owner, int ownerId) {
        return fieldDefaultRepository.findByOwner(owner, ownerId);
    }

    /**
     * Replaces all field defaults of an event or a template.
     *
     * <p>A template keeps only values for fields in use on its attendance sheet, and none while it
     * has no sheet: it is written before any event exists, so a value for a field of some other
     * sheet would fill in a sheet nobody opens. Such a value is dropped rather than stored, the way a
     * template question's tie to another sheet is.
     *
     * @param owner    whose values these are
     * @param ownerId  the event or template
     * @param defaults the new field default configurations
     */
    public void set(AppointmentOwner owner, int ownerId, List<EventFieldDefault> defaults) {
        Integer sheetId = sheetOf(owner, ownerId);
        if (sheetId != null)
            defaults.forEach(chosen -> attendanceTemplateGuards.requireFieldInUse(sheetId, chosen.fieldId()));
        var questions = sheetQuestions(sheetId);
        requireAnswerable(questions, defaults);
        var kept = owner == AppointmentOwner.TEMPLATE
                ? defaults.stream()
                        .filter(chosen -> questions.containsKey(chosen.fieldId()))
                        .toList()
                : defaults;
        Transactions.run(() -> fieldDefaultRepository.replace(owner, ownerId, kept));
        log.info("Set field defaults for {} {} ({} defaults)", owner, ownerId, kept.size());
    }

    /**
     * Refuses a value the sheet field it fills in would not take.
     *
     * <p>What an appointment writes into a field of the attendance sheet lands there as the answer,
     * so it is measured against that field: a choice has to be one the sheet offers. Only a value
     * typed here is measured; the ones that carry a property of the appointment across, its name or
     * its date, are whatever the appointment says.
     *
     * @throws RefusalResponse naming the field and what is wrong with the value
     */
    private static void requireAnswerable(Map<Integer, Question> questions, List<EventFieldDefault> defaults) {
        for (var chosen : defaults) {
            if (!"VALUE".equals(chosen.source())) continue;
            var question = questions.get(chosen.fieldId());
            if (question == null) continue;
            QuestionCheck.answerIfGiven(question, chosen.value()).ifPresent(problem -> {
                throw EventRefusal.EVENT_FIELD_DEFAULT_NOT_ACCEPTED.raise(problem.question());
            });
        }
    }

    /** The attendance sheet an event is taken on, or a template hands its events, if any. */
    private @Nullable Integer sheetOf(AppointmentOwner owner, int ownerId) {
        return switch (owner) {
            case EVENT ->
                eventRepository.findById(ownerId).map(StationEvent::templateId).orElse(null);
            case TEMPLATE ->
                templateRepository
                        .findById(ownerId)
                        .map(EventTemplate::attendanceTemplateId)
                        .orElse(null);
        };
    }

    /** The questions a sheet asks, by field, leaving out the fields it no longer uses. */
    private Map<Integer, Question> sheetQuestions(@Nullable Integer sheetId) {
        if (sheetId == null) return Map.of();
        return attendanceRepository.findTemplateFields(sheetId).stream()
                .collect(Collectors.toMap(AttendanceTemplateField::id, AttendanceTemplateField::question));
    }

    /**
     * Resolves field defaults for an event into concrete values by replacing event property links.
     */
    public Map<Integer, String> resolve(int eventId) {
        var event = eventRepository.findById(eventId).orElse(null);
        if (event == null) return Map.of();

        var defaults = fieldDefaultRepository.findByOwner(AppointmentOwner.EVENT, eventId);
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
