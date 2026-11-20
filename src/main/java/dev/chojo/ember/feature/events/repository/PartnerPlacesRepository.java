/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.repository;

import de.chojo.sadu.postgresql.types.PostgreSqlTypes;
import dev.chojo.ember.feature.events.entity.PartnerPlaces;
import dev.chojo.ember.feature.events.entity.PlacesKind;
import jakarta.inject.Singleton;

import java.util.Collection;
import java.util.List;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * The arrangements a host made with its partners, one table per {@link PlacesKind}.
 *
 * <p>Only arrangements somebody made are kept. The one nobody made, the host deciding without a cap, has
 * no row, so a host taking the decision back leaves nothing behind that could read as an arrangement.
 */
@Singleton
public class PartnerPlacesRepository {

    /**
     * What a partner may do, or the arrangement that holds where nobody has said: the host decides,
     * with no cap.
     *
     * @param kind      what the arrangement is about
     * @param ownerId   the appointment or event group
     * @param partnerId the host's own record of the partner
     * @return the arrangement
     */
    public PartnerPlaces find(PlacesKind kind, int ownerId, int partnerId) {
        return query("""
                SELECT %s AS owner_id, partner_id, slot_budget, partner_confirms
                FROM %s
                WHERE %s = :owner_id AND partner_id = :partner_id;""", kind.ownerColumn(), kind.table(), kind.ownerColumn())
                .single(call().bind("owner_id", ownerId).bind("partner_id", partnerId))
                .map(PartnerPlaces.map())
                .first()
                .orElseGet(() -> PartnerPlaces.hostDecides(ownerId, partnerId));
    }

    /**
     * Every arrangement made about one appointment or event group.
     *
     * @param kind    what the arrangements are about
     * @param ownerId the appointment or event group
     * @return one arrangement per partner that was given one
     */
    public List<PartnerPlaces> list(PlacesKind kind, int ownerId) {
        return query("""
                SELECT %s AS owner_id, partner_id, slot_budget, partner_confirms
                FROM %s
                WHERE %s = :owner_id
                ORDER BY partner_id;""", kind.ownerColumn(), kind.table(), kind.ownerColumn())
                .single(call().bind("owner_id", ownerId))
                .map(PartnerPlaces.map())
                .all();
    }

    /**
     * Keeps an arrangement, or stops keeping one where the host takes the decision back.
     *
     * @param kind   what the arrangement is about
     * @param places the arrangement, already in its stored shape
     */
    public void set(PlacesKind kind, PartnerPlaces places) {
        if (places.decidedByHost()) {
            query("DELETE FROM %s WHERE %s = :owner_id AND partner_id = :partner_id;", kind.table(), kind.ownerColumn())
                    .single(call().bind("owner_id", places.ownerId()).bind("partner_id", places.partnerId()))
                    .delete();
            return;
        }
        query("""
                INSERT INTO %1$s(%2$s, partner_id, slot_budget, partner_confirms)
                VALUES (:owner_id, :partner_id, :slot_budget, TRUE)
                ON CONFLICT (%2$s, partner_id)
                    DO UPDATE SET slot_budget = EXCLUDED.slot_budget, partner_confirms = TRUE;""", kind.table(), kind.ownerColumn())
                .single(call().bind("owner_id", places.ownerId())
                        .bind("partner_id", places.partnerId())
                        .bind("slot_budget", places.slotBudget()))
                .insert();
    }

    /**
     * Holds an arrangement still while a place is counted and taken against it, until the running
     * transaction ends.
     *
     * <p>Nothing is read from it: what matters is that a second taker waits here until the first has
     * committed, so the count it then makes is a count of what is really taken. Where nothing was
     * arranged there is no row and nothing to wait for, which is right, because there is no limit to
     * race for.
     *
     * @param kind      what the arrangement is about
     * @param ownerId   the appointment or event group
     * @param partnerId the host's own record of the partner
     */
    public void lock(PlacesKind kind, int ownerId, int partnerId) {
        query(
                        "SELECT 1 FROM %s WHERE %s = :owner_id AND partner_id = :partner_id FOR UPDATE;",
                        kind.table(), kind.ownerColumn())
                .single(call().bind("owner_id", ownerId).bind("partner_id", partnerId))
                .map(row -> row.getInt(1))
                .first();
    }

    /**
     * Holds the appointment or event group itself still until the running transaction ends, so whatever
     * decides who it is shared with and what each partner was given happens one writer at a time.
     *
     * @param kind    what the owner is
     * @param ownerId the appointment or event group
     */
    public void lockOwner(PlacesKind kind, int ownerId) {
        query("SELECT 1 FROM %s WHERE id = :owner_id FOR UPDATE;", kind.ownerTable())
                .single(call().bind("owner_id", ownerId))
                .map(row -> row.getInt(1))
                .first();
    }

    /**
     * Drops the arrangements with every partner but the ones named.
     *
     * @param kind     what the arrangements are about
     * @param ownerId  the appointment or event group
     * @param partners the partners whose arrangements stay, none to drop them all
     */
    public void retainOnly(PlacesKind kind, int ownerId, Collection<Integer> partners) {
        query(
                        "DELETE FROM %s WHERE %s = :owner_id AND NOT (partner_id = ANY(:partners));",
                        kind.table(), kind.ownerColumn())
                .single(call().bind("owner_id", ownerId)
                        .bind("partners", List.copyOf(partners), PostgreSqlTypes.INTEGER))
                .delete();
    }
}
