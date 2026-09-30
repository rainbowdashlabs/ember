/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.backend.smb;

import com.hierynomus.msdtyp.AccessMask;
import com.hierynomus.mssmb2.SMB2CreateDisposition;
import com.hierynomus.mssmb2.SMB2ShareAccess;
import com.hierynomus.smbj.SMBClient;
import com.hierynomus.smbj.SmbConfig;
import com.hierynomus.smbj.auth.AuthenticationContext;
import com.hierynomus.smbj.share.DiskShare;
import com.hierynomus.smbj.share.File;
import dev.chojo.ember.feature.storage.backend.FileTreeBackendContract;
import dev.chojo.ember.feature.storage.backend.ObjectMetadata;
import dev.chojo.ember.feature.storage.backend.StorageBackend;
import dev.chojo.ember.feature.storage.backend.StorageContainers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("storage")
class SmbStorageBackendContractTest extends FileTreeBackendContract {
    private SMBClient rawClient;
    private DiskShare raw;

    @Override
    protected void writeRaw(String key, byte[] bytes) {
        String path = key.replace('/', '\\');
        String[] segments = path.split("\\\\");
        String dir = "";
        for (int i = 0; i < segments.length - 1; i++) {
            dir = dir.isEmpty() ? segments[i] : dir + "\\" + segments[i];
            if (!raw().folderExists(dir)) raw().mkdir(dir);
        }
        try (File file = raw().openFile(
                                path,
                                EnumSet.of(AccessMask.GENERIC_WRITE),
                                null,
                                SMB2ShareAccess.ALL,
                                SMB2CreateDisposition.FILE_OVERWRITE_IF,
                                null);
                OutputStream out = file.getOutputStream()) {
            out.write(bytes);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    protected Optional<byte[]> readRaw(String key) {
        String path = key.replace('/', '\\');
        if (!raw().fileExists(path)) return Optional.empty();
        try (File file = raw().openFile(
                                path,
                                EnumSet.of(AccessMask.GENERIC_READ),
                                null,
                                SMB2ShareAccess.ALL,
                                SMB2CreateDisposition.FILE_OPEN,
                                null);
                InputStream in = file.getInputStream()) {
            return Optional.of(in.readAllBytes());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @AfterAll
    void closeRaw() {
        if (rawClient != null) rawClient.close();
    }

    private DiskShare raw() {
        if (raw != null) return raw;
        var config = config();
        rawClient = new SMBClient(SmbConfig.builder().withEncryptData(false).build());
        try {
            var session = rawClient
                    .connect(config.host(), config.port())
                    .authenticate(new AuthenticationContext(
                            config.username(), config.password().toCharArray(), ""));
            raw = (DiskShare) session.connectShare(config.share());
            return raw;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The settings the backend under test signs in with. */
    static SmbBackendConfig config() {
        var smb = StorageContainers.smb();
        return new SmbBackendConfig(
                smb.getHost(),
                smb.getMappedPort(445),
                StorageContainers.SHARE,
                "",
                StorageContainers.USER,
                StorageContainers.PASSWORD,
                "",
                false,
                false);
    }

    @Override
    protected StorageBackend openBackend() {
        return new SmbStorageBackend(config());
    }

    @Override
    protected Optional<GenericContainer<?>> server() {
        return Optional.of(StorageContainers.smb());
    }

    /**
     * A connection the server or the network dropped is opened again, and the session it carried
     * has to be signed in again with it. Reusing the session of the dead connection failed every call
     * of the backend until the instance was restarted.
     */
    @Test
    void aDroppedConnectionIsSignedInAgain() {
        var config = config();
        var client = new SMBClient(SmbConfig.builder().withEncryptData(false).build());
        try (var own = new SmbStorageBackend(config, client)) {
            byte[] payload = "before".getBytes(StandardCharsets.UTF_8);
            own.store(
                    "scope/drop/before",
                    new ByteArrayInputStream(payload),
                    payload.length,
                    ObjectMetadata.of("text/plain"));

            dropConnection(client, config);

            own.store(
                    "scope/drop/after",
                    new ByteArrayInputStream(payload),
                    payload.length,
                    ObjectMetadata.of("text/plain"));
            assertTrue(own.exists("scope/drop/after"));
        } finally {
            client.close();
        }
    }

    private static void dropConnection(SMBClient client, SmbBackendConfig config) {
        try {
            client.connect(config.host(), config.port()).close(true);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
