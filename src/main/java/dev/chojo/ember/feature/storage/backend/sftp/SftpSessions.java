/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.backend.sftp;

import dev.chojo.ember.feature.storage.backend.StorageException;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.config.hosts.HostConfigEntryResolver;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.AttributeRepository;
import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.common.util.security.SecurityUtils;
import org.apache.sshd.core.CoreModuleProperties;
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
 * The one SSH session an SFTP backend keeps, opened on first use and again after it was dropped. Every
 * tree is one SFTP channel on it. The client is shared by every backend, so the host key a session
 * expects travels with its connection rather than being fixed on the client.
 */
public final class SftpSessions implements AutoCloseable {
    /** How long connecting and signing in may each take. */
    public static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(15);

    /** How long a request waits for its answer; SSHD takes it from the session's idle timeout. */
    public static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    /** How often a quiet session proves it is alive, shorter than {@link #REQUEST_TIMEOUT} so an idle session survives. */
    public static final Duration HEARTBEAT = Duration.ofSeconds(10);

    private static final Logger log = LoggerFactory.getLogger(SftpSessions.class);
    private static final AttributeRepository.AttributeKey<SftpBackendConfig> CONFIG =
            new AttributeRepository.AttributeKey<>();

    private final SftpBackendConfig config;
    private final SshClient client;
    private ClientSession session;

    SftpSessions(SftpBackendConfig config, SshClient client) {
        this.config = config;
        this.client = client;
    }

    /**
     * A started SSH client for storage: host keys checked against the fingerprint of the backend a
     * session belongs to, no {@code ~/.ssh/config} consulted, and the timeouts above.
     */
    public static SshClient newClient() {
        SshClient client = SshClient.setUpDefaultClient();
        client.setHostConfigEntryResolver(HostConfigEntryResolver.EMPTY);
        client.setServerKeyVerifier((clientSession, remote, serverKey) -> {
            SftpBackendConfig expected = clientSession.getConnectionContext().getAttribute(CONFIG);
            if (expected == null) return false;
            if (expected.trustsAnyHost()) return true;
            try {
                return expected.knownHostsFingerprint().equalsIgnoreCase(KeyUtils.getFingerPrint(serverKey));
            } catch (RuntimeException e) {
                log.warn("Failed to verify SFTP host key", e);
                return false;
            }
        });
        CoreModuleProperties.IDLE_TIMEOUT.set(client, REQUEST_TIMEOUT);
        CoreModuleProperties.HEARTBEAT_INTERVAL.set(client, HEARTBEAT);
        client.start();
        return client;
    }

    /** Opens one SFTP channel, connecting and signing in first where no session is open. */
    SftpFileTree open() throws IOException {
        return new SftpFileTree(SftpClientFactory.instance().createSftpClient(session()));
    }

    private synchronized ClientSession session() throws IOException {
        if (session != null && session.isOpen()) return session;
        if (session != null) session.close(true);
        ClientSession opened = client.connect(
                        config.username(),
                        config.host(),
                        config.port(),
                        AttributeRepository.ofKeyValuePair(CONFIG, config))
                .verify(CONNECT_TIMEOUT.toMillis())
                .getSession();
        try {
            if (config.password().isPresent()) {
                opened.addPasswordIdentity(config.password().get());
            } else {
                opened.addPublicKeyIdentity(parsePrivateKey(config.privateKey().orElseThrow()));
            }
            opened.auth().verify(CONNECT_TIMEOUT.toMillis());
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
