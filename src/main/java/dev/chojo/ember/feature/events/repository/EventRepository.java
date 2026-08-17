/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.repository;

import de.chojo.sadu.postgresql.types.PostgreSqlTypes;
import de.chojo.sadu.queries.converter.StandardValueConverter;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import dev.chojo.ember.feature.restriction.RestrictionSql;
import dev.chojo.ember.feature.restriction.RestrictionType;
import dev.chojo.ember.util.sql.SqlSupport;
import dev.chojo.ember.util.sql.WhereBuilder;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * Repository for the {@code station_event} table: event definitions, their visibility filtering,
 * cancellation state and the scheduler reads that drive deadline, threshold and reminder handling.
 */
@Singleton
public class EventRepository {

    private static final String EVENT_COLUMNS =
            "id, station_id, name, description, event_type, day_of_week, start_time, end_time, template_id, requires_registration, registration_deadline, requires_confirmation, category_id, restriction_mode, \"public\", registration_limit, cancelled, cancelled_at, cancel_reason, min_registrations, threshold_date, threshold_notified, registration_close_days";
    private static final String EVENT_RESTRICTED_COLUMN = RestrictionSql.restrictedFlag(RestrictionType.EVENT, "e.id");
    private static final String EVENT_RESTRICTED_COLUMN_BARE =
            RestrictionSql.restrictedFlag(RestrictionType.EVENT, "id");
    private static final String EVENT_VISIBLE_FOR_MEMBER =
            RestrictionSql.visibleFor(RestrictionType.EVENT, "e.id", ":member_id");
    private static final String EVENT_MEMBER_PREDICATE = "AND " + EVENT_VISIBLE_FOR_MEMBER;

    /**
     * Retrieves all events for a station, ordered by event type and name.
     * No restriction filtering - used internally and for managers.
     *
     * @param stationId the station ID
     * @return the list of station events
     */
    public List<StationEvent> findByStation(int stationId) {
        return query("""
                SELECT %s, %s
                FROM station_event e
                WHERE e.station_id = :station_id
                ORDER BY e.event_type, e.name;""", SqlSupport.alias("e", EVENT_COLUMNS), EVENT_RESTRICTED_COLUMN)
                .single(call().bind("station_id", stationId))
                .map(StationEvent.map())
                .all();
    }

    /**
     * Bulk-resolves the public UUIDs for a set of event ids belonging to a single station. Used by
     * the public-events list endpoint to expose the {@code public_uid} column without bloating
     * every {@code new StationEvent(...)} call site.
     */
    public Map<Integer, UUID> findPublicUidsByIds(int stationId, Collection<Integer> ids) {
        if (ids.isEmpty()) return Map.of();
        var result = new HashMap<Integer, UUID>();
        query("""
                SELECT id, public_uid
                FROM station_event
                WHERE station_id = :station_id
                  AND id = ANY(:ids);""")
                .single(call().bind("station_id", stationId).bind("ids", ids, PostgreSqlTypes.INTEGER))
                .map(row -> Map.entry(row.getInt("id"), row.get("public_uid", StandardValueConverter.UUID_STRING)))
                .all()
                .forEach(e -> result.put(e.getKey(), e.getValue()));
        return result;
    }

    /**
     * Resolves a single station event by its public UUID. Used by the cell renderers to look up
     * the event a FEATURED_EVENT / PAST_EVENT_RECAP cell references.
     */
    public Optional<StationEvent> findByPublicUid(int stationId, UUID publicUid) {
        return query("""
                SELECT %s, %s
                FROM station_event e
                WHERE e.station_id = :station_id
                  AND e.public_uid = :public_uid::uuid;""", SqlSupport.alias("e", EVENT_COLUMNS), EVENT_RESTRICTED_COLUMN)
                .single(call().bind("station_id", stationId)
                        .bind("public_uid", publicUid, StandardValueConverter.UUID_STRING))
                .map(StationEvent.map())
                .first();
    }

