/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.entity;

/**
 * Supported mail provider types for station email configuration.
 */
public enum MailProviderType {
    NONE,
    SMTP,
    RAPIDMAIL,
    TWILIO,
    SWEEGO,
    BREVO;

    /**
     * Whether this provider is reached at an address the sender has to supply.
     *
     * <p>Most relays answer at one well-known name for everybody. Sweego does not: every account
     * gets its own relay host and port, shown in its credentials page, so its address can no more
     * be hardcoded than that of somebody's own server.
     */
    public boolean requiresServer() {
        return this == SMTP || this == SWEEGO;
    }
}
