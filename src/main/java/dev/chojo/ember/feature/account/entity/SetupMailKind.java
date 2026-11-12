/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.account.entity;

import org.jspecify.annotations.Nullable;

/**
 * Which mail an instance administrator can send an account that is not yet ready: the invitation to
 * choose a password, or the request to confirm its address.
 */
public enum SetupMailKind {
    /** The account has no password its person chose, so it is sent the invitation to set one. */
    INVITATION,
    /** The account has a password but its real address was never confirmed. */
    CONFIRMATION;

    /**
     * The mail an account would be sent, by one rule for the list and the action alike. An account
     * still waiting for its setup gets the invitation; one with a password and a real address never
     * confirmed gets the confirmation; any other needs neither.
     *
     * @param awaitsSetup whether it has never been set up and holds no password its person chose
     * @param realAddress whether its address is one mail can reach, rather than a stand-in
     * @param confirmed   whether its address was confirmed
     * @return the mail, or {@code null} when there is none to send
     */
    public static @Nullable SetupMailKind of(boolean awaitsSetup, boolean realAddress, boolean confirmed) {
        if (awaitsSetup) return INVITATION;
        if (realAddress && !confirmed) return CONFIRMATION;
        return null;
    }
}