    /**
     * Editor's event picker. Returns a compact public shape - UUID, name, start
     * time, category name - for the supplied station's events. {@code mode} filters by start time
     * (FUTURE/PAST/ALL). {@code search} is a case-insensitive substring match on the event name.
     * Only events that resolve as public are returned (per-event {@code public = TRUE} or
     * inherited from a public category).
     */
    public List<PickerEvent> searchForPicker(int stationId, String search, PickerMode mode, int limit) {
        String timePredicate =
                switch (mode) {
                    case FUTURE -> "AND e.start_time > NOW()";
                    case PAST -> "AND e.end_time < NOW()";
                    case ALL -> "";
                };
        String order = mode == PickerMode.PAST ? "e.start_time DESC" : "e.start_time ASC";
        var where = WhereBuilder.create()
                .like("AND LOWER(e.name) LIKE :q", "q", search)
                .add(timePredicate);
        return query("""
                SELECT e.public_uid, e.name, e.start_time, c.name AS category_name
                FROM station_event e
                LEFT JOIN event_category c ON c.id = e.category_id
                WHERE e.station_id = :station_id
                  AND (e.public = TRUE OR (e.public IS NULL AND c.public = TRUE))
                  %s
                ORDER BY %s
                LIMIT :limit;""", where.fragment(), order)
                .single(where.apply(call().bind("station_id", stationId).bind("limit", limit)))
                .map(row -> new PickerEvent(
                        row.get("public_uid", StandardValueConverter.UUID_STRING),
                        row.getString("name"),
                        row.get("start_time", INSTANT_TIMESTAMP),
                        row.getString("category_name")))
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
        return query("""
                SELECT %s, %s
                FROM station_event e
                WHERE e.station_id = :station_id
                  AND %s
                ORDER BY e.event_type, e.name;""", SqlSupport.alias("e", EVENT_COLUMNS), EVENT_RESTRICTED_COLUMN, EVENT_VISIBLE_FOR_MEMBER)
                .single(call().bind("station_id", stationId).bind("member_id", memberId))
                .map(StationEvent.map())
                .all();
    }

