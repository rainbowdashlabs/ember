/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.backend;

/**
 * Connection settings for {@link SmbStorageBackend}. Carries everything the smbj client needs
 * to authenticate against an SMB3 share plus the producer-side {@code basePath} that the
 * backend prepends to every key.
 *
 * <p>{@code seal} toggles SMB3 in-flight encryption; it is on by default and operators must
 * opt out explicitly when pointing at a legacy SMB1 server. {@code dfs} follows DFS referrals
 * and is off by default.
 *
 * @param host      SMB server host name or IP
 * @param port      SMB port (default {@code 445})
 * @param share     share name on the server
 * @param domain    optional Windows / Samba domain (empty string when unused)
 * @param username  authenticating user
 * @param password  authenticating user's password
 * @param basePath  optional path inside the share that the backend treats as its root; an
 *                  empty string means "the share root"
 * @param seal      whether SMB3 in-flight encryption is enabled
 * @param dfs       whether DFS referrals are followed
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
        boolean dfs) {

    /** Returns a copy with the supplied {@code basePath} replacing the existing one. */
    public SmbBackendConfig withBasePath(String basePath) {
        return new SmbBackendConfig(host, port, share, domain, username, password, basePath, seal, dfs);
    }
}
