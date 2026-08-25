/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.repository;

import de.chojo.sadu.postgresql.types.PostgreSqlTypes;
import dev.chojo.ember.feature.events.entity.EventRegistration;
import dev.chojo.ember.feature.events.entity.MemberRegistrationStats;
import dev.chojo.ember.feature.events.entity.RegistrationCount;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.util.sql.SqlSupport;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * Repository for the {@code event_registration} table: who signed up for which occurrence of an
 * event, in which state, and the aggregates the registration screens and fairness ranking read.
 */
@Singleton
public class EventRegistrationRepository {

    private static final String COLUMNS = "id, event_id, member_id, event_date, status, created_at, created_by";

    /**
     * Retrieves all registrations for an event on a specific date, ordered by creation time.
     *
     * @param eventId   the event ID
     * @param eventDate the specific occurrence date
     * @return the list of registrations
     */
    public List<EventRegistration> findByEventAndDate(int eventId, LocalDate eventDate) {
        return query("""
                SELECT %s
                FROM event_registration
                WHERE event_id = :event_id
                  AND event_date = :event_date
                ORDER BY created_at;""", COLUMNS)
                .single(call().bind("event_id", eventId).bind("event_date", eventDate))
                .map(EventRegistration.map())
                .all();
    }

    /**
     * Retrieves all registrations for an event across all dates, ordered by date descending, then status and creation time.
     *
     * @param eventId the event ID
     * @return the list of registrations
     */
    public List<EventRegistration> findByEvent(int eventId) {
        return query("""
                SELECT %s
                FROM event_registration
                WHERE event_id = :event_id
                ORDER BY event_date DESC, status, created_at;""", COLUMNS)
                .single(call().bind("event_id", eventId))
                .map(EventRegistration.map())
                .all();
    }

    /**
     * Retrieves the pending registrations of a single occurrence.
     *
     * @param eventId   the event ID
     * @param eventDate the occurrence date
     * @return the list of pending registrations
     */
    public List<EventRegistration> findPendingByEventAndDate(int eventId, LocalDate eventDate) {
        return query("""
                SELECT %s
                FROM event_registration
                WHERE event_id = :event_id
                  AND event_date = :event_date
                  AND status = 'PENDING'
                ORDER BY created_at;""", COLUMNS)
                .single(call().bind("event_id", eventId).bind("event_date", eventDate))
                .map(EventRegistration.map())
                .all();
    }

    /**
     * Retrieves all pending registrations for a specific event.
     *
     * @param eventId the event ID
     * @return the list of pending registrations
     */
    public List<EventRegistration> findPendingByEvent(int eventId) {
        return query("""
                SELECT %s
                FROM event_registration
                WHERE event_id = :event_id
                  AND status = 'PENDING'
                ORDER BY created_at;""", COLUMNS)
                .single(call().bind("event_id", eventId))
                .map(EventRegistration.map())
                .all();
    }

    /**
     * Retrieves all pending registrations across a station's events.
     *
     * @param stationId the station ID
     * @return the list of pending registrations
     */
    public List<EventRegistration> findPendingByStation(int stationId) {
        return query("""
                SELECT %s
                FROM event_registration er
                    JOIN station_event se ON er.event_id = se.id
                WHERE se.station_id = :station_id AND er.status = 'PENDING'
                ORDER BY er.event_date, er.created_at;""", SqlSupport.alias("er", COLUMNS))
                .single(call().bind("station_id", stationId))
                .map(EventRegistration.map())
                .all();
    }

    /**
     * Retrieves all upcoming registrations for a specific member, ordered by event date.
     *
     * @param memberId the member ID
     * @return the list of registrations
     */
    public List<EventRegistration> findByMember(int memberId) {
        return query("""
                SELECT %s
                FROM event_registration
                WHERE member_id = :member_id
                  AND ( event_date IS NULL OR event_date >= current_date )
                ORDER BY event_date;""", COLUMNS)
                .single(call().bind("member_id", memberId))
                .map(EventRegistration.map())
                .all();
    }

    /**
     * Retrieves upcoming registrations for any member in the given collection in a single query,
     * avoiding N+1 lookups when fanning out across a guardian and their managed members.
     *
     * @param memberIds the member IDs to fetch registrations for
     * @return the list of registrations, ordered by event date
     */
    public List<EventRegistration> findByMembers(Collection<Integer> memberIds) {
        if (memberIds.isEmpty()) return List.of();
        return query("""
                SELECT %s
                FROM event_registration
                WHERE member_id = ANY ( :member_ids )
                  AND ( event_date IS NULL OR event_date >= current_date )
                ORDER BY event_date;""", COLUMNS)
                .single(call().bind("member_ids", List.copyOf(memberIds), PostgreSqlTypes.INTEGER))
                .map(EventRegistration.map())
                .all();
    }

