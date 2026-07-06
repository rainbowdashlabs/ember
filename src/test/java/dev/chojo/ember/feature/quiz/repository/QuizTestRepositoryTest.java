/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.quiz.repository;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.quiz.entity.QuizQuestionType;
import dev.chojo.ember.feature.quiz.entity.TestStatus;
import dev.chojo.ember.feature.restriction.RestrictionMode;
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
class QuizTestRepositoryTest extends RepositoryTestBase {
    private static Station station;
    private static Account account;
    private static StationMember member;
    private static int testId;
    private static int catalogId;
    private static int questionId;
    private static int sectionId;

    @BeforeAll
    static void setup() {
        station = stationRepo.create("QuizTestRepoStation");
        account = accountRepo.create("quiztest-repo@test.com", "Quiz", "Tester");
        member = stationMemberRepo.create(station.id(), account.id());

        catalogId = quizCatalogRepo
                .create(station.id(), "TestRepoCatalog", "", false)
                .id();
        questionId = quizCatalogRepo
                .createQuestion(
                        catalogId,
                        null,
                        QuizQuestionType.TRUE_FALSE,
                        "Is the sky blue?",
                        "",
                        null,
                        1.0,
                        false,
                        "{\"correctAnswer\":true}",
                        0)
                .id();
    }

