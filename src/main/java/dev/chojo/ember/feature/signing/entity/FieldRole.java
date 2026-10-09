/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.entity;

/**
 * Who signs a signature field of a generated document, read back from the field's name.
 *
 * <p>The generator names a field after its signer: {@code participant}, {@code issuer}, {@code anyGuardian},
 * and {@code guardian<n>} for the guardian at that place. "Each guardian" and "guardian 1" both end up as
 * numbered guardian fields, so the document itself only knows a guardian by place, and that is what a
 * request for signatures asks for.
 */
public enum FieldRole {
    /** The member the document is about, with their own account or through a guardian's. */
    PARTICIPANT,
    /** The guardian at one place in the member's order, on behalf of the member. */
    GUARDIAN,
    /** Any one guardian of the member, on behalf of the member. */
    ANY_GUARDIAN,
    /** Whoever issues the document for the station. */
    ISSUER
}
