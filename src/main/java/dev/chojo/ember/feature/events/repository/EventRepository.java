/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.repository;

import de.chojo.sadu.queries.api.call.Call;
import de.chojo.sadu.queries.api.query.Query;
import dev.chojo.ember.feature.events.entity.EventBreak;
import dev.chojo.ember.feature.events.entity.EventCategory;
import dev.chojo.ember.feature.events.entity.EventFieldDefault;
import dev.chojo.ember.feature.events.entity.EventRegistration;
import dev.chojo.ember.feature.events.entity.MemberRegistrationStats;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * Repository for managing station events, event breaks, categories, restrictions, field defaults, and registrations.
 */
@Singleton
public class EventRepository {

    private static final String EVENT_COLUMNS =
            "e.id, e.station_id, e.name, e.description, e.event_type, e.day_of_week, e.start_time, e.end_time, e.template_id, e.requires_registration, e.registration_deadline, e.requires_confirmation, e.category_id, e.restriction_mode, EXISTS(SELECT 1 FROM event_restriction r WHERE r.event_id = e.id) AS restricted";
    private static final String EVENT_COLUMNS_BARE =
            "id, station_id, name, description, event_type, day_of_week, start_time, end_time, template_id, requires_registration, registration_deadline, requires_confirmation, category_id, restriction_mode, EXISTS(SELECT 1 FROM event_restriction r WHERE r.event_id = id) AS restricted";

    // -- Events --

    /**
     * Retrieves all events for a station, ordered by event type and name.
     * No restriction filtering — used internally and for managers.
     *
     * @param stationId the station ID
     * @return the list of station events
     */
    public List<StationEvent> findByStation(int stationId) {
        return Query.query("SELECT " + EVENT_COLUMNS
                        + " FROM station_event e WHERE e.station_id = :station_id ORDER BY e.event_type, e.name;")
                .single(Call.of().bind("station_id", stationId))
                .map(StationEvent.map())
                .all();
    }

    /**
     * Retrieves events for a station that the given member is allowed to see.
     * Uses the DB restriction check function which resolves role inheritance, mode, and manager bypass.
     *
     * @param stationId the station ID
     * @param memberId  the requesting member ID
     * @return the filtered list of station events
     */
    public List<StationEvent> findByStationForMember(int stationId, int memberId) {
        return Query.query("SELECT " + EVENT_COLUMNS + " FROM station_event e"
                        + " WHERE e.station_id = :station_id"
                        + " AND check_restriction('event_restriction', 'event_id', 'station_event', 'id', e.id, :member_id, 'EVENT_MANAGER')"
                        + " ORDER BY e.event_type, e.name;")
                .single(Call.of().bind("station_id", stationId).bind("member_id", memberId))
                .map(StationEvent.map())
                .all();
    }

    /**
     * Finds a station event by its ID.
     *
     * @param id the event ID
     * @return the event, if found
     */
    public Optional<StationEvent> findById(int id) {
        return Query.query("SELECT " + EVENT_COLUMNS + " FROM station_event e WHERE e.id = :id;")
                .single(Call.of().bind("id", id))
                .map(StationEvent.map())
                .first();
    }

    /**
     * Creates a new station event.
     *
     * @param stationId            the station this event belongs to
     * @param name                 the event name
     * @param description          the event description
     * @param eventType            the recurrence type
     * @param dayOfWeek            the ISO day of week for recurring events, or null
     * @param startTime            the start time
     * @param endTime              the end time
     * @param templateId           the optional attendance template ID
     * @param requiresRegistration whether registration is required
     * @param registrationDeadline the registration deadline, or null
     * @param requiresConfirmation whether registrations require manager confirmation
     * @param categoryId           the optional category ID
     * @return the created event
     */
    public StationEvent create(
            int stationId,
            String name,
            String description,
            StationEvent.EventType eventType,
            Integer dayOfWeek,
            Instant startTime,
            Instant endTime,
            Integer templateId,
            boolean requiresRegistration,
            Instant registrationDeadline,
            boolean requiresConfirmation,
            Integer categoryId) {
        return Query.query("""
                            INSERT INTO station_event(station_id, name, description, event_type, day_of_week, start_time, end_time, template_id, requires_registration, registration_deadline, requires_confirmation, category_id)
                            VALUES (:station_id, :name, :description, :event_type, :day_of_week, :start_time, :end_time, :template_id, :requires_registration, :registration_deadline, :requires_confirmation, :category_id)
                            RETURNING\s""" + EVENT_COLUMNS_BARE + ";")
                .single(Call.of()
                        .bind("station_id", stationId)
                        .bind("name", name)
                        .bind("description", description)
                        .bind("event_type", eventType.name())
                        .bind("day_of_week", dayOfWeek)
                        .bind("start_time", startTime, INSTANT_TIMESTAMP)
                        .bind("end_time", endTime, INSTANT_TIMESTAMP)
                        .bind("template_id", templateId)
                        .bind("requires_registration", requiresRegistration)
                        .bind("registration_deadline", registrationDeadline, INSTANT_TIMESTAMP)
                        .bind("requires_confirmation", requiresConfirmation)
                        .bind("category_id", categoryId))
                .map(StationEvent.map())
                .first()
                .orElseThrow();
    }

