/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.events.entity.EventTemplate;
import dev.chojo.ember.feature.events.entity.EventTemplateField;
import dev.chojo.ember.feature.events.entity.EventTemplateFieldData;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventTemplateRepository;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Optional;

@Singleton
public class EventTemplateService {
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
        return repository.create(stationId, name);
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
        return repository.update(
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
                registrationLimit);
    }

    public boolean delete(int id) {
        return repository.delete(id);
    }

    public List<EventTemplateField> findFields(int templateId) {
        return repository.findFields(templateId);
    }

    public void replaceFields(int templateId, List<EventTemplateFieldData> fields) {
        repository.replaceFields(templateId, fields);
    }

    public List<String> findRestrictions(int templateId) {
        return repository.findRestrictions(templateId);
    }

    public void setRestrictions(int templateId, List<StationUserType> userTypes) {
        repository.setRestrictions(templateId, userTypes);
    }

    public List<Integer> findReminderDays(int templateId) {
        return repository.findReminderDays(templateId);
    }

    public void setReminders(int templateId, List<Integer> daysBefore) {
        repository.replaceReminders(templateId, daysBefore);
    }
}
