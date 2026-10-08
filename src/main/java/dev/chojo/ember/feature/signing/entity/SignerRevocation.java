/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.entity;

import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * Whether a signer's certificate is revoked, read from the current revocation list of the
 * installation's authority that issued it, the list it publishes, never from the network.
 *
 * @param status    what the list says
 * @param revokedAt from when the list names it revoked; null unless it is revoked. A seal whose
 *                  timestamp lies before this date was made while the key was still good
 * @param reason    why the list names it revoked; null unless it is revoked or when the list gives a
 *                  reason Ember never uses
 */
public record SignerRevocation(
        RevocationStatus status,
        @Nullable Instant revokedAt,
        @Nullable RevocationReason reason) {

    /** @return the answer for a certificate no authority of this installation issued */
    public static SignerRevocation unknown() {
        return new SignerRevocation(RevocationStatus.UNKNOWN, null, null);
    }

    /** @return the answer for a certificate its authority's list does not name */
    public static SignerRevocation good() {
        return new SignerRevocation(RevocationStatus.GOOD, null, null);
    }
}
