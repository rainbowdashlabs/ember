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

import java.util.concurrent.Executor;

/**
 * SFTP storage through Apache MINA SSHD, with no mount and no SSH client on the host. The backend keeps
 * one SSH session and lends SFTP channels on it through a {@link LeasePool}; OpenSSH allows ten per
 * connection. Host keys are checked against the configured fingerprint.
 */
public final class SftpStorageBackend extends FileTreeBackend {
    private final SftpSessions sessions;

    /**
     * @param client  the started client to connect through, which the caller closes
     * @param drainer where a closed pool waits for the channels still lent
     */
    public SftpStorageBackend(SftpBackendConfig config, SshClient client, Executor drainer) {
        this(config, new SftpSessions(config, client), drainer);
    }

    private SftpStorageBackend(SftpBackendConfig config, SftpSessions sessions, Executor drainer) {
        super(
                StorageBackendType.SFTP,
                new LeasePool<>("SFTP storage at " + config.host(), sessions::open, drainer),
                config.basePath());
        this.sessions = sessions;
    }

    @Override
    protected void released() {
        sessions.close();
    }
}