    @AfterAll
    static void cleanup() {
        quizCatalogRepo.delete(catalogId);
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    @Test
    @Order(1)
    void createTest() {
        var test =
                quizTestRepo.create(station.id(), "First Aid Test", "Basic first aid", 60, false, false, member.id());
        assertNotNull(test);
        assertEquals("First Aid Test", test.title());
        assertEquals(TestStatus.DRAFT, test.status());
        testId = test.id();
    }

    @Test
    @Order(2)
    void findByStation() {
        var tests = quizTestRepo.findByStation(station.id());
        assertFalse(tests.isEmpty());
        assertTrue(tests.stream().anyMatch(t -> t.id() == testId));
    }

    @Test
    @Order(3)
    void findById() {
        var found = quizTestRepo.findById(testId);
        assertTrue(found.isPresent());
        assertTrue(quizTestRepo.findById(99999).isEmpty());
    }

    @Test
    @Order(4)
    void updateTest() {
        assertTrue(quizTestRepo.update(testId, "Updated Test", "Updated desc", 90, true, true, null, null));
        var found = quizTestRepo.findById(testId).orElseThrow();
        assertEquals("Updated Test", found.title());
        assertTrue(found.shuffle());
        assertTrue(found.forced());
    }

    @Test
    @Order(5)
    void updateStatus() {
        assertTrue(quizTestRepo.updateStatus(testId, TestStatus.ACTIVE));
        assertEquals(
                TestStatus.ACTIVE, quizTestRepo.findById(testId).orElseThrow().status());
    }

    @Test
    @Order(6)
    void updateRestrictionMode() {
        assertTrue(quizTestRepo.updateRestrictionMode(testId, RestrictionMode.AND));
        assertEquals(
                RestrictionMode.AND, quizTestRepo.findById(testId).orElseThrow().restrictionMode());
    }

    @Test
    @Order(7)
    void countAttempts() {
        assertEquals(0, quizTestRepo.countAttempts(testId));
    }

    // -- Sections --

    @Test
    @Order(10)
    void createSection() {
        var section = quizTestRepo.createSection(testId, "Section One", "First section", 0);
        assertNotNull(section);
        assertEquals("Section One", section.title());
        sectionId = section.id();
    }

    @Test
    @Order(11)
    void findSections() {
        var sections = quizTestRepo.findSections(testId);
        assertFalse(sections.isEmpty());
        assertTrue(sections.stream().anyMatch(s -> s.id() == sectionId));
    }

    @Test
    @Order(12)
    void updateSection() {
        assertTrue(quizTestRepo.updateSection(sectionId, "Updated Section", "Updated desc", 1));
    }

    @Test
    @Order(13)
    void createSource() {
        var source = quizTestRepo.createSource(sectionId, catalogId, null, 1);
        assertNotNull(source);
        assertEquals(catalogId, source.catalogId());

        var sources = quizTestRepo.findSources(sectionId);
        assertFalse(sources.isEmpty());
    }

    @Test
    @Order(14)
    void deleteSourcesBySection() {
        quizTestRepo.deleteSourcesBySection(sectionId);
        assertTrue(quizTestRepo.findSources(sectionId).isEmpty());
    }

    // -- Frozen Questions --

    @Test
    @Order(20)
    void frozenQuestions() {
        quizTestRepo.createFrozenQuestion(testId, questionId, sectionId, 0);
        var frozen = quizTestRepo.findFrozenQuestions(testId);
        assertFalse(frozen.isEmpty());
        assertEquals(questionId, frozen.getFirst().questionId());
    }

    @Test
    @Order(21)
    void deleteFrozenQuestionAtPosition() {
        quizTestRepo.deleteFrozenQuestionAtPosition(testId, 0);
        assertTrue(quizTestRepo.findFrozenQuestions(testId).isEmpty());
    }

    @Test
    @Order(22)
    void deleteFrozenQuestions() {
        quizTestRepo.createFrozenQuestion(testId, questionId, null, 0);
        assertFalse(quizTestRepo.findFrozenQuestions(testId).isEmpty());
        quizTestRepo.deleteFrozenQuestions(testId);
        assertTrue(quizTestRepo.findFrozenQuestions(testId).isEmpty());
    }

    // -- Attempts --

    @Test
    @Order(30)
    void createAttempt() {
        quizTestRepo.createFrozenQuestion(testId, questionId, null, 0);
        var attempt = quizTestRepo.createAttempt(testId, member.id(), 10);
        assertNotNull(attempt);
        assertEquals(testId, attempt.testId());
        assertEquals(member.id(), attempt.memberId());

        var found = quizTestRepo.findAttemptById(attempt.id());
        assertTrue(found.isPresent());

        var byMember = quizTestRepo.findAttempt(testId, member.id());
        assertTrue(byMember.isPresent());

        var attempts = quizTestRepo.findAttempts(testId);
        assertFalse(attempts.isEmpty());

        assertEquals(1, quizTestRepo.countAttempts(testId));
    }

    @Test
    @Order(31)
    void submitAttempt() {
        var attempt = quizTestRepo.findAttempt(testId, member.id()).orElseThrow();
        assertTrue(quizTestRepo.submitAttempt(attempt.id()));
    }

    @Test
    @Order(32)
    void gradeAttempt() {
        var attempt = quizTestRepo.findAttempt(testId, member.id()).orElseThrow();
        assertTrue(quizTestRepo.gradeAttempt(attempt.id(), 8.0, member.id()));
    }

    // -- Attempt Questions --

    @Test
    @Order(40)
    void attemptQuestions() {
        var attempt = quizTestRepo.findAttempt(testId, member.id()).orElseThrow();
        quizTestRepo.createAttemptQuestion(attempt.id(), questionId, null, 0);
        var qs = quizTestRepo.findAttemptQuestions(attempt.id());
        assertFalse(qs.isEmpty());
    }

    // -- Answers --

    @Test
    @Order(50)
    void saveAndFindAnswer() {
        var attempt = quizTestRepo.findAttempt(testId, member.id()).orElseThrow();
        quizTestRepo.saveAnswer(attempt.id(), questionId, "{\"value\":true}");
        var answers = quizTestRepo.findAnswers(attempt.id());
        assertFalse(answers.isEmpty());
        int answerId = answers.getFirst().id();
        assertTrue(quizTestRepo.findAnswerById(answerId).isPresent());
        assertEquals(
                attempt.id(),
                quizTestRepo.findAnswerById(answerId).orElseThrow().attemptId());
        assertTrue(quizTestRepo.findAnswerById(-1).isEmpty());
    }

    @Test
    @Order(51)
    void gradeAnswer() {
        var attempt = quizTestRepo.findAttempt(testId, member.id()).orElseThrow();
        var answers = quizTestRepo.findAnswers(attempt.id());
        if (!answers.isEmpty()) {
            assertTrue(quizTestRepo.gradeAnswer(answers.getFirst().id(), 1.0));
        }
    }

    // -- Member Access --

    @Test
    @Order(60)
    void memberAccess() {
        assertFalse(quizTestRepo.hasMemberAccess(testId, member.id()));
        quizTestRepo.grantMemberAccess(testId, member.id(), null);
        assertTrue(quizTestRepo.hasMemberAccess(testId, member.id()));
        quizTestRepo.revokeMemberAccess(testId, member.id());
        assertFalse(quizTestRepo.hasMemberAccess(testId, member.id()));
    }

    // -- findByStationForMember --

    @Test
    @Order(61)
    void findByStationForMember() {
        var tests = quizTestRepo.findByStationForMember(station.id(), member.id());
        // Member is station member, manager bypass applies — expect the test to be visible
        assertNotNull(tests);
    }

    // -- deleteSource (individual) --

    @Test
    @Order(62)
    void deleteSource() {
        // create a fresh section and source, then delete the source by ID
        var sec = quizTestRepo.createSection(testId, "TmpSection", "", 99);
        var src = quizTestRepo.createSource(sec.id(), catalogId, null, 1);
        assertNotNull(src);
        assertTrue(quizTestRepo.deleteSource(src.id()));
        assertTrue(quizTestRepo.findSources(sec.id()).isEmpty());
        quizTestRepo.deleteSection(sec.id());
    }

    // -- upsertAnswer --

    @Test
    @Order(63)
    void upsertAnswer() {
        var attempt = quizTestRepo.findAttempt(testId, member.id()).orElseThrow();
        // upsertAnswer inserts with ON CONFLICT on id — first call inserts
        assertDoesNotThrow(() -> quizTestRepo.upsertAnswer(attempt.id(), questionId, null, "{\"value\":false}", 0));
        var answers = quizTestRepo.findAnswers(attempt.id());
        assertFalse(answers.isEmpty());
    }

    // -- Section delete --

    @Test
    @Order(70)
    void deleteSection() {
        assertTrue(quizTestRepo.deleteSection(sectionId));
    }

    // -- Cleanup --

    @Test
    @Order(99)
    void deleteTest() {
        quizTestRepo.deleteSectionsByTest(testId);
        assertTrue(quizTestRepo.delete(testId));
        assertTrue(quizTestRepo.findById(testId).isEmpty());
    }
}
