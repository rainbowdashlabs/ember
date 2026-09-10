/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mailimport.entity;

/**
 * How a mailbox connection is secured.
 */
public enum MailSecurity {
    /** Encrypted from the first byte, which is what a provider on port 993 expects. */
    SSL,
    /** Plain to begin with and upgraded before the password is sent, which is port 143 done properly. */
    STARTTLS,
    /**
     * Not encrypted at all. Offered because a station may read a mailbox on a host beside it on the
     * same network, and refused anywhere the address is not private.
     */
    NONE
}
