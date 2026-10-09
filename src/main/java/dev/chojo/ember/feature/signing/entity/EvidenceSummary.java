/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.entity;

import dev.chojo.ember.feature.twofactor.entity.StepUpProof;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * What the evidence a signed document carries says about its signatures, in short, for the verification
 * page. It is read from the attachment as it is; whether the station's seal covers it is what the seals
 * beside it say.
 *
 * @param requestUid    the request for signatures the document belongs to
 * @param contentSha256 SHA-256 of the content every signature binds to, lower-case hexadecimal
 * @param fields        every signature field, in the order the evidence lists them
 * @param issued        the issuer's signature the document carried from its generation, or null
 */
public record EvidenceSummary(
        UUID requestUid, String contentSha256, List<Field> fields, SigningEvidenceFile.@Nullable Issued issued) {

    /**
     * @param file the evidence a document carries
     * @return what it says in short, its fields in a list that cannot change
     */
    public static EvidenceSummary of(SigningEvidenceFile file) {
        return new EvidenceSummary(
                file.requestUid(),
                file.contentSha256(),
                file.fields().stream().map(Field::of).toList(),
                file.issued());
    }

    /**
     * One signature field.
     *
     * @param fieldName  its name in the document
     * @param role       who signs it
     * @param state      where it stands
     * @param signerName the official name of whoever signed it, or null where nobody did electronically
     * @param signedAt   when it was signed, or null
     * @param proof      what the signature was confirmed with, or null
     * @param bound      whether that proof is bound to the document
     * @param together   whether it was confirmed with one proof together with other fields
     */
    public record Field(
            String fieldName,
            FieldRole role,
            FieldState state,
            @Nullable String signerName,
            @Nullable Instant signedAt,
            @Nullable StepUpProof proof,
            boolean bound,
            boolean together) {

        static Field of(SigningEvidenceFile.Field field) {
            var act = field.act();
            return new Field(
                    field.fieldName(),
                    field.role(),
                    field.state(),
                    act == null ? null : act.signerName(),
                    act == null ? null : act.signedAt(),
                    act == null ? null : act.proof(),
                    act != null && act.boundToDocument(),
                    act != null && act.batch() != null);
        }
    }
}
