/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.auth;

/**
 * Strategy interface for password hashing algorithms.
 * Implementations produce and verify {@link PasswordHash} values using a specific algorithm.
 */
public interface HashAlgorithm {

    /**
     * Returns the unique name of this algorithm, used to identify stored hashes.
     *
     * @return the algorithm name (e.g. "bcrypt")
     */
    String name();

    /**
     * Hashes the given plaintext password.
     *
     * @param password the plaintext password
     * @return the password hash containing algorithm, hash, and salt
     */
    PasswordHash hash(String password);

    /**
     * Verifies a plaintext password against a previously computed hash.
     *
     * @param password the plaintext password to verify
     * @param hash     the stored password hash
     * @return {@code true} if the password matches the hash
     */
    boolean verify(String password, PasswordHash hash);
}
