/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.entity;

import dev.chojo.ember.feature.twofactor.entity.StepUpProof;

import java.net.URI;
import java.time.Instant;
import java.util.Set;

/**
 * How a signing act goes on once a provider has started it: in Ember, with a step-up the signer gives
 * here, or somewhere else the signer is sent to.
 */
public sealed interface SigningStart permits SigningStart.InEmber, SigningStart.Redirect {

    /**
     * The act happens in Ember. The nonce is kept on the server until the act completes and is handed
     * back with the confirmation; it never travels through the browser on the way back.
     *
     * <p>The arrays are handed over as they are, without a copy.
     *
     * @param nonce          thirty-two random bytes that make this attempt unpredictable and unique
     * @param challenge      the challenge a passkey or security key signs, bound to the nonce and to
     *                       everything the request names
     * @param acceptedProofs the proofs this signer may confirm with, never empty
     */
    record InEmber(byte[] nonce, byte[] challenge, Set<StepUpProof> acceptedProofs) implements SigningStart {
        /** Copies the proofs, so they cannot change after the fact. */
        public InEmber {
            acceptedProofs = Set.copyOf(acceptedProofs);
        }
    }

    /**
     * The act happens at an outside provider, which calls back when it is done.
     *
     * @param url       where the signer is sent
     * @param expiresAt when the address stops working
     */
    record Redirect(URI url, Instant expiresAt) implements SigningStart {}
}
