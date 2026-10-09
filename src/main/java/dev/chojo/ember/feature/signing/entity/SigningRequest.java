/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.entity;

import dev.chojo.ember.util.Sha256;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/**
 * One person asked to sign one field of one document, with everything their act is bound to.
 *
 * <p>The content is the document as it was frozen when the request was made; its SHA-256 is what the
 * signer binds to, and it is computed from the bytes here rather than handed in beside them, so the two
 * can never disagree. The array is handed over as it is, without a copy.
 *
 * <p>Who may sign which field is decided when the request is made; a provider takes the request as given.
 *
 * @param requestUid the request, as it is known outside the provider
 * @param stationId  the station the document belongs to, whose key seals the result
 * @param contentPdf the frozen document the signer reads and signs
 * @param statement  the statement shown to the signer and confirmed by the act, exactly as shown
 * @param signer     who signs, and through whose account
 * @param fieldName  the name of the signature field the act fills, which carries its role
 * @param entries    what the signer typed into fields of their own, in the order the document lists
 *                   them; field names are unique
 */
public record SigningRequest(
        UUID requestUid,
        int stationId,
        byte[] contentPdf,
        String statement,
        Signer signer,
        String fieldName,
        List<SignerEntry> entries) {
    /** Copies the entries and refuses two values for one field. */
    public SigningRequest {
        entries = List.copyOf(entries);
        var fields = new HashSet<String>();
        for (SignerEntry entry : entries) {
            if (!fields.add(entry.field())) {
                throw new IllegalArgumentException("The field " + entry.field() + " is filled in twice");
            }
        }
    }

    /** @return the raw thirty-two byte SHA-256 of the frozen content */
    public byte[] contentSha256() {
        return Sha256.digest().digest(contentPdf);
    }
}
