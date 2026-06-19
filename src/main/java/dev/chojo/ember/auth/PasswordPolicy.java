/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.auth;

/**
 * Centralised plaintext-password policy.
 * <p>
 * Currently enforces a minimum length of {@value #MIN_LENGTH} characters. There is no
 * maximum length: hashes are produced by {@link BCryptSha256Algorithm} which
 * pre-hashes the plaintext with SHA-256 before BCrypt, eliminating the 72-byte
 * BCrypt input ceiling so passphrases of any length round-trip correctly without
 * silent truncation.
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 12;

    private PasswordPolicy() {}

    /**
     * Validates a plaintext password against the configured policy.
     *
     * @param plaintext the candidate password (may be {@code null})
     * @return the validation result; {@link Result#OK} when the password is acceptable
     */
    public static Result validate(String plaintext) {
        if (plaintext == null || plaintext.length() < MIN_LENGTH) {
            return Result.TOO_SHORT;
        }
        return Result.OK;
    }

    /**
     * Outcome of {@link #validate(String)}.
     */
    public enum Result {
        /** The password meets the policy. */
        OK,

        /** The password is shorter than {@link #MIN_LENGTH}. */
        TOO_SHORT;

        /**
         * Returns a human-readable failure message for non-{@link #OK} results.
         */
        public String message() {
            return switch (this) {
                case OK -> "";
                case TOO_SHORT -> "Password must be at least " + MIN_LENGTH + " characters long";
            };
        }
    }
}
