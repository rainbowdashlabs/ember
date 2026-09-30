/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.backend.smb;

import com.hierynomus.smbj.SMBClient;
import com.hierynomus.smbj.SmbConfig;
import dev.chojo.ember.feature.storage.backend.ObjectMetadata;
import dev.chojo.ember.feature.storage.backend.StorageBackend;
import dev.chojo.ember.feature.storage.backend.StorageBackendContract;
import dev.chojo.ember.feature.storage.backend.StorageContainers;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("storage")
class SmbStorageBackendContractTest extends StorageBackendContract {

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
