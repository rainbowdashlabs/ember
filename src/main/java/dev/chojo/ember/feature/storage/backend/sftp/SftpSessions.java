/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.backend.sftp;

import dev.chojo.ember.feature.storage.backend.StorageException;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.common.util.security.SecurityUtils;
import org.apache.sshd.sftp.client.SftpClientFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.time.Duration;

/**
 * The one SSH session an SFTP backend keeps, and the channels opened on it.
 *
 * <p>The session is opened on first use and opened again when the server or the network dropped it;
 * every tree is one SFTP channel on it, so the pool of a backend multiplexes its channels over one
 * connection. The server's host key is checked against the configured fingerprint on every connect.
 */
final class SftpSessions implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(SftpSessions.class);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration AUTH_TIMEOUT = Duration.ofSeconds(15);

    private final SftpBackendConfig config;
    private final SshClient client;
    private final boolean ownsClient;
    private ClientSession session;

    SftpSessions(SftpBackendConfig config, SshClient client, boolean ownsClient) {
        this.config = config;
        this.client = client;
        this.ownsClient = ownsClient;
    }

    /**
     * An SSH client that checks host keys against the configured fingerprint, started.
     *
     * @param config the backend's settings
     * @return the client
     */
    static SshClient clientFor(SftpBackendConfig config) {
        SshClient client = SshClient.setUpDefaultClient();
        client.setServerKeyVerifier((clientSession, remote, serverKey) -> {
            if (config.trustsAnyHost()) return true;
            try {
                return config.knownHostsFingerprint().equalsIgnoreCase(KeyUtils.getFingerPrint(serverKey));
            } catch (RuntimeException e) {
                log.warn("Failed to verify SFTP host key", e);
                return false;
            }
        });
        client.start();
        return client;
    }

    /**
     * Opens one SFTP channel, connecting and signing in first where no session is open.
     *
     * @return the channel as a tree
     * @throws IOException when the server cannot be reached or refuses the sign-in
     */
    SftpFileTree open() throws IOException {
        return new SftpFileTree(SftpClientFactory.instance().createSftpClient(session()));
    }

    private synchronized ClientSession session() throws IOException {
        if (session != null && session.isOpen()) return session;
        if (session != null) session.close(true);
        ClientSession opened = client.connect(config.username(), config.host(), config.port())
                .verify(CONNECT_TIMEOUT.toMillis())
                .getSession();
        try {
            if (config.password().isPresent()) {
                opened.addPasswordIdentity(config.password().get());
            } else {
                opened.addPublicKeyIdentity(parsePrivateKey(config.privateKey().orElseThrow()));
            }
            opened.auth().verify(AUTH_TIMEOUT.toMillis());
        } catch (IOException | RuntimeException e) {
            opened.close(true);
            throw e;
        }
        session = opened;
        return session;
    }

    @Override
    public synchronized void close() {
        if (session != null) session.close(true);
        session = null;
        if (ownsClient) client.stop();
    }

    private static KeyPair parsePrivateKey(String pem) {
        try {
            var keys = SecurityUtils.getKeyPairResourceParser()
                    .loadKeyPairs(null, null, null, new ByteArrayInputStream(pem.getBytes(StandardCharsets.UTF_8)));
            var it = keys.iterator();
            if (!it.hasNext()) throw new StorageException("SFTP private key PEM is empty");
            return it.next();
        } catch (IOException | GeneralSecurityException e) {
            throw new StorageException("Failed to parse SFTP private key", e);
        }
    }
}
