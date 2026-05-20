/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.account.entity;

/**
 * Enumerates the types of one-time tokens used for account operations.
 */
public enum TokenType {
    /**
     * Token sent to verify a newly registered email address.
     */
    VERIFY_EMAIL,
    /**
     * Token sent to set an initial password for an invited account.
     */
    SET_PASSWORD,
    /**
     * Token sent when a user requests a password reset.
     */
    RESET_PASSWORD,
    /**
     * Token issued at login when an admin has forced a password change.
     */
    FORCE_PASSWORD_CHANGE,
    /**
     * Token sent to confirm an email address change, with the new email stored as metadata.
     */
    EMAIL_CHANGE,
    /**
     * Token sent to confirm station deletion, with the station ID stored as metadata.
     */
    STATION_DELETE;

    /**
     * Parses a token type from its string representation (case-insensitive).
     *
     * @param value the string value to parse
     * @return the matching {@link TokenType}
     * @throws IllegalArgumentException if no matching type exists
     */
    public static TokenType fromValue(String value) {
        return valueOf(value.toUpperCase());
    }

    /**
     * Returns the lowercase string representation of this token type.
     *
     * @return the token type name in lowercase
     */
    public String value() {
        return name().toLowerCase();
    }
}
