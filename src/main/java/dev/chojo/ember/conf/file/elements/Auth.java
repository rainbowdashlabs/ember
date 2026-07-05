/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.conf.file.elements;

import dev.chojo.ocular.override.Env;
import dev.chojo.ocular.override.Overwrite;

/**
 * Authentication configuration controlling token sizes and expiration durations
 * for sessions, email verification, and password reset tokens.
 */
@SuppressWarnings({"FieldCanBeLocal", "FieldMayBeFinal", "CanBeFinal"})
public class Auth {
    @Overwrite(env = @Env)
    private int tokenBytes = 32;

    @Overwrite(env = @Env)
    private int verifyTokenHours = 24;

    @Overwrite(env = @Env)
    private int passwordTokenHours = 72;

    /**
     * Lifetime of a self-service password-reset link. Kept short because it is a
     * bearer secret delivered by email; the longer {@link #passwordTokenHours}
     * window applies only to operator-initiated invites and admin resets.
     */
    @Overwrite(env = @Env)
    private int resetTokenHours = 1;

    @Overwrite(env = @Env)
    private int sessionMinutes = 30;

    /**
     * Server-side secret mixed into the HMAC used to hash session and recovery
     * tokens before they are stored in the database. Empty by default; the
     * application refuses to boot in production with a blank value. A
     * deterministic placeholder is substituted during demo / dev runs so local
     * smoke tests do not have to configure a pepper.
     */
    @Overwrite(env = @Env)
    private String tokenPepper = "";

    /**
     * "Have I Been Pwned" k-anonymity breach-check configuration. New passwords
     * are checked synchronously at set time and asynchronously after every
     * successful login; pwned credentials are rejected on set and flagged for
     * forced rotation on login.
     */
    private HibpSettings hibp = new HibpSettings();

    private TwoFactorSettings twoFactor = new TwoFactorSettings();

    public int tokenBytes() {
        return tokenBytes;
    }

    public int verifyTokenHours() {
        return verifyTokenHours;
    }

    public int passwordTokenHours() {
        return passwordTokenHours;
    }

    public int resetTokenHours() {
        return resetTokenHours;
    }

    public int sessionMinutes() {
        return sessionMinutes;
    }

    /**
     * Returns the server-side pepper mixed into HMAC-SHA-256 when hashing
     * bearer-shaped tokens for at-rest storage.
     */
    public String tokenPepper() {
        return tokenPepper;
    }

    public HibpSettings hibp() {
        return hibp;
    }

    public TwoFactorSettings twoFactor() {
        return twoFactor;
    }

    @Override
    public String toString() {
        return "Auth{" + "tokenBytes="
                + tokenBytes + ", verifyTokenHours="
                + verifyTokenHours + ", passwordTokenHours="
                + passwordTokenHours + ", resetTokenHours="
                + resetTokenHours + ", sessionMinutes="
                + sessionMinutes + ", tokenPepperConfigured="
                + !tokenPepper.isBlank() + ", hibp="
                + hibp + '}';
    }
}
