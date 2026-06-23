/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.quiz.service;

import dev.chojo.ember.feature.media.service.ImageVariantService;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.storage.entity.StorageCategory;
import dev.chojo.ember.feature.storage.entity.StorageScope;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

/**
 * Per-domain wrapper for quiz question images (concept §11.3 / §11.7). Keyed by question id
 * under the owning station's scope: {@code station/<stationUid>/images/quiz-questions/<questionId>/...}.
 *
 * <p>Used by both the upload route (which already knows the owning station from the session)
 * and the quiz PDF service (which resolves it by question id).
 */
@Singleton
public class QuizQuestionImageService {
    private final ImageVariantService variants;
    private final StationRepository stationRepository;

    @Inject
    public QuizQuestionImageService(ImageVariantService variants, StationRepository stationRepository) {
        this.variants = variants;
        this.stationRepository = stationRepository;
    }

    /**
     * Persists the image for a (stationId, questionId) pair at all standard size variants.
     */
    public void store(int stationId, int questionId, byte[] data, String declaredMime, int maxBytes)
            throws IOException {
        variants.store(
                scope(stationId), StorageCategory.IMAGE_QUIZ_QUESTION, key(questionId), data, declaredMime, maxBytes);
    }

    /**
     * Reads the requested image size, falling back to the original when missing.
     */
    public Optional<ImageVariantService.ImageData> read(int stationId, int questionId, int size) {
        return variants.read(scope(stationId), StorageCategory.IMAGE_QUIZ_QUESTION, key(questionId), size);
    }

    /**
     * Whether an image exists for the given question.
     */
    public boolean exists(int stationId, int questionId) {
        return variants.exists(scope(stationId), StorageCategory.IMAGE_QUIZ_QUESTION, key(questionId));
    }

    /**
     * Removes every variant for the given question's image.
     */
    public void delete(int stationId, int questionId) {
        variants.delete(scope(stationId), StorageCategory.IMAGE_QUIZ_QUESTION, key(questionId));
    }

    private StorageScope.Station scope(int stationId) {
        UUID uid = stationRepository.resolveUid(stationId);
        return new StorageScope.Station(stationId, uid);
    }

    private String key(int questionId) {
        return String.valueOf(questionId);
    }
}
