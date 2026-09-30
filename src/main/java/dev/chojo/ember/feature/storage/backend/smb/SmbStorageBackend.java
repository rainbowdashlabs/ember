/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.backend.smb;

import com.hierynomus.smbj.SMBClient;
import com.hierynomus.smbj.SmbConfig;
import com.hierynomus.smbj.auth.AuthenticationContext;
import com.hierynomus.smbj.common.SMBRuntimeException;
import com.hierynomus.smbj.session.Session;
import com.hierynomus.smbj.share.DiskShare;
import dev.chojo.ember.feature.storage.backend.StorageBackendType;
import dev.chojo.ember.feature.storage.backend.tree.FileTreeBackend;
import dev.chojo.ember.feature.storage.backend.tree.LeasePool;

import java.io.IOException;
import java.time.Duration;
import java.util.Set;

/**
 * SMB3-backed storage, spoken directly to the share through smbj: no kernel mount, no FUSE, no
 * {@code SYS_ADMIN}. SMB3 {@code seal} (in-flight encryption) is on by default; operators pointing at
 * a legacy server switch it off in {@link SmbBackendConfig}. DFS referral following is off by default.
 *
 * <p>The file plumbing is {@link FileTreeBackend}'s. Every tree is a session of its own, signed in on
 * the client's connection to the server, which smbj opens again when it was dropped; a session is
 * therefore never used past the connection it was signed in on.
 */
public final class SmbStorageBackend extends FileTreeBackend {
    private static final Duration ACQUIRE_TIMEOUT = Duration.ofSeconds(10);

    private final SMBClient client;
    private final boolean ownsClient;

    /**
     * A backend with an SMB client of its own.
     *
     * @param config where the share is and how to sign in
     */
    public SmbStorageBackend(SmbBackendConfig config) {
        this(config, clientFor(config), true);
    }

    /**
     * A backend on a client the caller owns and closes.
     *
     * @param config where the share is and how to sign in
     * @param client the client to connect through
     */
    SmbStorageBackend(SmbBackendConfig config, SMBClient client) {
        this(config, client, false);
    }

    private SmbStorageBackend(SmbBackendConfig config, SMBClient client, boolean ownsClient) {
        super(
                StorageBackendType.SMB,
                new LeasePool<>("SMB storage at " + config.host(), () -> open(client, config), 1, ACQUIRE_TIMEOUT),
                config.basePath(),
                Set.of());
        this.client = client;
        this.ownsClient = ownsClient;
    }

    private static SMBClient clientFor(SmbBackendConfig config) {
        return new SMBClient(SmbConfig.builder()
                .withEncryptData(config.seal())
                .withDfsEnabled(config.dfs())
                .build());
    }

    private static SmbFileTree open(SMBClient client, SmbBackendConfig config) throws IOException {
        try {
            var connection = client.connect(config.host(), config.port());
            Session session = connection.authenticate(new AuthenticationContext(
                    config.username(),
                    config.password() == null ? new char[0] : config.password().toCharArray(),
                    config.domain() == null ? "" : config.domain()));
            try {
                return new SmbFileTree(session, (DiskShare) session.connectShare(config.share()));
            } catch (SMBRuntimeException | ClassCastException e) {
                session.close();
                throw e;
            }
        } catch (SMBRuntimeException e) {
            throw new IOException("SMB connect failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void close() {
        super.close();
        if (ownsClient) client.close();
    }
}
