/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.accountlink.repository;

import de.chojo.sadu.queries.converter.StandardValueConverter;
import dev.chojo.ember.feature.accountlink.entity.AccountLinkRequest;
import dev.chojo.ember.feature.accountlink.entity.LinkAnswer;
import dev.chojo.ember.feature.accountlink.entity.LinkOrigin;
import dev.chojo.ember.feature.accountlink.entity.LinkPrompt;
import dev.chojo.ember.util.sql.MemberNameSql;
import dev.chojo.ember.util.sql.SqlSupport;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

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
     * The requests waiting for this account, oldest first, as the person is shown them. A request whose
     * time is up is left out whether or not the sweep has marked it yet.
     *
     * @param accountId the account
     * @param now       the moment asked about
     * @return the requests
     */
    public List<LinkPrompt> findWaitingForAccount(int accountId, Instant now) {
        return query("""
                SELECT r.uid, s.name AS station_name, %s AS member_name, r.origin,
                       %s AS invited_by, r.created_at, r.expires_at
                FROM account_link_request r
                JOIN station s ON s.id = r.station_id
                JOIN station_member m ON m.id = r.station_member_id
                LEFT JOIN account ma ON ma.id = m.account_id
                LEFT JOIN station_member cb ON cb.id = r.created_by
                LEFT JOIN account cba ON cba.id = cb.account_id
                WHERE r.account_id = :account_id
                  AND r.answer IS NULL
                  AND r.expires_at > :now
                ORDER BY r.created_at, r.id;""", MemberNameSql.ofMember("m", "ma"), MemberNameSql.ofMemberOrNull("cb", "cba"))
                .single(call().bind("account_id", accountId).bind("now", now, INSTANT_TIMESTAMP))
                .map(LinkPrompt.map())
                .all();
    }

    /**
     * A request by the uid the person's screens name it by, whatever it stands.
     *
     * @param uid the request
     * @return the request, or empty where none carries the uid
     */
    public Optional<AccountLinkRequest> findByUid(UUID uid) {
        return query("SELECT %s FROM account_link_request WHERE uid = :uid::uuid;", AccountLinkRequest.COLUMNS)
                .single(call().bind("uid", uid, StandardValueConverter.UUID_STRING))
                .map(AccountLinkRequest.map())
                .first();
    }

    /**
     * The request a mailed link carries the token of. Answered requests carry no token any more.
     *
     * @param tokenHash the hash of the token
     * @return the request, or empty where no unanswered request carries it
     */
    public Optional<AccountLinkRequest> findByTokenHash(String tokenHash) {
        return query("SELECT %s FROM account_link_request WHERE token_hash = :token_hash;", AccountLinkRequest.COLUMNS)
                .single(call().bind("token_hash", tokenHash))
                .map(AccountLinkRequest.map())
                .first();
    }

    /**
     * The member's latest request, answered or not.
     *
     * @param memberId the member
     * @return the request, or empty where the station never asked for this member
     */
    public Optional<AccountLinkRequest> findLatestForMember(int memberId) {
        return query("""
                SELECT %s
                FROM account_link_request
                WHERE station_member_id = :member_id
                ORDER BY created_at DESC, id DESC
                LIMIT 1;""", AccountLinkRequest.COLUMNS)
                .single(call().bind("member_id", memberId))
                .map(AccountLinkRequest.map())
                .first();
    }

    /**
     * The latest request of every member of a station that has one, for the member list.
     *
     * @param stationId the station
     * @return each member's latest request, by member id
     */
    public Map<Integer, AccountLinkRequest> findLatestByStation(int stationId) {
        var latest = new HashMap<Integer, AccountLinkRequest>();
        query("""
                SELECT DISTINCT ON (station_member_id) %s
                FROM account_link_request
                WHERE station_id = :station_id
                ORDER BY station_member_id, created_at DESC, id DESC;""", AccountLinkRequest.COLUMNS)
                .single(call().bind("station_id", stationId))
                .map(AccountLinkRequest.map())
                .all()
                .forEach(request -> latest.put(request.memberId(), request));
        return latest;
    }

    /**
     * Answers a request that still waits, and drops its token.
     *
     * @param id     the request
     * @param answer how it ended
     * @return whether the request still waited, which only one answer finds
     */
    public boolean answer(int id, LinkAnswer answer) {
        return query("""
                UPDATE account_link_request
                SET answer = :answer, answered_at = now(), token_hash = NULL
                WHERE id = :id AND answer IS NULL;""")
                .single(call().bind("id", id).bind("answer", answer))
                .update()
                .changed();
    }

    /**
     * Sends a waiting request again: a fresh token, a fresh deadline, and the time it went out.
     *
     * @param id        the request
     * @param tokenHash the hash of the new token, or null where no mail goes out
     * @param expiresAt the new deadline
     * @return whether the request still waited
     */
    public boolean sendAgain(int id, @Nullable String tokenHash, Instant expiresAt) {
        return query("""
                UPDATE account_link_request
                SET token_hash = :token_hash, expires_at = :expires_at, sent_at = now()
                WHERE id = :id AND answer IS NULL;""")
                .single(call().bind("id", id)
                        .bind("token_hash", tokenHash)
                        .bind("expires_at", expiresAt, INSTANT_TIMESTAMP))
                .update()
                .changed();
    }

    /**
     * Marks every request whose time ran out without an answer as expired.
     *
     * @param now the moment asked about
     * @return how many ran out
     */
    public int expireOverdue(Instant now) {
        return query("""
                UPDATE account_link_request
                SET answer = 'EXPIRED', answered_at = now(), token_hash = NULL
                WHERE answer IS NULL AND expires_at <= :now;""")
                .single(call().bind("now", now, INSTANT_TIMESTAMP))
                .update()
                .rows();
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
