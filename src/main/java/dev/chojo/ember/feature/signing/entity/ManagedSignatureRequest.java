/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * A request for signatures as the manager of its document sees it: every field with who is asked, how it
 * stands and the act that signed it, and the documents a correction could ask anew on.
 *
 * @param request       the request
 * @param documentTitle the title of the document it is on, or null once that was deleted
 * @param supersededBy  the request that replaced it after a correction, or null
 * @param fields        its fields, in the order they were asked for
 * @param corrections   documents generated for the same member since, which the request can be asked anew on;
 *                      none once the request was withdrawn or replaced
 */
public record ManagedSignatureRequest(
        SignatureRequest request,
        @Nullable String documentTitle,
        @Nullable UUID supersededBy,
        List<ManagedField> fields,
        List<Correction> corrections) {

    /**
     * One field of the request.
     *
     * @param field         who is asked to sign it and how it stands
     * @param nobodyCanSign whether it is open and nobody can sign it, so only paper, waiving or withdrawing
     *                      settles it
     * @param evidence      the act that signed it, or null where no act did
     */
    public record ManagedField(
            RequestedSignature field,
            boolean nobodyCanSign,
            @Nullable StoredEvidence evidence) {}

    /**
     * A document generated for the request's member after the request's own, filed and not yet asked to be
     * signed, which a correction can ask the request's signatures on anew.
     *
     * @param generationId the generation log entry of the document
     * @param templateName what its template is called now
     * @param generatedAt  when it was generated
     */
    public record Correction(int generationId, String templateName, Instant generatedAt) {

        /** Maps a row carrying {@code id}, {@code template_name} and {@code generated_at}. */
        public static RowMapping<Correction> map() {
            return row -> new Correction(
                    row.getInt("id"), row.getString("template_name"), row.get("generated_at", INSTANT_TIMESTAMP));
        }
    }
}
