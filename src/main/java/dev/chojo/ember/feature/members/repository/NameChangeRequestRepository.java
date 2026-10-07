/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.repository;

import dev.chojo.ember.feature.members.entity.NameChangeOutcome;
import dev.chojo.ember.feature.members.entity.NameChangeRequest;
import dev.chojo.ember.feature.members.entity.PendingNameChange;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * The register names members asked for and the decisions on them.
 *
 * <p>An account has at most one open request; asking again rewrites it, so a member who corrects
 * a typo in the name they asked for does not leave a second request behind for somebody to deny.
 * A request is decided once: whoever decides first wins, and a second decision finds nothing open.
 */
@Singleton
public class NameChangeRequestRepository {

    /**
     * Stores a name asked for, replacing the open request of the same account where there is one.
     *
     * @param accountId the account whose name is to change
     * @param firstName the first name asked for
     * @param lastName  the last name asked for
     * @return the open request
     */
    public NameChangeRequest request(int accountId, String firstName, String lastName) {
        return query("""
                INSERT INTO name_change_request(account_id, first_name, last_name)
                VALUES (:account_id, :first_name, :last_name)
                ON CONFLICT (account_id) WHERE decided_at IS NULL
                DO UPDATE SET
                    first_name   = excluded.first_name,
                    last_name    = excluded.last_name,
                    requested_at = now()
                RETURNING %s;""", NameChangeRequest.COLUMNS)
                .single(call().bind("account_id", accountId)
                        .bind("first_name", firstName)
                        .bind("last_name", lastName))
                .map(NameChangeRequest.map())
                .first()
                .orElseThrow();
    }

    /**
     * The open request of an account.
     *
     * @param accountId the account
     * @return the request, or empty where nothing waits
     */
    public Optional<NameChangeRequest> findOpenByAccount(int accountId) {
        return query("""
                SELECT %s FROM name_change_request
                WHERE account_id = :account_id AND decided_at IS NULL;""", NameChangeRequest.COLUMNS)
                .single(call().bind("account_id", accountId))
                .map(NameChangeRequest.map())
                .first();
    }

    /**
     * An open request by its id.
     *
     * @param id the request
     * @return the request, or empty where there is none or it was decided
     */
    public Optional<NameChangeRequest> findOpenById(int id) {
        return query("""
                SELECT %s FROM name_change_request
                WHERE id = :id AND decided_at IS NULL;""", NameChangeRequest.COLUMNS)
                .single(call().bind("id", id))
                .map(NameChangeRequest.map())
                .first();
    }

    /**
     * The open requests of the accounts behind a station's current members, oldest first.
     *
     * @param stationId the station
     * @return the requests, as the station sees them
     */
    public List<PendingNameChange> findOpenAtStation(int stationId) {
        return query("""
                SELECT
                    r.id                                        AS request_id,
                    sm.id                                       AS member_id,
                    sm.uid                                      AS member_uid,
                    trim(a.first_name || ' ' || a.last_name)    AS current_name,
                    trim(r.first_name || ' ' || r.last_name)    AS requested_name,
                    r.requested_at
                FROM name_change_request r
                JOIN account a ON a.id = r.account_id
                JOIN station_member sm
                    ON sm.account_id = r.account_id
                    AND sm.station_id = :station_id
                    AND sm.former = FALSE
                WHERE r.decided_at IS NULL
                ORDER BY r.requested_at, r.id;""")
                .single(call().bind("station_id", stationId))
                .map(PendingNameChange.map())
                .all();
    }

    /**
     * Closes an open request.
     *
     * @param id        the request
     * @param outcome   how it ends
     * @param decidedBy the account deciding, or null for the member withdrawing it
     * @param reason    why it was denied, or null
     * @return whether it was still open, which is false for the second of two decisions
     */
    public boolean decide(int id, NameChangeOutcome outcome, @Nullable Integer decidedBy, @Nullable String reason) {
        return query("""
                UPDATE name_change_request
                SET
                    decided_at = now(),
                    outcome    = :outcome,
                    decided_by = :decided_by,
                    reason     = :reason
                WHERE id = :id AND decided_at IS NULL;""")
                .single(call().bind("id", id)
                        .bind("outcome", outcome)
                        .bind("decided_by", decidedBy)
                        .bind("reason", reason))
                .update()
                .changed();
    }
}
