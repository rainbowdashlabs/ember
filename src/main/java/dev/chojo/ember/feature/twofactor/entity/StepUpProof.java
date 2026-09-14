/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.twofactor.entity;

/**
 * A way a session can prove somebody is at the keyboard when step-up asks. The step-up refusal
 * names the set the account can currently give, so the dialog offers exactly those and never a
 * proof nobody can produce.
 */
public enum StepUpProof {
    /** A code from the authenticator app. */
    TOTP,
    /** An assertion from a second-factor security key. */
    SECURITY_KEY,
    /** A single-use backup code, offered alongside the factor it backs. */
    BACKUP_CODE,
    /** An assertion from a sign-in-capable passkey, user verification required. */
    PASSKEY,
    /** The account's password. Only a proof where no second factor is enrolled. */
    PASSWORD,
    /**
     * A device the account is already signed in on, confirming on the reader's behalf.
     *
     * <p>The answer for somebody with no password and no passkey on the machine in front of them,
     * which on a passwordless instance is every borrowed machine. The confirming device proves itself
     * locally, so the chain still ends in somebody answering for themselves.
     */
    ANOTHER_DEVICE;

    /**
     * Whether somebody proved themselves at this keyboard rather than on another device.
     *
     * <p>The distinction exists for the routes that vouch for a device. An approval has to rest on a
     * local proof, or two sessions could vouch for each other in a circle and the whole ladder would
     * stand on nothing.
     */
    public boolean isLocal() {
        return this != ANOTHER_DEVICE;
    }
}