    /**
     * Updates an existing station event.
     *
     * @param id                   the event ID
     * @param name                 the new event name
     * @param description          the new description
     * @param eventType            the new recurrence type
     * @param dayOfWeek            the new day of week, or null
     * @param startTime            the new start time
     * @param endTime              the new end time
     * @param templateId           the new template ID, or null
     * @param requiresRegistration whether registration is required
     * @param registrationDeadline the new registration deadline, or null
     * @param requiresConfirmation whether registrations require confirmation
     * @param categoryId           the new category ID, or null
     * @return true if a row was updated
     */
    public boolean update(
            int id,
            String name,
            String description,
            StationEvent.EventType eventType,
            Integer dayOfWeek,
            Instant startTime,
            Instant endTime,
            Integer templateId,
            boolean requiresRegistration,
            Instant registrationDeadline,
            boolean requiresConfirmation,
            Integer categoryId) {
        return Query.query("""
                            UPDATE station_event SET
                                name = :name, description = :description, event_type = :event_type,
                                day_of_week = :day_of_week,
                                start_time = :start_time, end_time = :end_time, template_id = :template_id,
                                requires_registration = :requires_registration, registration_deadline = :registration_deadline,
                                requires_confirmation = :requires_confirmation, category_id = :category_id
                            WHERE id = :id;""")
                .single(Call.of()
                        .bind("name", name)
                        .bind("description", description)
                        .bind("event_type", eventType.name())
                        .bind("day_of_week", dayOfWeek)
                        .bind("start_time", startTime, INSTANT_TIMESTAMP)
                        .bind("end_time", endTime, INSTANT_TIMESTAMP)
                        .bind("template_id", templateId)
                        .bind("requires_registration", requiresRegistration)
                        .bind("registration_deadline", registrationDeadline, INSTANT_TIMESTAMP)
                        .bind("requires_confirmation", requiresConfirmation)
                        .bind("category_id", categoryId)
                        .bind("id", id))
                .update()
                .changed();
    }

