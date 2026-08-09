/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.repository;

import dev.chojo.ember.feature.events.entity.EventCategory;
import dev.chojo.ember.util.sql.SqlSupport;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * Repository for the {@code event_category} table: the per-station grouping events are filed
 * under, including their display order and public visibility.
 */
@Singleton
public class EventCategoryRepository {

    private static final String COLUMNS = "id, station_id, name, position, max_shown_events, public, color";

    /**
     * Finds an event category by its ID.
     *
     * @param id the category ID
     * @return the category, if found
     */
    public Optional<EventCategory> findById(int id) {
        return SqlSupport.findById("event_category", COLUMNS, id, EventCategory.map());
    }

    /**
     * Retrieves all event categories for a station, ordered by position.
     *
     * @param stationId the station ID
     * @return the list of event categories
     */
    public List<EventCategory> findByStation(int stationId) {
        return query("""
                SELECT %s
                FROM event_category
                WHERE station_id = :station_id
                ORDER BY position;""", COLUMNS)
                .single(call().bind("station_id", stationId))
                .map(EventCategory.map())
                .all();
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
        return SqlSupport.insertReturning(
                """
                INSERT
                INTO
                    event_category(station_id, name, position, color)
                VALUES
                    (:station_id, :name, :position, :color)
                RETURNING %s;""",
                call().bind("station_id", stationId)
                        .bind("name", name)
                        .bind("position", position)
                        .bind("color", color),
                EventCategory.map(),
                COLUMNS);
    }

    /**
     * Updates an existing event category.
     *
     * @param id       the category ID
     * @param name     the new category name
     * @param position the new display order position
     * @param color    the optional new display color (#RRGGBB), or null to clear
     * @return true if a row was updated
     */
    public boolean update(int id, String name, int position, Integer maxShownEvents, boolean isPublic, String color) {
        return query("""
                UPDATE event_category
                SET name = :name,
                    position = :position,
                    max_shown_events = :max_shown_events,
                    public = :public,
                    color = :color
                WHERE id = :id;""")
                .single(call().bind("name", name)
                        .bind("position", position)
                        .bind("max_shown_events", maxShownEvents)
                        .bind("public", isPublic)
                        .bind("color", color)
                        .bind("id", id))
                .update()
                .changed();
    }

    /**
     * Deletes an event category by ID.
     *
     * @param id the category ID
     * @return true if a row was deleted
     */
    public boolean delete(int id) {
        return SqlSupport.deleteById("event_category", id);
    }

    /**
     * Rewrites the display order of a station's categories so each named id lands on its
     * zero-based index in the list. Ids belonging to another station are ignored.
     *
     * @param stationId  the owning station
     * @param orderedIds the category IDs in their new order
     */
    public void reorder(int stationId, List<Integer> orderedIds) {
        SqlSupport.reorder("event_category", "position", "station_id", stationId, orderedIds);
    }
}
