/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service;

import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.feature.generator.entity.FillInField;
import dev.chojo.ember.feature.generator.entity.SignatureRole;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.stream.IntStream;

/**
 * Checks the fields a template asks its signers to fill in when they sign, the same for a letter and a PDF
 * template.
 *
 * <p>A field to fill in belongs to a signer, named the way a signature field names one. In a member's
 * document it becomes one field for each signature field that signer has there, so a field for every
 * guardian is filled in by each guardian in their own act. That only works where the template has a
 * signature field for that signer: a field nobody signs would be filled in by nobody, so it is refused.
 *
 * <p>The issuer never fills a field in. Their signature can be made automatically with no act of theirs,
 * which would leave such a field empty and open to change in a document already signed for them.
 */
final class FillInChecks {
    private FillInChecks() {}

    /**
     * A field to fill in as it is kept.
     *
     * @param signer    who fills it in
     * @param label     what it asks for
     * @param maxLength the most characters it takes, or null for {@value FillInField#LONGEST_VALUE}
     */
    record Checked(
            SignatureRole signer, String label, @Nullable Integer maxLength) {}

    /**
     * @param signer    who fills the field in, as the editor sent it
     * @param label     what it asks for, as the editor sent it
     * @param maxLength the most characters it takes, as the editor sent it
     * @return the field as it is kept, its label stripped
     */
    static Checked checked(@Nullable SignatureRole signer, @Nullable String label, @Nullable Integer maxLength) {
        String stripped = label == null ? "" : label.strip();
        if (signer == null || stripped.isEmpty()) throw DocumentRefusal.DOCUMENT_TEMPLATE_FILL_IN_INCOMPLETE.raise();
        if (signer == SignatureRole.ISSUER) throw DocumentRefusal.DOCUMENT_TEMPLATE_FILL_IN_FOR_ISSUER.raise();
        if (stripped.length() > FillInField.LONGEST_LABEL) {
            throw DocumentRefusal.DOCUMENT_TEMPLATE_FILL_IN_LABEL_TOO_LONG.raise();
        }
        if (maxLength != null && (maxLength < 1 || maxLength > FillInField.LONGEST_VALUE)) {
            throw DocumentRefusal.DOCUMENT_TEMPLATE_FILL_IN_LENGTH_OUT_OF_RANGE.raise();
        }
        return new Checked(signer, stripped, maxLength);
    }

    /**
     * Refuses a field to fill in whose signer has no signature field in the template for some number of
     * guardians, and a signer asked to fill in more fields than one act takes.
     *
     * @param signatures the signers of the template's signature fields
     * @param fillIns    the signers of its fields to fill in, one entry per field
     */
    static void requireSigners(Collection<SignatureRole> signatures, Collection<SignatureRole> fillIns) {
        IntStream.rangeClosed(0, SignatureRole.GUARDIANS_CHECKED).forEach(guardians -> {
            var signed = new HashSet<String>();
            signatures.forEach(role -> signed.addAll(role.fieldNames(guardians)));
            var counts = new HashMap<String, Integer>();
            for (var role : fillIns) {
                for (var name : role.fieldNames(guardians)) {
                    if (!signed.contains(name)) {
                        throw DocumentRefusal.DOCUMENT_TEMPLATE_FILL_IN_WITHOUT_SIGNATURE.raise();
                    }
                    if (counts.merge(name, 1, Integer::sum) > FillInField.MOST_PER_SIGNER) {
                        throw DocumentRefusal.DOCUMENT_TEMPLATE_FILL_IN_TOO_MANY.raise();
                    }
                }
            }
        });
    }
}
