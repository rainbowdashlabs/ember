/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.auth.signing;

/**
 * The signature algorithms Ember's signed requests use, one per protocol family.
 */
public enum SignatureAlgorithm {
    /** RSA PKCS#1 v1.5 over SHA-256, used by federation between stations. */
    RSA_SHA256("SHA256withRSA"),
    /** Ed25519, used by discovery and beacon traffic between instances. */
    ED25519("Ed25519");

    private final String jcaName;

    SignatureAlgorithm(String jcaName) {
        this.jcaName = jcaName;
    }

    /**
     * The name the Java security provider knows the algorithm by.
     *
     * @return the JCA algorithm name
     */
    public String jcaName() {
        return jcaName;
    }
}