    /**
     * Returns the most recent {@code created_at} across the given members' registrations.
     * The registration upsert bumps {@code created_at} on every status change, so this acts as
     * a freshness signal for the iCal feed's per-member visibility filtering.
     */
    public Instant findMaxCreatedAt(Collection<Integer> memberIds) {
        if (memberIds.isEmpty()) return Instant.EPOCH;
        return query("SELECT max(created_at) AS m FROM event_registration WHERE member_id = ANY(:member_ids);")
                .single(call().bind("member_ids", List.copyOf(memberIds), PostgreSqlTypes.INTEGER))
                .map(row -> row.get("m", INSTANT_TIMESTAMP))
                .first()
                .orElse(Instant.EPOCH);
    }

    /**
     * Creates a registration with PENDING status and no creator.
     *
     * @param eventId   the event ID
     * @param memberId  the member ID
     * @param eventDate the event occurrence date
     * @return the created registration
     */
    public EventRegistration create(int eventId, int memberId, LocalDate eventDate) {
        return create(eventId, memberId, eventDate, RegistrationStatus.PENDING, null);
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
    public EventRegistration create(
            int eventId, int memberId, LocalDate eventDate, RegistrationStatus status, Integer createdBy) {
        return SqlSupport.insertReturning(
                """
                INSERT
                INTO
                    event_registration(event_id, member_id, event_date, status, created_by)
                VALUES
                    (:event_id, :member_id, :event_date, :status, :created_by)
                ON CONFLICT (event_id, member_id, event_date)
                    DO UPDATE
                    SET
                        status     = :status,
                        created_at = now(),
                        created_by = :created_by
                RETURNING %s;""",
                call().bind("event_id", eventId)
                        .bind("member_id", memberId)
                        .bind("event_date", eventDate)
                        .bind("status", status)
                        .bind("created_by", createdBy),
                EventRegistration.map(),
                COLUMNS);
    }

    /**
     * Returns registration counts by status for all events of a station on their relevant dates.
     */
    public List<RegistrationCount> findCountsByStation(int stationId) {
        return query("""
                SELECT er.event_id, er.event_date, er.status, count(*) AS count
                FROM event_registration er
                JOIN station_event se ON se.id = er.event_id
                WHERE se.station_id = :station_id
                GROUP BY er.event_id, er.event_date, er.status
                ORDER BY er.event_id, er.event_date;""")
                .single(call().bind("station_id", stationId))
                .map(row -> new RegistrationCount(
                        row.getInt("event_id"),
                        row.getObject("event_date", LocalDate.class),
                        row.getEnum("status", RegistrationStatus.class),
                        row.getInt("count")))
                .all();
    }

    /**
     * Find declined member IDs for a specific event and date.
     */
    public List<Integer> findDeclinedMemberIds(int eventId, LocalDate eventDate) {
        return query("""
                SELECT member_id FROM event_registration
                WHERE event_id = :event_id AND event_date = :event_date AND status = 'DECLINED';""")
                .single(call().bind("event_id", eventId).bind("event_date", eventDate))
                .map(row -> row.getInt("member_id"))
                .all();
    }

    /**
     * Aggregates the accept/deny history of everyone registered for an event, over the last
     * {@code months} months and optionally narrowed to one category, so the registration screen
     * can rank members by how often they were turned down.
     */
    public List<MemberRegistrationStats> findStatsByEvent(int eventId, Integer categoryId, int months) {
        return query("""
                SELECT
                    er.member_id,
                    count(*)                                               AS registered,
                    count(*) FILTER (WHERE er.status = 'ACCEPTED')         AS accepted,
                    count(*) FILTER (WHERE er.status = 'DENIED')           AS denied,
                    count(*) FILTER (WHERE er.status = 'DECLINED')         AS declined,
                    max(er.event_date) FILTER (WHERE er.status = 'DENIED') AS last_denied
                FROM
                    event_registration er
                        JOIN station_event se
                        ON se.id = er.event_id
                WHERE se.station_id = (
                    SELECT station_id
                    FROM station_event
                    WHERE id = :event_id
                                      )
                  AND ( :category_id IS NULL OR se.category_id = :category_id )
                  AND er.event_date >= ( current_date - make_interval(months => :months) )
                  AND er.member_id IN (
                    SELECT member_id
                    FROM event_registration
                    WHERE event_id = :event_id
                                      )
                GROUP BY er.member_id;""")
                .single(call().bind("event_id", eventId)
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
    public boolean updateStatus(int id, RegistrationStatus status) {
        return query("UPDATE event_registration SET status = :status WHERE id = :id;")
                .single(call().bind("status", status).bind("id", id))
                .update()
                .changed();
    }

    /**
     * Deletes a registration by ID.
     *
     * @param id the registration ID
     * @return true if a row was deleted
     */
    public boolean delete(int id) {
        return SqlSupport.deleteById("event_registration", id);
    }

    /**
     * Finds a registration by its ID.
     *
     * @param id the registration ID
     * @return the registration, if found
     */
    public Optional<EventRegistration> findById(int id) {
        return SqlSupport.findById("event_registration", COLUMNS, id, EventRegistration.map());
    }

    /**
     * Counts accepted registrations for an event.
     *
     * @param eventId the event ID
     * @return the count of accepted registrations
     */
    public int countAccepted(int eventId) {
        return SqlSupport.count("""
                SELECT
                    count(*) AS cnt
                FROM
                    event_registration
                WHERE event_id = :id
                  AND status = 'ACCEPTED';""", call().bind("id", eventId));
    }

    /**
     * Finds member IDs with pending or accepted registrations for an event.
     *
     * @param eventId the event ID
     * @return the list of member IDs
     */
    public List<Integer> findRegisteredMemberIds(int eventId) {
        return query("""
                SELECT member_id
                FROM event_registration
                WHERE event_id = :id
                  AND status IN ('PENDING', 'ACCEPTED');""")
                .single(call().bind("id", eventId))
                .map(row -> row.getInt("member_id"))
                .all();
    }

    /**
     * The members of a station who have said nothing about an event.
     *
     * <p>Saying nothing means having no answer on record, or having taken one back: a withdrawn
     * registration leaves the event without an answer again, which is exactly the state a reminder is for.
     * Being turned down is an answer, and so is declining, so neither is asked again.
     *
     * <p>Eligibility is not decided here. It depends on the reader's rights as well as the event's
     * restrictions, so the caller filters what this returns.
     *
     * @param eventId   the event
     * @param stationId the station whose members are candidates
     * @return the member ids still owing an answer
     */
    public List<Integer> findUnansweredMemberIds(int eventId, int stationId) {
        return query("""
                SELECT sm.id AS member_id
                FROM station_member sm
                WHERE sm.station_id = :station_id
                  AND sm.former = FALSE
                  AND NOT EXISTS (SELECT 1
                                  FROM event_registration er
                                  WHERE er.event_id = :event_id
                                    AND er.member_id = sm.id
                                    AND er.status <> 'WITHDRAWN');""")
                .single(call().bind("event_id", eventId).bind("station_id", stationId))
                .map(row -> row.getInt("member_id"))
                .all();
    }

    /**
     * The events still waiting on an answer from any of the given members.
     *
     * <p>One row per event and member, because a household can owe several answers to one event and the
     * screen groups them back together. Only events whose registration is still open are listed: once the
     * deadline has passed the answer is no longer the member's to give.
     *
     * <p>Eligibility is not decided here, for the same reason it is not decided in
     * {@link #findUnansweredMemberIds}: it depends on more than this table knows. The caller filters.
     *
     * @param memberIds the reader and everyone they answer for
     * @return one entry per event and member still owing an answer, soonest deadline first
     */
    public List<AwaitingAnswer> findAwaitingAnswer(List<Integer> memberIds) {
        if (memberIds.isEmpty()) return List.of();
        return query("""
                SELECT
                    e.id AS event_id,
                    e.name,
                    e.start_time,
                    e.registration_deadline,
                    sm.id AS member_id
                FROM station_member sm
                    JOIN station_event e ON e.station_id = sm.station_id
                WHERE sm.id = ANY (:member_ids)
                  AND sm.former = FALSE
                  AND e.requires_registration
                  AND e.event_type = 'ONE_TIME'
                  AND e.cancelled = FALSE
                  AND e.registration_deadline IS NOT NULL
                  AND e.registration_deadline > now()
                  AND NOT EXISTS (SELECT 1
                                  FROM event_registration er
                                  WHERE er.event_id = e.id
                                    AND er.member_id = sm.id
                                    AND er.status <> 'WITHDRAWN')
                ORDER BY e.registration_deadline, e.id;""")
                .single(call().bind("member_ids", memberIds, PostgreSqlTypes.INTEGER))
                .map(row -> new AwaitingAnswer(
                        row.getInt("event_id"),
                        row.getString("name"),
                        row.get("start_time", INSTANT_TIMESTAMP),
                        row.get("registration_deadline", INSTANT_TIMESTAMP),
                        row.getInt("member_id")))
                .all();
    }

    /** One event still waiting on one member's answer. */
    public record AwaitingAnswer(
            int eventId, String name, Instant startTime, Instant registrationDeadline, int memberId) {}

    /**
     * Counts the pending registrations across a station's events.
     *
     * @param stationId the station ID
     * @return the number of pending registrations
     */
    public int countPendingByStation(int stationId) {
        return SqlSupport.count("""
                SELECT
                    count(*) AS cnt
                FROM
                    event_registration er
                        JOIN station_event se
                        ON er.event_id = se.id
                WHERE se.station_id = :station_id
                  AND er.status = 'PENDING';""", call().bind("station_id", stationId));
    }
}
