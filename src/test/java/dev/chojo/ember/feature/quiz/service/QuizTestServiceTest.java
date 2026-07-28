/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.quiz.service;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.quiz.entity.QuizQuestionType;
import dev.chojo.ember.feature.quiz.entity.SectionEntry;
import dev.chojo.ember.feature.quiz.entity.SourceEntry;
import dev.chojo.ember.feature.quiz.entity.TestStatus;
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
class QuizTestServiceTest extends RepositoryTestBase {
    private static QuizTestService service;
    private static Station station;
    private static Account account;
    private static StationMember member;
    private static int catalogId;
    private static int categoryId;
    private static int questionId;
    private static int testId;

    @BeforeAll
    static void setup() {
        service = new QuizTestService(quizTestRepo, new QuizQuestionSelector(quizCatalogRepo, quizTestRepo));
        station = stationRepo.create("QuizTestSvcStation");
        account = accountRepo.create("quiz-test-svc@test.com", "Quiz", "Tester");
        member = stationMemberRepo.create(station.id(), account.id());
        catalogId = quizCatalogRepo
                .create(station.id(), "Test Catalog", "Questions", false)
                .id();
        categoryId =
                quizCatalogRepo.createCategory(station.id(), "TestCat", "", 0).id();
        questionId = createQuestion("Base question", categoryId, 0);
    }

