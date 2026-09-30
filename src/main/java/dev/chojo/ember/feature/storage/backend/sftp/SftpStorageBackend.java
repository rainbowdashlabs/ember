/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.backend.sftp;

import dev.chojo.ember.feature.storage.backend.StorageBackendType;
import dev.chojo.ember.feature.storage.backend.tree.FileTreeBackend;
import dev.chojo.ember.feature.storage.backend.tree.LeasePool;

import java.time.Duration;
import java.util.Set;

/**
 * SFTP-backed storage, spoken through Apache MINA SSHD: no kernel mount, no FUSE, no host-level SSH
 * client required. In-flight encryption is the SSH channel itself and is always on; host keys are
 * checked against the configured fingerprint, and an empty fingerprint, acceptable only in
 * development, trusts any host.
 *
 * <p>The file plumbing is {@link FileTreeBackend}'s; this only opens the channels.
 */
public final class SftpStorageBackend extends FileTreeBackend {
    private static final Duration ACQUIRE_TIMEOUT = Duration.ofSeconds(10);

    private final SftpSessions sessions;

    /**
     * A backend with an SSH client of its own.
     *
     * @param config where the server is and how to sign in
     */
    public SftpStorageBackend(SftpBackendConfig config) {
        this(config, new SftpSessions(config, SftpSessions.clientFor(config), true));
    }

    private SftpStorageBackend(SftpBackendConfig config, SftpSessions sessions) {
        super(
                StorageBackendType.SFTP,
                new LeasePool<>("SFTP storage at " + config.host(), sessions::open, 1, ACQUIRE_TIMEOUT),
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
