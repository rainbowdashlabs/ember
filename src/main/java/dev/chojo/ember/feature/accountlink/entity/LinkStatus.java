/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.accountlink.entity;

import org.jspecify.annotations.Nullable;

/** How a link request stands. */
public enum LinkStatus {
    /** The person has not answered yet and still may. */
    WAITING,
    /** The person took the account in. */
    ACCEPTED,
    /** The person refused. */
    DECLINED,
    /** Thirty days passed without an answer. */
    EXPIRED;

    /**
     * Where a request stands, which the sweep may not have written down yet: one without an answer whose
     * time ran out has expired all the same.
     *
     * @param answer     how it ended, or null while it waits
     * @param stillWaits whether its time has not run out
     * @return how it stands
     */
    public static LinkStatus of(@Nullable LinkAnswer answer, boolean stillWaits) {
        if (answer == null) return stillWaits ? WAITING : EXPIRED;
        return switch (answer) {
            case ACCEPTED -> ACCEPTED;
            case DECLINED -> DECLINED;
            case EXPIRED -> EXPIRED;
        };
    }
}
