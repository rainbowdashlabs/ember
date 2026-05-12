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

@Singleton
public class PasswordHasher {
    private final HashAlgorithm defaultAlgorithm;
    private final Map<String, HashAlgorithm> algorithms = new HashMap<>();

    @Inject
    public PasswordHasher() {
        this(new BCryptAlgorithm());
    }

    public PasswordHasher(HashAlgorithm defaultAlgorithm) {
        this.defaultAlgorithm = defaultAlgorithm;
        register(defaultAlgorithm);
    }

    public void register(HashAlgorithm algorithm) {
        algorithms.put(algorithm.name(), algorithm);
    }

    public String hash(String password) {
        return defaultAlgorithm.hash(password).encode();
    }

    public boolean verify(String password, String encoded) {
        PasswordHash parsed = PasswordHash.parse(encoded);
        HashAlgorithm algorithm = algorithms.get(parsed.algorithm());
        if (algorithm == null) {
            throw new IllegalArgumentException("Unknown hash algorithm: " + parsed.algorithm());
        }
        return algorithm.verify(password, parsed);
    }

    public boolean needsRehash(String encoded) {
        PasswordHash parsed = PasswordHash.parse(encoded);
        return !parsed.algorithm().equals(defaultAlgorithm.name());
    }
}
