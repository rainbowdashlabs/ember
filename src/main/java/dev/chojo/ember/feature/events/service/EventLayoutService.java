/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.feature.events.entity.EventLayout;
import dev.chojo.ember.feature.events.entity.EventLayoutField;
import dev.chojo.ember.feature.events.repository.EventLayoutRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Optional;

@Singleton
public class EventLayoutService {
    private final EventLayoutRepository repository;

    @Inject
    public EventLayoutService(EventLayoutRepository repository) {
        this.repository = repository;
    }

    public List<EventLayout> findByStation(int stationId) {
        return repository.findByStation(stationId);
    }

    public Optional<EventLayout> findById(int id) {
        return repository.findById(id);
    }

    public EventLayout create(int stationId, String name) {
        return repository.create(stationId, name);
    }

    public boolean update(int id, String name) {
        return repository.update(id, name);
    }

    public boolean delete(int id) {
        return repository.delete(id);
    }

    public List<EventLayoutField> findFieldsByLayout(int layoutId) {
        return repository.findFieldsByLayout(layoutId);
    }

    public void replaceLayoutFields(int layoutId, List<EventLayoutRepository.LayoutFieldEntry> fields) {
        repository.replaceLayoutFields(layoutId, fields);
    }
}
