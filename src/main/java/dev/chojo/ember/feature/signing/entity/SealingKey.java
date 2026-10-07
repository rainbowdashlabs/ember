/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.entity;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.List;

/**
 * A station's key, ready to seal a document with.
 *
 * @param privateKey the station's private key, unwrapped
 * @param chain      the station certificate first, then the installation's authority certificate
 */
public record SealingKey(PrivateKey privateKey, List<X509Certificate> chain) {
    /** Copies the chain, so it cannot change after the fact. */
    public SealingKey {
        chain = List.copyOf(chain);
    }

    /** @return the station certificate the key belongs to */
    public X509Certificate certificate() {
        return chain.getFirst();
    }

    /** @return the installation's authority certificate that issued the station certificate */
    public X509Certificate authority() {
        return chain.getLast();
    }
}
