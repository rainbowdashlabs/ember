/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.feature.events.entity.EventTemplate;
import dev.chojo.ember.feature.events.entity.EventTemplateField;
import dev.chojo.ember.feature.events.repository.EventTemplateRepository;
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
            String eventType,
            Boolean requiresRegistration,
            String registrationDeadlineOffset,
            Boolean requiresConfirmation,
            String restrictionMode) {
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
                restrictionMode);
    }

    public boolean delete(int id) {
        return repository.delete(id);
    }

    public List<EventTemplateField> findFields(int templateId) {
        return repository.findFields(templateId);
    }

    public void replaceFields(int templateId, List<EventTemplateRepository.EventTemplateFieldData> fields) {
        repository.replaceFields(templateId, fields);
    }

    public List<Integer> findRestrictions(int templateId) {
        return repository.findRestrictions(templateId);
    }

    public void setRestrictions(int templateId, List<Integer> roleIds) {
        repository.setRestrictions(templateId, roleIds);
    }
}
