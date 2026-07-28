/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.quiz.service;

import dev.chojo.ember.feature.quiz.entity.QuizQuestion;
import dev.chojo.ember.feature.quiz.entity.QuizTestSection;
import dev.chojo.ember.feature.quiz.repository.QuizCatalogRepository;
import dev.chojo.ember.feature.quiz.repository.QuizTestRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

/**
 * Draws questions out of the catalogs and categories a test's sections point at. Shared by
 * the initial draw that freezes a test's question set and by the replacement of a single
 * frozen question.
 */
@Singleton
public class QuizQuestionSelector {

    private final QuizCatalogRepository catalogRepository;
    private final QuizTestRepository testRepository;

    @Inject
    public QuizQuestionSelector(QuizCatalogRepository catalogRepository, QuizTestRepository testRepository) {
        this.catalogRepository = catalogRepository;
        this.testRepository = testRepository;
    }

    /**
     * Draws one question set for the given sections. A source naming a category draws from
     * that category alone, a source naming only a catalog spreads the draw evenly over the
     * catalog's categories.
     *
     * @param sections the sections to draw for
     * @return the drawn questions, each tagged with the section it was drawn for
     */
    public List<SelectedQuestion> selectForSections(List<QuizTestSection> sections) {
        List<SelectedQuestion> selected = new ArrayList<>();
        for (var section : sections) {
            for (var source : testRepository.findSources(section.id())) {
                List<QuizQuestion> picked;
                if (source.categoryId() != null) {
                    picked = pickFromPool(
                            catalogRepository.findQuestionsByCategory(source.catalogId(), source.categoryId()),
                            source.questionCount());
                } else {
                    picked = pickBalancedFromCatalog(source.catalogId(), source.questionCount());
                }
                for (var question : picked) {
                    selected.add(new SelectedQuestion(question.id(), section.id()));
                }
            }
        }
        return selected;
    }

    /**
     * Collects every question a test's sources offer that is not already in use, in source
     * order and without shuffling.
     *
     * @param testId          the test whose sources are scanned
     * @param usedQuestionIds the questions already placed on the test
     * @return the questions still available
     */
    public List<QuizQuestion> collectUnusedCandidates(int testId, Set<Integer> usedQuestionIds) {
        List<QuizQuestion> candidates = new ArrayList<>();
        for (var section : testRepository.findSections(testId)) {
            for (var source : testRepository.findSources(section.id())) {
                List<QuizQuestion> pool;
                if (source.categoryId() != null) {
                    pool = catalogRepository.findQuestionsByCategory(source.catalogId(), source.categoryId());
                } else {
                    pool = catalogRepository.findQuestions(source.catalogId());
                }
                for (var question : pool) {
                    if (!usedQuestionIds.contains(question.id())) {
                        candidates.add(question);
                    }
                }
            }
        }
        return candidates;
    }

    private List<QuizQuestion> pickFromPool(List<QuizQuestion> pool, int count) {
        List<QuizQuestion> shuffled = new ArrayList<>(pool);
        Collections.shuffle(shuffled);
        if (count == 0) return shuffled;
        return shuffled.subList(0, Math.min(count, shuffled.size()));
    }

    /**
     * Picks questions from a whole catalog while keeping the categories evenly represented:
     * questions are grouped by category, shuffled inside each group and then taken
     * round-robin across the groups.
     */
    private List<QuizQuestion> pickBalancedFromCatalog(int catalogId, int count) {
        var allQuestions = catalogRepository.findQuestions(catalogId);
        if (count == 0 || count >= allQuestions.size()) {
            Collections.shuffle(allQuestions);
            return allQuestions;
        }
        var byCategory = new LinkedHashMap<Integer, List<QuizQuestion>>();
        for (var question : allQuestions) {
            byCategory
                    .computeIfAbsent(question.categoryId() != null ? question.categoryId() : -1, _ -> new ArrayList<>())
                    .add(question);
        }
        for (var list : byCategory.values()) {
            Collections.shuffle(list);
        }
        List<QuizQuestion> result = new ArrayList<>();
        var categoryQueues = new ArrayList<>(byCategory.values());
        Collections.shuffle(categoryQueues);
        int[] indices = new int[categoryQueues.size()];
        while (result.size() < count) {
            boolean added = false;
            for (int i = 0; i < categoryQueues.size() && result.size() < count; i++) {
                var queue = categoryQueues.get(i);
                if (indices[i] < queue.size()) {
                    result.add(queue.get(indices[i]++));
                    added = true;
                }
            }
            if (!added) break;
        }
        return result;
    }

    /**
     * A drawn question together with the section it was drawn for.
     */
    public record SelectedQuestion(int questionId, Integer sectionId) {}
}
