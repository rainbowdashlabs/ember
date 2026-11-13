/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.repository;

import dev.chojo.ember.feature.events.entity.EventGroup;
import dev.chojo.ember.feature.events.entity.EventGroupDetails;
import dev.chojo.ember.feature.events.entity.EventGroupPart;
import dev.chojo.ember.feature.events.entity.EventGroupPartSummary;
import dev.chojo.ember.feature.events.entity.EventGroupSet;
import dev.chojo.ember.util.sql.SqlSupport;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * Event groups, their sets and which appointments are their parts.
 *
 * <p>A part is an ordinary appointment that names its set, so whatever reads appointments keeps
 * working; the event group only adds the grouping on top.
 */
@Singleton
public class EventGroupRepository {
    private static final String PART_COLUMNS = "e.id AS event_id, s.id AS set_id, s.group_id";

    /**
     * Creates an event group.
     *
     * @param stationId the station
     * @param details   what the event group holds
     * @return the event group
     */
    public EventGroup create(int stationId, EventGroupDetails details) {
        return SqlSupport.insertReturning(
                """
                INSERT INTO event_group(station_id, name, description, registration_limit, registration_deadline, requires_confirmation)
                VALUES (:station_id, :name, :description, :registration_limit, :registration_deadline, :requires_confirmation)
                RETURNING %s;""",
                call().bind("station_id", stationId)
                        .bind("name", details.name())
                        .bind("description", details.description())
                        .bind("registration_limit", details.registrationLimit())
                        .bind("registration_deadline", details.registrationDeadline(), INSTANT_TIMESTAMP)
                        .bind("requires_confirmation", details.requiresConfirmation()),
                EventGroup.map(),
                EventGroup.COLUMNS);
    }

    /**
     * Replaces what an event group holds besides its sets, parts and audiences. A deadline moved re-arms
     * the notice that it passed.
     *
     * @param id      the event group
     * @param details what it holds now
     * @return true where the event group exists
     */
    public boolean update(int id, EventGroupDetails details) {
        return query("""
                UPDATE event_group
                SET name                  = :name,
                    description           = :description,
                    registration_limit    = :registration_limit,
                    deadline_notified     = deadline_notified AND registration_deadline IS NOT DISTINCT FROM :registration_deadline,
                    registration_deadline = :registration_deadline,
                    requires_confirmation = :requires_confirmation
                WHERE id = :id;""")
                .single(call().bind("id", id)
                        .bind("name", details.name())
                        .bind("description", details.description())
                        .bind("registration_limit", details.registrationLimit())
                        .bind("registration_deadline", details.registrationDeadline(), INSTANT_TIMESTAMP)
                        .bind("requires_confirmation", details.requiresConfirmation()))
                .update()
                .changed();
    }

    /**
     * An event group by its id.
     *
     * @param id the event group
     * @return the event group, or empty where there is none
     */
    public Optional<EventGroup> findById(int id) {
        return SqlSupport.findById("event_group", EventGroup.COLUMNS, id, EventGroup.map());
    }

    /**
     * The event groups of a station, by name.
     *
     * @param stationId the station
     * @return its event groups
     */
    public List<EventGroup> findByStation(int stationId) {
        return query(
                        "SELECT %s FROM event_group WHERE station_id = :station_id ORDER BY lower(name), id;",
                        EventGroup.COLUMNS)
                .single(call().bind("station_id", stationId))
                .map(EventGroup.map())
                .all();
    }

    /**
     * Deletes an event group with its sets, sign-ups and audiences. Its parts have to be let go first.
     *
     * @param id the event group
     * @return true where it existed
     */
    public boolean delete(int id) {
        return SqlSupport.deleteById("event_group", id);
    }

    /**
     * The sets of an event group in their order.
     *
     * @param eventGroupId the event group
     * @return its sets
     */
    public List<EventGroupSet> findSets(int eventGroupId) {
        return query(
                        "SELECT %s FROM event_group_set WHERE group_id = :group_id ORDER BY position, id;",
                        EventGroupSet.COLUMNS)
                .single(call().bind("group_id", eventGroupId))
                .map(EventGroupSet.map())
                .all();
    }

