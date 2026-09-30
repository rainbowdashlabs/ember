/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.backend.local;

import dev.chojo.ember.feature.storage.backend.FileTreeBackendContract;
import dev.chojo.ember.feature.storage.backend.StorageBackend;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalStorageBackendContractTest extends FileTreeBackendContract {
    private Path root;

    @Override
    protected StorageBackend openBackend() {
        try {
            root = Files.createTempDirectory("ember-local-contract");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new LocalStorageBackend(root);
    }

    @Override
    protected void writeRaw(String key, byte[] bytes) throws IOException {
        Path file = root.resolve(key);
        Files.createDirectories(file.getParent());
        Files.write(file, bytes);
    }

    @Override
    protected Optional<byte[]> readRaw(String key) throws IOException {
        Path file = root.resolve(key);
        return Files.isRegularFile(file) ? Optional.of(Files.readAllBytes(file)) : Optional.empty();
    }

    @Test
    void aDeletedKeyTakesItsEmptyDirectoriesWithIt() {
        store("scope/prune/a/b/key", "x");

        backend().delete("scope/prune/a/b/key");

        assertFalse(Files.exists(root.resolve("scope/prune")));
        assertTrue(Files.exists(root));
    }
}
