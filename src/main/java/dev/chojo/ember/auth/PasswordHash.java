/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.auth;

/**
 * Represents a hashed password with its algorithm identifier.
 * Encoded format: {@code {algorithm:hash}}.
 * <p>
 * Earlier versions also stored a third {@code salt} segment alongside the hash;
 * BCrypt embeds its own salt in the hash string, so the extra value was never
 * read at verification time. {@link #parse(String)} still accepts the legacy
 * three-segment form so previously-stored credentials continue to verify; new
 * credentials are written with the simpler two-segment encoding.
 *
 * @param algorithm the name of the hashing algorithm
 * @param hash      the hash output (includes the algorithm-internal salt for BCrypt-style hashes)
 */
public record PasswordHash(String algorithm, String hash) {

    /**
     * Parses an encoded password hash string in the format {@code {algorithm:hash}}
     * (or the legacy {@code {algorithm:hash:salt}} — the trailing salt segment is
     * ignored, since BCrypt's hash output already contains its own salt).
     *
     * @param encoded the encoded hash string
     * @return the parsed password hash
     * @throws IllegalArgumentException if the format is invalid
     */
    public static PasswordHash parse(String encoded) {
        if (!encoded.startsWith("{") || !encoded.endsWith("}")) {
            throw new IllegalArgumentException("Invalid hash format: must be {algo:hash}");
        }
        String inner = encoded.substring(1, encoded.length() - 1);
        String[] parts = inner.split(":", 3);
        if (parts.length < 2) {
            throw new IllegalArgumentException("Invalid hash format: must have at least 'algo:hash'");
        }
        return new PasswordHash(parts[0], parts[1]);
    }

    /**
     * Encodes this password hash into the storage format {@code {algorithm:hash}}.
     *
     * @return the encoded string
     */
    public String encode() {
        return "{%s:%s}".formatted(algorithm, hash);
    }
}
