/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.quiz.service;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.quiz.entity.CreateQuestionCommand;
import dev.chojo.ember.feature.quiz.entity.QuizQuestionType;
import dev.chojo.ember.feature.quiz.entity.SectionEntry;
import dev.chojo.ember.feature.quiz.entity.SourceEntry;
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

/**
 * Covers the entry point other features use. Each method must reach the service that owns
 * the operation, so the test drives the facade end to end instead of mocking it out.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class QuizServiceTest extends RepositoryTestBase {
    private static QuizService service;
    private static QuizTestService testService;
    private static Station station;
    private static Account account;
    private static StationMember member;
    private static int catalogId;
    private static int questionId;
    private static int testId;

    @BeforeAll
    static void setup() {
        var catalogService = new QuizCatalogService(quizCatalogRepo);
        var questionService = new QuizQuestionService(quizCatalogRepo);
        testService = new QuizTestService(quizTestRepo, new QuizQuestionSelector(quizCatalogRepo, quizTestRepo));
        service = new QuizService(
                catalogService,
                questionService,
                testService,
                new QuizAttemptService(quizTestRepo, questionService, new QuizAnswerGrader()));

        station = stationRepo.create("QuizFacadeStation");
        account = accountRepo.create("quiz-facade@test.com", "Quiz", "Tester");
        member = stationMemberRepo.create(station.id(), account.id());
    }

    @AfterAll
    static void cleanup() {
        for (var test : testService.findTests(station.id())) {
            testService.deleteTest(test.id());
        }
        for (var question : quizCatalogRepo.findQuestions(catalogId)) {
            quizCatalogRepo.deleteQuestion(question.id());
        }
        quizCatalogRepo.delete(catalogId);
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    @Test
    @Order(1)
    void createsCatalogsCategoriesAndQuestions() {
        var catalog = service.createCatalog(station.id(), "Facade Catalog", "Catalog", false);
        catalogId = catalog.id();
        var category = service.createCategory(station.id(), "Facade Category", "Category", 0);

        var question = service.createQuestion(
                CreateQuestionCommand.builder(catalogId, QuizQuestionType.TRUE_FALSE, "Is the sky blue?")
                        .category(category.id())
                        .points(2.0)
                        .autoPoints(false)
                        .configJson("{\"correctAnswer\":true}")
                        .build());
        questionId = question.id();
        assertEquals(
                "Is the sky blue?",
                service.findQuestion(questionId).orElseThrow().title());
    }

    @Test
    @Order(2)
    void updatesAndActivatesTests() {
        testId = testService
                .createTest(station.id(), "Facade Test", "", null, false, true, member.id())
                .id();
        testService.replaceSections(
                testId, List.of(new SectionEntry("S", "", List.of(new SourceEntry(catalogId, null, 0)))));

        assertTrue(service.updateTest(testId, "Facade Test", "Updated", 30, false, true, null, null));
        assertTrue(service.activateTest(testId));
    }

    @Test
    @Order(3)
    void listsForcedPendingTests() {
        assertTrue(service.findForcedPending(station.id(), member.id()).stream().anyMatch(item -> item.id() == testId));
    }

    @Test
    @Order(4)
    void runsAnAttemptFromStartToSubmit() {
        var attempt = service.startAttempt(testId, member.id());
        assertFalse(service.findAttemptQuestions(attempt.id()).isEmpty());
        service.saveAnswer(attempt.id(), questionId, "{\"value\":true}");
        assertTrue(service.submitAttempt(attempt.id()));
        assertTrue(
                service.findForcedPending(station.id(), member.id()).stream().noneMatch(item -> item.id() == testId));
    }
}
