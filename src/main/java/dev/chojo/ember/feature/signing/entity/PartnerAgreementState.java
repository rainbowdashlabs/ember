/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.entity;

/**
 * Where a document an appointment asks partners to sign stands for one member of a partner station, as the
 * station holding the appointment sees it.
 */
public enum PartnerAgreementState {
    /**
     * The partner never said it takes the document on: its installation cannot sign, or signing failed there.
     * The member is registered all the same; a signed paper copy can be confirmed here.
     */
    MISSING,
    /** The partner took the document on and asks for it to be signed there. */
    ASKED,
    /** A sealed copy came back from the partner; whether every field is settled is said beside it. */
    SIGNED,
    /** A manager here confirmed a signed paper copy, and no sealed copy came back. */
    PAPER_CONFIRMED,
    /** The partner reported the signed agreement withdrawn there. */
    WITHDRAWN
}
