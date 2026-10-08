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
 * One signature field of a request and who must sign it.
 *
 * @param id            the field identifier
 * @param requestId     the request it belongs to
 * @param fieldName     the name of the signature field in the document
 * @param role          who signs it
 * @param memberId      the member the document is about, or null once they were deleted
 * @param signerId      the member who must sign, or null for a field any guardian may sign, a guardian place
 *                      nobody held, and once that member was deleted
 * @param signerName    their official name when the request was made, or null where nobody is named
 * @param capacity      in what capacity the field is signed
 * @param statement     the statement the signer confirms
 * @param state         where the field stands
 * @param settledAt     when it stopped being open, or null while it is open
 * @param settledBy     who settled it, or null
 * @param settledByName their official name when they settled it, or null
 */
public record RequestedSignature(
        int id,
        int requestId,
        String fieldName,
        FieldRole role,
        @Nullable Integer memberId,
        @Nullable Integer signerId,
        @Nullable String signerName,
        SignerCapacity capacity,
        String statement,
        FieldState state,
        @Nullable Instant settledAt,
        @Nullable Integer settledBy,
        @Nullable String settledByName) {

    /** The columns {@link #map()} reads, in a form a query can splice in. */
    public static final String COLUMNS = """
            id, request_id, field_name, role, member_id, signer_id, signer_name, capacity, statement, state,
            settled_at, settled_by, settled_by_name""";

    /** Maps a row of the signature fields of requests. */
    public static RowMapping<RequestedSignature> map() {
        return row -> new RequestedSignature(
                row.getInt("id"),
                row.getInt("request_id"),
                row.getString("field_name"),
                row.getEnum("role", FieldRole.class),
                row.getObject("member_id", Integer.class),
                row.getObject("signer_id", Integer.class),
                row.getString("signer_name"),
                row.getEnum("capacity", SignerCapacity.class),
                row.getString("statement"),
                row.getEnum("state", FieldState.class),
                row.get("settled_at", INSTANT_TIMESTAMP),
                row.getObject("settled_by", Integer.class),
                row.getString("settled_by_name"));
    }

    /**
     * A field as it is asked for, before it is written.
     *
     * @param fieldName  the name of the signature field in the document
     * @param role       who signs it
     * @param signerId   the member who must sign, or null where nobody in particular is named
     * @param signerName their official name, or null where nobody is named
     * @param capacity   in what capacity it is signed
     * @param statement  the statement the signer confirms
     */
    public record Draft(
            String fieldName,
            FieldRole role,
            @Nullable Integer signerId,
            @Nullable String signerName,
            SignerCapacity capacity,
            String statement) {}
}
