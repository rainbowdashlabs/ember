/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mail.entity;

/**
 * How the connection to an SMTP relay is secured.
 */
public enum SmtpEncryption {
    /** Encrypted from the first byte, which is what a relay on port 465 expects. */
    IMPLICIT_TLS,
    /**
     * Plain to begin with and upgraded before the login is sent. The upgrade is required: a relay
     * that does not offer it, or whose offer was stripped on the way, gets nothing.
     */
    STARTTLS,
    /**
     * Not encrypted at all, so the login and every mail cross the network readable. Only ever used
     * when chosen on purpose, for a relay on the same internal network that cannot do better.
     */
    NONE;

    /**
     * What a setting stored before the choice had three values meant. {@code false} used to mean
     * STARTTLS when the relay offered it and plain text when it did not; it now means STARTTLS,
     * required.
     *
     * @param ssl the old flag
     */
    public static SmtpEncryption fromLegacySsl(boolean ssl) {
        return ssl ? IMPLICIT_TLS : STARTTLS;
    }
}
