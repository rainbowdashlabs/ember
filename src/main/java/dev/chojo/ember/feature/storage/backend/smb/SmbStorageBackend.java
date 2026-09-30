/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.backend.smb;

import com.hierynomus.smbj.SMBClient;
import com.hierynomus.smbj.auth.AuthenticationContext;
import com.hierynomus.smbj.common.SMBRuntimeException;
import com.hierynomus.smbj.connection.Connection;
import com.hierynomus.smbj.session.Session;
import com.hierynomus.smbj.share.DiskShare;
import dev.chojo.ember.feature.storage.backend.StorageBackendType;
import dev.chojo.ember.feature.storage.backend.StorageClients;
import dev.chojo.ember.feature.storage.backend.tree.FileTreeBackend;
import dev.chojo.ember.feature.storage.backend.tree.LeasePool;

import java.io.IOException;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.Executor;

/**
 * SMB3-backed storage, spoken directly to the share through smbj: no kernel mount, no FUSE, no
 * {@code SYS_ADMIN}. SMB3 {@code seal} (in-flight encryption) is on by default; operators pointing at
 * a legacy server switch it off in {@link SmbBackendConfig}. DFS referral following is off by default.
 *
 * <p>The file plumbing is {@link FileTreeBackend}'s. Every tree is a session and share of its own,
 * signed in on the client's one connection to the server, which smbj opens again when it was dropped;
 * a session is therefore never used past the connection it was signed in on. Up to
 * {@link #CONNECTIONS} of them serve calls at once.
 */
public final class SmbStorageBackend extends FileTreeBackend {
    /** How many calls one backend serves at once. */
    public static final int CONNECTIONS = 4;

    /** How long a call waits for a free session. */
    public static final Duration ACQUIRE_TIMEOUT = Duration.ofSeconds(10);

    private final SMBClient client;
    private final boolean ownsClient;

    /**
     * A backend with an SMB client of its own, closed with it.
     *
     * @param config where the share is and how to sign in
     */
    public SmbStorageBackend(SmbBackendConfig config) {
        this(config, StorageClients.newSmbClient(config.seal(), config.dfs()), true, Runnable::run);
    }

    /**
     * A backend on a client shared with other backends, which the caller closes.
     *
     * @param config  where the share is and how to sign in
     * @param client  the client to connect through
     * @param drainer where a closed pool waits for the sessions still lent
     */
    public SmbStorageBackend(SmbBackendConfig config, SMBClient client, Executor drainer) {
        this(config, client, false, drainer);
    }

    private SmbStorageBackend(SmbBackendConfig config, SMBClient client, boolean ownsClient, Executor drainer) {
        super(
                StorageBackendType.SMB,
                new LeasePool<>(
                        "SMB storage at " + config.host(),
                        () -> open(client, config),
                        CONNECTIONS,
                        ACQUIRE_TIMEOUT,
                        drainer),
                config.basePath(),
                Set.of());
        this.client = client;
        this.ownsClient = ownsClient;
    }

    private static SmbFileTree open(SMBClient client, SmbBackendConfig config) throws IOException {
        Connection connection;
        try {
            connection = client.connect(config.host(), config.port());
        } catch (SMBRuntimeException e) {
            throw new IOException("SMB connect failed: " + e.getMessage(), e);
        }
        Session session = null;
        try {
            session = connection.authenticate(new AuthenticationContext(
                    config.username(),
                    config.password() == null ? new char[0] : config.password().toCharArray(),
                    config.domain() == null ? "" : config.domain()));
            return new SmbFileTree(session, (DiskShare) session.connectShare(config.share()));
        } catch (SMBRuntimeException | ClassCastException e) {
            if (session != null) session.close();
            connection.close();
            throw new IOException("SMB sign-in failed: " + e.getMessage(), e);
        }
    }

    @Override
    protected void released() {
        if (ownsClient) client.close();
    }
}
