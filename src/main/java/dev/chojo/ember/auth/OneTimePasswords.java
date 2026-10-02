/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.auth;

import dev.chojo.ember.util.RandomTokens;

import java.util.StringJoiner;

/**
 * The passwords an administrator hands to somebody who cannot be sent a link.
 *
 * <p>Sixteen characters in four groups of four, joined by hyphens, and the hyphens belong to the
 * password: what is typed is exactly what was shown. The alphabet is lower case and leaves out the
 * characters that are mistaken for one another when read off a sheet of paper ({@code 0} and
 * {@code o}, {@code 1}, {@code l} and {@code i}). Sixteen characters from thirty-one is about 79 bits,
 * which a password that is changed at the first sign-in and expires within days does not need to
 * exceed, and the result is longer than {@link PasswordPolicy#MIN_LENGTH}.
 */
public final class OneTimePasswords {

    /** What every character is drawn from. */
    public static final String ALPHABET = "abcdefghjkmnpqrstuvwxyz23456789";

    /** How many groups a password has. */
    public static final int GROUPS = 4;

    /** How many characters each group has. */
    public static final int GROUP_LENGTH = 4;

    private OneTimePasswords() {}

    /**
     * A fresh one-time password.
     *
     * @return the password, grouped as it is shown and typed
     */
    public static String generate() {
        var password = new StringJoiner("-");
        for (int group = 0; group < GROUPS; group++) {
            password.add(RandomTokens.code(ALPHABET, GROUP_LENGTH));
        }
        return password.toString();
    }
}
