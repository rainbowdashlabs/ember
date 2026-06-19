/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.auth;

import at.favre.lib.crypto.bcrypt.BCrypt;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * BCrypt-based password hashing algorithm that pre-hashes the plaintext with
 * SHA-256 before feeding it into BCrypt.
 * <p>
 * BCrypt silently truncates inputs longer than 72 bytes, which makes two distinct
 * long passphrases sharing a 72-byte prefix authenticate as the same credential.
 * Pre-hashing with SHA-256 and Base64-encoding the digest (44 characters) keeps the
 * effective input within BCrypt's safe range and removes the truncation footgun
 * regardless of how long the user's password is.
 */
public class BCryptSha256Algorithm implements HashAlgorithm {
    private static final int COST = 12;
    private static final SecureRandom RANDOM = new SecureRandom();

    @Override
    public String name() {
        return "bcrypt-sha256";
    }

    @Override
    public PasswordHash hash(String password) {
        byte[] saltBytes = new byte[16];
        RANDOM.nextBytes(saltBytes);
        String salt = Base64.getEncoder().encodeToString(saltBytes);

        String hash = BCrypt.withDefaults().hashToString(COST, prehash(password).toCharArray());
        return new PasswordHash(name(), hash, salt);
    }

    @Override
    public boolean verify(String password, PasswordHash hash) {
        BCrypt.Result result = BCrypt.verifyer().verify(prehash(password).toCharArray(), hash.hash());
        return result.verified;
    }

    private static String prehash(String password) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
