/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.quiz.service;

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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuizQuestionImageServiceTest {
    private static final int STATION_ID = 1;

    @TempDir
    Path root;

    @Test
    void aQuestionPictureIsStoredReadAndLetGoOfUnderItsStation() throws IOException {
        var backend = new LocalStorageBackend(root);
        var stations = Mockito.mock(StationRepository.class);
        Mockito.when(stations.requireUid(STATION_ID)).thenReturn(MediaLayoutFixtures.STATION_UID);
        var pictures = new QuizQuestionImageService(
                new ImageVariants(new StorageService(new StorageBackendResolver(backend), backend)), stations);
        byte[] gif = MediaLayoutFixtures.picture("animated.gif");

        pictures.store(STATION_ID, 12, gif, "image/gif", 0);

        assertTrue(pictures.exists(STATION_ID, 12));
        assertTrue(Files.exists(
                root.resolve("station/" + MediaLayoutFixtures.STATION_UID + "/images/quiz-questions/12/original.gif")));
        assertArrayEquals(gif, pictures.read(STATION_ID, 12, 128).orElseThrow().data());

        pictures.delete(STATION_ID, 12);

        assertFalse(pictures.exists(STATION_ID, 12));
    }
}
