/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.backend;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What every {@link StorageBackend} promises, asserted once and run against each of them.
 *
 * <p>A subclass hands over a backend on a real server, and every case here runs against it
 * unchanged, so the local disk, SFTP, SMB and S3 are held to one surface rather than to four copies
 * of it that drift apart.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class StorageBackendContract {
    private static final int LARGE_BYTES = 20 * 1024 * 1024;

    private StorageBackend backend;

    /**
     * Opens the backend under test, on a server that is already running.
     *
     * @return the backend
     */
    protected abstract StorageBackend openBackend();

    /** The backend under test. */
    protected StorageBackend backend() {
        return backend;
    }

    @BeforeAll
    void open() {
        backend = openBackend();
    }

    @AfterAll
    void close() {
        if (backend != null) backend.close();
    }

    @AfterEach
    void cleanUp() {
        for (String key : backend.listByPrefix("scope")) {
            backend.delete(key);
        }
    }

    @Test
    void storeAndReadRoundTrip() throws IOException {
        store("scope/cat/key", "hello storage");

        try (var stream = backend.read("scope/cat/key").orElseThrow()) {
            assertEquals(13, stream.contentLength());
            assertEquals("hello storage", new String(stream.body().readAllBytes(), StandardCharsets.UTF_8));
            assertEquals("text/plain", stream.metadata().contentType());
        }
    }

    @Test
    void existsAndDelete() {
        store("scope/cat/del", "bytes");

        assertTrue(backend.exists("scope/cat/del"));
        backend.delete("scope/cat/del");
        assertFalse(backend.exists("scope/cat/del"));
        backend.delete("scope/cat/del");
    }

    @Test
    void listByPrefixReturnsEveryNestedKeyAndNothingElse() {
        for (String key : List.of("scope/cat/a", "scope/cat/sub/b", "scope/cat/sub/c", "scope/other/d")) {
            store(key, "data");
        }

        assertEquals(List.of("scope/cat/a", "scope/cat/sub/b", "scope/cat/sub/c"), backend.listByPrefix("scope/cat"));
        assertEquals(List.of("scope/cat/sub/b"), backend.listByPrefix("scope/cat/sub/b"));
        assertEquals(List.of(), backend.listByPrefix("scope/missing"));
    }

    @Test
    void sumSizeAggregatesAllStoredBytes() {
        store("scope/cat/x", "12345");
        store("scope/cat/y", "12345");

        assertEquals(10L, backend.sumSizeByPrefix("scope/cat"));
    }

    @Test
    void updateMetadataSeesNewSha256() throws IOException {
        store("scope/cat/meta", "data");

        backend.updateMetadata("scope/cat/meta", ObjectMetadata.of("text/plain").withSha256("abc123"));

        try (var stream = backend.read("scope/cat/meta").orElseThrow()) {
            assertEquals("abc123", stream.metadata().sha256());
            assertEquals("text/plain", stream.metadata().contentType());
        }
    }

    @Test
    void probeReturnsHealthy() {
        var status = backend.probe();
        assertTrue(status.healthy(), () -> "expected a healthy probe but got " + status);
        assertEquals(List.of(), backend.listByPrefix("_probe"));
    }

    @Test
    void readMissingKeyReturnsEmpty() {
        assertTrue(backend.read("scope/does/not/exist").isEmpty());
        assertFalse(backend.exists("scope/does/not/exist"));
    }

    @Test
    void storingAgainReplacesTheBytesAndTheMetadata() throws IOException {
        store("scope/cat/replaced", "first");
        backend.store(
                "scope/cat/replaced",
                new ByteArrayInputStream("second!".getBytes(StandardCharsets.UTF_8)),
                7,
                ObjectMetadata.of("application/json", "second.json"));

        try (var stream = backend.read("scope/cat/replaced").orElseThrow()) {
            assertEquals("second!", new String(stream.body().readAllBytes(), StandardCharsets.UTF_8));
            assertEquals("application/json", stream.metadata().contentType());
            assertEquals("second.json", stream.metadata().originalFilename().orElseThrow());
        }
        assertEquals(List.of("scope/cat/replaced"), backend.listByPrefix("scope/cat"));
    }

    @Test
    void aDeletedKeyLeavesNothingBehind() {
        store("scope/deep/er/still/key", "gone soon");

        backend.delete("scope/deep/er/still/key");

        assertEquals(List.of(), backend.listByPrefix("scope/deep"));
        assertEquals(0L, backend.sumSizeByPrefix("scope/deep"));
    }

    @Test
    void aLargeStreamRoundTrips() throws IOException {
        byte[] large = new byte[LARGE_BYTES];
        new Random(7).nextBytes(large);

        backend.store(
                "scope/cat/large",
                new ByteArrayInputStream(large),
                large.length,
                ObjectMetadata.of("application/octet-stream"));

        try (var stream = backend.read("scope/cat/large").orElseThrow()) {
            assertEquals(LARGE_BYTES, stream.contentLength());
            assertEquals(sha256(new ByteArrayInputStream(large)), sha256(stream.body()));
        }
    }

    @Test
    void theStoredBytesAreExactlyWhatWasSent() throws IOException {
        byte[] binary = {0, 1, 2, (byte) 0xFF, (byte) 0xFE, 10, 13};
        backend.store(
                "scope/cat/binary", new ByteArrayInputStream(binary), binary.length, ObjectMetadata.of("image/png"));

        try (var stream = backend.read("scope/cat/binary").orElseThrow()) {
            assertArrayEquals(binary, stream.body().readAllBytes());
        }
    }

    @Test
    void aCopyCarriesTheBytesAndTheMetadataAndLeavesTheSource() throws IOException {
        backend.store(
                "scope/cat/original",
                new ByteArrayInputStream("copied bytes".getBytes(StandardCharsets.UTF_8)),
                12,
                ObjectMetadata.of("application/json", "original.json"));
        String sealed;
        try (var stream = backend.read("scope/cat/original").orElseThrow()) {
            sealed = stream.metadata().sha256();
        }

        assertTrue(backend.copy("scope/cat/original", "scope/other/deep/copy"));

        for (String key : List.of("scope/cat/original", "scope/other/deep/copy")) {
            try (var stream = backend.read(key).orElseThrow()) {
                assertEquals("copied bytes", new String(stream.body().readAllBytes(), StandardCharsets.UTF_8));
                assertEquals("application/json", stream.metadata().contentType());
                assertEquals(
                        "original.json", stream.metadata().originalFilename().orElseThrow());
                assertEquals(sealed, stream.metadata().sha256());
            }
        }
    }

    @Test
    void aCopyReplacesWhatIsThere() throws IOException {
        store("scope/cat/new", "new bytes");
        store("scope/cat/old", "old bytes, longer");

        assertTrue(backend.copy("scope/cat/new", "scope/cat/old"));

        try (var stream = backend.read("scope/cat/old").orElseThrow()) {
            assertEquals("new bytes", new String(stream.body().readAllBytes(), StandardCharsets.UTF_8));
        }
        assertEquals(List.of("scope/cat/new", "scope/cat/old"), backend.listByPrefix("scope/cat"));
    }

    @Test
    void aLargeObjectIsCopiedWhole() throws IOException {
        byte[] large = new byte[LARGE_BYTES];
        new Random(11).nextBytes(large);
        backend.store(
                "scope/cat/large-source",
                new ByteArrayInputStream(large),
                large.length,
                ObjectMetadata.of("application/octet-stream"));

        assertTrue(backend.copy("scope/cat/large-source", "scope/cat/large-copy"));

        try (var stream = backend.read("scope/cat/large-copy").orElseThrow()) {
            assertEquals(LARGE_BYTES, stream.contentLength());
            assertEquals(sha256(new ByteArrayInputStream(large)), sha256(stream.body()));
        }
    }

    @Test
    void copyingAMissingKeyCopiesNothing() {
        assertFalse(backend.copy("scope/cat/missing", "scope/cat/target"));

        assertFalse(backend.exists("scope/cat/target"));
    }

    /**
     * Stores a short text under a key.
     *
     * @param key  the full key
     * @param text what to store
     */
    protected void store(String key, String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        backend.store(key, new ByteArrayInputStream(bytes), bytes.length, ObjectMetadata.of("text/plain"));
    }

    private static String sha256(InputStream in) throws IOException {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) digest.update(buffer, 0, read);
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
