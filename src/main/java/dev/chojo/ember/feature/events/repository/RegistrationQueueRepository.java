/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.repository;

import de.chojo.sadu.postgresql.types.PostgreSqlTypes;
import de.chojo.sadu.queries.api.call.Call;
import dev.chojo.ember.feature.events.entity.Places;
import dev.chojo.ember.feature.events.entity.QueueEntry;
import dev.chojo.ember.feature.events.entity.QueueOrder;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * The places of an appointment date and the waiting lists of appointment dates and event groups,
 * holding members of this station and of partner stations together.
 *
 * <p>A list is ordered by its position key, lowest first. Gaps are allowed, so an entry leaving never
 * renumbers the others; the place a member is shown is the rank. Callers hold the lock of the
 * appointment or event group the list belongs to.
 */
@Singleton
public class RegistrationQueueRepository {

    /**
     * The places of one appointment date: the limit in effect, the accepted sign-ups and how many wait.
     *
     * @param eventId   the appointment
     * @param eventDate the date
     * @return the places, or none at all where the appointment does not exist
     */
    public Places places(int eventId, LocalDate eventDate) {
        return query("""
                        SELECT %s                                                                AS registration_limit,
                               (SELECT count(*) FROM event_registration r
                                WHERE r.event_id = e.id AND r.event_date = :event_date AND r.status = 'ACCEPTED')
                                   + (SELECT count(*) FROM event_federation_registration f
                                      WHERE f.event_id = e.id AND f.event_date = :event_date AND f.status = 'ACCEPTED')
                                                                                                  AS accepted,
                               (SELECT count(*) FROM registration_queue q
                                WHERE q.event_id = e.id AND q.event_date = :event_date)           AS queued
                        FROM station_event e
                        WHERE e.id = :event_id;""", EventGroupInheritance.registrationLimit("e"))
                .single(call().bind("event_id", eventId).bind("event_date", eventDate))
                .map(row -> new Places(
                        row.getObject("registration_limit", Integer.class),
                        row.getInt("accepted"),
                        row.getInt("queued")))
                .first()
                .orElse(Places.UNLIMITED);
    }

    /**
     * The waiting list of one appointment date.
     *
     * @param eventId   the appointment
     * @param eventDate the date
     * @return the list
     */
    public static QueueList ofDate(int eventId, LocalDate eventDate) {
        return new QueueList(QueueKind.DATE, eventId, eventDate);
    }

    /**
     * The waiting list of an event group.
     *
     * @param eventGroupId the event group
     * @return the list
     */
    public static QueueList ofEventGroup(int eventGroupId) {
        return new QueueList(QueueKind.EVENT_GROUP, eventGroupId, null);
    }

    /**
     * The entries of a waiting list in the order they move up in.
     *
     * @param list the list
     * @return its entries, first in line first
     */
    public List<QueueEntry> entries(QueueList list) {
        return query("""
                        SELECT id, %s AS local_id, %s AS partner_id, position, score::FLOAT8 AS score, joined_at,
                               row_number() OVER (ORDER BY position) AS rank
                        FROM %s
                        WHERE %s
                        ORDER BY position;""", list.kind().localColumn, list.kind().partnerColumn, list.kind().table, list.kind().scope)
                .single(list.bind(call()))
                .map(QueueEntry::map)
                .all();
    }

