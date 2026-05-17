/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.service;

import dev.chojo.ember.entity.EventField;
import dev.chojo.ember.entity.EventFieldValue;
import dev.chojo.ember.repository.EventFieldRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Optional;

@Singleton
public class EventFieldService {
    private final EventFieldRepository repository;

    @Inject
    public EventFieldService(EventFieldRepository repository) {
        this.repository = repository;
    }

    public List<EventField> findByStation(int stationId) {
        return repository.findByStation(stationId);
    }

    public Optional<EventField> findById(int id) {
        return repository.findById(id);
    }

    public EventField create(int stationId, String name, String fieldType, String config, int position) {
        return repository.create(stationId, name, fieldType, config, position);
    }

    public boolean update(int id, String name, String fieldType, String config, int position) {
        return repository.update(id, name, fieldType, config, position);
    }

    public boolean delete(int id) {
        return repository.delete(id);
    }

    public List<EventFieldValue> findValues(int eventId) {
        return repository.findValues(eventId);
    }

    public void setValues(int eventId, List<FieldValueEntry> entries) {
        for (var entry : entries) {
            repository.setValue(eventId, entry.fieldId(), entry.value());
        }
    }

    public boolean deleteValue(int eventId, int fieldId) {
        return repository.deleteValue(eventId, fieldId);
    }

    public record FieldValueEntry(int fieldId, String value) {}
}
