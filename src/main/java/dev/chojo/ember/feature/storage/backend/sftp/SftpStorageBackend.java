/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.backend.sftp;

import dev.chojo.ember.feature.storage.backend.StorageBackendType;
import dev.chojo.ember.feature.storage.backend.tree.FileTreeBackend;
import dev.chojo.ember.feature.storage.backend.tree.LeasePool;
import org.apache.sshd.client.SshClient;

import java.time.Duration;
import java.util.Set;

/**
 * SFTP-backed storage, spoken through Apache MINA SSHD: no kernel mount, no FUSE, no host-level SSH
 * client required. In-flight encryption is the SSH channel itself and is always on; host keys are
 * checked against the configured fingerprint, and an empty fingerprint, acceptable only in
 * development, trusts any host.
 *
 * <p>The file plumbing is {@link FileTreeBackend}'s. The backend keeps one SSH session and lends up to
 * {@link #CONNECTIONS} SFTP channels on it at once (OpenSSH allows ten per connection); a caller
 * beyond that waits at most {@link #ACQUIRE_TIMEOUT}.
 */
public final class SftpStorageBackend extends FileTreeBackend {
    /** How many calls one backend serves at once. */
    public static final int CONNECTIONS = 4;

    /** How long a call waits for a free channel. */
    public static final Duration ACQUIRE_TIMEOUT = Duration.ofSeconds(10);

    private final SftpSessions sessions;

    /**
     * A backend with an SSH client of its own, closed with it.
     *
     * @param config where the server is and how to sign in
     */
    public SftpStorageBackend(SftpBackendConfig config) {
        this(config, new SftpSessions(config, SftpSessions.newClient(), true));
    }

    /**
     * A backend on a client shared with other backends, which the caller closes.
     *
     * @param config where the server is and how to sign in
     * @param client the started client to connect through
     */
    public SftpStorageBackend(SftpBackendConfig config, SshClient client) {
        this(config, new SftpSessions(config, client, false));
    }

    private SftpStorageBackend(SftpBackendConfig config, SftpSessions sessions) {
        super(
                StorageBackendType.SFTP,
                new LeasePool<>("SFTP storage at " + config.host(), sessions::open, CONNECTIONS, ACQUIRE_TIMEOUT),
                config.basePath(),
                Set.of());
        this.sessions = sessions;
    }

    @Override
    public void close() {
        super.close();
        sessions.close();
    }
}
