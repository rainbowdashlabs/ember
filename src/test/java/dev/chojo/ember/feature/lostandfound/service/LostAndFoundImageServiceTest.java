/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.lostandfound.service;

import dev.chojo.ember.feature.media.MediaLayoutFixtures;
import dev.chojo.ember.feature.media.service.ImageVariants;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.storage.backend.StorageBackendResolver;
import dev.chojo.ember.feature.storage.backend.local.LocalStorageBackend;
import dev.chojo.ember.feature.storage.service.StorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LostAndFoundImageServiceTest {
    private static final int STATION_ID = 1;

    @TempDir
    Path root;

    @Test
    void anItemPictureIsStoredReadAndLetGoOfUnderItsStation() throws IOException {
        var backend = new LocalStorageBackend(root);
        var stations = Mockito.mock(StationRepository.class);
        Mockito.when(stations.resolveUid(STATION_ID)).thenReturn(MediaLayoutFixtures.STATION_UID);
        var pictures = new LostAndFoundImageService(
                new ImageVariants(new StorageService(new StorageBackendResolver(backend), backend)), stations);
        byte[] png = MediaLayoutFixtures.samplePng();

        pictures.store(STATION_ID, 7, png, "image/png", 0);

        assertTrue(pictures.exists(STATION_ID, 7));
        assertTrue(Files.exists(
                root.resolve("station/" + MediaLayoutFixtures.STATION_UID + "/images/lost-and-found/7/original.png")));
        assertTrue(pictures.read(STATION_ID, 7, 64).isPresent());
        assertEquals("image/png", pictures.read(STATION_ID, 7, 0).orElseThrow().contentType());

        pictures.delete(STATION_ID, 7);

        assertFalse(pictures.exists(STATION_ID, 7));
    }
}
