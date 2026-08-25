/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.quiz.service;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.quiz.entity.CatalogMetadata;
import dev.chojo.ember.feature.quiz.entity.QuizQuestionType;
import dev.chojo.ember.feature.quiz.entity.QuizTest;
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

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class QuizAttemptServiceTest extends RepositoryTestBase {
    private static QuizAttemptService service;
    private static QuizTestService testService;
    private static Station station;
    private static Account account;
    private static StationMember member;
    private static int catalogId;
    private static int questionId;
    private static int testId;
    private static int attemptId;

    @BeforeAll
    static void setup() {
        service =
                new QuizAttemptService(quizTestRepo, new QuizQuestionService(quizCatalogRepo), new QuizAnswerGrader());
        testService = new QuizTestService(quizTestRepo, new QuizQuestionSelector(quizCatalogRepo, quizTestRepo));
        station = stationRepo.create("QuizAttemptSvcStation");
        account = accountRepo.create("quiz-attempt-svc@test.com", "Quiz", "Tester");
        member = stationMemberRepo.create(station.id(), account.id());
        catalogId = quizCatalogRepo
                .create(station.id(), "Attempt Catalog", "Questions", false, CatalogMetadata.none())
                .id();
        questionId =
                createQuestion(QuizQuestionType.TRUE_FALSE, "Is the sky blue?", "{\"correctAnswer\":true}", 2.0, 0);
        testId = activeTest("Attempt Test").id();
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

    private static int createQuestion(QuizQuestionType type, String title, String config, double points, int position) {
        return quizCatalogRepo
                .createQuestion(catalogId, null, type, title, "", null, points, false, config, position)
                .id();
    }

    private static QuizTest activeTest(String title) {
        var test = testService.createTest(station.id(), title, "", null, false, false, member.id());
        testService.replaceSections(
                test.id(), List.of(new SectionEntry("S", "", List.of(new SourceEntry(catalogId, null, 0)))));
        testService.generateFrozenQuestions(test.id());
        testService.activateTest(test.id());
        return test;
    }

    @Test
    @Order(1)
    void startAttempt() {
        var attempt = service.startAttempt(testId, member.id());
        assertEquals(testId, attempt.testId());
        attemptId = attempt.id();
    }

    @Test
    @Order(2)
    void startAttemptWithoutFrozenQuestions() {
        var test = testService.createTest(station.id(), "No questions", "", null, false, false, member.id());
        assertThrows(IllegalStateException.class, () -> service.startAttempt(test.id(), member.id()));
        testService.deleteTest(test.id());
    }

    @Test
    @Order(3)
    void findAttempts() {
        assertTrue(service.findAttempt(testId, member.id()).isPresent());
        assertTrue(service.findAttemptById(attemptId).isPresent());
        assertFalse(service.findAttempts(testId).isEmpty());
        assertFalse(service.findAttemptQuestions(attemptId).isEmpty());
    }

    @Test
    @Order(10)
    void saveAnswer() {
        service.saveAnswer(attemptId, questionId, "{\"value\":true}");
        var answers = service.findAnswers(attemptId);
        assertFalse(answers.isEmpty());
        assertTrue(service.findAnswerById(answers.getFirst().id()).isPresent());
        assertTrue(service.findAnswerById(-1).isEmpty());
    }

    @Test
    @Order(11)
    void saveAnswerForUnknownQuestion() {
        assertThrows(IllegalArgumentException.class, () -> service.saveAnswer(attemptId, 99999, "{}"));
    }

    @Test
    @Order(12)
    void gradeAnswer() {
        var answers = service.findAnswers(attemptId);
        assertTrue(service.gradeAnswer(answers.getFirst().id(), 2.0));
        assertFalse(service.gradeAnswer(99999, 1.0));
    }

    @Test
    @Order(20)
    void submitAttempt() {
        assertTrue(service.submitAttempt(attemptId));
        assertFalse(service.submitAttempt(99999));
    }

    @Test
    @Order(21)
    void gradeAttempt() {
        assertTrue(service.gradeAttempt(attemptId, member.id()));
        assertFalse(service.gradeAttempt(99999, member.id()));
    }

    @Test
    @Order(30)
    void submitAttemptAutoGradesAnswers() {
        int multipleChoice = createQuestion(
                QuizQuestionType.MULTIPLE_CHOICE,
                "Pick the right ones",
                "{\"options\":[{\"text\":\"A\",\"correct\":true},{\"text\":\"B\",\"correct\":false}],"
                        + "\"pointsPerCorrect\":1.0}",
                2.0,
                1);
        int freeAnswer = createQuestion(QuizQuestionType.FREE_ANSWER, "Explain", "{}", 5.0, 2);

        var test = activeTest("AutoGrade");
        var attempt = service.startAttempt(test.id(), member.id());
        service.saveAnswer(attempt.id(), multipleChoice, "{\"selected\":[0]}");
        service.saveAnswer(attempt.id(), freeAnswer, "{\"text\":\"Because.\"}");
        assertTrue(service.submitAttempt(attempt.id()));

        var answers = service.findAnswers(attempt.id());
        assertTrue(answers.stream()
                .filter(a -> a.questionId() == multipleChoice)
                .allMatch(a -> a.graded() && a.points() == 1.0));
        assertTrue(answers.stream().filter(a -> a.questionId() == freeAnswer).noneMatch(a -> a.graded()));

        testService.deleteTest(test.id());
        quizCatalogRepo.deleteQuestion(multipleChoice);
        quizCatalogRepo.deleteQuestion(freeAnswer);
    }
}
