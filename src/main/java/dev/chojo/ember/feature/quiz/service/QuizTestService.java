/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.quiz.service;

import dev.chojo.ember.feature.quiz.entity.QuizQuestion;
import dev.chojo.ember.feature.quiz.entity.QuizTest;
import dev.chojo.ember.feature.quiz.entity.QuizTestFrozenQuestion;
import dev.chojo.ember.feature.quiz.entity.QuizTestSection;
import dev.chojo.ember.feature.quiz.entity.QuizTestSectionSource;
import dev.chojo.ember.feature.quiz.entity.SectionEntry;
import dev.chojo.ember.feature.quiz.entity.TestStatus;
import dev.chojo.ember.feature.quiz.repository.QuizTestRepository;
import dev.chojo.ember.feature.system.service.RequirementsService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Tests and their configuration: the test itself, its lifecycle, the sections and sources
 * it draws questions from, and the frozen question set that fixes what every member taking
 * the test is asked.
 */
@Singleton
public class QuizTestService {
    private static final Logger log = LoggerFactory.getLogger(QuizTestService.class);

    private final QuizTestRepository testRepository;
    private final QuizQuestionSelector questionSelector;

    @Inject
    public QuizTestService(QuizTestRepository testRepository, QuizQuestionSelector questionSelector) {
        this.testRepository = testRepository;
        this.questionSelector = questionSelector;
    }

    public List<QuizTest> findTests(int stationId) {
        return testRepository.findByStation(stationId);
    }

    public List<QuizTest> findTestsForMember(int stationId, int memberId) {
        return testRepository.findByStationForMember(stationId, memberId);
    }

    public Optional<QuizTest> findTest(int id) {
        return testRepository.findById(id);
    }

    public int countAttempts(int testId) {
        return testRepository.countAttempts(testId);
    }

    public List<RequirementsService.RequirementItem> findForcedPending(int stationId, int memberId) {
        return testRepository.findForcedPending(stationId, memberId).stream()
                .map(t -> new RequirementsService.RequirementItem(t.id(), t.title()))
                .toList();
    }

    public QuizTest createTest(
            int stationId,
            String title,
            String description,
            Integer timeLimit,
            boolean shuffle,
            boolean forced,
            int createdBy) {
        var test = testRepository.create(stationId, title, description, timeLimit, shuffle, forced, createdBy);
        log.info("Created quiz test {} for station {} (member {})", test.id(), stationId, createdBy);
        return test;
    }

    public boolean updateTest(
            int id,
            String title,
            String description,
            Integer timeLimit,
            boolean shuffle,
            boolean forced,
            Instant startAt,
            Instant endAt) {
        boolean updated = testRepository.update(id, title, description, timeLimit, shuffle, forced, startAt, endAt);
        if (updated) {
            log.info("Updated quiz test {}", id);
        } else {
            log.warn("Update for quiz test {} affected zero rows", id);
        }
        return updated;
    }

    public boolean activateTest(int id) {
        var frozen = testRepository.findFrozenQuestions(id);
        if (frozen.isEmpty()) {
            generateFrozenQuestions(id);
        }
        boolean activated = testRepository.updateStatus(id, TestStatus.ACTIVE);
        if (activated) {
            log.info("Activated quiz test {}", id);
        } else {
            log.warn("Activation for quiz test {} affected zero rows", id);
        }
        return activated;
    }

    public boolean closeTest(int id) {
        boolean closed = testRepository.updateStatus(id, TestStatus.CLOSED);
        if (closed) {
            log.info("Closed quiz test {}", id);
        } else {
            log.warn("Close for quiz test {} affected zero rows", id);
        }
        return closed;
    }

    public boolean deleteTest(int id) {
        boolean deleted = testRepository.delete(id);
        if (deleted) {
            log.info("Deleted quiz test {}", id);
        } else {
            log.warn("Delete for quiz test {} affected zero rows", id);
        }
        return deleted;
    }

    /**
     * Draws a fresh question set for the test and stores it as the frozen set, replacing any
     * previous one.
     */
    public void generateFrozenQuestions(int testId) {
        var test = testRepository.findById(testId).orElseThrow();
        var sections = testRepository.findSections(testId);

        testRepository.deleteFrozenQuestions(testId);

        var selected = questionSelector.selectForSections(sections);
        if (test.shuffle()) {
            Collections.shuffle(selected);
        }
        for (int i = 0; i < selected.size(); i++) {
            var entry = selected.get(i);
            testRepository.createFrozenQuestion(testId, entry.questionId(), entry.sectionId(), i);
        }
        log.info("Generated {} frozen questions for quiz test {}", selected.size(), testId);
    }

    public List<QuizTestFrozenQuestion> findFrozenQuestions(int testId) {
        return testRepository.findFrozenQuestions(testId);
    }

    public void replaceFrozenQuestion(int testId, int position, int newQuestionId) {
        Integer sectionId = sectionAt(testRepository.findFrozenQuestions(testId), position);
        testRepository.deleteFrozenQuestionAtPosition(testId, position);
        testRepository.createFrozenQuestion(testId, newQuestionId, sectionId, position);
        log.info(
                "Replaced frozen question at position {} on quiz test {} with question {}",
                position,
                testId,
                newQuestionId);
    }

    /**
     * Swaps the frozen question at a position for a random one the test's sources still
     * offer.
     *
     * @throws IllegalStateException if every question the sources offer is already in use
     */
    public void replaceWithRandomQuestion(int testId, int position) {
        var frozen = testRepository.findFrozenQuestions(testId);
        Integer sectionId = sectionAt(frozen, position);

        var candidates = questionSelector.collectUnusedCandidates(testId, usedQuestionIds(frozen));
        if (candidates.isEmpty()) {
            throw new IllegalStateException("No unused questions available for replacement");
        }

        Collections.shuffle(candidates);
        var replacement = candidates.getFirst();

        testRepository.deleteFrozenQuestionAtPosition(testId, position);
        testRepository.createFrozenQuestion(testId, replacement.id(), sectionId, position);
        log.info(
                "Replaced frozen question at position {} on quiz test {} with random question {}",
                position,
                testId,
                replacement.id());
    }

    public List<QuizQuestion> findAvailableReplacements(int testId) {
        var frozen = testRepository.findFrozenQuestions(testId);
        return questionSelector.collectUnusedCandidates(testId, usedQuestionIds(frozen));
    }

    public List<QuizTestSection> findSections(int testId) {
        return testRepository.findSections(testId);
    }

    public List<QuizTestSectionSource> findSources(int sectionId) {
        return testRepository.findSources(sectionId);
    }

    public void replaceSections(int testId, List<SectionEntry> sections) {
        testRepository.deleteSectionsByTest(testId);
        for (int i = 0; i < sections.size(); i++) {
            var entry = sections.get(i);
            var section = testRepository.createSection(testId, entry.title(), entry.description(), i);
            for (var source : entry.sources()) {
                testRepository.createSource(
                        section.id(), source.catalogId(), source.categoryId(), source.questionCount());
            }
        }
        log.info("Replaced sections on quiz test {} with {} sections", testId, sections.size());
    }

    private Integer sectionAt(List<QuizTestFrozenQuestion> frozen, int position) {
        return frozen.stream()
                .filter(fq -> fq.position() == position)
                .map(QuizTestFrozenQuestion::sectionId)
                .findFirst()
                .orElse(null);
    }

    private Set<Integer> usedQuestionIds(List<QuizTestFrozenQuestion> frozen) {
        return frozen.stream().map(QuizTestFrozenQuestion::questionId).collect(Collectors.toSet());
    }
}
