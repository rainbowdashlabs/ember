/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.accountlink.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import de.chojo.sadu.queries.converter.StandardValueConverter;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * A station asking a person to link their existing account to one of its members.
 *
 * @param id         the row's id
 * @param uid        how the person's screens name the request
 * @param stationId  the station that asks
 * @param memberId   the member the account would be linked to
 * @param accountId  the account the station asks to link
 * @param origin     how the station came to ask
 * @param createdAt  when the station first asked
 * @param createdBy  the member who invited the address, or null for an import
 * @param sentAt     when the request was last sent
 * @param expiresAt  until when the person may answer
 * @param answeredAt when the request was answered, or null while it waits
 * @param answer     how it ended, or null while it waits
 */
public record AccountLinkRequest(
        int id,
        UUID uid,
        int stationId,
        int memberId,
        int accountId,
        LinkOrigin origin,
        Instant createdAt,
        @Nullable Integer createdBy,
        Instant sentAt,
        Instant expiresAt,
        @Nullable Instant answeredAt,
        @Nullable LinkAnswer answer) {

    /** The columns {@link #map()} reads. */
    public static final String COLUMNS =
            "id, uid, station_id, station_member_id, account_id, origin, created_at, created_by, sent_at, expires_at, answered_at, answer";

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
     * Creates a row mapping for database result set conversion.
     */
    public static RowMapping<AccountLinkRequest> map() {
        return row -> new AccountLinkRequest(
                row.getInt("id"),
                row.get("uid", StandardValueConverter.UUID_STRING),
                row.getInt("station_id"),
                row.getInt("station_member_id"),
                row.getInt("account_id"),
                row.getEnum("origin", LinkOrigin.class),
                row.get("created_at", INSTANT_TIMESTAMP),
                row.getObject("created_by", Integer.class),
                row.get("sent_at", INSTANT_TIMESTAMP),
                row.get("expires_at", INSTANT_TIMESTAMP),
                row.get("answered_at", INSTANT_TIMESTAMP),
                row.getString("answer") == null ? null : row.getEnum("answer", LinkAnswer.class));
    }
}
