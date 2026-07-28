/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.quiz.service;

import dev.chojo.ember.feature.quiz.entity.CreateQuestionCommand;
import dev.chojo.ember.feature.quiz.entity.QuestionConfig;
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
class QuizQuestionServiceTest extends RepositoryTestBase {
    private static QuizQuestionService service;
    private static Station station;
    private static int catalogId;
    private static int categoryId;
    private static int questionId;

    @BeforeAll
    static void setup() {
        service = new QuizQuestionService(quizCatalogRepo);
        station = stationRepo.create("QuizQuestionSvcStation");
        catalogId = quizCatalogRepo
                .create(station.id(), "Question Catalog", "Questions", false)
                .id();
        categoryId = quizCatalogRepo
                .createCategory(station.id(), "QuestionCat", "", 0)
                .id();
    }

    @AfterAll
    static void cleanup() {
        for (var question : service.findQuestions(catalogId)) {
            quizCatalogRepo.deleteQuestion(question.id());
        }
        quizCatalogRepo.deleteCategory(categoryId);
        quizCatalogRepo.delete(catalogId);
        stationRepo.delete(station.id());
    }

    @Test
    @Order(1)
    void createQuestion() {
        var question = service.createQuestion(
                CreateQuestionCommand.builder(catalogId, QuizQuestionType.TRUE_FALSE, "Is the sky blue?")
                        .category(categoryId)
                        .description("Sky color")
                        .points(2.0)
                        .autoPoints(false)
                        .configJson("{\"correctAnswer\":true}")
                        .build());
        assertEquals("Is the sky blue?", question.title());
        assertEquals(2.0, question.points());
        questionId = question.id();
    }

    @Test
    @Order(2)
    void createQuestionDerivesAutoPoints() {
        var question = service.createQuestion(
                CreateQuestionCommand.builder(catalogId, QuizQuestionType.MULTIPLE_CHOICE, "What is 2+2?")
                        .configJson("{\"options\":[{\"text\":\"4\",\"correct\":true}],\"pointsPerCorrect\":3.0}")
                        .position(1)
                        .build());
        assertEquals(3.0, question.points());
        service.deleteQuestion(question.id());
    }

    @Test
    @Order(3)
    void createQuestionKeepsPointsWhenConfigYieldsNone() {
        var question = service.createQuestion(
                CreateQuestionCommand.builder(catalogId, QuizQuestionType.MULTIPLE_CHOICE, "Nothing correct")
                        .points(7.0)
                        .configJson("{\"options\":[]}")
                        .position(2)
                        .build());
        assertEquals(7.0, question.points());
        service.deleteQuestion(question.id());
    }

    @Test
    @Order(4)
    void createQuestionSerializesTypedConfig() {
        var question = service.createQuestion(
                CreateQuestionCommand.builder(catalogId, QuizQuestionType.TRUE_FALSE, "Typed config")
                        .points(1.0)
                        .autoPoints(false)
                        .config(new QuestionConfig.TrueFalse(true))
                        .position(3)
                        .build());
        assertInstanceOf(QuestionConfig.TrueFalse.class, question.config());
        service.deleteQuestion(question.id());
    }

    @Test
    @Order(10)
    void findQuestions() {
        assertTrue(service.findQuestions(catalogId).stream().anyMatch(q -> q.id() == questionId));
    }

    @Test
    @Order(11)
    void findQuestion() {
        assertTrue(service.findQuestion(questionId).isPresent());
        assertTrue(service.findQuestion(99999).isEmpty());
    }

    @Test
    @Order(12)
    void findQuestionsByIds() {
        assertEquals(1, service.findQuestionsByIds(List.of(questionId)).size());
    }

    @Test
    @Order(13)
    void countQuestions() {
        assertTrue(service.countQuestions(catalogId) >= 1);
    }

    @Test
    @Order(20)
    void updateQuestion() {
        assertTrue(service.updateQuestion(
                questionId, categoryId, "Is water wet?", "Water", null, 2.0, false, "{\"correctAnswer\":true}", 0));
        assertFalse(service.updateQuestion(99999, null, "Nothing", "", null, 1.0, false, "{}", 0));
    }

    @Test
    @Order(21)
    void updateQuestionRecalculatesAutoPoints() {
        assertTrue(service.updateQuestion(
                questionId, categoryId, "Pick the right ones", "", null, 1.0, true, "{\"correctAnswer\":true}", 0));
    }

    @Test
    @Order(23)
    void updateQuestionKeepsPointsForUnparsableConfig() {
        assertTrue(service.updateQuestion(questionId, categoryId, "Broken config", "", null, 9.0, true, "[1,2,3]", 0));
        assertEquals(9.0, service.findQuestion(questionId).orElseThrow().points());
    }

    @Test
    @Order(90)
    void deleteQuestion() {
        assertTrue(service.deleteQuestion(questionId));
        assertFalse(service.deleteQuestion(questionId));
    }
}