    /**
     * Deletes a station event by ID.
     *
     * @param id the event ID
     * @return true if a row was deleted
     */
    public boolean delete(int id) {
        return Query.query("DELETE FROM station_event WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .delete()
                .changed();
    }

    // -- Breaks --

    /**
     * Retrieves all event breaks for a station, ordered by start date.
     *
     * @param stationId the station ID
     * @return the list of event breaks
     */
    public List<EventBreak> findBreaksByStation(int stationId) {
        return Query.query(
                        "SELECT id, station_id, name, start_date, end_date FROM station_event_break WHERE station_id = :station_id ORDER BY start_date;")
                .single(Call.of().bind("station_id", stationId))
                .map(EventBreak.map())
                .all();
    }

    /**
     * Finds an event break by its ID.
     *
     * @param id the break ID
     * @return the break, if found
     */
    public Optional<EventBreak> findBreakById(int id) {
        return Query.query("SELECT id, station_id, name, start_date, end_date FROM station_event_break WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .map(EventBreak.map())
                .first();
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
    public EventBreak createBreak(int stationId, String name, LocalDate startDate, LocalDate endDate) {
        return Query.query("""
                            INSERT INTO station_event_break(station_id, name, start_date, end_date)
                            VALUES (:station_id, :name, :start_date, :end_date)
                            RETURNING id, station_id, name, start_date, end_date;""")
                .single(Call.of()
                        .bind("station_id", stationId)
                        .bind("name", name)
                        .bind("start_date", startDate)
                        .bind("end_date", endDate))
                .map(EventBreak.map())
                .first()
                .orElseThrow();
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
    public boolean updateBreak(int id, String name, LocalDate startDate, LocalDate endDate) {
        return Query.query(
                        "UPDATE station_event_break SET name = :name, start_date = :start_date, end_date = :end_date WHERE id = :id;")
                .single(Call.of()
                        .bind("name", name)
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
    public boolean deleteBreak(int id) {
        return Query.query("DELETE FROM station_event_break WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .delete()
                .changed();
    }

    // -- Categories --

    /**
     * Retrieves all event categories for a station, ordered by position.
     *
     * @param id the station ID
     * @return the list of event categories
     */
    public Optional<EventCategory> findCategoryById(int id) {
        return Query.query(
                        "SELECT id, station_id, name, position, max_shown_events FROM event_category WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .map(EventCategory.map())
                .first();
    }

    public List<EventCategory> findCategoriesByStation(int stationId) {
        return Query.query(
                        "SELECT id, station_id, name, position, max_shown_events FROM event_category WHERE station_id = :station_id ORDER BY position;")
                .single(Call.of().bind("station_id", stationId))
                .map(EventCategory.map())
                .all();
    }

    /**
     * Creates a new event category.
     *
     * @param stationId the station ID
     * @param name      the category name
     * @param position  the display order position
     * @return the created category
     */
    public EventCategory createCategory(int stationId, String name, int position) {
        return Query.query(
                        "INSERT INTO event_category(station_id, name, position) VALUES(:station_id, :name, :position) RETURNING id, station_id, name, position, max_shown_events;")
                .single(Call.of()
                        .bind("station_id", stationId)
                        .bind("name", name)
                        .bind("position", position))
                .map(EventCategory.map())
                .first()
                .orElseThrow();
    }

    /**
     * Updates an existing event category.
     *
     * @param id       the category ID
     * @param name     the new category name
     * @param position the new display order position
     * @return true if a row was updated
     */
    public boolean updateCategory(int id, String name, int position, Integer maxShownEvents) {
        return Query.query(
                        "UPDATE event_category SET name = :name, position = :position, max_shown_events = :max_shown_events WHERE id = :id;")
                .single(Call.of()
                        .bind("name", name)
                        .bind("position", position)
                        .bind("max_shown_events", maxShownEvents)
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
    public boolean deleteCategory(int id) {
        return Query.query("DELETE FROM event_category WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .delete()
                .changed();
    }

    /**
     * Checks whether a given date falls within any break period for the station.
     *
     * @param stationId the station ID
     * @param date      the date to check
     * @return true if the date is within a break
     */
    public boolean isDateInBreak(int stationId, LocalDate date) {
        return Query.query(
                        "SELECT 1 FROM station_event_break WHERE station_id = :station_id AND start_date <= :date AND end_date >= :date LIMIT 1;")
                .single(Call.of().bind("station_id", stationId).bind("date", date))
                .map(row -> true)
                .first()
                .isPresent();
    }

    // -- Restrictions --

    /**
     * Updates the restriction mode for an event.
     *
     * @param eventId the event ID
     * @param mode    the restriction mode
     * @return true if a row was updated
     */
    public boolean updateRestrictionMode(int eventId, RestrictionMode mode) {
        return Query.query("UPDATE station_event SET restriction_mode = :mode WHERE id = :id;")
                .single(Call.of().bind("mode", mode.name()).bind("id", eventId))
                .update()
                .changed();
    }

    // -- Field Defaults --

    /**
     * Retrieves all field default configurations for an event.
     *
     * @param eventId the event ID
     * @return the list of field defaults
     */
    public List<EventFieldDefault> findFieldDefaults(int eventId) {
        return Query.query(
                        "SELECT event_id, field_id, source, value FROM event_field_default WHERE event_id = :event_id;")
                .single(Call.of().bind("event_id", eventId))
                .map(EventFieldDefault.map())
                .all();
    }

    /**
     * Replaces all field defaults for an event by deleting existing ones and inserting the given defaults.
     *
     * @param eventId  the event ID
     * @param defaults the new field default configurations
     */
    public void setFieldDefaults(int eventId, List<EventFieldDefault> defaults) {
        Query.query("DELETE FROM event_field_default WHERE event_id = :event_id;")
                .single(Call.of().bind("event_id", eventId))
                .delete();
        for (var def : defaults) {
            Query.query(
                            "INSERT INTO event_field_default(event_id, field_id, source, value) VALUES(:event_id, :field_id, :source, :value);")
                    .single(Call.of()
                            .bind("event_id", eventId)
                            .bind("field_id", def.fieldId())
                            .bind("source", def.source())
                            .bind("value", def.value()))
                    .insert();
        }
    }

    // -- Registrations --

    /**
     * Retrieves all registrations for an event on a specific date, ordered by creation time.
     *
     * @param eventId   the event ID
     * @param eventDate the specific occurrence date
     * @return the list of registrations
     */
    public List<EventRegistration> findRegistrations(int eventId, LocalDate eventDate) {
        return Query.query(
                        "SELECT id, event_id, member_id, event_date, status, created_at, created_by FROM event_registration WHERE event_id = :event_id AND event_date = :event_date ORDER BY created_at;")
                .single(Call.of().bind("event_id", eventId).bind("event_date", eventDate))
                .map(EventRegistration.map())
                .all();
    }

    /**
     * Retrieves all registrations for an event across all dates, ordered by date descending, then status and creation time.
     *
     * @param eventId the event ID
     * @return the list of registrations
     */
    public List<EventRegistration> findAllRegistrations(int eventId) {
        return Query.query(
                        "SELECT id, event_id, member_id, event_date, status, created_at, created_by FROM event_registration WHERE event_id = :event_id ORDER BY event_date DESC, status, created_at;")
                .single(Call.of().bind("event_id", eventId))
                .map(EventRegistration.map())
                .all();
    }

    /**
     * Retrieves all pending registrations for events in a station.
     *
     * @param stationId the station ID
     * @return the list of pending registrations
     */
    public List<EventRegistration> findPendingRegistrationsByStation(int stationId) {
        return Query.query("""
                            SELECT er.id, er.event_id, er.member_id, er.event_date, er.status, er.created_at, er.created_by
                            FROM event_registration er
                                JOIN station_event se ON er.event_id = se.id
                            WHERE se.station_id = :station_id AND er.status = 'PENDING'
                            ORDER BY er.event_date, er.created_at;""")
                .single(Call.of().bind("station_id", stationId))
                .map(EventRegistration.map())
                .all();
    }

    /**
     * Retrieves all registrations for a specific member, ordered by event date.
     *
     * @param memberId the member ID
     * @return the list of registrations
     */
    public List<EventRegistration> findRegistrationsByMember(int memberId) {
        return Query.query(
                        "SELECT id, event_id, member_id, event_date, status, created_at, created_by FROM event_registration WHERE member_id = :member_id ORDER BY event_date;")
                .single(Call.of().bind("member_id", memberId))
                .map(EventRegistration.map())
                .all();
    }

    /**
     * Creates a registration with PENDING status and no creator.
     *
     * @param eventId   the event ID
     * @param memberId  the member ID
     * @param eventDate the event occurrence date
     * @return the created registration
     */
    public EventRegistration createRegistration(int eventId, int memberId, LocalDate eventDate) {
        return createRegistration(eventId, memberId, eventDate, EventRegistration.RegistrationStatus.PENDING, null);
    }

    /**
     * Creates or upserts a registration with the specified status. On conflict (same event, member, and date),
     * the existing registration is updated with the new status and creator.
     *
     * @param eventId   the event ID
     * @param memberId  the member ID
     * @param eventDate the event occurrence date
     * @param status    the registration status
     * @param createdBy the member ID of the creator, or null if self-registered
     * @return the created or updated registration
     */
    public EventRegistration createRegistration(
            int eventId,
            int memberId,
            LocalDate eventDate,
            EventRegistration.RegistrationStatus status,
            Integer createdBy) {
        return Query.query("""
                            INSERT INTO event_registration(event_id, member_id, event_date, status, created_by)
                            VALUES (:event_id, :member_id, :event_date, :status, :created_by)
                            ON CONFLICT (event_id, member_id, event_date) DO UPDATE SET status = :status, created_at = now(), created_by = :created_by
                            RETURNING id, event_id, member_id, event_date, status, created_at, created_by;""")
                .single(Call.of()
                        .bind("event_id", eventId)
                        .bind("member_id", memberId)
                        .bind("event_date", eventDate)
                        .bind("status", status.name())
                        .bind("created_by", createdBy))
                .map(EventRegistration.map())
                .first()
                .orElseThrow();
    }

    /**
     * Returns registration counts by status for all events of a station on their relevant dates.
     */
    public List<RegistrationCount> findRegistrationCounts(int stationId) {
        return Query.query("""
                            SELECT er.event_id, er.event_date, er.status, count(*) AS count
                            FROM event_registration er
                            JOIN station_event se ON se.id = er.event_id
                            WHERE se.station_id = :station_id
                            GROUP BY er.event_id, er.event_date, er.status
                            ORDER BY er.event_id, er.event_date;""")
                .single(Call.of().bind("station_id", stationId))
                .map(row -> new RegistrationCount(
                        row.getInt("event_id"),
                        row.getObject("event_date", LocalDate.class),
                        row.getString("status"),
                        row.getInt("count")))
                .all();
    }

    /**
     * Find declined member IDs for a specific event and date.
     */
    public List<Integer> findDeclinedMemberIds(int eventId, LocalDate eventDate) {
        return Query.query("""
                            SELECT member_id FROM event_registration
                            WHERE event_id = :event_id AND event_date = :event_date AND status = 'DECLINED';""")
                .single(Call.of().bind("event_id", eventId).bind("event_date", eventDate))
                .map(row -> row.getInt("member_id"))
                .all();
    }

    public List<MemberRegistrationStats> findRegistrationStatsByEvent(int eventId, Integer categoryId, int months) {
        return Query.query("""
                        SELECT er.member_id,
                               count(*)                                              AS registered,
                               count(*) FILTER (WHERE er.status = 'ACCEPTED')        AS accepted,
                               count(*) FILTER (WHERE er.status = 'DENIED')          AS denied,
                               count(*) FILTER (WHERE er.status = 'DECLINED')        AS declined,
                               max(er.event_date) FILTER (WHERE er.status = 'DENIED') AS last_denied
                        FROM event_registration er
                        JOIN station_event se ON se.id = er.event_id
                        WHERE se.station_id = (SELECT station_id FROM station_event WHERE id = :event_id)
                          AND (:category_id IS NULL OR se.category_id = :category_id)
                          AND er.event_date >= (current_date - make_interval(months => :months))
                          AND er.member_id IN (SELECT member_id FROM event_registration WHERE event_id = :event_id)
                        GROUP BY er.member_id;""")
                .single(Call.of()
                        .bind("event_id", eventId)
                        .bind("category_id", categoryId)
                        .bind("months", months))
                .map(MemberRegistrationStats.map())
                .all();
    }

    /**
     * Updates the status of a registration.
     *
     * @param id     the registration ID
     * @param status the new status
     * @return true if a row was updated
     */
    public boolean updateRegistrationStatus(int id, EventRegistration.RegistrationStatus status) {
        return Query.query("UPDATE event_registration SET status = :status WHERE id = :id;")
                .single(Call.of().bind("status", status.name()).bind("id", id))
                .update()
                .changed();
    }

    /**
     * Deletes a registration by ID.
     *
     * @param id the registration ID
     * @return true if a row was deleted
     */
    public boolean deleteRegistration(int id) {
        return Query.query("DELETE FROM event_registration WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .delete()
                .changed();
    }

    /**
     * Finds a registration by its ID.
     *
     * @param id the registration ID
     * @return the registration, if found
     */
    public Optional<EventRegistration> findRegistrationById(int id) {
        return Query.query(
                        "SELECT id, event_id, member_id, event_date, status, created_at, created_by FROM event_registration WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .map(EventRegistration.map())
                .first();
    }

    /**
     * Aggregated registration count for an event on a specific date with a given status.
     *
     * @param eventId   the event ID
     * @param eventDate the event occurrence date
     * @param status    the registration status name
     * @param count     the number of registrations
     */
    public record RegistrationCount(int eventId, LocalDate eventDate, String status, int count) {}
}
