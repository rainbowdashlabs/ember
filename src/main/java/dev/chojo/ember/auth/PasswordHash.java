/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.auth;

/**
 * Represents a hashed password with its algorithm identifier and salt.
 * Encoded format: {@code {algorithm:hash:salt}}.
 *
 * @param algorithm the name of the hashing algorithm
 * @param hash      the hash output
 * @param salt      the salt used during hashing
 */
public record PasswordHash(String algorithm, String hash, String salt) {

    /**
     * Parses an encoded password hash string in the format {@code {algorithm:hash:salt}}.
     *
     * @param encoded the encoded hash string
     * @return the parsed password hash
     * @throws IllegalArgumentException if the format is invalid
     */
    public static PasswordHash parse(String encoded) {
        if (!encoded.startsWith("{") || !encoded.endsWith("}")) {
            throw new IllegalArgumentException("Invalid hash format: must be {algo:hash:salt}");
        }
        String inner = encoded.substring(1, encoded.length() - 1);
        String[] parts = inner.split(":", 3);
        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid hash format: must have exactly 3 parts separated by ':'");
        }
        return new PasswordHash(parts[0], parts[1], parts[2]);
    }

    /**
     * Encodes this password hash into the storage format {@code {algorithm:hash:salt}}.
     *
     * @return the encoded string
     */
    public String encode() {
        return "{%s:%s:%s}".formatted(algorithm, hash, salt);
    }
}
