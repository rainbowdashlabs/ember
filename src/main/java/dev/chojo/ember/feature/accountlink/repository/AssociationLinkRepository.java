/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.accountlink.repository;

import de.chojo.sadu.queries.converter.StandardValueConverter;
import dev.chojo.ember.api.auth.ClusterUserType;
import dev.chojo.ember.feature.accountlink.entity.AssociationLinkRequest;
import dev.chojo.ember.feature.accountlink.entity.LinkOrigin;
import dev.chojo.ember.feature.accountlink.entity.LinkPrompt;
import dev.chojo.ember.util.sql.SqlSupport;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * The requests associations send to an existing account to take a role there.
 *
 * <p>They share their table with the requests of stations, and answering, sending again and the expiry
 * are written by {@link AccountLinkRepository} for both. An association waits for at most one answer
 * per account at a time; the answered requests stay behind as the record of what was asked and how it
 * ended.
 */
@Singleton
public class AssociationLinkRepository {

    /**
     * Records a new request.
     *
     * @param clusterId the association that asks
     * @param accountId the account it asks
     * @param userType  the role it offers
     * @param address   the address it typed
     * @param tokenHash the hash of the token in the mailed link, or null where no mail goes out
     * @param expiresAt until when the person may answer
     * @return the request
     */
    public AssociationLinkRequest create(
            int clusterId,
            int accountId,
            ClusterUserType userType,
            String address,
            @Nullable String tokenHash,
            Instant expiresAt) {
        return SqlSupport.insertReturning(
                """
                INSERT INTO account_link_request(cluster_id, account_id, user_type, address, origin, token_hash, expires_at)
                VALUES (:cluster_id, :account_id, :user_type, :address, :origin, :token_hash, :expires_at)
                RETURNING %s;""",
                call().bind("cluster_id", clusterId)
                        .bind("account_id", accountId)
                        .bind("user_type", userType)
                        .bind("address", address)
                        .bind("origin", LinkOrigin.ASSOCIATION_INVITE)
                        .bind("token_hash", tokenHash)
                        .bind("expires_at", expiresAt, INSTANT_TIMESTAMP),
                AssociationLinkRequest.map(),
                AssociationLinkRequest.COLUMNS);
    }

    /**
     * The associations' requests waiting for this account, oldest first, as the person is shown them. A
     * request whose time is up is left out whether or not the sweep has marked it yet.
     *
     * @param accountId the account
     * @param now       the moment asked about
     * @return the requests
     */
    public List<LinkPrompt> findWaitingForAccount(int accountId, Instant now) {
        return query("""
                SELECT r.uid, NULL::text AS station_name, NULL::text AS member_name,
                       c.name AS association_name, r.user_type, r.origin,
                       NULL::text AS invited_by, r.created_at, r.expires_at
                FROM account_link_request r
                JOIN cluster c ON c.id = r.cluster_id
                WHERE r.account_id = :account_id
                  AND r.answer IS NULL
                  AND r.expires_at > :now
                ORDER BY r.created_at, r.id;""")
                .single(call().bind("account_id", accountId).bind("now", now, INSTANT_TIMESTAMP))
                .map(LinkPrompt.map())
                .all();
    }

    /**
     * An association's request by its uid, whatever it stands.
     *
     * @param uid the request
     * @return the request, or empty where no association's request carries the uid
     */
    public Optional<AssociationLinkRequest> findByUid(UUID uid) {
        return query("""
                SELECT %s FROM account_link_request
                WHERE uid = :uid::uuid AND cluster_id IS NOT NULL;""", AssociationLinkRequest.COLUMNS)
                .single(call().bind("uid", uid, StandardValueConverter.UUID_STRING))
                .map(AssociationLinkRequest.map())
                .first();
    }

    /**
     * The association's request a mailed link carries the token of. Answered requests carry no token any
     * more.
     *
     * @param tokenHash the hash of the token
     * @return the request, or empty where no unanswered request of an association carries it
     */
    public Optional<AssociationLinkRequest> findByTokenHash(String tokenHash) {
        return query("""
                SELECT %s FROM account_link_request
                WHERE token_hash = :token_hash AND cluster_id IS NOT NULL;""", AssociationLinkRequest.COLUMNS)
                .single(call().bind("token_hash", tokenHash))
                .map(AssociationLinkRequest.map())
                .first();
    }

    /**
     * The request the association waits on from this account, answered or not by the sweep yet.
     *
     * @param clusterId the association
     * @param accountId the account
     * @return the unanswered request, or empty where the association waits for none
     */
    public Optional<AssociationLinkRequest> findUnanswered(int clusterId, int accountId) {
        return query("""
                SELECT %s
                FROM account_link_request
                WHERE cluster_id = :cluster_id
                  AND account_id = :account_id
                  AND answer IS NULL;""", AssociationLinkRequest.COLUMNS)
                .single(call().bind("cluster_id", clusterId).bind("account_id", accountId))
                .map(AssociationLinkRequest.map())
                .first();
    }

    /**
     * The association's latest request to this account, answered or not.
     *
     * @param clusterId the association
     * @param accountId the account
     * @return the request, or empty where the association never asked this account
     */
    public Optional<AssociationLinkRequest> findLatest(int clusterId, int accountId) {
        return query("""
                SELECT %s
                FROM account_link_request
                WHERE cluster_id = :cluster_id
                  AND account_id = :account_id
                ORDER BY created_at DESC, id DESC
                LIMIT 1;""", AssociationLinkRequest.COLUMNS)
                .single(call().bind("cluster_id", clusterId).bind("account_id", accountId))
                .map(AssociationLinkRequest.map())
                .first();
    }

    /**
     * The association's latest request to every account it ever asked, newest first, for its member page.
     *
     * @param clusterId the association
     * @return each account's latest request
     */
    public List<AssociationLinkRequest> findLatestByCluster(int clusterId) {
        return query("""
                SELECT *
                FROM (SELECT DISTINCT ON (account_id) %s
                      FROM account_link_request
                      WHERE cluster_id = :cluster_id
                      ORDER BY account_id, created_at DESC, id DESC) latest
                ORDER BY created_at DESC, id DESC;""", AssociationLinkRequest.COLUMNS)
                .single(call().bind("cluster_id", clusterId))
                .map(AssociationLinkRequest.map())
                .all();
    }
}
