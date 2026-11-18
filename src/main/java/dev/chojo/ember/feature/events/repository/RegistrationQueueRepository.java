/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.repository;

import dev.chojo.ember.feature.events.entity.Places;
import jakarta.inject.Singleton;

import java.time.LocalDate;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * The places of an appointment date and its waiting list, counting members of this station and of
 * partner stations together.
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
}
