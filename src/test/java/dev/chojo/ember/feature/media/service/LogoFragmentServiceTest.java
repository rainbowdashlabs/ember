/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.media.service;

import dev.chojo.ember.feature.media.MediaLayoutFixtures;
import dev.chojo.ember.feature.storage.backend.StorageBackendResolver;
import dev.chojo.ember.feature.storage.backend.local.LocalStorageBackend;
import dev.chojo.ember.feature.storage.service.StorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogoFragmentServiceTest {

    @TempDir
    Path root;

    @Test
    void aFragmentIsWrittenAgainOnlyWhenItsBytesChange() throws IOException {
        var backend = new LocalStorageBackend(root);
        var storage = new StorageService(new StorageBackendResolver(backend), backend);
        var fragments = new LogoFragmentService(new ImageVariants(storage), storage);
        Path original = root.resolve("inst/images/logo-fragments/blink/original.gif");
        byte[] gif = MediaLayoutFixtures.picture("animated.gif");

        fragments.storeIfChanged("blink", gif, "image/gif");
        assertArrayEquals(gif, fragments.read("blink", 64).orElseThrow().data());

        Files.delete(original);
        fragments.storeIfChanged("blink", gif, "image/gif");
        assertFalse(Files.exists(original));

        fragments.storeIfChanged("blink", MediaLayoutFixtures.samplePng(), "image/png");
        assertTrue(Files.exists(root.resolve("inst/images/logo-fragments/blink/original.png")));
    }
}
