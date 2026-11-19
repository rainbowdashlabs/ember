/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.repository;

import dev.chojo.ember.feature.events.entity.RegistrationFieldValue;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Map;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * The answers partner stations' members gave to the registration questions of an appointment this
 * station shares, one row per partner registration and question. Read back as
 * {@link RegistrationFieldValue}, whose registration is the partner's registration.
 */
@Singleton
public class PartnerAnswerRepository {
    /**
     * Writes the answers of one partner registration, replacing every answer it carried before. A
     * registration taken up again by a new sign-up keeps none of the old answers, so a question left
     * open this time stays open.
     *
     * @param federationRegistrationId the partner's registration
     * @param answers                  the value per question id
     */
    public void setAnswers(int federationRegistrationId, Map<Integer, String> answers) {
        query("DELETE FROM event_federation_registration_answer WHERE federation_registration_id = :registration_id;")
                .single(call().bind("registration_id", federationRegistrationId))
                .delete();
        for (var answer : answers.entrySet()) {
            query("""
                    INSERT INTO event_federation_registration_answer(federation_registration_id, field_id, value)
                    VALUES (:registration_id, :field_id, :value)
                    ON CONFLICT (federation_registration_id, field_id) DO UPDATE SET value = EXCLUDED.value;""")
                    .single(call().bind("registration_id", federationRegistrationId)
                            .bind("field_id", answer.getKey())
                            .bind("value", answer.getValue()))
                    .insert();
        }
    }

    /**
     * The answers of the partner registrations of one appointment of a station, on every date. An
     * appointment of another station answers nothing.
     *
     * @param stationId the station holding the appointment
     * @param eventId   the appointment
     * @return the answers, keyed by the partner's registration
     */
    public List<RegistrationFieldValue> findForEvent(int stationId, int eventId) {
        return query("""
                SELECT a.federation_registration_id AS registration_id, a.field_id, a.value
                FROM event_federation_registration_answer a
                         JOIN event_federation_registration r ON r.id = a.federation_registration_id
                         JOIN station_event e ON e.id = r.event_id
                WHERE r.event_id = :event_id
                  AND e.station_id = :station_id
                ORDER BY a.federation_registration_id, a.field_id;""")
                .single(call().bind("event_id", eventId).bind("station_id", stationId))
                .map(RegistrationFieldValue.map())
                .all();
    }
}
