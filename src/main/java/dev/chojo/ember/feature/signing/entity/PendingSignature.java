/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

import static de.chojo.sadu.queries.converter.StandardValueConverter.UUID_STRING;

/**
 * An open signature field together with the document it is on.
 *
 * @param requestUid the request it belongs to
 * @param documentId the member document it is on, or null once that was deleted
 * @param memberName the official name of the member the document is about
 * @param field      the field
 */
public record PendingSignature(
        UUID requestUid, @Nullable Integer documentId, String memberName, RequestedSignature field) {

    /** Maps a field row joined with its request's {@code request_uid}, {@code document_id} and {@code request_member_name}. */
    public static RowMapping<PendingSignature> map() {
        RowMapping<RequestedSignature> field = RequestedSignature.map();
        return row -> new PendingSignature(
                row.get("request_uid", UUID_STRING),
                row.getObject("document_id", Integer.class),
                row.getString("request_member_name"),
                field.map(row));
    }
}
