/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.backend.smb;

/**
 * Connection settings for {@link SmbStorageBackend}.
 *
 * @param host     SMB server host name or IP
 * @param port     SMB port, typically {@code 445}
 * @param share    share name on the server
 * @param domain   Windows or Samba domain; empty when unused
 * @param username authenticating user
 * @param password authenticating user's password
 * @param basePath path inside the share the backend treats as its root; empty for the share root
 * @param seal     whether SMB3 in-flight encryption is on; only a legacy server needs it off
 * @param dfs      whether DFS referrals are followed
 */
public record SmbBackendConfig(
        String host,
        int port,
        String share,
        String domain,
        String username,
        String password,
        String basePath,
        boolean seal,
        boolean dfs) {}