    /**
     * Adds a set to an event group.
     *
     * @param eventGroupId the event group
     * @param name     its name
     * @param minimum  how many of its parts a sign-up has to choose
     * @param position its place among the sets
     * @return the set
     */
    public EventGroupSet createSet(int eventGroupId, String name, int minimum, int position) {
        return SqlSupport.insertReturning(
                """
                INSERT INTO event_group_set(group_id, name, minimum, position)
                VALUES (:group_id, :name, :minimum, :position)
                RETURNING %s;""",
                call().bind("group_id", eventGroupId)
                        .bind("name", name)
                        .bind("minimum", minimum)
                        .bind("position", position),
                EventGroupSet.map(),
                EventGroupSet.COLUMNS);
    }

    /**
     * Changes a set of an event group.
     *
     * @param setId    the set
     * @param name     its name
     * @param minimum  how many of its parts a sign-up has to choose
     * @param position its place among the sets
     */
    public void updateSet(int setId, String name, int minimum, int position) {
        query("UPDATE event_group_set SET name = :name, minimum = :minimum, position = :position WHERE id = :id;")
                .single(call().bind("id", setId)
                        .bind("name", name)
                        .bind("minimum", minimum)
                        .bind("position", position))
                .update();
    }

    /**
     * Removes a set that holds no parts.
     *
     * @param setId the set
     */
    public void deleteSet(int setId) {
        SqlSupport.deleteById("event_group_set", setId);
    }

    /**
     * The parts of an event group, in date order.
     *
     * @param eventGroupId the event group
     * @return its parts
     */
    public List<EventGroupPart> findParts(int eventGroupId) {
        return query("""
                SELECT %s
                FROM station_event e
                         JOIN event_group_set s ON s.id = e.event_group_set_id
                WHERE s.group_id = :group_id
                ORDER BY e.start_time, e.id;""", PART_COLUMNS)
                .single(call().bind("group_id", eventGroupId))
                .map(EventGroupPart.map())
                .all();
    }

