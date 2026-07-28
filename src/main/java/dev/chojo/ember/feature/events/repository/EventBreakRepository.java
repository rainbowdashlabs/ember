/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.repository;

import dev.chojo.ember.feature.events.entity.EventBreak;
import dev.chojo.ember.util.sql.SqlSupport;
import jakarta.inject.Singleton;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * Repository for the {@code station_event_break} table: the date ranges during which a station's
 * recurring events do not take place.
 */
@Singleton
public class EventBreakRepository {

    private static final String COLUMNS = "id, station_id, name, start_date, end_date";

    /**
     * Retrieves all event breaks for a station, ordered by start date.
     *
     * @param stationId the station ID
     * @return the list of event breaks
     */
    public List<EventBreak> findByStation(int stationId) {
        return query("""
                SELECT %s
                FROM station_event_break
                WHERE station_id = :station_id
                ORDER BY start_date;""", COLUMNS)
                .single(call().bind("station_id", stationId))
                .map(EventBreak.map())
                .all();
    }

    /**
     * Finds an event break by its ID.
     *
     * @param id the break ID
     * @return the break, if found
     */
    public Optional<EventBreak> findById(int id) {
        return SqlSupport.findById("station_event_break", COLUMNS, id, EventBreak.map());
    }

    /**
     * Creates a new event break.
     *
     * @param stationId the station ID
     * @param name      the break name
     * @param startDate the first day of the break
     * @param endDate   the last day of the break
     * @return the created break
     */
    public EventBreak create(int stationId, String name, LocalDate startDate, LocalDate endDate) {
        return SqlSupport.insertReturning(
                """
                INSERT INTO station_event_break(station_id, name, start_date, end_date)
                VALUES (:station_id, :name, :start_date, :end_date)
                RETURNING %s;""",
                call().bind("station_id", stationId)
                        .bind("name", name)
                        .bind("start_date", startDate)
                        .bind("end_date", endDate),
                EventBreak.map(),
                COLUMNS);
    }

    /**
     * Updates an existing event break.
     *
     * @param id        the break ID
     * @param name      the new break name
     * @param startDate the new start date
     * @param endDate   the new end date
     * @return true if a row was updated
     */
    public boolean update(int id, String name, LocalDate startDate, LocalDate endDate) {
        return query("""
                UPDATE station_event_break
                SET
                    name       = :name,
                    start_date = :start_date,
                    end_date   = :end_date
                WHERE id = :id;""")
                .single(call().bind("name", name)
                        .bind("start_date", startDate)
                        .bind("end_date", endDate)
                        .bind("id", id))
                .update()
                .changed();
    }

    /**
     * Deletes an event break by ID.
     *
     * @param id the break ID
     * @return true if a row was deleted
     */
    public boolean delete(int id) {
        return SqlSupport.deleteById("station_event_break", id);
    }

    /**
     * Checks whether a given date falls within any break period for the station.
     *
     * @param stationId the station ID
     * @param date      the date to check
     * @return true if the date is within a break
     */
    public boolean isDateInBreak(int stationId, LocalDate date) {
        return SqlSupport.exists("""
                SELECT 1
                FROM station_event_break
                WHERE station_id = :station_id
                  AND start_date <= :date
                  AND end_date >= :date
                LIMIT 1;""", call().bind("station_id", stationId).bind("date", date));
    }
}
