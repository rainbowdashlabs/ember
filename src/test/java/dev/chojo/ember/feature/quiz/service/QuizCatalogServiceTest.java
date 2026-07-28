/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.quiz.service;

import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class QuizCatalogServiceTest extends RepositoryTestBase {
    private static QuizCatalogService service;
    private static Station station;
    private static int catalogId;
    private static int categoryId;

    @BeforeAll
    static void setup() {
        service = new QuizCatalogService(quizCatalogRepo);
        station = stationRepo.create("QuizCatalogSvcStation");
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
    }

    @Test
    @Order(1)
    void createCatalog() {
        var catalog = service.createCatalog(station.id(), "Safety Quiz", "Safety questions", true);
        assertNotNull(catalog);
        assertEquals("Safety Quiz", catalog.name());
        catalogId = catalog.id();
    }

    @Test
    @Order(2)
    void findCatalogs() {
        assertTrue(service.findCatalogs(station.id()).stream().anyMatch(c -> c.id() == catalogId));
    }

    @Test
    @Order(3)
    void findCatalog() {
        assertTrue(service.findCatalog(catalogId).isPresent());
        assertTrue(service.findCatalog(99999).isEmpty());
    }

    @Test
    @Order(4)
    void updateCatalog() {
        assertTrue(service.updateCatalog(catalogId, "Updated Safety", "Updated", false));
        assertFalse(service.updateCatalog(99999, "Nothing", "", false));
    }

    @Test
    @Order(5)
    void findTrainingCatalogs() {
        assertFalse(service.findTrainingCatalogs(station.id()).stream().anyMatch(c -> c.id() == catalogId));

        service.updateCatalog(catalogId, "Updated Safety", "Updated", true);
        assertTrue(service.findTrainingCatalogs(station.id()).stream().anyMatch(c -> c.id() == catalogId));
    }

    @Test
    @Order(10)
    void createCategory() {
        var category = service.createCategory(station.id(), "Fire Safety", "Fire questions", 0);
        assertNotNull(category);
        categoryId = category.id();
    }

    @Test
    @Order(11)
    void findCategories() {
        assertTrue(service.findCategories(station.id()).stream().anyMatch(c -> c.id() == categoryId));
    }

    @Test
    @Order(12)
    void findCategory() {
        assertTrue(service.findCategory(categoryId).isPresent());
        assertTrue(service.findCategory(99999).isEmpty());
    }

    @Test
    @Order(13)
    void updateCategory() {
        assertTrue(service.updateCategory(categoryId, "Updated Fire", "Updated", 1));
        assertFalse(service.updateCategory(99999, "Nothing", "", 0));
    }

    @Test
    @Order(90)
    void deleteCategory() {
        assertTrue(service.deleteCategory(categoryId));
        assertFalse(service.deleteCategory(categoryId));
    }

    @Test
    @Order(91)
    void deleteCatalog() {
        assertTrue(service.deleteCatalog(catalogId));
        assertFalse(service.deleteCatalog(catalogId));
    }
}
