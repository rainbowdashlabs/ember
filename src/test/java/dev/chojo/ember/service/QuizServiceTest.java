/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.service;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.federation.entity.ShareScope;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.federation.service.FederationHttpClient;
import dev.chojo.ember.feature.federation.service.FederationService;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.quiz.entity.QuizCatalog;
import dev.chojo.ember.feature.quiz.entity.QuizQuestionType;
import dev.chojo.ember.feature.quiz.entity.SectionEntry;
import dev.chojo.ember.feature.quiz.entity.SourceEntry;
import dev.chojo.ember.feature.quiz.entity.TestStatus;
import dev.chojo.ember.feature.quiz.service.QuizService;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import dev.chojo.ember.feature.restriction.RestrictionRepository;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class QuizServiceTest extends RepositoryTestBase {
    private static QuizService service;
    private static Station station;
    private static Account account;
    private static StationMember member;
    private static int catalogId;
    private static int categoryId;
    private static int questionId;
    private static int testId;
    private static int attemptId;

    // Federation fields
    private static FederationRepository federationRepo;
    private static FederationService federationService;
    private static FederationHttpClient httpClient;
    private static Station stationB;
    private static Station stationC;
    private static int partnerIdAtoB;

    @BeforeAll
    static void setup() {
        federationRepo = new FederationRepository();
        federationService = new FederationService(federationRepo, stationRepo, new Api());
        httpClient = mock(FederationHttpClient.class);

        service = new QuizService(
                quizCatalogRepo,
                quizTestRepo,
                new RestrictionRepository(stationMemberRepo, memberGroupRepo, userTagRepo),
                federationService,
                federationRepo,
                httpClient,
                stationRepo);

        station = stationRepo.create("QuizSvcStation");
        stationB = stationRepo.create("QuizSvcStationB");
        stationC = stationRepo.create("QuizSvcStationC");
        account = accountRepo.create("quiz-svc@test.com", "Quiz", "Tester");
        member = stationMemberRepo.create(station.id(), account.id());

        // Create bidirectional federation partnership (all capabilities enabled by default)
        var keyPair = federationService.generateKeyPair();
        var partner = federationService.acceptInvite(
                station.id(), stationB.id(), federationService.encodePublicKey(keyPair), null, null);
        partnerIdAtoB = partner.id();

        // Create remote federation partnership (stationC is a remote partner)
        var keyPairC = federationService.generateKeyPair();
        federationService.acceptInvite(
                station.id(),
                stationC.id(),
                federationService.encodePublicKey(keyPairC),
                "https://remote-quiz.example.com",
                null);
    }

    @AfterAll
    static void cleanup() {
        for (var p : federationService.findPartners(station.id())) federationRepo.deletePartner(p.id());
        for (var p : federationService.findPartners(stationB.id())) federationRepo.deletePartner(p.id());
        for (var p : federationService.findPartners(stationC.id())) federationRepo.deletePartner(p.id());
        stationRepo.delete(station.id());
        stationRepo.delete(stationB.id());
        stationRepo.delete(stationC.id());
        accountRepo.delete(account.id());
    }

    // -- Catalogs --

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
        var catalogs = service.findCatalogs(station.id());
        assertTrue(catalogs.stream().anyMatch(c -> c.id() == catalogId));
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
    }

    @Test
    @Order(5)
    void findTrainingCatalogs() {
        // training_enabled was set to false above
        var training = service.findTrainingCatalogs(station.id());
        assertFalse(training.stream().anyMatch(c -> c.id() == catalogId));

        // Re-enable and verify
        service.updateCatalog(catalogId, "Updated Safety", "Updated", true);
        training = service.findTrainingCatalogs(station.id());
        assertTrue(training.stream().anyMatch(c -> c.id() == catalogId));
    }

    // -- Categories --

    @Test
    @Order(10)
    void createCategory() {
        var cat = service.createCategory(station.id(), "Fire Safety", "Fire questions", 0);
        assertNotNull(cat);
        categoryId = cat.id();
    }

    @Test
    @Order(11)
    void findCategories() {
        var cats = service.findCategories(station.id());
        assertTrue(cats.stream().anyMatch(c -> c.id() == categoryId));
    }

    @Test
    @Order(12)
    void findCategory() {
        assertTrue(service.findCategory(categoryId).isPresent());
    }

    @Test
    @Order(13)
    void updateCategory() {
        assertTrue(service.updateCategory(categoryId, "Updated Fire", "Updated", 1));
    }

    // -- Questions --

    @Test
    @Order(20)
    void createQuestion() {
        var q = service.createQuestion(
                catalogId,
                categoryId,
                QuizQuestionType.TRUE_FALSE,
                "Is the sky blue?",
                "Sky color",
                null,
                2.0,
                false,
                "{\"correctAnswer\":true}",
                0);
        assertNotNull(q);
        assertEquals("Is the sky blue?", q.title());
        questionId = q.id();
    }

    @Test
    @Order(21)
    void createQuestionAutoPoints() {
        var q = service.createQuestion(
                catalogId,
                null,
                QuizQuestionType.MULTIPLE_CHOICE,
                "What is 2+2?",
                "Math",
                null,
                1.0,
                true,
                "{\"options\":[{\"text\":\"4\",\"correct\":true}],\"pointsPerCorrect\":3.0}",
                1);
        assertNotNull(q);
        // Auto points from config: pointsPerCorrect = 3.0
        assertEquals(3.0, q.points());
        service.deleteQuestion(q.id());
    }

    @Test
    @Order(22)
    void findQuestions() {
        var qs = service.findQuestions(catalogId);
        assertTrue(qs.stream().anyMatch(q -> q.id() == questionId));
    }

    @Test
    @Order(23)
    void findQuestion() {
        assertTrue(service.findQuestion(questionId).isPresent());
    }

    @Test
    @Order(24)
    void countQuestions() {
        assertTrue(service.countQuestions(catalogId) >= 1);
    }

    @Test
    @Order(25)
    void updateQuestion() {
        assertTrue(service.updateQuestion(
                questionId, categoryId, "Is water wet?", "Water", null, 2.0, false, "{\"correctAnswer\":true}", 0));
    }

    @Test
    @Order(26)
    void updateQuestionAutoPoints() {
        // With autoPoints=true, should recalculate from config
        assertTrue(service.updateQuestion(
                questionId, categoryId, "Is sky blue?", "", null, 1.0, true, "{\"correctAnswer\":true}", 0));
    }

    // -- Tests --

    @Test
    @Order(30)
    void createTest() {
        var test = service.createTest(station.id(), "First Aid Test", "Basic first aid", 60, false, member.id());
        assertNotNull(test);
        assertEquals("First Aid Test", test.title());
        assertEquals(TestStatus.DRAFT, test.status());
        testId = test.id();
    }

    @Test
    @Order(31)
    void findTests() {
        var tests = service.findTests(station.id());
        assertTrue(tests.stream().anyMatch(t -> t.id() == testId));
    }

    @Test
    @Order(32)
    void findTest() {
        assertTrue(service.findTest(testId).isPresent());
    }

    @Test
    @Order(33)
    void findTestsForMember() {
        var tests = service.findTestsForMember(station.id(), member.id());
        assertNotNull(tests);
    }

    @Test
    @Order(34)
    void updateTest() {
        assertTrue(service.updateTest(testId, "Updated Test", "Updated", 90, true, null, null));
    }

    @Test
    @Order(35)
    void countAttempts() {
        assertEquals(0, service.countAttempts(testId));
    }

    // -- Sections and Frozen Questions --

    @Test
    @Order(40)
    void replaceSections() {
        var source = new SourceEntry(catalogId, categoryId, 1);
        var section = new SectionEntry("Section One", "First section", List.of(source));
        service.replaceSections(testId, List.of(section));

        var sections = service.findSections(testId);
        assertFalse(sections.isEmpty());
        assertEquals("Section One", sections.getFirst().title());
    }

    @Test
    @Order(41)
    void findSources() {
        var sections = service.findSections(testId);
        var sources = service.findSources(sections.getFirst().id());
        assertFalse(sources.isEmpty());
    }

    @Test
    @Order(42)
    void generateFrozenQuestions() {
        service.generateFrozenQuestions(testId);
        var frozen = service.findFrozenQuestions(testId);
        assertFalse(frozen.isEmpty());
    }

    @Test
    @Order(43)
    void findAvailableReplacements() {
        // add extra question to have replacement candidates
        var q2 = quizCatalogRepo.createQuestion(
                catalogId,
                categoryId,
                QuizQuestionType.TRUE_FALSE,
                "Another question",
                "",
                null,
                1.0,
                false,
                "{\"correctAnswer\":false}",
                1);
        var replacements = service.findAvailableReplacements(testId);
        assertNotNull(replacements);
        quizCatalogRepo.deleteQuestion(q2.id());
    }

    @Test
    @Order(44)
    void replaceFrozenQuestion() {
        var q2 = quizCatalogRepo.createQuestion(
                catalogId,
                null,
                QuizQuestionType.TRUE_FALSE,
                "Extra q",
                "",
                null,
                1.0,
                false,
                "{\"correctAnswer\":false}",
                2);
        service.replaceFrozenQuestion(testId, 0, q2.id());
        var frozen = service.findFrozenQuestions(testId);
        assertTrue(frozen.stream().anyMatch(fq -> fq.questionId() == q2.id()));
        quizCatalogRepo.deleteQuestion(q2.id());
    }

    // -- Activation --

    @Test
    @Order(50)
    void activateTest() {
        // regenerate frozen with current question
        service.replaceSections(
                testId, List.of(new SectionEntry("Section", "", List.of(new SourceEntry(catalogId, categoryId, 1)))));
        service.generateFrozenQuestions(testId);
        assertTrue(service.activateTest(testId));
        assertEquals(TestStatus.ACTIVE, service.findTest(testId).orElseThrow().status());
    }

    @Test
    @Order(51)
    void isTestAccessible() {
        var test = service.findTest(testId).orElseThrow();
        // No time window restrictions, should be accessible
        assertTrue(service.isTestAccessible(test, member.id(), EnumSet.noneOf(StationPermission.class)));
    }

    @Test
    @Order(52)
    void isTestAccessibleDraftNotAccessible() {
        var draftTest = service.createTest(station.id(), "Draft", "", null, false, member.id());
        var draft = service.findTest(draftTest.id()).orElseThrow();
        assertFalse(service.isTestAccessible(draft, member.id(), EnumSet.noneOf(StationPermission.class)));
        service.deleteTest(draftTest.id());
    }

    // -- Attempts --

    @Test
    @Order(60)
    void startAttempt() {
        var attempt = service.startAttempt(testId, member.id());
        assertNotNull(attempt);
        assertEquals(testId, attempt.testId());
        attemptId = attempt.id();
    }

    @Test
    @Order(61)
    void findAttempt() {
        var found = service.findAttempt(testId, member.id());
        assertTrue(found.isPresent());
    }

    @Test
    @Order(62)
    void findAttemptById() {
        assertTrue(service.findAttemptById(attemptId).isPresent());
    }

    @Test
    @Order(63)
    void findAttempts() {
        var attempts = service.findAttempts(testId);
        assertFalse(attempts.isEmpty());
    }

    @Test
    @Order(64)
    void findAttemptQuestions() {
        var qs = service.findAttemptQuestions(attemptId);
        assertFalse(qs.isEmpty());
    }

    // -- Answers --

    @Test
    @Order(70)
    void saveAnswer() {
        var qs = service.findAttemptQuestions(attemptId);
        int qId = qs.getFirst().questionId();
        service.saveAnswer(attemptId, qId, "{\"value\":true}");
        var answers = service.findAnswers(attemptId);
        assertFalse(answers.isEmpty());
    }

    @Test
    @Order(71)
    void gradeAnswer() {
        var answers = service.findAnswers(attemptId);
        if (!answers.isEmpty()) {
            assertTrue(service.gradeAnswer(answers.getFirst().id(), 2.0));
        }
    }

    @Test
    @Order(72)
    void submitAttempt() {
        assertTrue(service.submitAttempt(attemptId));
    }

    @Test
    @Order(73)
    void gradeAttempt() {
        assertTrue(service.gradeAttempt(attemptId, member.id()));
    }

    // -- Member Access --

    @Test
    @Order(80)
    void memberAccess() {
        service.grantMemberAccess(testId, member.id(), null);
        assertTrue(service.isTestAccessible(
                service.findTest(testId).orElseThrow(), member.id(), EnumSet.noneOf(StationPermission.class)));
        service.revokeMemberAccess(testId, member.id());
    }

    // -- Restrictions --

    @Test
    @Order(85)
    void restrictions() {
        service.setRestrictions(testId, List.of(), List.of(), List.of(), List.of());
        var rs = service.findRestrictions(testId);
        assertNotNull(rs);
        assertFalse(rs.hasRestrictions());

        service.updateRestrictionMode(testId, RestrictionMode.AND);
        assertEquals(RestrictionMode.AND, service.findTest(testId).orElseThrow().restrictionMode());

        assertTrue(service.canMemberAccess(testId, member.id(), EnumSet.noneOf(StationPermission.class)));
    }

    // -- Close / Delete --

    @Test
    @Order(90)
    void closeTest() {
        assertTrue(service.closeTest(testId));
        assertEquals(TestStatus.CLOSED, service.findTest(testId).orElseThrow().status());
    }

    @Test
    @Order(98)
    void deleteCategory() {
        assertTrue(service.deleteCategory(categoryId));
    }

    @Test
    @Order(92)
    void activateTestAutoGeneratesFrozen() {
        // Create a test with sections, don't generate frozen, then activate
        var test2 = service.createTest(station.id(), "AutoFrozen", "", null, false, member.id());
        service.replaceSections(
                test2.id(), List.of(new SectionEntry("S", "", List.of(new SourceEntry(catalogId, categoryId, 1)))));
        // Don't call generateFrozenQuestions — activateTest should do it
        assertTrue(service.activateTest(test2.id()));
        var frozen = service.findFrozenQuestions(test2.id());
        assertFalse(frozen.isEmpty());
        service.deleteTest(test2.id());
    }

    @Test
    @Order(93)
    void replaceWithRandomQuestion() {
        var test2 = service.createTest(station.id(), "Random Replace", "", null, false, member.id());
        // Create two questions so there's an available replacement
        var q1 = quizCatalogRepo.createQuestion(
                catalogId,
                categoryId,
                QuizQuestionType.TRUE_FALSE,
                "Q1",
                "",
                null,
                1.0,
                false,
                "{\"correctAnswer\":true}",
                0);
        var q2 = quizCatalogRepo.createQuestion(
                catalogId,
                categoryId,
                QuizQuestionType.TRUE_FALSE,
                "Q2",
                "",
                null,
                1.0,
                false,
                "{\"correctAnswer\":false}",
                1);
        service.replaceSections(
                test2.id(), List.of(new SectionEntry("S", "", List.of(new SourceEntry(catalogId, categoryId, 1)))));
        service.generateFrozenQuestions(test2.id());
        // Should succeed without exception
        service.replaceWithRandomQuestion(test2.id(), 0);
        quizCatalogRepo.deleteQuestion(q1.id());
        quizCatalogRepo.deleteQuestion(q2.id());
        service.deleteTest(test2.id());
    }

    @Test
    @Order(94)
    void isTestAccessibleWithTimeWindow() {
        var test2 = service.createTest(station.id(), "Timed", "", null, false, member.id());
        // Set start_at in the future
        service.updateTest(test2.id(), "Timed", "", null, false, Instant.now().plusSeconds(86400), null);
        service.replaceSections(
                test2.id(), List.of(new SectionEntry("S", "", List.of(new SourceEntry(catalogId, categoryId, 1)))));
        service.activateTest(test2.id());
        // Active but start_at is in the future — not accessible
        var test = service.findTest(test2.id()).orElseThrow();
        assertFalse(service.isTestAccessible(test, member.id(), EnumSet.noneOf(StationPermission.class)));
        service.deleteTest(test2.id());
    }

    @Test
    @Order(95)
    void isTestAccessibleWithEndInPast() {
        var test2 = service.createTest(station.id(), "Ended", "", null, false, member.id());
        service.updateTest(
                test2.id(),
                "Ended",
                "",
                null,
                false,
                Instant.now().minusSeconds(86400),
                Instant.now().minusSeconds(3600));
        service.replaceSections(
                test2.id(), List.of(new SectionEntry("S", "", List.of(new SourceEntry(catalogId, categoryId, 1)))));
        service.activateTest(test2.id());
        var test = service.findTest(test2.id()).orElseThrow();
        assertFalse(service.isTestAccessible(test, member.id(), EnumSet.noneOf(StationPermission.class)));
        service.deleteTest(test2.id());
    }

    @Test
    @Order(96)
    void isTestAccessibleGrantedMemberAccess() {
        var test2 = service.createTest(station.id(), "MemberAccess", "", null, false, member.id());
        service.replaceSections(
                test2.id(), List.of(new SectionEntry("S", "", List.of(new SourceEntry(catalogId, categoryId, 1)))));
        service.activateTest(test2.id());
        service.grantMemberAccess(test2.id(), member.id(), null);
        var test = service.findTest(test2.id()).orElseThrow();
        assertTrue(service.isTestAccessible(test, member.id(), EnumSet.noneOf(StationPermission.class)));
        service.revokeMemberAccess(test2.id(), member.id());
        service.deleteTest(test2.id());
    }

    // -- Auto-grading various question types --

    @Test
    @Order(75)
    void autoGradeMultipleChoice() {
        var q = service.createQuestion(
                catalogId,
                null,
                QuizQuestionType.MULTIPLE_CHOICE,
                "MC Question",
                "",
                null,
                3.0,
                false,
                "{\"options\":[{\"text\":\"A\",\"correct\":true},{\"text\":\"B\",\"correct\":false},{\"text\":\"C\",\"correct\":true}],\"pointsPerCorrect\":1.0}",
                10);
        var test2 = service.createTest(station.id(), "MC Grade", "", null, false, member.id());
        service.replaceSections(
                test2.id(), List.of(new SectionEntry("S", "", List.of(new SourceEntry(catalogId, null, 0)))));
        service.generateFrozenQuestions(test2.id());
        service.activateTest(test2.id());
        var attempt = service.startAttempt(test2.id(), member.id());
        // Answer correctly: selected indices 0 and 2
        service.saveAnswer(attempt.id(), q.id(), "{\"selected\":[0,2]}");
        service.submitAttempt(attempt.id());
        // Check that answer was auto-graded
        var answers = service.findAnswers(attempt.id());
        var answer = answers.stream().filter(a -> a.questionId() == q.id()).findFirst();
        assertTrue(answer.isPresent());
        assertTrue(answer.get().graded());

        service.deleteTest(test2.id());
        service.deleteQuestion(q.id());
    }

    @Test
    @Order(76)
    void autoGradeTrueFalse() {
        var q = service.createQuestion(
                catalogId,
                null,
                QuizQuestionType.TRUE_FALSE,
                "TF Question",
                "",
                null,
                2.0,
                false,
                "{\"correctAnswer\":true}",
                11);
        var test2 = service.createTest(station.id(), "TF Grade", "", null, false, member.id());
        service.replaceSections(
                test2.id(), List.of(new SectionEntry("S", "", List.of(new SourceEntry(catalogId, null, 0)))));
        service.generateFrozenQuestions(test2.id());
        service.activateTest(test2.id());
        var attempt = service.startAttempt(test2.id(), member.id());
        service.saveAnswer(attempt.id(), q.id(), "{\"value\":true}");
        service.submitAttempt(attempt.id());
        var answers = service.findAnswers(attempt.id());
        var answer = answers.stream().filter(a -> a.questionId() == q.id()).findFirst();
        assertTrue(answer.isPresent());
        assertTrue(answer.get().graded());
        assertEquals(2.0, answer.get().points());

        service.deleteTest(test2.id());
        service.deleteQuestion(q.id());
    }

    @Test
    @Order(77)
    void autoGradeFreeAnswerNotAutoGraded() {
        var q = service.createQuestion(
                catalogId, null, QuizQuestionType.FREE_ANSWER, "Free Answer", "", null, 5.0, false, "{}", 12);
        var test2 = service.createTest(station.id(), "FA Grade", "", null, false, member.id());
        service.replaceSections(
                test2.id(), List.of(new SectionEntry("S", "", List.of(new SourceEntry(catalogId, null, 0)))));
        service.generateFrozenQuestions(test2.id());
        service.activateTest(test2.id());
        var attempt = service.startAttempt(test2.id(), member.id());
        service.saveAnswer(attempt.id(), q.id(), "{\"text\":\"My answer\"}");
        service.submitAttempt(attempt.id());
        // Free answer is NOT auto-graded
        var answers = service.findAnswers(attempt.id());
        var answer = answers.stream().filter(a -> a.questionId() == q.id()).findFirst();
        assertTrue(answer.isPresent());
        assertFalse(answer.get().graded());

        service.deleteTest(test2.id());
        service.deleteQuestion(q.id());
    }

    @Test
    @Order(78)
    void autoGradeOrdering() {
        var q = service.createQuestion(
                catalogId,
                null,
                QuizQuestionType.ORDERING,
                "Order Question",
                "",
                null,
                3.0,
                false,
                "{\"items\":[\"A\",\"B\",\"C\"]}",
                13);
        var test2 = service.createTest(station.id(), "Ord Grade", "", null, false, member.id());
        service.replaceSections(
                test2.id(), List.of(new SectionEntry("S", "", List.of(new SourceEntry(catalogId, null, 0)))));
        service.generateFrozenQuestions(test2.id());
        service.activateTest(test2.id());
        var attempt = service.startAttempt(test2.id(), member.id());
        // Perfect order: [0, 1, 2]
        service.saveAnswer(attempt.id(), q.id(), "{\"order\":[0,1,2]}");
        service.submitAttempt(attempt.id());
        var answers = service.findAnswers(attempt.id());
        var answer = answers.stream().filter(a -> a.questionId() == q.id()).findFirst();
        assertTrue(answer.isPresent());
        assertTrue(answer.get().graded());
        assertEquals(3.0, answer.get().points());

        service.deleteTest(test2.id());
        service.deleteQuestion(q.id());
    }

    @Test
    @Order(79)
    void autoGradeFillBlank() {
        var q = service.createQuestion(
                catalogId,
                null,
                QuizQuestionType.FILL_IN_THE_BLANK,
                "Fill Question",
                "",
                null,
                2.0,
                false,
                "{\"answers\":[\"Paris\"]}",
                14);
        var test2 = service.createTest(station.id(), "Fill Grade", "", null, false, member.id());
        service.replaceSections(
                test2.id(), List.of(new SectionEntry("S", "", List.of(new SourceEntry(catalogId, null, 0)))));
        service.generateFrozenQuestions(test2.id());
        service.activateTest(test2.id());
        var attempt = service.startAttempt(test2.id(), member.id());
        service.saveAnswer(attempt.id(), q.id(), "{\"text\":\"Paris\"}");
        service.submitAttempt(attempt.id());
        var answers = service.findAnswers(attempt.id());
        var answer = answers.stream().filter(a -> a.questionId() == q.id()).findFirst();
        assertTrue(answer.isPresent());
        assertTrue(answer.get().graded());

        service.deleteTest(test2.id());
        service.deleteQuestion(q.id());
    }

    @Test
    @Order(86)
    void autoGradeEnumeration() {
        var q = service.createQuestion(
                catalogId,
                null,
                QuizQuestionType.ENUMERATION,
                "Enum Question",
                "",
                null,
                4.0,
                false,
                "{\"answers\":[\"red\",\"blue\",\"green\"],\"requiredCount\":3}",
                15);
        var test2 = service.createTest(station.id(), "Enum Grade", "", null, false, member.id());
        service.replaceSections(
                test2.id(), List.of(new SectionEntry("S", "", List.of(new SourceEntry(catalogId, null, 0)))));
        service.generateFrozenQuestions(test2.id());
        service.activateTest(test2.id());
        var attempt = service.startAttempt(test2.id(), member.id());
        service.saveAnswer(attempt.id(), q.id(), "{\"items\":[\"red\",\"blue\",\"green\"]}");
        service.submitAttempt(attempt.id());
        var answers = service.findAnswers(attempt.id());
        var answer = answers.stream().filter(a -> a.questionId() == q.id()).findFirst();
        assertTrue(answer.isPresent());
        assertTrue(answer.get().graded());

        service.deleteTest(test2.id());
        service.deleteQuestion(q.id());
    }

    @Test
    @Order(87)
    void autoGradeConnect() {
        var q = service.createQuestion(
                catalogId,
                null,
                QuizQuestionType.CONNECT,
                "Connect Question",
                "",
                null,
                2.0,
                false,
                "{\"pairs\":[{\"left\":\"A\",\"right\":\"1\"},{\"left\":\"B\",\"right\":\"2\"}]}",
                16);
        var test2 = service.createTest(station.id(), "Con Grade", "", null, false, member.id());
        service.replaceSections(
                test2.id(), List.of(new SectionEntry("S", "", List.of(new SourceEntry(catalogId, null, 0)))));
        service.generateFrozenQuestions(test2.id());
        service.activateTest(test2.id());
        var attempt = service.startAttempt(test2.id(), member.id());
        service.saveAnswer(attempt.id(), q.id(), "{\"pairs\":{\"0\":\"1\",\"1\":\"2\"}}");
        service.submitAttempt(attempt.id());
        var answers = service.findAnswers(attempt.id());
        var answer = answers.stream().filter(a -> a.questionId() == q.id()).findFirst();
        assertTrue(answer.isPresent());
        assertTrue(answer.get().graded());

        service.deleteTest(test2.id());
        service.deleteQuestion(q.id());
    }

    @Test
    @Order(88)
    void autoGradeMultipleChoiceWrongAnswer() {
        var q = service.createQuestion(
                catalogId,
                null,
                QuizQuestionType.MULTIPLE_CHOICE,
                "MC Wrong",
                "",
                null,
                2.0,
                false,
                "{\"options\":[{\"text\":\"A\",\"correct\":true},{\"text\":\"B\",\"correct\":false}],\"pointsPerCorrect\":1.0}",
                18);
        var test2 = service.createTest(station.id(), "MCW Grade", "", null, false, member.id());
        service.replaceSections(
                test2.id(), List.of(new SectionEntry("S", "", List.of(new SourceEntry(catalogId, null, 0)))));
        service.generateFrozenQuestions(test2.id());
        service.activateTest(test2.id());
        var attempt = service.startAttempt(test2.id(), member.id());
        // Select wrong answer (index 1)
        service.saveAnswer(attempt.id(), q.id(), "{\"selected\":[1]}");
        service.submitAttempt(attempt.id());
        var answers = service.findAnswers(attempt.id());
        var answer = answers.stream().filter(a -> a.questionId() == q.id()).findFirst();
        assertTrue(answer.isPresent());
        assertTrue(answer.get().graded());
        assertEquals(0.0, answer.get().points());

        service.deleteTest(test2.id());
        service.deleteQuestion(q.id());
    }

    @Test
    @Order(89)
    void balancedCatalogPicking() {
        // Create multiple questions in different categories
        var cat2 = service.createCategory(station.id(), "BalCat", "", 99);
        var q1 = service.createQuestion(
                catalogId,
                categoryId,
                QuizQuestionType.TRUE_FALSE,
                "BQ1",
                "",
                null,
                1.0,
                false,
                "{\"correctAnswer\":true}",
                20);
        var q2 = service.createQuestion(
                catalogId,
                categoryId,
                QuizQuestionType.TRUE_FALSE,
                "BQ2",
                "",
                null,
                1.0,
                false,
                "{\"correctAnswer\":false}",
                21);
        var q3 = service.createQuestion(
                catalogId,
                cat2.id(),
                QuizQuestionType.TRUE_FALSE,
                "BQ3",
                "",
                null,
                1.0,
                false,
                "{\"correctAnswer\":true}",
                22);

        // Use a source WITHOUT categoryId but WITH count=2 to trigger pickBalancedFromCatalog
        var test2 = service.createTest(station.id(), "Balanced", "", null, false, member.id());
        service.replaceSections(
                test2.id(), List.of(new SectionEntry("S", "", List.of(new SourceEntry(catalogId, null, 2)))));
        service.generateFrozenQuestions(test2.id());
        var frozen = service.findFrozenQuestions(test2.id());
        assertEquals(2, frozen.size());

        service.deleteTest(test2.id());
        service.deleteQuestion(q1.id());
        service.deleteQuestion(q2.id());
        service.deleteQuestion(q3.id());
        service.deleteCategory(cat2.id());
    }

    @Test
    @Order(74)
    void autoGradeFillBlankWithGaps() {
        var q = service.createQuestion(
                catalogId,
                null,
                QuizQuestionType.FILL_IN_THE_BLANK,
                "Fill Gaps",
                "",
                null,
                4.0,
                false,
                "{\"answers\":[\"Paris\",\"Berlin\"]}",
                17);
        var test2 = service.createTest(station.id(), "FillGap", "", null, false, member.id());
        service.replaceSections(
                test2.id(), List.of(new SectionEntry("S", "", List.of(new SourceEntry(catalogId, null, 0)))));
        service.generateFrozenQuestions(test2.id());
        service.activateTest(test2.id());
        var attempt = service.startAttempt(test2.id(), member.id());
        service.saveAnswer(attempt.id(), q.id(), "{\"gaps\":{\"0\":\"Paris\",\"1\":\"Berlin\"}}");
        service.submitAttempt(attempt.id());
        var answers = service.findAnswers(attempt.id());
        var answer = answers.stream().filter(a -> a.questionId() == q.id()).findFirst();
        assertTrue(answer.isPresent());
        assertTrue(answer.get().graded());

        service.deleteTest(test2.id());
        service.deleteQuestion(q.id());
    }

    @Test
    @Order(97)
    void deleteQuestion() {
        var q = service.createQuestion(
                catalogId,
                null,
                QuizQuestionType.TRUE_FALSE,
                "ToDelete",
                "",
                null,
                1.0,
                false,
                "{\"correctAnswer\":true}",
                99);
        assertTrue(service.deleteQuestion(q.id()));
    }

    @Test
    @Order(99)
    void deleteTestAndCatalog() {
        // Delete test first (removes frozen questions which reference category/questions)
        assertTrue(service.deleteTest(testId));
        // Delete remaining question before category (FK constraint)
        service.deleteQuestion(questionId);
        // Category may already be deleted by order 98 in original; try without assertion
        service.deleteCategory(categoryId);
        assertTrue(service.deleteCatalog(catalogId));
    }

    // -- Federated Quiz Tests --

    @Test
    @Order(200)
    void browseSharedQuizWithShare() {
        var fedCatalog = quizCatalogRepo.create(stationB.id(), "FedCatalog", "Federated catalog", false);
        var share = federationRepo.createQuizShare(stationB.id(), fedCatalog.id(), ShareScope.ALL_PARTNERS);
        var shared = service.browseSharedQuiz(station.id());
        assertTrue(shared.stream().anyMatch(s -> s.id() == fedCatalog.id()));
        federationRepo.deleteQuizShare(share.id());
        quizCatalogRepo.delete(fedCatalog.id());
    }

    @Test
    @Order(201)
    void browseSharedQuizEmptyNoShares() {
        var shared = service.browseSharedQuiz(station.id());
        assertTrue(shared.isEmpty());
    }

    @Test
    @Order(202)
    void getFederatedQuizCatalogLocal() {
        var fedCatalog = quizCatalogRepo.create(stationB.id(), "FedDetail", "Detailed catalog", false);
        var fedQuestion = quizCatalogRepo.createQuestion(
                fedCatalog.id(),
                null,
                QuizQuestionType.TRUE_FALSE,
                "FedQ1",
                "desc",
                null,
                2.0,
                false,
                "{\"correctAnswer\":true}",
                0);
        var result = service.getFederatedQuizCatalog(station.id(), stationB.uid(), fedCatalog.id());
        assertNotNull(result);
        assertNotNull(result.catalog());
        assertNotNull(result.categories());
        assertNotNull(result.questions());
        quizCatalogRepo.deleteQuestion(fedQuestion.id());
        quizCatalogRepo.delete(fedCatalog.id());
    }

    @Test
    @Order(203)
    void getFederatedQuizCatalogWrongStation() {
        // Create catalog on station (not stationB) — fetching via stationB should fail.
        // Partner may or may not exist due to cross-test interference;
        // either way the call must reject access (wrong ownership or unknown partner).
        var localCatalog = quizCatalogRepo.create(station.id(), "LocalOnly", "Not on stationB", false);

        assertThrows(
                Exception.class,
                () -> service.getFederatedQuizCatalog(station.id(), stationB.uid(), localCatalog.id()));

        quizCatalogRepo.delete(localCatalog.id());
    }

    @Test
    @Order(204)
    void copyQuizCatalog() {
        // Create catalog with category and question on stationB
        var srcCatalog = quizCatalogRepo.create(stationB.id(), "CopySrc", "Source for copy", true);
        var srcCat = quizCatalogRepo.createCategory(stationB.id(), "CopyCat", "Category to copy", 0);
        var srcQ = quizCatalogRepo.createQuestion(
                srcCatalog.id(),
                srcCat.id(),
                QuizQuestionType.TRUE_FALSE,
                "CopyQ1",
                "desc",
                null,
                3.0,
                false,
                "{\"correctAnswer\":true}",
                0);

        // Copy catalog to station
        var copied = service.copyQuizCatalog(srcCatalog.id(), station.id());
        assertNotNull(copied);
        assertEquals("CopySrc", copied.name());
        assertNotEquals(srcCatalog.id(), copied.id());

        // Verify questions were copied
        var copiedQuestions = service.findQuestions(copied.id());
        assertFalse(copiedQuestions.isEmpty());
        assertEquals("CopyQ1", copiedQuestions.getFirst().title());

        // Cleanup
        for (var q : copiedQuestions) {
            quizCatalogRepo.deleteQuestion(q.id());
        }
        quizCatalogRepo.delete(copied.id());
        quizCatalogRepo.deleteQuestion(srcQ.id());
        quizCatalogRepo.deleteCategory(srcCat.id());
        quizCatalogRepo.delete(srcCatalog.id());
    }

    @Test
    @Order(205)
    void sharedQuizItemRecord() {
        var item = new QuizService.SharedQuizItem(42, "Test Catalog", "A description", 7, 3);
        assertEquals(42, item.id());
        assertEquals("Test Catalog", item.name());
        assertEquals("A description", item.description());
        assertEquals(7, item.sourceStationId());
        assertEquals(3, item.partnerId());
    }

    // -- Remote HTTP federation tests --

    @Test
    @Order(210)
    void browseSharedQuizViaHttp() {
        when(httpClient.getList(
                        eq("https://remote-quiz.example.com"),
                        eq("/remote/quiz/catalogs"),
                        eq(station.id()),
                        any(),
                        eq(QuizService.RemoteQuizCatalog.class)))
                .thenReturn(List.of(new QuizService.RemoteQuizCatalog(99, "RemoteCatalog", "remote desc")));
        var items = service.browseSharedQuiz(station.id());
        assertTrue(items.stream().anyMatch(i -> i.name().equals("RemoteCatalog")));
    }

    @Test
    @Order(211)
    void getFederatedQuizCatalogRemote() {
        var remoteResult = new QuizService.FederatedCatalogDetail(
                new QuizCatalog(88, 0, "RemoteCatalog", "desc", false, null, null), List.of(), List.of());
        when(httpClient.get(
                        eq("https://remote-quiz.example.com"),
                        eq("/remote/quiz/catalogs/88"),
                        eq(station.id()),
                        any(),
                        any()))
                .thenReturn(remoteResult);
        var result = service.getFederatedQuizCatalog(station.id(), stationC.uid(), 88);
        assertNotNull(result);
        assertNotNull(result.catalog());
    }
}
