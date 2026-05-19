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
import dev.chojo.ember.feature.events.entity.StationEvent;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

@Singleton
public class EventRepository {

    private static final String EVENT_COLUMNS =
            "id, station_id, name, description, event_type, day_of_week, start_time, end_time, template_id, requires_registration, registration_deadline, requires_confirmation, category_id";

    // -- Events --

    public List<StationEvent> findByStation(int stationId) {
        return Query.query("SELECT " + EVENT_COLUMNS
                        + " FROM station_event WHERE station_id = :station_id ORDER BY event_type, name;")
                .single(Call.of().bind("station_id", stationId))
                .map(StationEvent.map())
                .all();
    }

    public Optional<StationEvent> findById(int id) {
        return Query.query("SELECT " + EVENT_COLUMNS + " FROM station_event WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .map(StationEvent.map())
                .first();
    }

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
                            RETURNING\s""" + EVENT_COLUMNS + ";")
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

    public boolean delete(int id) {
        return Query.query("DELETE FROM station_event WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .delete()
                .changed();
    }

    // -- Breaks --

    public List<EventBreak> findBreaksByStation(int stationId) {
        return Query.query(
                        "SELECT id, station_id, name, start_date, end_date FROM station_event_break WHERE station_id = :station_id ORDER BY start_date;")
                .single(Call.of().bind("station_id", stationId))
                .map(EventBreak.map())
                .all();
    }

    public Optional<EventBreak> findBreakById(int id) {
        return Query.query("SELECT id, station_id, name, start_date, end_date FROM station_event_break WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .map(EventBreak.map())
                .first();
    }

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

    public boolean deleteBreak(int id) {
        return Query.query("DELETE FROM station_event_break WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .delete()
                .changed();
    }

    // -- Categories --

    public List<EventCategory> findCategoriesByStation(int stationId) {
        return Query.query(
                        "SELECT id, station_id, name, position FROM event_category WHERE station_id = :station_id ORDER BY position;")
                .single(Call.of().bind("station_id", stationId))
                .map(EventCategory.map())
                .all();
    }

    public EventCategory createCategory(int stationId, String name, int position) {
        return Query.query(
                        "INSERT INTO event_category(station_id, name, position) VALUES(:station_id, :name, :position) RETURNING id, station_id, name, position;")
                .single(Call.of()
                        .bind("station_id", stationId)
                        .bind("name", name)
                        .bind("position", position))
                .map(EventCategory.map())
                .first()
                .orElseThrow();
    }

    public boolean updateCategory(int id, String name, int position) {
        return Query.query("UPDATE event_category SET name = :name, position = :position WHERE id = :id;")
                .single(Call.of().bind("name", name).bind("position", position).bind("id", id))
                .update()
                .changed();
    }

    public boolean deleteCategory(int id) {
        return Query.query("DELETE FROM event_category WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .delete()
                .changed();
    }

    public boolean isDateInBreak(int stationId, LocalDate date) {
        return Query.query(
                        "SELECT 1 FROM station_event_break WHERE station_id = :station_id AND start_date <= :date AND end_date >= :date LIMIT 1;")
                .single(Call.of().bind("station_id", stationId).bind("date", date))
                .map(row -> true)
                .first()
                .isPresent();
    }

    // -- Restrictions --

    public List<Integer> findRoleRestrictions(int eventId) {
        return Query.query("SELECT role_id FROM event_role_restriction WHERE event_id = :event_id;")
                .single(Call.of().bind("event_id", eventId))
                .map(row -> row.getInt("role_id"))
                .all();
    }

    public List<String> findRoleRestrictionNames(int eventId) {
        return Query.query("""
                            SELECT r.name FROM event_role_restriction err
                            JOIN role r ON r.id = err.role_id
                            WHERE err.event_id = :event_id;""")
                .single(Call.of().bind("event_id", eventId))
                .map(row -> row.getString("name"))
                .all();
    }

    public List<Integer> findGroupRestrictions(int eventId) {
        return Query.query("SELECT group_id FROM event_group_restriction WHERE event_id = :event_id;")
                .single(Call.of().bind("event_id", eventId))
                .map(row -> row.getInt("group_id"))
                .all();
    }

    public void setRoleRestrictions(int eventId, List<Integer> roleIds) {
        Query.query("DELETE FROM event_role_restriction WHERE event_id = :event_id;")
                .single(Call.of().bind("event_id", eventId))
                .delete();
        for (int roleId : roleIds) {
            Query.query("INSERT INTO event_role_restriction(event_id, role_id) VALUES(:event_id, :role_id);")
                    .single(Call.of().bind("event_id", eventId).bind("role_id", roleId))
                    .insert();
        }
    }

    public void setGroupRestrictions(int eventId, List<Integer> groupIds) {
        Query.query("DELETE FROM event_group_restriction WHERE event_id = :event_id;")
                .single(Call.of().bind("event_id", eventId))
                .delete();
        for (int groupId : groupIds) {
            Query.query("INSERT INTO event_group_restriction(event_id, group_id) VALUES(:event_id, :group_id);")
                    .single(Call.of().bind("event_id", eventId).bind("group_id", groupId))
                    .insert();
        }
    }

    /**
     * Find all event IDs in a station that have restrictions.
     */
    public Map<Integer, List<Integer>> findAllRoleRestrictionsByStation(int stationId) {
        var result = new HashMap<Integer, List<Integer>>();
        Query.query("""
                     SELECT err.event_id, err.role_id
                     FROM event_role_restriction err
                     JOIN station_event se ON se.id = err.event_id
                     WHERE se.station_id = :station_id;""")
                .single(Call.of().bind("station_id", stationId))
                .map(row -> new int[] {row.getInt("event_id"), row.getInt("role_id")})
                .all()
                .forEach(r ->
                        result.computeIfAbsent(r[0], k -> new ArrayList<>()).add(r[1]));
        return result;
    }

    public Map<Integer, List<Integer>> findAllGroupRestrictionsByStation(int stationId) {
        var result = new HashMap<Integer, List<Integer>>();
        Query.query("""
                     SELECT egr.event_id, egr.group_id
                     FROM event_group_restriction egr
                     JOIN station_event se ON se.id = egr.event_id
                     WHERE se.station_id = :station_id;""")
                .single(Call.of().bind("station_id", stationId))
                .map(row -> new int[] {row.getInt("event_id"), row.getInt("group_id")})
                .all()
                .forEach(r ->
                        result.computeIfAbsent(r[0], k -> new ArrayList<>()).add(r[1]));
        return result;
    }

    // -- Field Defaults --

    public List<EventFieldDefault> findFieldDefaults(int eventId) {
        return Query.query(
                        "SELECT event_id, field_id, source, value FROM event_field_default WHERE event_id = :event_id;")
                .single(Call.of().bind("event_id", eventId))
                .map(EventFieldDefault.map())
                .all();
    }

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

    public List<EventRegistration> findRegistrations(int eventId, LocalDate eventDate) {
        return Query.query(
                        "SELECT id, event_id, member_id, event_date, status, created_at, created_by FROM event_registration WHERE event_id = :event_id AND event_date = :event_date ORDER BY created_at;")
                .single(Call.of().bind("event_id", eventId).bind("event_date", eventDate))
                .map(EventRegistration.map())
                .all();
    }

    public List<EventRegistration> findAllRegistrations(int eventId) {
        return Query.query(
                        "SELECT id, event_id, member_id, event_date, status, created_at, created_by FROM event_registration WHERE event_id = :event_id ORDER BY event_date DESC, status, created_at;")
                .single(Call.of().bind("event_id", eventId))
                .map(EventRegistration.map())
                .all();
    }

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

    public List<EventRegistration> findRegistrationsByMember(int memberId) {
        return Query.query(
                        "SELECT id, event_id, member_id, event_date, status, created_at, created_by FROM event_registration WHERE member_id = :member_id ORDER BY event_date;")
                .single(Call.of().bind("member_id", memberId))
                .map(EventRegistration.map())
                .all();
    }

    public EventRegistration createRegistration(int eventId, int memberId, LocalDate eventDate) {
        return createRegistration(eventId, memberId, eventDate, EventRegistration.RegistrationStatus.PENDING, null);
    }

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

    public boolean updateRegistrationStatus(int id, EventRegistration.RegistrationStatus status) {
        return Query.query("UPDATE event_registration SET status = :status WHERE id = :id;")
                .single(Call.of().bind("status", status.name()).bind("id", id))
                .update()
                .changed();
    }

    public boolean deleteRegistration(int id) {
        return Query.query("DELETE FROM event_registration WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .delete()
                .changed();
    }

    public Optional<EventRegistration> findRegistrationById(int id) {
        return Query.query(
                        "SELECT id, event_id, member_id, event_date, status, created_at, created_by FROM event_registration WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .map(EventRegistration.map())
                .first();
    }

    public record RegistrationCount(int eventId, LocalDate eventDate, String status, int count) {}
}
