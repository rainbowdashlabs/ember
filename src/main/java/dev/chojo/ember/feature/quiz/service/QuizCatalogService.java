/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.quiz.service;

import dev.chojo.ember.feature.quiz.entity.CatalogMetadata;
import dev.chojo.ember.feature.quiz.entity.QuizCatalog;
import dev.chojo.ember.feature.quiz.entity.QuizCategory;
import dev.chojo.ember.feature.quiz.repository.QuizCatalogRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

/**
 * Question catalogs and the station-wide categories they draw from, plus the subset of
 * catalogs members may train against.
 */
@Singleton
public class QuizCatalogService {
    private static final Logger log = LoggerFactory.getLogger(QuizCatalogService.class);

    private final QuizCatalogRepository catalogRepository;

    @Inject
    public QuizCatalogService(QuizCatalogRepository catalogRepository) {
        this.catalogRepository = catalogRepository;
    }

    public List<QuizCatalog> findCatalogs(int stationId) {
        return catalogRepository.findByStation(stationId);
    }

    public Optional<QuizCatalog> findCatalog(int id) {
        return catalogRepository.findById(id);
    }

    public QuizCatalog createCatalog(
            int stationId, String name, String description, boolean trainingEnabled, CatalogMetadata metadata) {
        var catalog = catalogRepository.create(stationId, name, description, trainingEnabled, metadata);
        log.info("Created quiz catalog {} for station {}", catalog.id(), stationId);
        return catalog;
    }

    public boolean updateCatalog(
            int id, String name, String description, boolean trainingEnabled, CatalogMetadata metadata) {
        boolean updated = catalogRepository.update(id, name, description, trainingEnabled, metadata);
        if (updated) {
            log.info("Updated quiz catalog {}", id);
        } else {
            log.warn("Update for quiz catalog {} affected zero rows", id);
        }
        return updated;
    }

    public boolean deleteCatalog(int id) {
        boolean deleted = catalogRepository.delete(id);
        if (deleted) {
            log.info("Deleted quiz catalog {}", id);
        } else {
            log.warn("Delete for quiz catalog {} affected zero rows", id);
        }
        return deleted;
    }

    public List<QuizCatalog> findTrainingCatalogs(int stationId) {
        return catalogRepository.findTrainingCatalogs(stationId);
    }

    public List<QuizCategory> findCategories(int stationId) {
        return catalogRepository.findCategoriesByStation(stationId);
    }

    public Optional<QuizCategory> findCategory(int id) {
        return catalogRepository.findCategoryById(id);
    }

    public QuizCategory createCategory(int stationId, String name, String description, int position) {
        var category = catalogRepository.createCategory(stationId, name, description, position);
        log.info("Created quiz category {} for station {}", category.id(), stationId);
        return category;
    }

    public boolean updateCategory(int id, String name, String description, int position) {
        boolean updated = catalogRepository.updateCategory(id, name, description, position);
        if (updated) {
            log.info("Updated quiz category {}", id);
        } else {
            log.warn("Update for quiz category {} affected zero rows", id);
        }
        return updated;
    }

    public boolean deleteCategory(int id) {
        boolean deleted = catalogRepository.deleteCategory(id);
        if (deleted) {
            log.info("Deleted quiz category {}", id);
        } else {
            log.warn("Delete for quiz category {} affected zero rows", id);
        }
        return deleted;
    }
}
