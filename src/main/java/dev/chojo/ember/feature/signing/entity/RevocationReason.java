/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.entity;

/**
 * Why a station key was revoked, as the authority's revocation list states it. Each reason carries its
 * {@code CRLReason} code from RFC 5280, section 5.3.1.
 */
public enum RevocationReason {
    /** The private key leaked or may have; documents it sealed are no longer to be trusted on their own. */
    KEY_COMPROMISE(1),
    /** The key was replaced by another one and nothing is known against it. */
    SUPERSEDED(4),
    /** The station stopped sealing with the key and nothing is known against it. */
    CESSATION_OF_OPERATION(5);

    private final int code;

    RevocationReason(int code) {
        this.code = code;
    }

    /** @return the reason's {@code CRLReason} code */
    public int code() {
        return code;
    }
}
