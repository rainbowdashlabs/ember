/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.accountlink.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import de.chojo.sadu.queries.converter.StandardValueConverter;
import dev.chojo.ember.api.auth.ClusterUserType;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * An association asking a person to take a role there with their existing account.
 *
 * @param id         the row's id
 * @param uid        how the person's and the association's screens name the request
 * @param clusterId  the association that asks
 * @param userType   the role it offers
 * @param accountId  the account it asks
 * @param address    the address the association typed
 * @param createdAt  when the association first asked
 * @param sentAt     when the request was last sent
 * @param expiresAt  until when the person may answer
 * @param answeredAt when the request was answered, or null while it waits
 * @param answer     how it ended, or null while it waits
 */
public record AssociationLinkRequest(
        int id,
        UUID uid,
        int clusterId,
        ClusterUserType userType,
        int accountId,
        String address,
        Instant createdAt,
        Instant sentAt,
        Instant expiresAt,
        @Nullable Instant answeredAt,
        @Nullable LinkAnswer answer) {

    /** The columns {@link #map()} reads. */
    public static final String COLUMNS =
            "id, uid, cluster_id, user_type, account_id, address, created_at, sent_at, expires_at, answered_at, answer";

    /**
     * Whether the request still waits for the person, which is the case while it has no answer and its
     * time has not run out, whether or not the sweep has marked it yet.
     *
     * @param now the moment asked about
     * @return whether the person may still answer
     */
    public boolean waits(Instant now) {
        return answer == null && expiresAt.isAfter(now);
    }

    /**
     * Where the request stands at a moment, which the sweep may not have written down yet.
     *
     * @param now the moment asked about
     * @return how it stands
     */
    public LinkStatus statusAt(Instant now) {
        return LinkStatus.of(answer, waits(now));
    }

    /**
     * Creates a row mapping for database result set conversion.
     */
    public static RowMapping<AssociationLinkRequest> map() {
        return row -> new AssociationLinkRequest(
                row.getInt("id"),
                row.get("uid", StandardValueConverter.UUID_STRING),
                row.getInt("cluster_id"),
                row.getEnum("user_type", ClusterUserType.class),
                row.getInt("account_id"),
                row.getString("address"),
                row.get("created_at", INSTANT_TIMESTAMP),
                row.get("sent_at", INSTANT_TIMESTAMP),
                row.get("expires_at", INSTANT_TIMESTAMP),
                row.get("answered_at", INSTANT_TIMESTAMP),
                row.getString("answer") == null ? null : row.getEnum("answer", LinkAnswer.class));
    }
}
