/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;
import static de.chojo.sadu.queries.converter.StandardValueConverter.UUID_STRING;

/**
 * Signatures asked for on one generated document.
 *
 * @param id              the request identifier
 * @param uid             the request as signing acts name it
 * @param stationId       the station the document belongs to
 * @param generationId    the generation log entry the document came from, or null once that is gone
 * @param documentId      the member document the file was filed as, or null once that was deleted
 * @param memberId        the member the document is about, or null once they were deleted
 * @param memberName      their official name when the request was made
 * @param contentSha256   SHA-256 of the frozen file every signature binds to, lower-case hexadecimal
 * @param state           where the request stands
 * @param supersededBy    the request for the corrected document that replaced this one, or null
 * @param retentionMonths how long it is kept after the member is gone, or null to keep it only while they
 *                        are a member
 * @param retainUntil     until when it is kept, set once the member is gone; null while they are a member
 * @param createdAt       when the signatures were asked for
 * @param createdBy       who asked for them, or null
 * @param closedAt        when it stopped waiting, or null while it is open
 * @param copyAttached    whether the copy each signer gets by mail carries the sealed PDF
 */
public record SignatureRequest(
        int id,
        UUID uid,
        int stationId,
        @Nullable Integer generationId,
        @Nullable Integer documentId,
        @Nullable Integer memberId,
        String memberName,
        String contentSha256,
        RequestState state,
        @Nullable Integer supersededBy,
        @Nullable Integer retentionMonths,
        @Nullable Instant retainUntil,
        Instant createdAt,
        @Nullable Integer createdBy,
        @Nullable Instant closedAt,
        boolean copyAttached) {

    /** The columns {@link #map()} reads, in a form a query can splice in. */
    public static final String COLUMNS = """
            id, uid, station_id, generation_id, document_id, member_id, member_name, content_sha256, state,
            superseded_by, retention_months, retain_until, created_at, created_by, closed_at, copy_attached""";

    /** Maps a row of the signing requests. */
    public static RowMapping<SignatureRequest> map() {
        return row -> new SignatureRequest(
                row.getInt("id"),
                row.get("uid", UUID_STRING),
                row.getInt("station_id"),
                row.getObject("generation_id", Integer.class),
                row.getObject("document_id", Integer.class),
                row.getObject("member_id", Integer.class),
                row.getString("member_name"),
                row.getString("content_sha256"),
                row.getEnum("state", RequestState.class),
                row.getObject("superseded_by", Integer.class),
                row.getObject("retention_months", Integer.class),
                row.get("retain_until", INSTANT_TIMESTAMP),
                row.get("created_at", INSTANT_TIMESTAMP),
                row.getObject("created_by", Integer.class),
                row.get("closed_at", INSTANT_TIMESTAMP),
                row.getBoolean("copy_attached"));
    }

    /** @return whether a field of it may still be signed or settled */
    public boolean open() {
        return state == RequestState.OPEN;
    }

    /**
     * A request as it is asked for, before it is written.
     *
     * @param stationId     the station the document belongs to
     * @param generationId  the generation log entry the document came from
     * @param templateId    the template it came from, whose retention the request copies
     * @param documentId    the member document the file was filed as
     * @param memberId      the member the document is about
     * @param memberName    their official name
     * @param contentSha256 SHA-256 of the generated file, lower-case hexadecimal
     * @param createdBy     who asks for the signatures, or null where nobody in particular does
     */
    public record Draft(
            int stationId,
            int generationId,
            int templateId,
            int documentId,
            int memberId,
            String memberName,
            String contentSha256,
            @Nullable Integer createdBy) {}

    /**
     * A request for a document a partner station handed out for its appointment, as it is asked for: nobody
     * here generated it and nobody here asks, so it names no generation and nobody who asked.
     *
     * @param stationId       the station of the member
     * @param documentId      the member document the copy was filed as
     * @param memberId        the member asked to sign
     * @param memberName      their official name
     * @param contentSha256   SHA-256 of the copy, lower-case hexadecimal
     * @param retentionMonths how long the partner's template keeps signed documents, or null
     * @param copyAttached    whether a signer's copy by mail may carry the sealed PDF
     */
    public record PartnerDraft(
            int stationId,
            int documentId,
            int memberId,
            String memberName,
            String contentSha256,
            @Nullable Integer retentionMonths,
            boolean copyAttached) {}
}
