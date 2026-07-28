/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.quiz.service;

import dev.chojo.ember.feature.quiz.entity.CreateQuestionCommand;
import dev.chojo.ember.feature.quiz.entity.QuestionConfig;
import dev.chojo.ember.feature.quiz.entity.QuizQuestion;
import dev.chojo.ember.feature.quiz.entity.QuizQuestionType;
import dev.chojo.ember.feature.quiz.repository.QuizCatalogRepository;
import dev.chojo.ember.util.Json;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

/**
 * The questions inside a catalog, including the point value a question derives from its
 * own config when it is configured for automatic points.
 */
@Singleton
public class QuizQuestionService {
    private static final Logger log = LoggerFactory.getLogger(QuizQuestionService.class);

    private final QuizCatalogRepository catalogRepository;

    @Inject
    public QuizQuestionService(QuizCatalogRepository catalogRepository) {
        this.catalogRepository = catalogRepository;
    }

    public List<QuizQuestion> findQuestions(int catalogId) {
        return catalogRepository.findQuestions(catalogId);
    }

    public Optional<QuizQuestion> findQuestion(int id) {
        return catalogRepository.findQuestionById(id);
    }

    public List<QuizQuestion> findQuestionsByIds(List<Integer> ids) {
        return catalogRepository.findQuestionsByIds(ids);
    }

    public int countQuestions(int catalogId) {
        return catalogRepository.countQuestions(catalogId);
    }

    /**
     * Creates a question from a {@link CreateQuestionCommand}. When the command asks for
     * automatic points, the point value is derived from the config instead of the value
     * the command carries.
     */
    public QuizQuestion createQuestion(CreateQuestionCommand command) {
        String configStr = serializeConfig(command.config());
        double effectivePoints = command.autoPoints()
                ? calculateAutoPoints(command.questionType(), configStr, command.points())
                : command.points();
        var question = catalogRepository.createQuestion(
                command.catalogId(),
                command.categoryId(),
                command.questionType(),
                command.title(),
                command.description(),
                command.imageUrl(),
                effectivePoints,
                command.autoPoints(),
                configStr,
                command.position());
        log.info(
                "Created quiz question {} in catalog {} (type {})",
                question.id(),
                command.catalogId(),
                command.questionType());
        return question;
    }

    public boolean updateQuestion(
            int id,
            Integer categoryId,
            String title,
            String description,
            String imageUrl,
            double points,
            boolean autoPoints,
            String config,
            int position) {
        double effectivePoints = points;
        if (autoPoints && config != null) {
            var existing = catalogRepository.findQuestionById(id);
            if (existing.isPresent()) {
                effectivePoints = calculateAutoPoints(existing.get().quizQuestionType(), config, points);
            }
        }
        boolean updated = catalogRepository.updateQuestion(
                id, categoryId, title, description, imageUrl, effectivePoints, autoPoints, config, position);
        if (updated) {
            log.info("Updated quiz question {}", id);
        } else {
            log.warn("Update for quiz question {} affected zero rows", id);
        }
        return updated;
    }

    public boolean deleteQuestion(int id) {
        boolean deleted = catalogRepository.deleteQuestion(id);
        if (deleted) {
            log.info("Deleted quiz question {}", id);
        } else {
            log.warn("Delete for quiz question {} affected zero rows", id);
        }
        return deleted;
    }

    private String serializeConfig(QuestionConfig config) {
        if (config == null) return "{}";
        try {
            return Json.MAPPER.writeValueAsString(config);
        } catch (Exception e) {
            return "{}";
        }
    }

    /**
     * Calculates auto-points from the question config based on type.
     * Uses {@link QuizQuestionType#parseConfig} for typed deserialization and
     * {@link QuestionConfig#autoPoints} for the calculation.
     */
    private double calculateAutoPoints(QuizQuestionType type, String configStr, double fallback) {
        var config = type.parseConfig(configStr);
        if (config instanceof QuestionConfig.Unknown) return fallback;
        double points = config.autoPoints();
        return points > 0 ? points : fallback;
    }
}