    /**
     * Retrieves a station's events narrowed by any combination of member visibility, category and
     * registration requirement. Absent filters widen the result rather than restricting it.
     */
    public List<StationEvent> findFiltered(
            int stationId, Integer memberId, Integer categoryId, Boolean requiresRegistration) {
        var where = WhereBuilder.create()
                .add("AND " + EVENT_VISIBLE_FOR_MEMBER, "member_id", memberId)
                .add("AND e.category_id = :category_id", "category_id", categoryId)
                .add(
                        "AND e.requires_registration = :requires_registration",
                        "requires_registration",
                        requiresRegistration);
        return query("""
                SELECT %s, %s
                FROM station_event e
                WHERE e.station_id = :station_id
                  %s
                ORDER BY e.event_type, e.name;""", SqlSupport.alias("e", EVENT_COLUMNS), EVENT_RESTRICTED_COLUMN, where.fragment())
                .single(where.apply(call().bind("station_id", stationId)))
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
        return query("""
                SELECT %s, %s
                FROM station_event e
                WHERE e.id = :id;""", SqlSupport.alias("e", EVENT_COLUMNS), EVENT_RESTRICTED_COLUMN)
                .single(call().bind("id", id))
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
            Integer categoryId,
            Integer registrationLimit,
            Integer minRegistrations,
            Instant thresholdDate,
            Integer registrationCloseDays) {
        return SqlSupport.insertReturning(
                """
                INSERT INTO station_event(station_id, name, description, event_type, day_of_week, start_time, end_time, template_id, requires_registration, registration_deadline, requires_confirmation, category_id, registration_limit, min_registrations, threshold_date, registration_close_days)
                VALUES (:station_id, :name, :description, :event_type, :day_of_week, :start_time, :end_time, :template_id, :requires_registration, :registration_deadline, :requires_confirmation, :category_id, :registration_limit, :min_registrations, :threshold_date, :registration_close_days)
                RETURNING %s, %s;""",
                call().bind("station_id", stationId)
                        .bind("name", name)
                        .bind("description", description)
                        .bind("event_type", eventType)
                        .bind("day_of_week", dayOfWeek)
                        .bind("start_time", startTime, INSTANT_TIMESTAMP)
                        .bind("end_time", endTime, INSTANT_TIMESTAMP)
                        .bind("template_id", templateId)
                        .bind("requires_registration", requiresRegistration)
                        .bind("registration_deadline", registrationDeadline, INSTANT_TIMESTAMP)
                        .bind("requires_confirmation", requiresConfirmation)
                        .bind("category_id", categoryId)
                        .bind("registration_limit", registrationLimit)
                        .bind("min_registrations", minRegistrations)
                        .bind("threshold_date", thresholdDate, INSTANT_TIMESTAMP)
                        .bind("registration_close_days", registrationCloseDays),
                StationEvent.map(),
                EVENT_COLUMNS,
                EVENT_RESTRICTED_COLUMN_BARE);
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
            Integer categoryId,
            Boolean isPublic,
            Integer registrationLimit,
            Integer minRegistrations,
            Instant thresholdDate,
            Integer registrationCloseDays) {
        return query("""
                UPDATE station_event
                SET
                    name                    = :name,
                    description             = :description,
                    event_type              = :event_type,
                    day_of_week             = :day_of_week,
                    start_time              = :start_time,
                    end_time                = :end_time,
                    template_id             = :template_id,
                    requires_registration   = :requires_registration,
                    registration_deadline   = :registration_deadline,
                    requires_confirmation   = :requires_confirmation,
                    category_id             = :category_id,
                    public                  = :public,
                    registration_limit      = :registration_limit,
                    min_registrations       = :min_registrations,
                    threshold_date          = :threshold_date,
                    registration_close_days = :registration_close_days,
                    updated_at              = now()
                WHERE id = :id;""")
                .single(call().bind("name", name)
                        .bind("description", description)
                        .bind("event_type", eventType)
                        .bind("day_of_week", dayOfWeek)
                        .bind("start_time", startTime, INSTANT_TIMESTAMP)
                        .bind("end_time", endTime, INSTANT_TIMESTAMP)
                        .bind("template_id", templateId)
                        .bind("requires_registration", requiresRegistration)
                        .bind("registration_deadline", registrationDeadline, INSTANT_TIMESTAMP)
                        .bind("requires_confirmation", requiresConfirmation)
                        .bind("category_id", categoryId)
                        .bind("public", isPublic)
                        .bind("registration_limit", registrationLimit)
                        .bind("min_registrations", minRegistrations)
                        .bind("threshold_date", thresholdDate, INSTANT_TIMESTAMP)
                        .bind("registration_close_days", registrationCloseDays)
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
        return SqlSupport.deleteById("station_event", id);
    }

    /**
     * Updates the restriction mode for an event.
     *
     * @param eventId the event ID
     * @param mode    the restriction mode
     * @return true if a row was updated
     */
    public boolean updateRestrictionMode(int eventId, RestrictionMode mode) {
        return query("""
                UPDATE station_event
                SET
                    restriction_mode = :mode,
                    updated_at       = now()
                WHERE id = :id;""")
                .single(call().bind("mode", mode).bind("id", eventId))
                .update()
                .changed();
    }

    /**
     * Finds one-time events whose registration deadline has passed while pending registrations
     * remain and no deadline notification has gone out yet.
     */
    public List<ExpiredDeadlineEvent> findOneTimeEventsWithExpiredDeadline() {
        return query("""
                SELECT
                    e.id         AS event_id,
                    e.station_id,
                    e.name,
                    count(er.id) AS pending_count
                FROM
                    station_event e
                        JOIN event_registration er
                        ON er.event_id = e.id AND er.status = 'PENDING'
                WHERE e.requires_registration
                  AND e.event_type = 'ONE_TIME'
                  AND e.registration_deadline IS NOT NULL
                  AND e.registration_deadline < now()
                  AND e.deadline_notified = FALSE
                  AND e.cancelled = FALSE
                GROUP BY e.id, e.station_id, e.name;""")
                .single(call())
                .map(row -> new ExpiredDeadlineEvent(
                        row.getInt("event_id"),
                        row.getInt("station_id"),
                        row.getString("name"),
                        row.getInt("pending_count")))
                .all();
    }

    /**
     * Finds active recurring events that close their registration a fixed number of days before
     * each occurrence.
     */
    public List<StationEvent> findRecurringEventsWithCloseDays() {
        return query("""
                SELECT %s, %s
                FROM
                    station_event e
                WHERE e.requires_registration
                  AND e.registration_close_days IS NOT NULL
                  AND e.event_type != 'ONE_TIME'
                  AND e.cancelled = FALSE;""", SqlSupport.alias("e", EVENT_COLUMNS), EVENT_RESTRICTED_COLUMN)
                .single(call())
                .map(StationEvent.map())
                .all();
    }

    /**
     * Marks an event's registration deadline as notified, preventing duplicate warnings.
     *
     * @param eventId the event ID
     */
    public void markDeadlineNotified(int eventId) {
        query("UPDATE station_event SET deadline_notified = TRUE, updated_at = now() WHERE id = :id;")
                .single(call().bind("id", eventId))
                .update();
    }

    /**
     * Returns the most recent {@code updated_at} across all events of a station, or
     * {@link Instant#EPOCH} when the station has no events. Used to derive feed ETags so the
     * cached iCal feed picks up event mutations.
     */
    public Instant findMaxEventUpdatedAt(int stationId) {
        return query("SELECT max(updated_at) AS m FROM station_event WHERE station_id = :station_id;")
                .single(call().bind("station_id", stationId))
                .map(row -> row.get("m", INSTANT_TIMESTAMP))
                .first()
                .orElse(Instant.EPOCH);
    }

    /**
     * Cancels an event, recording the current timestamp and an optional reason.
     *
     * @param id     the event ID
     * @param reason optional cancellation reason
     * @return true if a row was updated
     */
    public boolean cancelEvent(int id, String reason) {
        return query("""
                UPDATE station_event
                SET cancelled = TRUE,
                    cancelled_at = now(),
                    cancel_reason = :reason,
                    updated_at = now()
                WHERE id = :id;""")
                .single(call().bind("id", id).bind("reason", reason))
                .update()
                .changed();
    }

    /**
     * Finds events that should be auto-cancelled because their threshold date has passed
     * and they have not reached the minimum number of accepted registrations.
     *
     * @return the list of events to auto-cancel
     */
    public List<StationEvent> findAutoCancel() {
        return query("""
                SELECT %s, %s
                FROM station_event e
                WHERE e.cancelled = FALSE
                  AND e.min_registrations IS NOT NULL
                  AND e.threshold_date IS NOT NULL
                  AND e.threshold_date <= now()
                  AND (SELECT count(*) FROM event_registration er
                       WHERE er.event_id = e.id AND er.status = 'ACCEPTED') < e.min_registrations;""", SqlSupport.alias("e", EVENT_COLUMNS), EVENT_RESTRICTED_COLUMN)
                .single(call())
                .map(StationEvent.map())
                .all();
    }

    /**
     * Marks an event's threshold as notified, preventing duplicate warnings.
     *
     * @param eventId the event ID
     * @return true if a row was updated
     */
    public boolean setThresholdNotified(int eventId) {
        return query("UPDATE station_event SET threshold_notified = TRUE, updated_at = now() WHERE id = :id;")
                .single(call().bind("id", eventId))
                .update()
                .changed();
    }

    /**
     * Finds all active events that have at least one reminder configured.
     */
    public List<StationEvent> findEventsWithReminders() {
        return query("""
                SELECT %s, %s
                FROM
                    station_event e
                WHERE e.cancelled = FALSE
                  AND exists (
                    SELECT 1
                    FROM event_reminder er
                    WHERE er.event_id = e.id);""", SqlSupport.alias("e", EVENT_COLUMNS), EVENT_RESTRICTED_COLUMN)
                .single(call())
                .map(StationEvent.map())
                .all();
    }

    /**
     * Returns {@code true} if the station has at least one event. Used by the setup wizard's
     * status endpoint to mark the "first event" optional step complete.
     */
    public boolean existsForStation(int stationId) {
        return SqlSupport.exists(
                "SELECT 1 FROM station_event WHERE station_id = :station_id LIMIT 1;",
                call().bind("station_id", stationId));
    }

    /**
     * Time-window filter for the event picker.
     */
    public enum PickerMode {
        FUTURE,
        PAST,
        ALL
    }

    /**
     * Lightweight picker result row. Exposes only the public UUID - never the internal id.
     */
    public record PickerEvent(UUID eventUid, String name, Instant startTime, String categoryName) {}

    /**
     * A one-time event whose registration deadline has passed with pending registrations left.
     */
    public record ExpiredDeadlineEvent(int eventId, int stationId, String name, int pendingCount) {}
}
