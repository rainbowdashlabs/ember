/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.conf.file.elements;

/**
 * Authentication configuration controlling token sizes and expiration durations
 * for sessions, email verification, and password reset tokens.
 */
@SuppressWarnings({"FieldCanBeLocal", "FieldMayBeFinal", "CanBeFinal"})
public class Auth {
    private int tokenBytes = 32;
    private int verifyTokenHours = 24;
    private int passwordTokenHours = 72;
    private int sessionMinutes = 30;

    public int tokenBytes() {
        return tokenBytes;
    }

    public int verifyTokenHours() {
        return verifyTokenHours;
    }

    public int passwordTokenHours() {
        return passwordTokenHours;
    }

    public int sessionMinutes() {
        return sessionMinutes;
    }

    @Override
    public String toString() {
        return "Auth{" + "tokenBytes="
                + tokenBytes + ", verifyTokenHours="
                + verifyTokenHours + ", passwordTokenHours="
                + passwordTokenHours + ", sessionMinutes="
                + sessionMinutes + '}';
    }
}
