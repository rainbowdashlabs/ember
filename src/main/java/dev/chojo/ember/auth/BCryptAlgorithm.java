/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.auth;

import at.favre.lib.crypto.bcrypt.BCrypt;

/**
 * BCrypt-based password hashing algorithm with a cost factor of 12.
 */
public class BCryptAlgorithm implements HashAlgorithm {
    private static final int COST = 12;

    @Override
    public String name() {
        return "bcrypt";
    }

    @Override
    public PasswordHash hash(String password) {
        String hash = BCrypt.withDefaults().hashToString(COST, password.toCharArray());
        return new PasswordHash(name(), hash);
    }

    @Override
    public boolean verify(String password, PasswordHash hash) {
        BCrypt.Result result = BCrypt.verifyer().verify(password.toCharArray(), hash.hash());
        return result.verified;
    }
}
