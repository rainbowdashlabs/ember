/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.auth;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.HashMap;
import java.util.Map;

/**
 * Manages password hashing and verification using pluggable {@link HashAlgorithm} implementations.
 * Supports multiple algorithms simultaneously and can detect when a stored hash needs re-hashing
 * with the current default algorithm.
 */
@Singleton
public class PasswordHasher {
    private final HashAlgorithm defaultAlgorithm;
    private final Map<String, HashAlgorithm> algorithms = new HashMap<>();

    @Inject
    public PasswordHasher() {
        this(new BCryptSha256Algorithm());
        register(new BCryptAlgorithm());
    }

    /**
     * Creates a password hasher with the given default algorithm.
     *
     * @param defaultAlgorithm the algorithm to use for new hashes
     */
    public PasswordHasher(HashAlgorithm defaultAlgorithm) {
        this.defaultAlgorithm = defaultAlgorithm;
        register(defaultAlgorithm);
    }

    /**
     * Registers an additional hash algorithm for verification of existing hashes.
     *
     * @param algorithm the algorithm to register
     */
    public void register(HashAlgorithm algorithm) {
        algorithms.put(algorithm.name(), algorithm);
    }

    /**
     * Hashes a plaintext password using the default algorithm.
     *
     * @param password the plaintext password
     * @return the encoded hash string in {@code {algorithm:hash:salt}} format
     */
    public String hash(String password) {
        return defaultAlgorithm.hash(password).encode();
    }

    /**
     * Verifies a plaintext password against an encoded hash string.
     *
     * @param password the plaintext password to verify
     * @param encoded  the encoded hash string
     * @return {@code true} if the password matches
     * @throws IllegalArgumentException if the hash uses an unknown algorithm
     */
    public boolean verify(String password, String encoded) {
        PasswordHash parsed = PasswordHash.parse(encoded);
        HashAlgorithm algorithm = algorithms.get(parsed.algorithm());
        if (algorithm == null) {
            throw new IllegalArgumentException("Unknown hash algorithm: " + parsed.algorithm());
        }
        return algorithm.verify(password, parsed);
    }

    /**
     * Checks whether the encoded hash was created with a different algorithm than the current default,
     * indicating it should be re-hashed on the next successful login.
     *
     * @param encoded the encoded hash string
     * @return {@code true} if the hash uses a non-default algorithm
     */
    public boolean needsRehash(String encoded) {
        PasswordHash parsed = PasswordHash.parse(encoded);
        return !parsed.algorithm().equals(defaultAlgorithm.name());
    }
}
