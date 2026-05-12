/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.auth;

import at.favre.lib.crypto.bcrypt.BCrypt;

import java.security.SecureRandom;
import java.util.Base64;

public class BCryptAlgorithm implements HashAlgorithm {
    private static final int COST = 12;
    private static final SecureRandom RANDOM = new SecureRandom();

    @Override
    public String name() {
        return "bcrypt";
    }

    @Override
    public PasswordHash hash(String password) {
        byte[] saltBytes = new byte[16];
        RANDOM.nextBytes(saltBytes);
        String salt = Base64.getEncoder().encodeToString(saltBytes);

        String hash = BCrypt.withDefaults().hashToString(COST, password.toCharArray());
        return new PasswordHash(name(), hash, salt);
    }

    @Override
    public boolean verify(String password, PasswordHash hash) {
        BCrypt.Result result = BCrypt.verifyer().verify(password.toCharArray(), hash.hash());
        return result.verified;
    }
}
