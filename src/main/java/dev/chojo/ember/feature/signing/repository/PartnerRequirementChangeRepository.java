/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.repository;

import de.chojo.sadu.postgresql.types.PostgreSqlTypes;
import dev.chojo.ember.feature.signing.entity.PartnerRequirementChange;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * The changes of shared appointments' documents that partner stations still have to be told, one row per
 * appointment and partner that gathers every change until the partner took the notice.
 */
@Singleton
public class PartnerRequirementChangeRepository {

    /**
     * Notes a change for a partner, gathered into what it was not told yet, and makes it due at once. A document
     * added back is no longer told as taken off.
     *
     * @param stationId the station holding the appointment
     * @param eventId   the appointment
     * @param partnerId the partnership with the station to tell
     * @param removed   the templates taken off
     * @param added     the templates added
     */
    public void note(
            int stationId, int eventId, int partnerId, Collection<Integer> removed, Collection<Integer> added) {
        query("""
                INSERT INTO partner_requirement_change(station_id, event_id, partner_id, removed_template_ids)
                VALUES (:station_id, :event_id, :partner_id, :removed::INT[])
                ON CONFLICT ON CONSTRAINT partner_requirement_change_once DO UPDATE
                    SET removed_template_ids = ARRAY(
                            SELECT DISTINCT kept.id
                            FROM unnest(partner_requirement_change.removed_template_ids
                                            || excluded.removed_template_ids) AS kept(id)
                            WHERE kept.id <> ALL (:added::INT[])
                            ORDER BY kept.id),
                        changed_at = now(),
                        delivery_attempts = 0,
                        next_delivery_at = now();""")
                .single(call().bind("station_id", stationId)
                        .bind("event_id", eventId)
                        .bind("partner_id", partnerId)
                        .bind("removed", List.copyOf(removed), PostgreSqlTypes.INTEGER)
                        .bind("added", List.copyOf(added), PostgreSqlTypes.INTEGER))
                .insert();
    }

    /**
     * @param eventId the appointment
     * @return what its partners still have to be told
     */
    public List<PartnerRequirementChange> forEvent(int eventId) {
        return query(
                        "SELECT %s FROM partner_requirement_change WHERE event_id = :event_id ORDER BY id;",
                        PartnerRequirementChange.COLUMNS)
                .single(call().bind("event_id", eventId))
                .map(PartnerRequirementChange.map())
                .all();
    }

    /**
     * @param now         the moment that counts as now
     * @param maxAttempts the failures in a row after which telling is given up
     * @param limit       the most rows to name
     * @return the changes whose next try is due, the one waiting longest first
     */
    public List<PartnerRequirementChange> due(Instant now, int maxAttempts, int limit) {
        return query("""
                        SELECT %s FROM partner_requirement_change
                        WHERE next_delivery_at <= :now AND delivery_attempts < :max_attempts
                        ORDER BY next_delivery_at, id
                        LIMIT :limit;""", PartnerRequirementChange.COLUMNS)
                .single(call().bind("now", now, INSTANT_TIMESTAMP)
                        .bind("max_attempts", maxAttempts)
                        .bind("limit", limit))
                .map(PartnerRequirementChange.map())
                .all();
    }

    /**
     * Forgets a change the partner took, unless the documents changed again since it was sent.
     *
     * @param change the change as it was sent
     * @return whether it was forgotten
     */
    public boolean delivered(PartnerRequirementChange change) {
        return query("DELETE FROM partner_requirement_change WHERE id = :id AND changed_at = :changed_at;")
                .single(call().bind("id", change.id()).bind("changed_at", change.changedAt(), INSTANT_TIMESTAMP))
                .delete()
                .changed();
    }

    /**
     * Forgets a change that no longer has anybody to tell.
     *
     * @param id the row
     */
    public void drop(int id) {
        query("DELETE FROM partner_requirement_change WHERE id = :id;")
                .single(call().bind("id", id))
                .delete();
    }

    /**
     * Records that telling the partner failed, and when to try again.
     *
     * @param id    the row
     * @param retry when to try again
     */
    public void failed(int id, Instant retry) {
        query("""
                UPDATE partner_requirement_change
                SET delivery_attempts = delivery_attempts + 1,
                    next_delivery_at  = :retry
                WHERE id = :id;""")
                .single(call().bind("id", id).bind("retry", retry, INSTANT_TIMESTAMP))
                .update();
    }
}
