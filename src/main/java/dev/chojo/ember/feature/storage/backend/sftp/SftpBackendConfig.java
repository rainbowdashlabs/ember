/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.backend.sftp;

import java.util.Optional;

/**
 * Connection settings for {@link SftpStorageBackend}.
 *
 * @param host                  SFTP server host name or IP
 * @param port                  SSH port, typically {@code 22}
 * @param username              authenticating user
 * @param password              password authentication; exactly one of it and {@code privateKey} is set
 * @param privateKey            PEM-encoded private key
 * @param knownHostsFingerprint expected host-key fingerprint such as {@code SHA256:abc...}; empty trusts
 *                              any host and is meant for development only
 * @param basePath              directory on the server the backend treats as its root; empty for the
 *                              login directory
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

    /** Whether host-key verification is skipped. */
    public boolean trustsAnyHost() {
        return knownHostsFingerprint == null || knownHostsFingerprint.isBlank();
    }
}
