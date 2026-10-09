/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.entity;

import org.jspecify.annotations.Nullable;

/**
 * The signature picture a signer signs with: one made for this act, or, where none was made, the one their
 * account keeps.
 *
 * <p>The array is handed over as it is, without a copy.
 *
 * @param made   the picture made for this act as it was sent, or null to sign with the saved one
 * @param source how the picture made for this act was made, or null where it does not say
 * @param keep   whether the picture made for this act replaces the saved one afterwards; never for a member
 *               signing through another person's account, whose picture is not that person's
 */
public record SigningPicture(
        byte @Nullable [] made, @Nullable SignatureImageSource source, boolean keep) {

    /** Signs with the saved picture. */
    public static final SigningPicture SAVED = new SigningPicture(null, null, false);
}
