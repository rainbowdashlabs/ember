/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.entity;

/** Why a partner's statement of its signing authorities was not taken. Nothing it names is pinned then. */
public enum StatementRefusal {
    /** The partnership holds no federation key of the partner to check the statement against. */
    NO_PARTNER_KEY,
    /** The statement names another station as the one making it. */
    WRONG_STATION,
    /** The statement was made to another station. */
    WRONG_RECIPIENT,
    /** The statement answers another challenge than the one asked with. */
    WRONG_CHALLENGE,
    /** The statement's time is no moment. */
    NO_MOMENT,
    /** The signature does not fit the partner's federation key, so somebody else made or changed it. */
    SIGNATURE,
    /** A certificate in it is no self-signed authority, or a revocation list in it is not that authority's. */
    NOT_AN_AUTHORITY
}
