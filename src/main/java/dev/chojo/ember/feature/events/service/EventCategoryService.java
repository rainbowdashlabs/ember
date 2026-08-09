/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.feature.events.entity.EventCategory;
import dev.chojo.ember.feature.events.repository.EventCategoryRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

/**
 * Owns the categories events are grouped under, including their display order.
 */
@Singleton
public class EventCategoryService {
    private static final Logger log = LoggerFactory.getLogger(EventCategoryService.class);

    private final EventCategoryRepository categoryRepository;

    @Inject
    public EventCategoryService(EventCategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    /**
     * Finds an event category by its ID.
     *
     * @param id the ID
     * @return the category, if found
     */
    public Optional<EventCategory> findById(int id) {
        return categoryRepository.findById(id);
    }

    /**
     * Retrieves all event categories for a station.
     *
     * @param stationId the station ID
     * @return the list of categories
     */
    public List<EventCategory> findByStation(int stationId) {
        return categoryRepository.findByStation(stationId);
    }

    /**
     * Creates a new event category.
     *
     * @param stationId the station ID
     * @param name      the category name
     * @param position  the display order position
     * @param color     optional display color (#RRGGBB), or null
     * @return the created category
     */
    public EventCategory create(int stationId, String name, int position, String color) {
        var category = categoryRepository.create(stationId, name, position, color);
        log.info("Created event category {} for station {}", category.id(), stationId);
        return category;
    }

    /**
     * Updates an event category.
     *
     * @param id       the category ID
     * @param name     the new name
     * @param position the new position
     * @param color    the optional new display color (#RRGGBB), or null to clear
     * @return true if the category was updated
     */
    public boolean update(int id, String name, int position, Integer maxShownEvents, boolean isPublic, String color) {
        if (categoryRepository.update(id, name, position, maxShownEvents, isPublic, color)) {
            log.info("Updated event category {}", id);
            return true;
        }
        log.warn("Cannot update event category: category {} not found", id);
        return false;
    }

    /**
     * Deletes an event category by ID.
     *
     * @param id the category ID
     * @return true if the category was deleted
     */
    public boolean delete(int id) {
        if (categoryRepository.delete(id)) {
            log.info("Deleted event category {}", id);
            return true;
        }
        log.warn("Cannot delete event category: category {} not found", id);
        return false;
    }

    /**
     * Rewrites the display order of a station's event categories. Ids from another station are
     * ignored by the repository, so a caller cannot reorder a foreign station's categories.
     *
     * @param stationId  the owning station
     * @param orderedIds the category IDs in their new order
     */
    public void reorder(int stationId, List<Integer> orderedIds) {
        categoryRepository.reorder(stationId, orderedIds);
        log.info("Reordered {} event categories", orderedIds.size());
    }
}
