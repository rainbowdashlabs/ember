/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.entity;

import java.util.List;

/**
 * An open signature field a witness may collect, with the people it names, one of whom signs in front of
 * the witness.
 *
 * @param pending the field and its document
 * @param signers the people the field names, without the witness; none where nobody can sign it now
 */
public record WitnessedField(PendingSignature pending, List<WitnessPerson> signers) {
    /** Copies the people, so they cannot change after the fact. */
    public WitnessedField {
        signers = List.copyOf(signers);
    }
}
