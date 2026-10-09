/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * A signed agreement its signer withdrew. The request it withdraws stays with its signed fields and their
 * evidence; the withdrawal is sealed into a version of the document of its own.
 *
 * @param id              the withdrawal identifier
 * @param requestId       the request whose agreement was withdrawn
 * @param memberId        the member the document is about, or null once they were deleted
 * @param withdrawnBy     the member who withdrew it, or null once they are gone
 * @param withdrawnByName their official name when they withdrew it
 * @param capacity        {@link SignerCapacity#ACCOUNT_HOLDER} where the member or a signer withdrew for
 *                        themselves, {@link SignerCapacity#GUARDIAN} where a guardian withdrew for the member
 * @param reason          why, as written, or null where no reason was given
 * @param withdrawnAt     when
 * @param truncatedIp     the network it came from, shortened, or null
 * @param userAgent       the browser it came from, or null
 * @param sealedSha256    SHA-256 of the first sealed version carrying it, or null until it is sealed
 */
public record SignatureWithdrawal(
        int id,
        int requestId,
        @Nullable Integer memberId,
        @Nullable Integer withdrawnBy,
        String withdrawnByName,
        SignerCapacity capacity,
        @Nullable String reason,
        Instant withdrawnAt,
        @Nullable String truncatedIp,
        @Nullable String userAgent,
        @Nullable String sealedSha256) {

    /** The columns {@link #map()} reads, in a form a query can splice in. */
    public static final String COLUMNS = """
            id, request_id, member_id, withdrawn_by, withdrawn_by_name, capacity, reason, withdrawn_at, truncated_ip,
            user_agent, sealed_sha256""";

    /** Maps a row of the withdrawals. */
    public static RowMapping<SignatureWithdrawal> map() {
        return row -> new SignatureWithdrawal(
                row.getInt("id"),
                row.getInt("request_id"),
                row.getObject("member_id", Integer.class),
                row.getObject("withdrawn_by", Integer.class),
                row.getString("withdrawn_by_name"),
                row.getEnum("capacity", SignerCapacity.class),
                row.getString("reason"),
                row.get("withdrawn_at", INSTANT_TIMESTAMP),
                row.getString("truncated_ip"),
                row.getString("user_agent"),
                row.getString("sealed_sha256"));
    }

    /**
     * A withdrawal as it is made, before it is written.
     *
     * @param requestId       the request whose agreement is withdrawn
     * @param memberId        the member the document is about
     * @param withdrawnBy     the member who withdraws it
     * @param withdrawnByName their official name
     * @param capacity        in what capacity they withdraw it
     * @param reason          why, or null
     * @param withdrawnAt     when
     * @param truncatedIp     the network it comes from, shortened, or null
     * @param userAgent       the browser it comes from, or null
     */
    public record Draft(
            int requestId,
            @Nullable Integer memberId,
            int withdrawnBy,
            String withdrawnByName,
            SignerCapacity capacity,
            @Nullable String reason,
            Instant withdrawnAt,
            @Nullable String truncatedIp,
            @Nullable String userAgent) {}
}
