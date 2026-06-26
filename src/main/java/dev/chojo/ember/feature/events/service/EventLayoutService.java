/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.feature.events.entity.EventLayout;
import dev.chojo.ember.feature.events.entity.EventLayoutField;
import dev.chojo.ember.feature.events.entity.LayoutFieldEntry;
import dev.chojo.ember.feature.events.repository.EventLayoutRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

@Singleton
public class EventLayoutService {
    private static final Logger log = LoggerFactory.getLogger(EventLayoutService.class);

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
        var layout = repository.create(stationId, name);
        log.info("Created event layout {} for station {}", layout.id(), stationId);
        return layout;
    }

    public boolean update(int id, String name) {
        if (repository.update(id, name)) {
            log.info("Updated event layout {}", id);
            return true;
        }
        log.warn("Cannot update event layout: layout {} not found", id);
        return false;
    }

    public boolean delete(int id) {
        if (repository.delete(id)) {
            log.info("Deleted event layout {}", id);
            return true;
        }
        log.warn("Cannot delete event layout: layout {} not found", id);
        return false;
    }

    public List<EventLayoutField> findFieldsByLayout(int layoutId) {
        return repository.findFieldsByLayout(layoutId);
    }

    public void replaceLayoutFields(int layoutId, List<LayoutFieldEntry> fields) {
        repository.replaceLayoutFields(layoutId, fields);
        log.info("Replaced fields for event layout {} ({} fields)", layoutId, fields.size());
    }
}
