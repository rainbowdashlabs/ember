/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.tracking;

/**
 * Coverage status for a table or column within a specific context
 * (station export, station import, GDPR export, GDPR deletion).
 */
public enum Status {
    /**
     * Handled by the service and verified.
     */
    TRACKED,

    /**
     * Intentionally not handled. Requires a non-empty {@code reason}.
     */
    IGNORED,

    /**
     * New or changed - reviewer must decide. Verification test fails until resolved.
     */
    UNVERIFIED
}
