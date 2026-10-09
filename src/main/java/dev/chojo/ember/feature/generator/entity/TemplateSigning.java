/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.entity;

import org.jspecify.annotations.Nullable;

/**
 * How a template's documents are kept and sent once they are signed. A request for signatures copies both
 * when it is made, so a later change of the template never changes what a signer was told.
 *
 * @param retentionMonths how many months the signatures, their evidence and the signed document are kept
 *                        after the member has left or was deleted, or null to keep them only while the member
 *                        is a member and for the grace of an archived member
 * @param copyAttached    whether the copy a signer gets by mail carries the sealed PDF, rather than only its
 *                        SHA-256 and a link
 */
public record TemplateSigning(@Nullable Integer retentionMonths, boolean copyAttached) {

    /** The longest a template keeps signed documents after the member has gone, twenty years. */
    public static final int MAX_RETENTION_MONTHS = 240;

    /**
     * @param legal whether the template makes a legal document
     * @return what a new template starts with: a legal one keeps signed documents for
     *         {@link DocumentTemplate#LEGAL_SIGNATURE_RETENTION_MONTHS} months, any other only while the
     *         member is a member, and no copy carries the PDF
     */
    public static TemplateSigning startingWith(boolean legal) {
        return new TemplateSigning(legal ? DocumentTemplate.LEGAL_SIGNATURE_RETENTION_MONTHS : null, false);
    }
}
