/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.knowledgebase.service;

import dev.chojo.ember.feature.media.MediaLayoutFixtures;
import dev.chojo.ember.feature.media.service.ImageVariants;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.storage.backend.StorageBackendResolver;
import dev.chojo.ember.feature.storage.backend.local.LocalStorageBackend;
import dev.chojo.ember.feature.storage.service.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The wiki's folder icons and inline images, both thin families over the one picture pipeline. */
class KbPictureWrappersTest {
    private static final int STATION_ID = 1;

    @TempDir
    Path root;

    private ImageVariants images;
    private StationRepository stations;

    @BeforeEach
    void setUp() {
        var backend = new LocalStorageBackend(root);
        images = new ImageVariants(new StorageService(new StorageBackendResolver(backend), backend));
        stations = Mockito.mock(StationRepository.class);
        Mockito.when(stations.resolveUid(STATION_ID)).thenReturn(MediaLayoutFixtures.STATION_UID);
    }

    @Test
    void aFolderIconIsKeptUnderTheFoldersKey() throws IOException {
        var icons = new KbIconService(images, stations);
        byte[] webp = MediaLayoutFixtures.picture("picture.webp");

        icons.store(STATION_ID, 3, webp, "image/webp", 0);

        assertEquals("folder-3", icons.key(3));
        assertTrue(icons.exists(STATION_ID, 3));
        assertTrue(Files.exists(root.resolve(
                "station/" + MediaLayoutFixtures.STATION_UID + "/images/kb-icons/folder-3/original.webp")));
        assertArrayEquals(webp, icons.read(STATION_ID, 3, 0).orElseThrow().data());

        icons.delete(STATION_ID, 3);

        assertFalse(icons.exists(STATION_ID, 3));
    }

    @Test
    void anInlineImageIsKeptUnderTheKeyItWasGiven() throws IOException {
        var inline = new KbImageService(images, stations);

        inline.store(STATION_ID, "file-9-100", MediaLayoutFixtures.samplePng(), "image/png", 0);

        assertTrue(inline.exists(STATION_ID, "file-9-100"));
        assertTrue(inline.read(STATION_ID, "file-9-100", 64).isPresent());

        inline.delete(STATION_ID, "file-9-100");

        assertFalse(inline.exists(STATION_ID, "file-9-100"));
    }
}
