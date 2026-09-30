/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.backend.smb;

import com.hierynomus.smbj.SMBClient;
import com.hierynomus.smbj.SmbConfig;
import dev.chojo.ember.TestContainers;
import dev.chojo.ember.feature.storage.backend.ObjectMetadata;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contract test for {@link SmbStorageBackend} against a real Samba server. Mirrors the cases
 * covered by the {@link dev.chojo.ember.feature.storage.backend.local.LocalStorageBackend} test surface (store / read / delete / exists /
 * listByPrefix / probe / metadata round-trip) so behavior parity is asserted at the same
 * surface every other backend will be tested against.
 */
@Tag("storage")
class SmbStorageBackendTest {
    private static final String SHARE = "tests";
    private static final String USER = "tester";
    private static final String PASSWORD = "testpass";

    static final GenericContainer<?> SAMBA = new GenericContainer<>(
                    "dperson/samba:latest@sha256:66088b78a19810dd1457a8f39340e95e663c728083efa5fe7dc0d40b2478e869")
            .withExposedPorts(445)
            .withCommand("-p", "-w", "WORKGROUP", "-u", USER + ";" + PASSWORD, "-s", SHARE + ";/tmp;yes;no;no;" + USER)
            .waitingFor(Wait.forListeningPort())
            .withStartupAttempts(4);

    private static SmbBackendConfig config;
    private static SmbStorageBackend backend;

    @BeforeAll
    static void setup() {
        TestContainers.startExclusively(SAMBA);
        /* seal */
        /* dfs */
        config = new SmbBackendConfig(
                SAMBA.getHost(),
                SAMBA.getMappedPort(445),
                SHARE,
                "",
                USER,
                PASSWORD,
                "",
                /* seal */ false,
                /* dfs */ false);
        backend = new SmbStorageBackend(config);
    }

    @AfterAll
    static void teardown() {
        if (backend != null) backend.close();
    }

    @AfterEach
    void cleanup() {
        for (String key : backend.listByPrefix("")) {
            backend.delete(key);
        }
    }

    @Test
    void storeAndReadRoundTrip() throws Exception {
        byte[] payload = "hello smb".getBytes(StandardCharsets.UTF_8);
        backend.store(
                "scope/cat/key", new ByteArrayInputStream(payload), payload.length, ObjectMetadata.of("text/plain"));

        try (var stream = backend.read("scope/cat/key").orElseThrow()) {
            assertEquals(payload.length, stream.contentLength());
            assertEquals("hello smb", new String(stream.body().readAllBytes(), StandardCharsets.UTF_8));
            assertEquals("text/plain", stream.metadata().contentType());
        }
    }

    @Test
    void existsAndDelete() {
        byte[] payload = "bytes".getBytes(StandardCharsets.UTF_8);
        backend.store(
                "scope/cat/del", new ByteArrayInputStream(payload), payload.length, ObjectMetadata.of("text/plain"));

        assertTrue(backend.exists("scope/cat/del"));
        backend.delete("scope/cat/del");
        assertFalse(backend.exists("scope/cat/del"));
    }

    @Test
    void listByPrefixReturnsEveryNestedKey() {
        byte[] payload = "data".getBytes(StandardCharsets.UTF_8);
        for (String key : List.of("scope/cat/a", "scope/cat/sub/b", "scope/cat/sub/c")) {
            backend.store(
                    key,
                    new ByteArrayInputStream(payload),
                    payload.length,
                    ObjectMetadata.of("application/octet-stream"));
        }

        List<String> keys = backend.listByPrefix("scope/cat");
        assertEquals(3, keys.size());
        assertTrue(keys.contains("scope/cat/a"));
        assertTrue(keys.contains("scope/cat/sub/b"));
        assertTrue(keys.contains("scope/cat/sub/c"));
    }

    @Test
    void sumSizeAggregatesAllStoredBytes() {
        byte[] payload = "12345".getBytes(StandardCharsets.UTF_8);
        backend.store(
                "scope/cat/x", new ByteArrayInputStream(payload), payload.length, ObjectMetadata.of("text/plain"));
        backend.store(
                "scope/cat/y", new ByteArrayInputStream(payload), payload.length, ObjectMetadata.of("text/plain"));
        assertEquals(payload.length * 2L, backend.sumSizeByPrefix("scope/cat"));
    }

    @Test
    void updateMetadataSeesNewSha256() throws Exception {
        byte[] payload = "data".getBytes(StandardCharsets.UTF_8);
        backend.store(
                "scope/cat/meta",
                new ByteArrayInputStream(payload),
                payload.length,
                ObjectMetadata.of("application/octet-stream"));

        backend.updateMetadata(
                "scope/cat/meta", ObjectMetadata.of("application/octet-stream").withSha256("abc123"));

        try (var stream = backend.read("scope/cat/meta").orElseThrow()) {
            assertEquals("abc123", stream.metadata().sha256());
        }
    }

    @Test
    void probeReturnsHealthy() {
        var status = backend.probe();
        assertNotNull(status);
        assertTrue(status.healthy(), () -> "expected healthy probe but got: " + status);
    }

    @Test
    void readMissingKeyReturnsEmpty() {
        assertTrue(backend.read("does/not/exist").isEmpty());
    }

    /**
     * A connection the server or the network dropped is opened again, and the session it carried
     * has to be signed in again with it. Reusing the session of the dead connection failed every call
     * of the backend until the instance was restarted.
     */
    @Test
    void aDroppedConnectionIsSignedInAgain() throws Exception {
        var client = new SMBClient(SmbConfig.builder().withEncryptData(false).build());
        try (var own = new SmbStorageBackend(config, client)) {
            byte[] payload = "before".getBytes(StandardCharsets.UTF_8);
            own.store(
                    "scope/cat/before",
                    new ByteArrayInputStream(payload),
                    payload.length,
                    ObjectMetadata.of("text/plain"));

            client.connect(SAMBA.getHost(), SAMBA.getMappedPort(445)).close(true);

            own.store(
                    "scope/cat/after",
                    new ByteArrayInputStream(payload),
                    payload.length,
                    ObjectMetadata.of("text/plain"));
            assertTrue(own.exists("scope/cat/after"));
        }
    }
}
