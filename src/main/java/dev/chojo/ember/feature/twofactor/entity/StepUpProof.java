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
    PASSWORD
}