    /**
     * The places in line of members' registrations that wait on the waiting list of their date.
     *
     * @param registrationIds the registrations
     * @return the place in line by registration, only for those waiting
     */
    public Map<Integer, Integer> ranksOf(Collection<Integer> registrationIds) {
        if (registrationIds.isEmpty()) return Map.of();
        return query("""
                        SELECT registration_id, rank
                        FROM (SELECT q.registration_id,
                                     row_number() OVER (PARTITION BY q.event_id, q.event_date ORDER BY q.position) AS rank
                              FROM registration_queue q
                              WHERE (q.event_id, q.event_date) IN (SELECT w.event_id, w.event_date
                                                                   FROM registration_queue w
                                                                   WHERE w.registration_id = ANY(:ids))) ranked
                        WHERE registration_id = ANY(:ids);""")
                .single(call().bind("ids", List.copyOf(registrationIds), PostgreSqlTypes.INTEGER))
                .map(row -> Map.entry(row.getInt("registration_id"), row.getInt("rank")))
                .all()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /**
     * The entry of a sign-up on a waiting list.
     *
     * @param list    the list
     * @param owner   the sign-up
     * @return its entry, or empty where it does not wait on this list
     */
    public Optional<QueueEntry> entryOf(QueueList list, QueueOwner owner) {
        return entries(list).stream().filter(owner::owns).findFirst();
    }

    /**
     * Puts a sign-up on a waiting list at the given position key, pushing every entry at or behind it
     * one further back.
     *
     * @param list     the list
     * @param owner    the sign-up
     * @param position the position key it takes
     * @param score    its fairness score
     */
    public void insertAt(QueueList list, QueueOwner owner, int position, @Nullable Double score) {
        query(
                        "UPDATE %s SET position = position + 1 WHERE %s AND position >= :position;",
                        list.kind().table, list.kind().scope)
                .single(list.bind(call()).bind("position", position))
                .update();
        query(
                        """
                        INSERT INTO %s(%s, %s, position, score)
                        VALUES (%s, :owner_id, :position, :score);""",
                        list.kind().table,
                        list.kind().scopeColumns,
                        owner.partner() ? list.kind().partnerColumn : list.kind().localColumn,
                        list.kind().scopeValues)
                .single(list.bind(call())
                        .bind("owner_id", owner.id())
                        .bind("position", position)
                        .bind("score", score))
                .insert();
    }

    /**
     * The position key one behind the last entry of a list.
     *
     * @param list the list
     * @return the key a sign-up joining at the end takes
     */
    public int endOf(QueueList list) {
        return query(
                        "SELECT coalesce(max(position), 0) + 1 AS next FROM %s WHERE %s;",
                        list.kind().table, list.kind().scope)
                .single(list.bind(call()))
                .map(row -> row.getInt("next"))
                .first()
                .orElse(1);
    }

    /**
     * Takes a sign-up off a waiting list, where it waits on it.
     *
     * @param list  the list
     * @param owner the sign-up
     * @return whether it was on the list
     */
    public boolean remove(QueueList list, QueueOwner owner) {
        return query(
                        "DELETE FROM %s WHERE %s AND %s = :owner_id;",
                        list.kind().table,
                        list.kind().scope,
                        owner.partner() ? list.kind().partnerColumn : list.kind().localColumn)
                .single(list.bind(call()).bind("owner_id", owner.id()))
                .delete()
                .changed();
    }

    /**
     * Gives a partner station's member who waited on a date's list the place that fell free. Leaving
     * the list follows from the status, as for a member of this station.
     *
     * @param federationRegistrationId the partner's registration
     * @return whether it was still waiting
     */
    public boolean acceptPartner(int federationRegistrationId) {
        return query("""
                        UPDATE event_federation_registration
                        SET status = 'ACCEPTED', status_changed_at = now()
                        WHERE id = :id AND status = 'QUEUED';""")
                .single(call().bind("id", federationRegistrationId))
                .update()
                .changed();
    }

    /**
     * The dates of an appointment from one day on whose waiting list is not empty.
     *
     * @param eventId the appointment
     * @param from    the first date looked at
     * @return the dates, earliest first
     */
    public List<LocalDate> datesWaitedFor(int eventId, LocalDate from) {
        return query("""
                        SELECT DISTINCT event_date FROM registration_queue
                        WHERE event_id = :event_id AND event_date >= :from
                        ORDER BY event_date;""")
                .single(call().bind("event_id", eventId).bind("from", from))
                .map(row -> row.getObject("event_date", LocalDate.class))
                .all();
    }

    /**
     * The order of an appointment's waiting lists.
     *
     * @param eventId the appointment
     * @return its order, by time where it does not exist
     */
    public QueueOrder orderOfEvent(int eventId) {
        return query("SELECT queue_order FROM station_event WHERE id = :id;")
                .single(call().bind("id", eventId))
                .map(row -> QueueOrder.valueOf(row.getString("queue_order")))
                .first()
                .orElse(QueueOrder.TIME);
    }

    /**
     * The order of an event group's waiting list.
     *
     * @param eventGroupId the event group
     * @return its order, by time where it does not exist
     */
    public QueueOrder orderOfEventGroup(int eventGroupId) {
        return query("SELECT queue_order FROM event_group WHERE id = :id;")
                .single(call().bind("id", eventGroupId))
                .map(row -> QueueOrder.valueOf(row.getString("queue_order")))
                .first()
                .orElse(QueueOrder.TIME);
    }

    /** Which table a waiting list lives in, with the columns that pick it out. */
    public enum QueueKind {
        DATE(
                "registration_queue",
                "registration_id",
                "federation_registration_id",
                "event_id = :scope_id AND event_date = :scope_date",
                "event_id, event_date",
                ":scope_id, :scope_date"),
        EVENT_GROUP(
                "event_group_queue",
                "sign_up_id",
                "partner_sign_up_id",
                "event_group_id = :scope_id",
                "event_group_id",
                ":scope_id");

        private final String table;
        private final String localColumn;
        private final String partnerColumn;
        private final String scope;
        private final String scopeColumns;
        private final String scopeValues;

        QueueKind(
                String table,
                String localColumn,
                String partnerColumn,
                String scope,
                String scopeColumns,
                String scopeValues) {
            this.table = table;
            this.localColumn = localColumn;
            this.partnerColumn = partnerColumn;
            this.scope = scope;
            this.scopeColumns = scopeColumns;
            this.scopeValues = scopeValues;
        }
    }

    /**
     * A waiting list: of one appointment date, or of an event group. Only {@link #ofDate} and
     * {@link #ofEventGroup} make one.
     *
     * @param kind      which table holds it
     * @param scopeId   the appointment or event group
     * @param scopeDate the appointment date, or null for an event group
     */
    public record QueueList(
            QueueKind kind, int scopeId, @Nullable LocalDate scopeDate) {
        /** Refuses a date list without its date and an event group list with one. */
        public QueueList {
            if ((kind == QueueKind.DATE) != (scopeDate != null)) {
                throw new IllegalArgumentException("A date list needs its date, an event group list none");
            }
        }

        Call bind(Call call) {
            var bound = call.bind("scope_id", scopeId);
            return scopeDate == null ? bound : bound.bind("scope_date", scopeDate);
        }
    }

    /**
     * The sign-up an entry stands for: a member of this station's, or a partner's member's.
     *
     * @param id      the sign-up
     * @param partner whether it is a partner's member's sign-up
     */
    public record QueueOwner(int id, boolean partner) {
        /**
         * The sign-up of a member of this station.
         *
         * @param id the sign-up
         * @return its owner
         */
        public static QueueOwner local(int id) {
            return new QueueOwner(id, false);
        }

        /**
         * The sign-up of a partner station's member.
         *
         * @param id the sign-up
         * @return its owner
         */
        public static QueueOwner ofPartner(int id) {
            return new QueueOwner(id, true);
        }

        /**
         * Whether an entry stands for this sign-up.
         *
         * @param entry the entry
         * @return true where it does
         */
        public boolean owns(QueueEntry entry) {
            Integer owned = partner ? entry.partnerId() : entry.localId();
            return owned != null && owned == id;
        }
    }
}
