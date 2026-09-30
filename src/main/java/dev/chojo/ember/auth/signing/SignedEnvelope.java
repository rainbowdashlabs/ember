/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.auth.signing;

/**
 * What a signature covers, laid out the way one protocol lays it out.
 *
 * <p>Each protocol decides which parts of a request are bound into the signature and in which
 * order. That layout is part of the wire format between installations, so an envelope never
 * changes the bytes it produces for the same request: an older installation on the other end
 * computes them the same way.
 */
public sealed interface SignedEnvelope permits FederationEnvelope, RawBodyEnvelope {

    /**
     * The exact bytes the signature is computed over.
     *
     * @return the bytes to sign or verify
     */
    byte[] bytes();
}
