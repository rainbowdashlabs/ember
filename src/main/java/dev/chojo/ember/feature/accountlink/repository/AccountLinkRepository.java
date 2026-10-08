/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.accountlink.repository;

import dev.chojo.ember.feature.accountlink.entity.AccountLinkRequest;
import dev.chojo.ember.feature.accountlink.entity.LinkOrigin;
import dev.chojo.ember.util.sql.SqlSupport;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * The requests stations send to link an existing account to one of their members.
 *
 * <p>A member waits for at most one link at a time; the answered requests stay behind as the record
 * of what was asked and how it ended.
 */
@Singleton
public class AccountLinkRepository {

    /**
     * Records a new request.
     *
     * @param stationId the station that asks
     * @param memberId  the member the account would be linked to
     * @param accountId the account it asks to link
     * @param origin    how the station came to ask
     * @param createdBy the member who invited the address, or null for an import
     * @param tokenHash the hash of the token in the mailed link, or null where no mail goes out
     * @param expiresAt until when the person may answer
     * @return the request
     */
    public AccountLinkRequest create(
            int stationId,
            int memberId,
            int accountId,
            LinkOrigin origin,
            @Nullable Integer createdBy,
            @Nullable String tokenHash,
            Instant expiresAt) {
        return SqlSupport.insertReturning(
                """
                INSERT INTO account_link_request(station_id, station_member_id, account_id, origin, created_by, token_hash, expires_at)
                VALUES (:station_id, :member_id, :account_id, :origin, :created_by, :token_hash, :expires_at)
                RETURNING %s;""",
                call().bind("station_id", stationId)
                        .bind("member_id", memberId)
                        .bind("account_id", accountId)
                        .bind("origin", origin)
                        .bind("created_by", createdBy)
                        .bind("token_hash", tokenHash)
                        .bind("expires_at", expiresAt, INSTANT_TIMESTAMP),
                AccountLinkRequest.map(),
                AccountLinkRequest.COLUMNS);
    }

    /**
     * The request this member waits for, answered or not by the sweep yet.
     *
     * @param memberId the member
     * @return the unanswered request, or empty where the member waits for none
     */
    public Optional<AccountLinkRequest> findUnansweredForMember(int memberId) {
        return query("""
                SELECT %s
                FROM account_link_request
                WHERE station_member_id = :member_id
                  AND answer IS NULL;""", AccountLinkRequest.COLUMNS)
                .single(call().bind("member_id", memberId))
                .map(AccountLinkRequest.map())
                .first();
    }
}
