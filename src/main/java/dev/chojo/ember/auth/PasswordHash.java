/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.auth;

public record PasswordHash(String algorithm, String hash, String salt) {

    public String encode() {
        return "{%s:%s:%s}".formatted(algorithm, hash, salt);
    }

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
}
