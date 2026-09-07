/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.twofactor.entity;

/**
 * Which ceremony a pending WebAuthn challenge was minted for. A challenge is only spendable at
 * the finish of its own ceremony: a token minted for one purpose is refused at every other
 * finish, so the difference between two ceremonies never comes down to which URL the browser
 * chose to call.
 */
public enum ChallengePurpose {
    /**
     * A credential registration ceremony, second factor and passkey alike.
     */
    REGISTRATION,
    /**
     * An assertion asked for after a password: login second step or step-up.
     */
    SECOND_FACTOR_ASSERTION,
    /**
     * A passwordless sign-in assertion. The only purpose whose challenge knows no account.
     */
    PASSKEY_SIGN_IN,
    /**
     * The trial that follows a passkey creation: cryptographically the sign-in, but verified
     * against the session's own account and minting nothing.
     */
    PASSKEY_TRIAL,
    /**
     * A passkey assertion answering a step-up demand: verified like the sign-in, against the
     * session's own account, stamping freshness and minting nothing.
     */
    STEPUP_ASSERTION,
    /**
     * A registration opened by a device-enrolment token rather than a session.
     */
    DEVICE_ENROLLMENT
}
