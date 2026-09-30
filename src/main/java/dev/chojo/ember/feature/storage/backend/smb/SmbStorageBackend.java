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
import dev.chojo.ember.feature.storage.backend.tree.FileTreeBackend;
import dev.chojo.ember.feature.storage.backend.tree.LeasePool;

import java.io.IOException;
import java.util.concurrent.Executor;

/**
 * SMB3 storage spoken directly to the share through smbj, with no kernel mount. Every tree is a session
 * and share of its own on the client's one connection to the server, which smbj reopens after a drop,
 * so a session is never used past the connection it was signed in on.
 */
public final class SmbStorageBackend extends FileTreeBackend {

    /**
     * @param client  the client to connect through, which the caller closes
     * @param drainer where a closed pool waits for the sessions still lent
     */
    public SmbStorageBackend(SmbBackendConfig config, SMBClient client, Executor drainer) {
        super(
                StorageBackendType.SMB,
                new LeasePool<>("SMB storage at " + config.host(), () -> open(client, config), drainer),
                config.basePath());
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
}