    @AfterAll
    static void cleanup() {
        for (var test : service.findTests(station.id())) {
            service.deleteTest(test.id());
        }
        for (var question : quizCatalogRepo.findQuestions(catalogId)) {
            quizCatalogRepo.deleteQuestion(question.id());
        }
        quizCatalogRepo.deleteCategory(categoryId);
        quizCatalogRepo.delete(catalogId);
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    private static int createQuestion(String title, Integer categoryId, int position) {
        return quizCatalogRepo
                .createQuestion(
                        catalogId,
                        categoryId,
                        QuizQuestionType.TRUE_FALSE,
                        title,
                        "",
                        null,
                        1.0,
                        false,
                        "{\"correctAnswer\":true}",
                        position)
                .id();
    }

    private static List<SectionEntry> oneSection(Integer categoryId, int questionCount) {
        return List.of(new SectionEntry("Section", "", List.of(new SourceEntry(catalogId, categoryId, questionCount))));
    }

    @Test
    @Order(1)
    void createTest() {
        var test = service.createTest(station.id(), "First Aid Test", "Basic first aid", 60, false, false, member.id());
        assertEquals("First Aid Test", test.title());
        assertEquals(TestStatus.DRAFT, test.status());
        testId = test.id();
    }

    @Test
    @Order(2)
    void findTests() {
        assertTrue(service.findTests(station.id()).stream().anyMatch(t -> t.id() == testId));
    }

    @Test
    @Order(3)
    void findTest() {
        assertTrue(service.findTest(testId).isPresent());
        assertTrue(service.findTest(99999).isEmpty());
    }

    @Test
    @Order(4)
    void findTestsForMember() {
        assertNotNull(service.findTestsForMember(station.id(), member.id()));
    }

    @Test
    @Order(5)
    void updateTest() {
        assertTrue(service.updateTest(testId, "Updated Test", "Updated", 90, true, false, null, null));
        assertFalse(service.updateTest(99999, "Nothing", "", null, false, false, null, null));
    }

    @Test
    @Order(6)
    void countAttempts() {
        assertEquals(0, service.countAttempts(testId));
    }

    @Test
    @Order(10)
    void replaceSections() {
        service.replaceSections(testId, oneSection(categoryId, 1));
        var sections = service.findSections(testId);
        assertEquals(1, sections.size());
        assertEquals("Section", sections.getFirst().title());
        assertFalse(service.findSources(sections.getFirst().id()).isEmpty());
    }

    @Test
    @Order(11)
    void generateFrozenQuestions() {
        service.generateFrozenQuestions(testId);
        var frozen = service.findFrozenQuestions(testId);
        assertEquals(1, frozen.size());
        assertEquals(questionId, frozen.getFirst().questionId());
    }

    @Test
    @Order(12)
    void findAvailableReplacements() {
        int extra = createQuestion("Replacement candidate", categoryId, 1);
        var replacements = service.findAvailableReplacements(testId);
        assertTrue(replacements.stream().anyMatch(q -> q.id() == extra));
        quizCatalogRepo.deleteQuestion(extra);
    }

    @Test
    @Order(13)
    void replaceFrozenQuestion() {
        int extra = createQuestion("Manual replacement", null, 2);
        service.replaceFrozenQuestion(testId, 0, extra);
        assertTrue(service.findFrozenQuestions(testId).stream().anyMatch(fq -> fq.questionId() == extra));
        service.replaceFrozenQuestion(testId, 0, questionId);
        quizCatalogRepo.deleteQuestion(extra);
    }

    @Test
    @Order(14)
    void replaceWithRandomQuestion() {
        int extra = createQuestion("Random replacement", categoryId, 3);
        service.replaceWithRandomQuestion(testId, 0);
        assertTrue(service.findFrozenQuestions(testId).stream().anyMatch(fq -> fq.questionId() == extra));
        service.replaceFrozenQuestion(testId, 0, questionId);
        quizCatalogRepo.deleteQuestion(extra);
    }

    @Test
    @Order(15)
    void replaceWithRandomQuestionWithoutCandidates() {
        assertThrows(IllegalStateException.class, () -> service.replaceWithRandomQuestion(testId, 0));
    }

    @Test
    @Order(20)
    void activateTest() {
        assertTrue(service.activateTest(testId));
        assertEquals(TestStatus.ACTIVE, service.findTest(testId).orElseThrow().status());
    }

    @Test
    @Order(21)
    void closeTest() {
        assertTrue(service.closeTest(testId));
        assertEquals(TestStatus.CLOSED, service.findTest(testId).orElseThrow().status());
    }

    @Test
    @Order(30)
    void activateTestGeneratesFrozenQuestions() {
        var test = service.createTest(station.id(), "AutoFrozen", "", null, false, false, member.id());
        service.replaceSections(test.id(), oneSection(categoryId, 1));
        assertTrue(service.activateTest(test.id()));
        assertFalse(service.findFrozenQuestions(test.id()).isEmpty());
        service.deleteTest(test.id());
    }

    @Test
    @Order(31)
    void generateFrozenQuestionsShuffled() {
        var test = service.createTest(station.id(), "Shuffled", "", null, true, false, member.id());
        int second = createQuestion("Shuffle partner", categoryId, 4);
        service.replaceSections(test.id(), oneSection(categoryId, 0));
        service.generateFrozenQuestions(test.id());
        assertEquals(2, service.findFrozenQuestions(test.id()).size());
        service.deleteTest(test.id());
        quizCatalogRepo.deleteQuestion(second);
    }

    @Test
    @Order(32)
    void generateFrozenQuestionsBalancedAcrossCategories() {
        int otherCategory =
                quizCatalogRepo.createCategory(station.id(), "OtherCat", "", 1).id();
        int first = createQuestion("Balanced one", categoryId, 5);
        int second = createQuestion("Balanced two", categoryId, 6);
        int third = createQuestion("Balanced three", otherCategory, 7);

        var test = service.createTest(station.id(), "Balanced", "", null, false, false, member.id());
        service.replaceSections(test.id(), oneSection(null, 2));
        service.generateFrozenQuestions(test.id());
        assertEquals(2, service.findFrozenQuestions(test.id()).size());

        service.deleteTest(test.id());
        quizCatalogRepo.deleteQuestion(first);
        quizCatalogRepo.deleteQuestion(second);
        quizCatalogRepo.deleteQuestion(third);
        quizCatalogRepo.deleteCategory(otherCategory);
    }

    @Test
    @Order(33)
    void generateFrozenQuestionsTakesWholeCatalogWhenCountExceedsPool() {
        var test = service.createTest(station.id(), "WholeCatalog", "", null, false, false, member.id());
        service.replaceSections(test.id(), oneSection(null, 10));
        service.generateFrozenQuestions(test.id());
        assertEquals(1, service.findFrozenQuestions(test.id()).size());
        service.deleteTest(test.id());
    }

    @Test
    @Order(40)
    void findForcedPending() {
        var test = service.createTest(station.id(), "Forced", "", null, false, true, member.id());
        service.replaceSections(test.id(), oneSection(categoryId, 1));
        service.activateTest(test.id());
        assertTrue(
                service.findForcedPending(station.id(), member.id()).stream().anyMatch(item -> item.id() == test.id()));
        service.deleteTest(test.id());
    }

    @Test
    @Order(90)
    void deleteTest() {
        assertTrue(service.deleteTest(testId));
        assertFalse(service.deleteTest(testId));
    }
}