    /**
     * What every part of a station's event groups says about its event group in a list, by appointment.
     *
     * @param stationId the station
     * @return the event group of each part, numbered in date order within its event group
     */
    public Map<Integer, EventGroupPartSummary> findPartSummaries(int stationId) {
        return query("""
                SELECT e.id AS event_id,
                       c.id AS group_id,
                       c.name AS group_name,
                       s.id AS set_id,
                       s.name AS set_name,
                       row_number() OVER (PARTITION BY c.id ORDER BY e.start_time, e.id) AS number,
                       count(*) OVER (PARTITION BY c.id) AS part_count
                FROM station_event e
                         JOIN event_group_set s ON s.id = e.event_group_set_id
                         JOIN event_group c ON c.id = s.group_id
                WHERE c.station_id = :station_id;""")
                .single(call().bind("station_id", stationId))
                .map(row -> Map.entry(
                        row.getInt("event_id"),
                        new EventGroupPartSummary(
                                row.getInt("group_id"),
                                row.getString("group_name"),
                                row.getInt("set_id"),
                                row.getString("set_name"),
                                row.getInt("number"),
                                row.getInt("part_count"))))
                .all()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /**
     * The event group an appointment is a part of.
     *
     * @param eventId the appointment
     * @return the part, or empty for an appointment of its own
     */
    public Optional<EventGroupPart> findPart(int eventId) {
        return query("""
                SELECT %s
                FROM station_event e
                         JOIN event_group_set s ON s.id = e.event_group_set_id
                WHERE e.id = :event_id;""", PART_COLUMNS)
                .single(call().bind("event_id", eventId))
                .map(EventGroupPart.map())
                .first();
    }

    /**
     * Locks an event group for the rest of the running transaction, so changes to its sets and parts
     * happen one after another.
     *
     * @param eventGroupId the event group
     */
    public void lockEventGroup(int eventGroupId) {
        SqlSupport.exists("SELECT 1 FROM event_group WHERE id = :id FOR UPDATE;", call().bind("id", eventGroupId));
    }

    /**
     * Locks an appointment for the rest of the running transaction, so it cannot become a part of
     * two event groups, or be signed up for on its own, while it is being added.
     *
     * @param eventId the appointment
     */
    public void lockEvent(int eventId) {
        SqlSupport.exists("SELECT 1 FROM station_event WHERE id = :id FOR UPDATE;", call().bind("id", eventId));
    }

    /**
     * Puts an appointment that is a part of no event group into a set.
     *
     * @param eventId the appointment
     * @param setId   the set
     * @return false where the appointment already is a part of an event group
     */
    public boolean claimPart(int eventId, int setId) {
        return query("""
                UPDATE station_event
                SET event_group_set_id = :set_id,
                    updated_at    = now()
                WHERE id = :event_id
                  AND event_group_set_id IS NULL;""")
                .single(call().bind("event_id", eventId).bind("set_id", setId))
                .update()
                .changed();
    }

    /**
     * Whether an appointment is shared with partner stations on its own: shared, given places for
     * partners, or holding partner sign-ups that still stand.
     *
     * @param eventId the appointment
     * @return true where partners take part in it
     */
    public boolean isSharedWithPartners(int eventId) {
        return SqlSupport.exists("""
                SELECT 1 FROM event_federation_share WHERE event_id = :event_id
                UNION ALL
                SELECT 1 FROM event_partner_places WHERE event_id = :event_id
                UNION ALL
                SELECT 1 FROM event_federation_registration
                WHERE event_id = :event_id AND status IN ('PENDING', 'ACCEPTED')
                LIMIT 1;""", call().bind("event_id", eventId));
    }

    /**
     * Puts an appointment into a set, or takes it out of its event group.
     *
     * @param eventId the appointment
     * @param setId   the set, or null to make it an appointment of its own again
     */
    public void setPartSet(int eventId, @Nullable Integer setId) {
        query("UPDATE station_event SET event_group_set_id = :set_id, updated_at = now() WHERE id = :event_id;")
                .single(call().bind("event_id", eventId).bind("set_id", setId))
                .update();
    }

    /**
     * Unties the registrations of an appointment from the event group sign-ups they were chosen with, so
     * the appointment's sign-ups stand on their own once it is no part of an event group.
     *
     * @param eventId the appointment
     */
    public void untieRegistrations(int eventId) {
        query("UPDATE event_registration SET group_registration_id = NULL WHERE event_id = :event_id;")
                .single(call().bind("event_id", eventId))
                .update();
        query("""
                UPDATE event_federation_registration
                SET group_partner_registration_id = NULL
                WHERE event_id = :event_id;""").single(call().bind("event_id", eventId)).update();
    }

    /**
     * Who was marked present on the attendance sheets of an event group's parts, by member.
     *
     * @param eventGroupId the event group
     * @return the parts each member was present at
     */
    public Map<Integer, Set<Integer>> findPresent(int eventGroupId) {
        var present = new HashMap<Integer, Set<Integer>>();
        query("""
                SELECT DISTINCT s.event_id, e.member_id
                FROM attendance_session s
                    JOIN attendance_entry e ON e.session_id = s.id
                    JOIN station_event se ON se.id = s.event_id
                    JOIN event_group_set cs ON cs.id = se.event_group_set_id
                WHERE cs.group_id = :group_id
                  AND e.status = 'PRESENT';""")
                .single(call().bind("group_id", eventGroupId))
                .map(row -> new int[] {row.getInt("member_id"), row.getInt("event_id")})
                .all()
                .forEach(pair -> present.computeIfAbsent(pair[0], member -> new HashSet<>())
                        .add(pair[1]));
        return present;
    }

    /**
     * Gives a part the partner share of its event group, where the event group is shared.
     *
     * @param eventGroupId the event group
     * @param eventId  the part
     */
    public void handDownShare(int eventGroupId, int eventId) {
        query("""
                INSERT INTO event_federation_share(event_id, scope)
                SELECT :event_id, scope FROM event_group_share WHERE group_id = :group_id
                ON CONFLICT (event_id) DO UPDATE SET scope = EXCLUDED.scope;""")
                .single(call().bind("event_id", eventId).bind("group_id", eventGroupId))
                .insert();
        query("""
                INSERT INTO event_federation_share_target(share_id, partner_id)
                SELECT efs.id, t.partner_id
                FROM event_federation_share efs
                    JOIN event_group_share_target t ON t.group_id = :group_id
                WHERE efs.event_id = :event_id
                ON CONFLICT DO NOTHING;""")
                .single(call().bind("event_id", eventId).bind("group_id", eventGroupId))
                .insert();
    }

    /**
     * Makes every part of an event group an appointment of its own again.
     *
     * @param eventGroupId the event group
     */
    public void releaseParts(int eventGroupId) {
        query("""
                UPDATE station_event
                SET event_group_set_id = NULL,
                    updated_at    = now()
                WHERE event_group_set_id IN (SELECT id FROM event_group_set WHERE group_id = :group_id);""").single(call().bind("group_id", eventGroupId)).update();
    }

    /**
     * How many parts a set holds.
     *
     * @param setId the set
     * @return its parts
     */
    public int countPartsInSet(int setId) {
        return SqlSupport.count(
                "SELECT count(*) AS count FROM station_event WHERE event_group_set_id = :set_id;",
                call().bind("set_id", setId));
    }

    /**
     * Gives every part of an event group the sign-up of the event group: parts take sign-ups, confirm them as
     * the event group does and close with it, and carry no limit, closing days or minimum of their own.
     *
     * @param eventGroupId             the event group
     * @param requiresConfirmation whether the event group confirms sign-ups
     * @param deadline             when the event group closes, or null
     */
    public void alignParts(int eventGroupId, boolean requiresConfirmation, @Nullable Instant deadline) {
        query("""
                UPDATE station_event
                SET requires_registration   = TRUE,
                    requires_confirmation   = :requires_confirmation,
                    registration_deadline   = :registration_deadline,
                    registration_limit      = NULL,
                    registration_close_days = NULL,
                    min_registrations       = NULL,
                    threshold_days          = NULL,
                    updated_at              = now()
                WHERE event_group_set_id IN (SELECT id FROM event_group_set WHERE group_id = :group_id);""")
                .single(call().bind("group_id", eventGroupId)
                        .bind("requires_confirmation", requiresConfirmation)
                        .bind("registration_deadline", deadline, INSTANT_TIMESTAMP))
                .update();
    }

    /**
     * Whether an appointment holds sign-ups of its own that still stand: waiting or accepted, and
     * not placed by an appointment field.
     *
     * @param eventId the appointment
     * @return true where somebody is signed up for it
     */
    public boolean hasOpenSignUps(int eventId) {
        return SqlSupport.exists("""
                SELECT 1
                FROM event_registration
                WHERE event_id = :event_id
                  AND status IN ('PENDING', 'ACCEPTED')
                  AND NOT from_field
                LIMIT 1;""", call().bind("event_id", eventId));
    }

    /**
     * Calls an event group off.
     *
     * @param eventGroupId the event group
     * @param reason   why, or null
     * @return false where it was called off already
     */
    public boolean cancel(int eventGroupId, @Nullable String reason) {
        return query("""
                UPDATE event_group
                SET cancelled     = TRUE,
                    cancelled_at  = now(),
                    cancel_reason = :reason
                WHERE id = :id
                  AND NOT cancelled;""")
                .single(call().bind("id", eventGroupId).bind("reason", reason))
                .update()
                .changed();
    }

    /**
     * Event groups whose sign-ups closed while some still wait for a decision, and whose managers were not
     * told yet.
     *
     * @return those event groups
     */
    public List<ExpiredEventGroupDeadline> findExpiredDeadlines() {
        return query("""
                SELECT c.id AS group_id, c.station_id, c.name, count(r.id) AS pending_count
                FROM event_group c
                         JOIN event_group_registration r ON r.group_id = c.id AND r.status = 'PENDING'
                WHERE c.registration_deadline IS NOT NULL
                  AND c.registration_deadline < now()
                  AND NOT c.deadline_notified
                  AND NOT c.cancelled
                GROUP BY c.id, c.station_id, c.name;""")
                .single(call())
                .map(row -> new ExpiredEventGroupDeadline(
                        row.getInt("group_id"),
                        row.getInt("station_id"),
                        row.getString("name"),
                        row.getInt("pending_count")))
                .all();
    }

    /**
     * The event groups of a station members can still answer before a deadline: not called off, a
     * deadline still ahead, and a part still to come.
     *
     * @param stationId the station
     * @return those event groups, soonest deadline first
     */
    public List<EventGroup> findOpenWithDeadline(int stationId) {
        return query("""
                SELECT %s
                FROM event_group c
                WHERE c.station_id = :station_id
                  AND NOT c.cancelled
                  AND c.registration_deadline IS NOT NULL
                  AND c.registration_deadline > now()
                  AND EXISTS (SELECT 1
                              FROM station_event e
                                       JOIN event_group_set cs ON cs.id = e.event_group_set_id
                              WHERE cs.group_id = c.id
                                AND e.start_time > now()
                                AND NOT e.cancelled
                                AND %s)
                ORDER BY c.registration_deadline, c.id;""", "c." + EventGroup.COLUMNS.replace(", ", ", c."), CancellationSql.NO_DATE_CANCELLED)
                .single(call().bind("station_id", stationId))
                .map(EventGroup.map())
                .all();
    }

    /**
     * Notes that managers were told that an event group's sign-ups closed.
     *
     * @param eventGroupId the event group
     */
    public void markDeadlineNotified(int eventGroupId) {
        query("UPDATE event_group SET deadline_notified = TRUE WHERE id = :id;")
                .single(call().bind("id", eventGroupId))
                .update();
    }

    /**
     * Event groups whose sign-ups close within the given number of days and whose warning for that lead
     * time has not gone out yet.
     *
     * @param daysBefore the lead time warned about
     * @return those event groups
     */
    public List<ClosingEventGroup> findClosingIn(int daysBefore) {
        return query("""
                SELECT c.id AS group_id, c.station_id, c.name
                FROM event_group c
                WHERE NOT c.cancelled
                  AND c.registration_deadline IS NOT NULL
                  AND c.registration_deadline > now()
                  AND c.registration_deadline <= now() + make_interval(days => :days_before)
                  AND EXISTS (SELECT 1
                              FROM station_event e
                                       JOIN event_group_set cs ON cs.id = e.event_group_set_id
                              WHERE cs.group_id = c.id
                                AND e.start_time > now()
                                AND NOT e.cancelled
                                AND %s)
                  AND NOT EXISTS (SELECT 1
                                  FROM event_group_deadline_reminder_sent s
                                  WHERE s.group_id = c.id
                                    AND s.days_before = :days_before);""", CancellationSql.NO_DATE_CANCELLED)
                .single(call().bind("days_before", daysBefore))
                .map(row ->
                        new ClosingEventGroup(row.getInt("group_id"), row.getInt("station_id"), row.getString("name")))
                .all();
    }

    /**
     * Forgets which warnings went out for an event group, so a moved deadline is warned about again.
     *
     * @param eventGroupId the event group
     */
    public void clearClosingWarnings(int eventGroupId) {
        query("DELETE FROM event_group_deadline_reminder_sent WHERE group_id = :group_id;")
                .single(call().bind("group_id", eventGroupId))
                .update();
    }

    /**
     * Notes that the warning for a lead time went out for an event group.
     *
     * @param eventGroupId   the event group
     * @param daysBefore the lead time
     */
    public void markClosingWarned(int eventGroupId, int daysBefore) {
        query("""
                INSERT INTO event_group_deadline_reminder_sent(group_id, days_before)
                VALUES (:group_id, :days_before)
                ON CONFLICT DO NOTHING;""")
                .single(call().bind("group_id", eventGroupId).bind("days_before", daysBefore))
                .insert();
    }

    /**
     * The current members of a station who never answered an event group: neither signed up nor withdrew.
     * Whether the event group is meant for them is not decided here.
     *
     * @param eventGroupId  the event group
     * @param stationId the station
     * @return the members
     */
    public List<Integer> findUnansweredMemberIds(int eventGroupId, int stationId) {
        return query("""
                SELECT sm.id AS member_id
                FROM station_member sm
                WHERE sm.station_id = :station_id
                  AND sm.former = FALSE
                  AND NOT EXISTS (SELECT 1
                                  FROM event_group_registration r
                                  WHERE r.group_id = :group_id
                                    AND r.member_id = sm.id);""")
                .single(call().bind("group_id", eventGroupId).bind("station_id", stationId))
                .map(row -> row.getInt("member_id"))
                .all();
    }

    /**
     * An event group whose sign-ups closed with some still waiting.
     *
     * @param eventGroupId     the event group
     * @param stationId    its station
     * @param name         its name
     * @param pendingCount how many wait for a decision
     */
    public record ExpiredEventGroupDeadline(int eventGroupId, int stationId, String name, int pendingCount) {}

    /**
     * An event group whose sign-ups close soon.
     *
     * @param eventGroupId  the event group
     * @param stationId its station
     * @param name      its name
     */
    public record ClosingEventGroup(int eventGroupId, int stationId, String name) {}
}
