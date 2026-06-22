/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.quiz.repository;

import dev.chojo.ember.feature.quiz.entity.QuizQuestionType;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class QuizCatalogRepositoryTest extends RepositoryTestBase {
    private static Station station;
    private static int catalogId;
    private static int categoryId;
    private static int questionId;

    @BeforeAll
    static void setup() {
        station = stationRepo.create("QuizCatalogStation");
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
    }

    @Test
    @Order(1)
    void createCatalog() {
        var catalog = quizCatalogRepo.create(station.id(), "Safety Catalog", "Safety questions", true);
        assertNotNull(catalog);
        assertEquals("Safety Catalog", catalog.name());
        assertTrue(catalog.trainingEnabled());
        catalogId = catalog.id();
    }

    @Test
    @Order(2)
    void findByStation() {
        var catalogs = quizCatalogRepo.findByStation(station.id());
        assertFalse(catalogs.isEmpty());
        assertTrue(catalogs.stream().anyMatch(c -> c.id() == catalogId));
    }

    @Test
    @Order(3)
    void findById() {
        var found = quizCatalogRepo.findById(catalogId);
        assertTrue(found.isPresent());
        assertEquals("Safety Catalog", found.get().name());

        assertTrue(quizCatalogRepo.findById(99999).isEmpty());
    }

    @Test
    @Order(4)
    void findTrainingCatalogs() {
        var training = quizCatalogRepo.findTrainingCatalogs(station.id());
        assertTrue(training.stream().anyMatch(c -> c.id() == catalogId));
    }

    @Test
    @Order(5)
    void updateCatalog() {
        boolean updated = quizCatalogRepo.update(catalogId, "Updated Catalog", "Updated desc", false);
        assertTrue(updated);
        var found = quizCatalogRepo.findById(catalogId).orElseThrow();
        assertEquals("Updated Catalog", found.name());
        assertFalse(found.trainingEnabled());
    }

    // -- Categories --

    @Test
    @Order(10)
    void createCategory() {
        var cat = quizCatalogRepo.createCategory(station.id(), "Fire Safety", "All about fire", 0);
        assertNotNull(cat);
        assertEquals("Fire Safety", cat.name());
        categoryId = cat.id();
    }

    @Test
    @Order(11)
    void findCategoriesByStation() {
        var cats = quizCatalogRepo.findCategoriesByStation(station.id());
        assertFalse(cats.isEmpty());
        assertTrue(cats.stream().anyMatch(c -> c.id() == categoryId));
    }

    @Test
    @Order(12)
    void findCategoryById() {
        var found = quizCatalogRepo.findCategoryById(categoryId);
        assertTrue(found.isPresent());
        assertTrue(quizCatalogRepo.findCategoryById(99999).isEmpty());
    }

    @Test
    @Order(13)
    void updateCategory() {
        assertTrue(quizCatalogRepo.updateCategory(categoryId, "Fire & Safety", "Updated", 1));
        assertEquals(
                "Fire & Safety",
                quizCatalogRepo.findCategoryById(categoryId).orElseThrow().name());
    }

    // -- Questions --

    @Test
    @Order(20)
    void createQuestion() {
        var q = quizCatalogRepo.createQuestion(
                catalogId,
                categoryId,
                QuizQuestionType.MULTIPLE_CHOICE,
                "What is 2+2?",
                "Math question",
                null,
                2.0,
                false,
                "{\"options\":[{\"text\":\"4\",\"correct\":true},{\"text\":\"3\",\"correct\":false}]}",
                0);
        assertNotNull(q);
        assertEquals("What is 2+2?", q.title());
        assertEquals(QuizQuestionType.MULTIPLE_CHOICE, q.quizQuestionType());
        questionId = q.id();
    }

    @Test
    @Order(21)
    void findQuestions() {
        var questions = quizCatalogRepo.findQuestions(catalogId);
        assertFalse(questions.isEmpty());
        assertTrue(questions.stream().anyMatch(q -> q.id() == questionId));
    }

    @Test
    @Order(22)
    void findQuestionsByCategory() {
        var questions = quizCatalogRepo.findQuestionsByCategory(catalogId, categoryId);
        assertFalse(questions.isEmpty());
        assertTrue(questions.stream().anyMatch(q -> q.id() == questionId));
    }

    @Test
    @Order(23)
    void findQuestionById() {
        var found = quizCatalogRepo.findQuestionById(questionId);
        assertTrue(found.isPresent());
        assertTrue(quizCatalogRepo.findQuestionById(99999).isEmpty());
    }

    @Test
    @Order(24)
    void countQuestions() {
        int count = quizCatalogRepo.countQuestions(catalogId);
        assertTrue(count >= 1);
    }

    @Test
    @Order(25)
    void updateQuestion() {
        boolean updated = quizCatalogRepo.updateQuestion(
                questionId, null, "What is 3+3?", "Updated math", null, 3.0, false, "{}", 1);
        assertTrue(updated);
        var found = quizCatalogRepo.findQuestionById(questionId).orElseThrow();
        assertEquals("What is 3+3?", found.title());
        assertNull(found.categoryId());
    }

    // -- Cleanup --

    @Test
    @Order(30)
    void setPublicRenderAndFindPublicByStation() {
        assertTrue(quizCatalogRepo.findPublicByStation(station.id()).isEmpty());
        assertTrue(quizCatalogRepo.setPublicRender(catalogId, true));
        var publicCats = quizCatalogRepo.findPublicByStation(station.id());
        assertTrue(publicCats.stream().anyMatch(c -> c.id() == catalogId));
        assertTrue(quizCatalogRepo.setPublicRender(catalogId, false));
        assertTrue(quizCatalogRepo.findPublicByStation(station.id()).isEmpty());
        assertFalse(quizCatalogRepo.setPublicRender(99999, true));
    }

    @Test
    @Order(31)
    void findRandomPublicQuestion() {
        assertTrue(quizCatalogRepo
                .findRandomPublicQuestion(station.id(), List.of())
                .isEmpty());
        assertTrue(quizCatalogRepo.findRandomPublicQuestion(station.id(), null).isEmpty());

        assertTrue(quizCatalogRepo
                .findRandomPublicQuestion(station.id(), List.of(catalogId))
                .isEmpty());

        quizCatalogRepo.setPublicRender(catalogId, true);
        try {
            var picked = quizCatalogRepo.findRandomPublicQuestion(station.id(), List.of(catalogId));
            assertTrue(picked.isPresent());
            assertEquals(questionId, picked.orElseThrow().id());
        } finally {
            quizCatalogRepo.setPublicRender(catalogId, false);
        }
    }

    @Test
    @Order(32)
    void findQuestionsByIds() {
        assertTrue(quizCatalogRepo.findQuestionsByIds(List.of()).isEmpty());
        var found = quizCatalogRepo.findQuestionsByIds(List.of(questionId));
        assertEquals(1, found.size());
    }

    @Test
    @Order(90)
    void deleteQuestion() {
        assertTrue(quizCatalogRepo.deleteQuestion(questionId));
        assertTrue(quizCatalogRepo.findQuestionById(questionId).isEmpty());
    }

    @Test
    @Order(91)
    void deleteCategory() {
        assertTrue(quizCatalogRepo.deleteCategory(categoryId));
        assertTrue(quizCatalogRepo.findCategoryById(categoryId).isEmpty());
    }

    @Test
    @Order(99)
    void deleteCatalog() {
        assertTrue(quizCatalogRepo.delete(catalogId));
        assertTrue(quizCatalogRepo.findById(catalogId).isEmpty());
    }
}
