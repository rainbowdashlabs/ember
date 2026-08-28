/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.feature.attendance.repository.AttendanceRepository;
import dev.chojo.ember.feature.events.entity.EventTemplate;
import dev.chojo.ember.feature.events.entity.EventTemplateField;
import dev.chojo.ember.feature.events.entity.EventTemplateFieldData;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventTemplateRepository;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

@Singleton
public class EventTemplateService {
    private static final Logger log = LoggerFactory.getLogger(EventTemplateService.class);

    private final EventTemplateRepository repository;
    private final AttendanceRepository attendanceRepository;

    @Inject
    public EventTemplateService(EventTemplateRepository repository, AttendanceRepository attendanceRepository) {
        this.repository = repository;
        this.attendanceRepository = attendanceRepository;
    }

    public List<EventTemplate> findByStation(int stationId) {
        return repository.findByStation(stationId);
    }

    public Optional<EventTemplate> findById(int id) {
        return repository.findById(id);
    }

    public EventTemplate create(int stationId, String name) {
        var template = repository.create(stationId, name);
        log.info("Created event template {} for station {}", template.id(), stationId);
        return template;
    }

    public boolean update(
            int id,
            String name,
            String title,
            String description,
            Integer categoryId,
            StationEvent.EventType eventType,
            Boolean requiresRegistration,
            String registrationDeadlineOffset,
            Boolean requiresConfirmation,
            RestrictionMode restrictionMode,
            Integer attendanceTemplateId,
            Integer registrationLimit) {
        if (repository.update(
                id,
                name,
                title,
                description,
                categoryId,
                eventType,
                requiresRegistration,
                registrationDeadlineOffset,
                requiresConfirmation,
                restrictionMode,
                attendanceTemplateId,
                registrationLimit)) {
            log.info("Updated event template {}", id);
            return true;
        }
        log.warn("Cannot update event template: template {} not found", id);
        return false;
    }

    public boolean delete(int id) {
        if (repository.delete(id)) {
            log.info("Deleted event template {}", id);
            return true;
        }
        log.warn("Cannot delete event template: template {} not found", id);
        return false;
    }

    public List<EventTemplateField> findFields(int templateId) {
        return repository.findFields(templateId);
    }

    /**
     * Replaces the questions a template asks, keeping only the ties that lead somewhere.
     *
     * <p>A question can be tied to a field of the attendance sheet the template names, so that
     * answering the question fills the sheet in. A tie to a field of some other sheet fills in a
     * sheet nobody opens: the value is written and never seen again. Such a tie is dropped here
     * rather than stored, because the only ways to acquire one are a stale value left behind when
     * the sheet was changed and a caller that is not the editor.
     */
    public void replaceFields(int templateId, List<EventTemplateFieldData> fields) {
        var kept = fields.stream()
                .map(field -> reachable(templateId, field.attendanceFieldId())
                        ? field
                        : new EventTemplateFieldData(
                                field.name(),
                                field.fieldType(),
                                field.config(),
                                field.position(),
                                field.overview(),
                                field.isPublic(),
                                null,
                                field.defaultValue()))
                .toList();
        repository.replaceFields(templateId, kept);
        log.info("Replaced fields for event template {} ({} fields)", templateId, kept.size());
    }

    /** Whether this attendance field belongs to the sheet the template writes into. */
    private boolean reachable(int templateId, Integer attendanceFieldId) {
        if (attendanceFieldId == null) return true;
        var sheetId = repository
                .findById(templateId)
                .map(EventTemplate::attendanceTemplateId)
                .orElse(null);
        if (sheetId == null) {
            log.info(
                    "Dropped the tie to attendance field {}: template {} names no sheet",
                    attendanceFieldId,
                    templateId);
            return false;
        }
        boolean belongs = attendanceRepository.findTemplateFields(sheetId).stream()
                .anyMatch(field -> field.id() == attendanceFieldId);
        if (!belongs) {
            log.info(
                    "Dropped the tie to attendance field {}: it is not on sheet {} of template {}",
                    attendanceFieldId,
                    sheetId,
                    templateId);
        }
        return belongs;
    }

    public List<Integer> findReminderDays(int templateId) {
        return repository.findReminderDays(templateId);
    }

    public void setReminders(int templateId, List<Integer> daysBefore) {
        repository.replaceReminders(templateId, daysBefore);
        log.info("Set reminders for event template {} ({} days)", templateId, daysBefore.size());
    }
}
