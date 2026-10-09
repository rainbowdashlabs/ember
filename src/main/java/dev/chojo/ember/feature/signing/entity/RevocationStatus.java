/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.entity;

/**
 * What this installation's current revocation lists say about a signer's certificate.
 */
public enum RevocationStatus {
    /** One of this installation's authorities issued the certificate and its list does not name it. */
    GOOD,
    /** One of this installation's authorities issued the certificate and its list names it as revoked. */
    REVOKED,
    /** No authority of this installation issued the certificate, so its lists say nothing about it. */
    UNKNOWN
}
