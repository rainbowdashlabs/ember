/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.backend.sftp;

import dev.chojo.ember.feature.storage.backend.FileTreeBackendContract;
import dev.chojo.ember.feature.storage.backend.StorageBackend;
import dev.chojo.ember.feature.storage.backend.StorageClients;
import dev.chojo.ember.feature.storage.backend.StorageContainers;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.sftp.client.SftpClient;
import org.apache.sshd.sftp.client.SftpClientFactory;
import org.apache.sshd.sftp.common.SftpException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Tag;
import org.testcontainers.containers.GenericContainer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.EnumSet;
import java.util.Optional;

@Tag("storage")
class SftpStorageBackendContractTest extends FileTreeBackendContract {
    private final StorageClients clients = new StorageClients(Runnable::run);
    private SshClient rawClient;
    private ClientSession rawSession;
    private SftpClient raw;

    /** The settings the backend under test signs in with. */
    static SftpBackendConfig config() {
        var sftp = StorageContainers.sftp();
        return new SftpBackendConfig(
                sftp.getHost(),
                sftp.getMappedPort(22),
                StorageContainers.USER,
                Optional.of(StorageContainers.PASSWORD),
                Optional.empty(),
                "",
                StorageContainers.SFTP_BASE);
    }

    @Override
    protected StorageBackend openBackend() {
        return new SftpStorageBackend(config(), clients.ssh(), clients.drainer());
    }

    @Override
    protected Optional<GenericContainer<?>> server() {
        return Optional.of(StorageContainers.sftp());
    }

    @Override
    protected void writeRaw(String key, byte[] bytes) throws IOException {
        String path = StorageContainers.SFTP_BASE + "/" + key;
        makeParents(path);
        try (OutputStream out = raw().write(
                        path,
                        EnumSet.of(
                                SftpClient.OpenMode.Create, SftpClient.OpenMode.Write, SftpClient.OpenMode.Truncate))) {
            out.write(bytes);
        }
    }

    @Override
    protected Optional<byte[]> readRaw(String key) throws IOException {
        try (InputStream in = raw().read(StorageContainers.SFTP_BASE + "/" + key)) {
            return Optional.of(in.readAllBytes());
        } catch (SftpException e) {
            return Optional.empty();
        }
    }

    @AfterAll
    void closeRaw() throws IOException {
        if (raw != null) raw.close();
        if (rawSession != null) rawSession.close();
        if (rawClient != null) rawClient.stop();
        clients.close();
    }

    private void makeParents(String path) throws IOException {
        String dir = "";
        String[] segments = path.substring(1).split("/");
        for (int i = 0; i < segments.length - 1; i++) {
            dir = dir + "/" + segments[i];
            try {
                raw().mkdir(dir);
            } catch (SftpException exists) {
                continue;
            }
        }
    }

    private SftpClient raw() throws IOException {
        if (raw != null) return raw;
        var config = config();
        rawClient = SshClient.setUpDefaultClient();
        rawClient.start();
        rawSession = rawClient
                .connect(config.username(), config.host(), config.port())
                .verify(15_000)
                .getSession();
        rawSession.addPasswordIdentity(StorageContainers.PASSWORD);
        rawSession.auth().verify(15_000);
        raw = SftpClientFactory.instance().createSftpClient(rawSession);
        return raw;
    }
}
