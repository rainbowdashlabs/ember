/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

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

    @Inject
    public EventTemplateService(EventTemplateRepository repository) {
        this.repository = repository;
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

    public void replaceFields(int templateId, List<EventTemplateFieldData> fields) {
        repository.replaceFields(templateId, fields);
        log.info("Replaced fields for event template {} ({} fields)", templateId, fields.size());
    }

    public List<Integer> findReminderDays(int templateId) {
        return repository.findReminderDays(templateId);
    }

    public void setReminders(int templateId, List<Integer> daysBefore) {
        repository.replaceReminders(templateId, daysBefore);
        log.info("Set reminders for event template {} ({} days)", templateId, daysBefore.size());
    }
}
