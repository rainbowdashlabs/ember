/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.backend.sftp;

import java.util.Optional;

/**
 * Connection settings for {@link SftpStorageBackend}. {@code password} and {@code privateKey}
 * are mutually exclusive — exactly one must be set; both empty or both populated is rejected
 * by the backend constructor.
 *
 * <p>{@code knownHostsFingerprint} is the server's expected SSH host key fingerprint (e.g.
 * {@code SHA256:abc...}). It is mandatory in production and the backend refuses to connect
 * when the live server key does not match.
 *
 * @param host                  SFTP server host name or IP
 * @param port                  SSH port (typically {@code 22})
 * @param username              authenticating user
 * @param password              password authentication; mutually exclusive with {@code privateKey}
 * @param privateKey            PEM-encoded private key; mutually exclusive with {@code password}
 * @param knownHostsFingerprint expected host-key fingerprint; empty disables verification (dev only)
 * @param basePath              optional path on the server treated as the backend root; an
 *                              empty string means "the user's login directory"
 */
public record SftpBackendConfig(
        String host,
        int port,
        String username,
        Optional<String> password,
        Optional<String> privateKey,
        String knownHostsFingerprint,
        String basePath) {

    public SftpBackendConfig {
        if (password.isPresent() == privateKey.isPresent()) {
            throw new IllegalArgumentException("Exactly one of password or privateKey must be set");
        }
    }

    /** Returns a copy with the supplied {@code basePath} replacing the existing one. */
    public SftpBackendConfig withBasePath(String basePath) {
        return new SftpBackendConfig(host, port, username, password, privateKey, knownHostsFingerprint, basePath);
    }

    /** Whether the config skips host-key verification — only valid in dev. */
    public boolean trustsAnyHost() {
        return knownHostsFingerprint == null || knownHostsFingerprint.isBlank();
    }
}
