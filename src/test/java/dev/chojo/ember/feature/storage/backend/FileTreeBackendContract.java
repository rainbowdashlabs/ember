/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.backend;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The byte layout every tree-shaped backend keeps, on top of the contract all backends share.
 *
 * <p>The layout is what makes a store written by one build readable by another and lets a migration
 * copy bytes between backends: the object under its key, its metadata beside it as
 * {@code <key>.meta.json}, and nothing else left behind. A subclass writes and reads the raw files
 * through the protocol itself, around the backend, to check exactly that.
 */
public abstract class FileTreeBackendContract extends StorageBackendContract {
    private static final String LEGACY_SIDECAR =
            "{\"contentType\":\"image/png\",\"sha256\":\"abc123\",\"originalFilename\":\"a.png\",\"contentEncoding\":\"\"}";

    /**
     * Writes a file past the backend, the way an earlier build left it.
     *
     * @param key   the key, relative to the backend's base path
     * @param bytes what the file holds
     */
    protected abstract void writeRaw(String key, byte[] bytes) throws IOException;

    /**
     * Reads a file past the backend.
     *
     * @param key the key, relative to the backend's base path
     * @return what the file holds, or empty when it does not exist
     */
    protected abstract Optional<byte[]> readRaw(String key) throws IOException;

    @Test
    void anObjectAnEarlierBuildStoredIsReadAsItWas() throws IOException {
        byte[] body = "legacy".getBytes(StandardCharsets.UTF_8);
        writeRaw("scope/legacy/key", body);
        writeRaw("scope/legacy/key.meta.json", LEGACY_SIDECAR.getBytes(StandardCharsets.UTF_8));

        try (var stream = backend().read("scope/legacy/key").orElseThrow()) {
            assertArrayEquals(body, stream.body().readAllBytes());
            assertEquals("image/png", stream.metadata().contentType());
            assertEquals("abc123", stream.metadata().sha256());
            assertEquals("a.png", stream.metadata().originalFilename().orElseThrow());
        }
        assertEquals(List.of("scope/legacy/key"), backend().listByPrefix("scope/legacy"));
    }

    @Test
    void whatIsStoredLiesWhereAnEarlierBuildLooksForIt() throws IOException {
        byte[] body = "fresh".getBytes(StandardCharsets.UTF_8);

        var sealed = backend()
                .storeSealed(
                        "scope/fresh/key",
                        new ByteArrayInputStream(body),
                        body.length,
                        ObjectMetadata.of("text/plain"));

        assertArrayEquals(body, readRaw("scope/fresh/key").orElseThrow());
        String sidecar = new String(readRaw("scope/fresh/key.meta.json").orElseThrow(), StandardCharsets.UTF_8);
        assertTrue(sidecar.contains("\"contentType\":\"text/plain\""), sidecar);
        assertTrue(sidecar.contains("\"sha256\":\"" + sealed.sha256() + "\""), sidecar);
        assertEquals(64, sealed.sha256().length());
    }
}
