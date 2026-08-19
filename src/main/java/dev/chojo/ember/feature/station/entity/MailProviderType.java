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

    /**
     * The last part of the address this provider reports delivery events to.
     *
     * <p>Each provider sends a report of its own shape, so the address says which shape to expect.
     * A provider that reports nothing of its own is read as Brevo's, which is what a plain server
     * relayed through one looks like.
     */
    /**
     * Reads a provider from what a request named, empty when it named nothing we know.
     */
    public static java.util.Optional<MailProviderType> fromName(String value) {
        if (value == null || value.isBlank()) return java.util.Optional.empty();
        try {
            return java.util.Optional.of(valueOf(value.trim().toUpperCase(java.util.Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return java.util.Optional.empty();
        }
    }

    public String webhookPath() {
        return switch (this) {
            case SWEEGO -> "mail/sweego";
            case TWILIO -> "mail/sendgrid";
            default -> "mail/brevo";
        };
    }
}
