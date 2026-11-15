/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.repository;

import de.chojo.sadu.postgresql.types.PostgreSqlTypes;
import dev.chojo.ember.feature.events.entity.EventGroupRegistration;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.util.sql.SqlSupport;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/** Sign-ups for event groups, one per member and event group. */
@Singleton
public class EventGroupRegistrationRepository {

    /**
     * A member's sign-up for an event group.
     *
     * @param eventGroupId the event group
     * @param memberId the member
     * @return the sign-up, or empty where the member never signed up
     */
    public Optional<EventGroupRegistration> find(int eventGroupId, int memberId) {
        return query(
                        "SELECT %s FROM event_group_registration WHERE event_group_id = :event_group_id AND member_id = :member_id;",
                        EventGroupRegistration.COLUMNS)
                .single(call().bind("event_group_id", eventGroupId).bind("member_id", memberId))
                .map(EventGroupRegistration.map())
                .first();
    }

    /**
     * The sign-ups of an event group, oldest first.
     *
     * @param eventGroupId the event group
     * @return its sign-ups in every status
     */
    public List<EventGroupRegistration> findByEventGroup(int eventGroupId) {
        return query(
                        "SELECT %s FROM event_group_registration WHERE event_group_id = :event_group_id ORDER BY registered_at, id;",
                        EventGroupRegistration.COLUMNS)
                .single(call().bind("event_group_id", eventGroupId))
                .map(EventGroupRegistration.map())
                .all();
    }

    /**
     * Which of the given members gave any answer to which event group, a sign-up in any status.
     *
     * @param memberIds the members
     * @return the members who answered, by event group
     */
    public Map<Integer, Set<Integer>> findAnsweredBy(Collection<Integer> memberIds) {
        if (memberIds.isEmpty()) return Map.of();
        return query(
                        "SELECT event_group_id, member_id FROM event_group_registration WHERE member_id = ANY(:member_ids);")
                .single(call().bind("member_ids", List.copyOf(memberIds), PostgreSqlTypes.INTEGER))
                .map(row -> Map.entry(row.getInt("event_group_id"), row.getInt("member_id")))
                .all()
                .stream()
                .collect(Collectors.groupingBy(
                        Map.Entry::getKey, Collectors.mapping(Map.Entry::getValue, Collectors.toSet())));
    }

    /**
     * Signs a member up for an event group, or signs them up again after they withdrew or were turned
     * down.
     *
     * @param eventGroupId  the event group
     * @param memberId  the member
     * @param status    the status the sign-up starts with
     * @param createdBy the member who signed somebody else up, or null
     * @return the sign-up
     */
    public EventGroupRegistration signUp(
            int eventGroupId, int memberId, RegistrationStatus status, @Nullable Integer createdBy) {
        return SqlSupport.insertReturning(
                """
                INSERT INTO event_group_registration(event_group_id, member_id, status, created_by)
                VALUES (:event_group_id, :member_id, :status, :created_by)
                ON CONFLICT (event_group_id, member_id) DO UPDATE
                    SET status        = :status,
                        registered_at = now(),
                        created_by    = :created_by
                RETURNING %s;""",
                call().bind("event_group_id", eventGroupId)
                        .bind("member_id", memberId)
                        .bind("status", status)
                        .bind("created_by", createdBy),
                EventGroupRegistration.map(),
                EventGroupRegistration.COLUMNS);
    }

    /**
     * Moves a sign-up to a new status.
     *
     * @param id     the sign-up
     * @param status the new status
     */
    public void updateStatus(int id, RegistrationStatus status) {
        query("UPDATE event_group_registration SET status = :status WHERE id = :id;")
                .single(call().bind("id", id).bind("status", status))
                .update();
    }

    /**
     * How many sign-ups of an event group hold or wait for a place.
     *
     * @param eventGroupId the event group
     * @return the pending and accepted sign-ups
     */
    public int countHolding(int eventGroupId) {
        return SqlSupport.count("""
                SELECT count(*) FROM event_group_registration
                WHERE event_group_id = :event_group_id AND status IN ('PENDING', 'ACCEPTED');""", call().bind("event_group_id", eventGroupId));
    }

    /**
     * How many sign-ups of an event group were accepted.
     *
     * @param eventGroupId the event group
     * @return the accepted sign-ups
     */
    public int countAccepted(int eventGroupId) {
        return SqlSupport.count("""
                SELECT count(*) FROM event_group_registration
                WHERE event_group_id = :event_group_id AND status = 'ACCEPTED';""", call().bind("event_group_id", eventGroupId));
    }

    /**
     * The members whose sign-up for an event group holds or waits for a place.
     *
     * @param eventGroupId the event group
     * @return those members
     */
    public List<Integer> findHoldingMemberIds(int eventGroupId) {
        return query("""
                SELECT member_id FROM event_group_registration
                WHERE event_group_id = :event_group_id AND status IN ('PENDING', 'ACCEPTED')
                ORDER BY member_id;""")
                .single(call().bind("event_group_id", eventGroupId))
                .map(row -> row.getInt("member_id"))
                .all();
    }
}
