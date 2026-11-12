/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.account.entity;

/**
 * What came of an instance administrator sending an account the mail it waits for.
 *
 * @param outcome what actually happened
 */
public record AccountSetupMail(Outcome outcome) {

    /** What came of the request. */
    public enum Outcome {
        /** The invitation to choose a password went out. */
        INVITATION_SENT,
        /** The request to confirm the address went out. */
        CONFIRMATION_SENT,
        /**
         * No mail went out: the instance cannot send any, so the address was marked confirmed
         * instead, as it would be at the account's own next request.
         */
        CONFIRMED
    }
}
