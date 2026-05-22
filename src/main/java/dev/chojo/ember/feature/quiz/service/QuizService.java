/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.quiz.service;

import dev.chojo.ember.feature.quiz.entity.QuestionType;
import dev.chojo.ember.feature.quiz.entity.QuizCatalog;
import dev.chojo.ember.feature.quiz.entity.QuizCategory;
import dev.chojo.ember.feature.quiz.entity.QuizQuestion;
import dev.chojo.ember.feature.quiz.entity.QuizTest;
import dev.chojo.ember.feature.quiz.entity.QuizTestAnswer;
import dev.chojo.ember.feature.quiz.entity.QuizTestAttempt;
import dev.chojo.ember.feature.quiz.entity.QuizTestAttemptQuestion;
import dev.chojo.ember.feature.quiz.entity.QuizTestFrozenQuestion;
import dev.chojo.ember.feature.quiz.entity.QuizTestSection;
import dev.chojo.ember.feature.quiz.entity.QuizTestSectionSource;
import dev.chojo.ember.feature.quiz.entity.TestStatus;
import dev.chojo.ember.feature.quiz.repository.QuizCatalogRepository;
import dev.chojo.ember.feature.quiz.repository.QuizTestRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

@Singleton
public class QuizService {
    private final QuizCatalogRepository catalogRepository;
    private final QuizTestRepository testRepository;

    @Inject
    public QuizService(QuizCatalogRepository catalogRepository, QuizTestRepository testRepository) {
        this.catalogRepository = catalogRepository;
        this.testRepository = testRepository;
    }

    // -- Catalogs --

    public List<QuizCatalog> findCatalogs(int stationId) {
        return catalogRepository.findByStation(stationId);
    }

    public Optional<QuizCatalog> findCatalog(int id) {
        return catalogRepository.findById(id);
    }

    public QuizCatalog createCatalog(int stationId, String name, String description, boolean trainingEnabled) {
        return catalogRepository.create(stationId, name, description, trainingEnabled);
    }

    public boolean updateCatalog(int id, String name, String description, boolean trainingEnabled) {
        return catalogRepository.update(id, name, description, trainingEnabled);
    }

    public boolean deleteCatalog(int id) {
        return catalogRepository.delete(id);
    }

    public List<QuizCatalog> findTrainingCatalogs(int stationId) {
        return catalogRepository.findTrainingCatalogs(stationId);
    }

    // -- Categories --

    public List<QuizCategory> findCategories(int stationId) {
        return catalogRepository.findCategoriesByStation(stationId);
    }

    public QuizCategory createCategory(int stationId, String name, String description, int position) {
        return catalogRepository.createCategory(stationId, name, description, position);
    }

    public boolean updateCategory(int id, String name, String description, int position) {
        return catalogRepository.updateCategory(id, name, description, position);
    }

    public boolean deleteCategory(int id) {
        return catalogRepository.deleteCategory(id);
    }

    // -- Questions --

    public List<QuizQuestion> findQuestions(int catalogId) {
        return catalogRepository.findQuestions(catalogId);
    }

    public Optional<QuizQuestion> findQuestion(int id) {
        return catalogRepository.findQuestionById(id);
    }

    public QuizQuestion createQuestion(
            int catalogId,
            Integer categoryId,
            QuestionType questionType,
            String title,
            String description,
            String imageUrl,
            int points,
            boolean autoPoints,
            String config,
            int position) {
        return catalogRepository.createQuestion(
                catalogId,
                categoryId,
                questionType,
                title,
                description,
                imageUrl,
                points,
                autoPoints,
                config,
                position);
    }

    public boolean updateQuestion(
            int id,
            Integer categoryId,
            String title,
            String description,
            String imageUrl,
            int points,
            boolean autoPoints,
            String config,
            int position) {
        return catalogRepository.updateQuestion(
                id, categoryId, title, description, imageUrl, points, autoPoints, config, position);
    }

    public boolean deleteQuestion(int id) {
        return catalogRepository.deleteQuestion(id);
    }

    public int countQuestions(int catalogId) {
        return catalogRepository.countQuestions(catalogId);
    }

    // -- Tests --

    public List<QuizTest> findTests(int stationId) {
        return testRepository.findByStation(stationId);
    }

    public int countAttempts(int testId) {
        return testRepository.countAttempts(testId);
    }

    public Optional<QuizTest> findTest(int id) {
        return testRepository.findById(id);
    }

    public QuizTest createTest(
            int stationId, String title, String description, Integer timeLimit, boolean shuffle, int createdBy) {
        return testRepository.create(stationId, title, description, timeLimit, shuffle, createdBy);
    }

    public boolean updateTest(
            int id,
            String title,
            String description,
            Integer timeLimit,
            boolean shuffle,
            Instant startAt,
            Instant endAt) {
        return testRepository.update(id, title, description, timeLimit, shuffle, startAt, endAt);
    }

    public void generateFrozenQuestions(int testId) {
        var test = testRepository.findById(testId).orElseThrow();
        var sections = testRepository.findSections(testId);

        testRepository.deleteFrozenQuestions(testId);

        List<AttemptQuestionEntry> selectedQuestions = resolveQuestionsFromSections(sections);
        if (test.shuffle()) {
            Collections.shuffle(selectedQuestions);
        }
        for (int i = 0; i < selectedQuestions.size(); i++) {
            var entry = selectedQuestions.get(i);
            testRepository.createFrozenQuestion(testId, entry.questionId(), entry.sectionId(), i);
        }
    }

    public List<QuizTestFrozenQuestion> findFrozenQuestions(int testId) {
        return testRepository.findFrozenQuestions(testId);
    }

    public void replaceFrozenQuestion(int testId, int position, int newQuestionId) {
        var frozen = testRepository.findFrozenQuestions(testId);
        Integer sectionId = null;
        for (var fq : frozen) {
            if (fq.position() == position) {
                sectionId = fq.sectionId();
                break;
            }
        }
        testRepository.deleteFrozenQuestionAtPosition(testId, position);
        testRepository.createFrozenQuestion(testId, newQuestionId, sectionId, position);
    }

    public void replaceWithRandomQuestion(int testId, int position) {
        var frozen = testRepository.findFrozenQuestions(testId);
        var usedQuestionIds =
                frozen.stream().map(QuizTestFrozenQuestion::questionId).collect(java.util.stream.Collectors.toSet());

        Integer sectionId = null;
        for (var fq : frozen) {
            if (fq.position() == position) {
                sectionId = fq.sectionId();
                break;
            }
        }

        // Collect all questions from test sources
        var sections = testRepository.findSections(testId);
        List<QuizQuestion> candidates = new ArrayList<>();
        for (var section : sections) {
            var sources = testRepository.findSources(section.id());
            for (var source : sources) {
                List<QuizQuestion> pool;
                if (source.categoryId() != null) {
                    pool = catalogRepository.findQuestionsByCategory(source.catalogId(), source.categoryId());
                } else {
                    pool = catalogRepository.findQuestions(source.catalogId());
                }
                for (var q : pool) {
                    if (!usedQuestionIds.contains(q.id())) {
                        candidates.add(q);
                    }
                }
            }
        }

        if (candidates.isEmpty()) {
            throw new IllegalStateException("No unused questions available for replacement");
        }

        Collections.shuffle(candidates);
        var replacement = candidates.getFirst();

        testRepository.deleteFrozenQuestionAtPosition(testId, position);
        testRepository.createFrozenQuestion(testId, replacement.id(), sectionId, position);
    }

    public List<QuizQuestion> findAvailableReplacements(int testId) {
        var frozen = testRepository.findFrozenQuestions(testId);
        var usedQuestionIds =
                frozen.stream().map(QuizTestFrozenQuestion::questionId).collect(java.util.stream.Collectors.toSet());

        var sections = testRepository.findSections(testId);
        List<QuizQuestion> candidates = new ArrayList<>();
        for (var section : sections) {
            var sources = testRepository.findSources(section.id());
            for (var source : sources) {
                List<QuizQuestion> pool;
                if (source.categoryId() != null) {
                    pool = catalogRepository.findQuestionsByCategory(source.catalogId(), source.categoryId());
                } else {
                    pool = catalogRepository.findQuestions(source.catalogId());
                }
                for (var q : pool) {
                    if (!usedQuestionIds.contains(q.id())) {
                        candidates.add(q);
                    }
                }
            }
        }
        return candidates;
    }

    public boolean activateTest(int id) {
        var frozen = testRepository.findFrozenQuestions(id);
        if (frozen.isEmpty()) {
            generateFrozenQuestions(id);
        }
        return testRepository.updateStatus(id, TestStatus.ACTIVE);
    }

    public boolean closeTest(int id) {
        return testRepository.updateStatus(id, TestStatus.CLOSED);
    }

    public boolean deleteTest(int id) {
        return testRepository.delete(id);
    }

    public boolean isTestAccessible(QuizTest test, int memberId) {
        if (test.status() != TestStatus.ACTIVE) return false;
        Instant now = Instant.now();
        if (test.startAt() != null && now.isBefore(test.startAt())) return false;
        if (test.endAt() != null && now.isAfter(test.endAt())) return false;
        // Check per-member override
        testRepository.hasMemberAccess(test.id(), memberId);
        // Global access when no time restrictions or within window
        return true;
    }

    // -- Sections --

    public List<QuizTestSection> findSections(int testId) {
        return testRepository.findSections(testId);
    }

    public QuizTestSection createSection(int testId, String title, String description, int position) {
        return testRepository.createSection(testId, title, description, position);
    }

    public boolean updateSection(int id, String title, String description, int position) {
        return testRepository.updateSection(id, title, description, position);
    }

    public boolean deleteSection(int id) {
        return testRepository.deleteSection(id);
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
    }

    // -- Section Sources --

    public List<QuizTestSectionSource> findSources(int sectionId) {
        return testRepository.findSources(sectionId);
    }

    // -- Attempts --

    public List<QuizTestAttempt> findAttempts(int testId) {
        return testRepository.findAttempts(testId);
    }

    public Optional<QuizTestAttempt> findAttempt(int testId, int memberId) {
        return testRepository.findAttempt(testId, memberId);
    }

    public Optional<QuizTestAttempt> findAttemptById(int id) {
        return testRepository.findAttemptById(id);
    }

    private List<QuizQuestion> pickFromPool(List<QuizQuestion> pool, int count) {
        List<QuizQuestion> shuffled = new ArrayList<>(pool);
        Collections.shuffle(shuffled);
        if (count == 0) return shuffled;
        return shuffled.subList(0, Math.min(count, shuffled.size()));
    }

    private List<QuizQuestion> pickBalancedFromCatalog(int catalogId, int count) {
        var allQuestions = catalogRepository.findQuestions(catalogId);
        if (count == 0 || count >= allQuestions.size()) {
            Collections.shuffle(allQuestions);
            return allQuestions;
        }
        // Group by category (null category is its own group)
        var byCategory = new LinkedHashMap<Integer, List<QuizQuestion>>();
        for (var q : allQuestions) {
            byCategory
                    .computeIfAbsent(q.categoryId() != null ? q.categoryId() : -1, k -> new ArrayList<>())
                    .add(q);
        }
        // Shuffle within each category
        for (var list : byCategory.values()) {
            Collections.shuffle(list);
        }
        // Round-robin pick from categories
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

    private List<AttemptQuestionEntry> resolveQuestionsFromSections(List<QuizTestSection> sections) {
        List<AttemptQuestionEntry> selectedQuestions = new ArrayList<>();
        for (var section : sections) {
            var sources = testRepository.findSources(section.id());
            for (var source : sources) {
                List<QuizQuestion> picked;
                if (source.categoryId() != null) {
                    picked = pickFromPool(
                            catalogRepository.findQuestionsByCategory(source.catalogId(), source.categoryId()),
                            source.questionCount());
                } else {
                    picked = pickBalancedFromCatalog(source.catalogId(), source.questionCount());
                }
                for (var q : picked) {
                    selectedQuestions.add(new AttemptQuestionEntry(q.id(), section.id()));
                }
            }
        }
        return selectedQuestions;
    }

    public QuizTestAttempt startAttempt(int testId, int memberId) {
        // Use the frozen question set created at activation time
        var frozenQuestions = testRepository.findFrozenQuestions(testId);
        if (frozenQuestions.isEmpty()) {
            throw new IllegalStateException("Test has no frozen questions — was it activated properly?");
        }

        int totalMaxPoints = 0;
        for (var fq : frozenQuestions) {
            var question = catalogRepository.findQuestionById(fq.questionId());
            if (question.isPresent()) {
                totalMaxPoints += question.get().points();
            }
        }

        var attempt = testRepository.createAttempt(testId, memberId, totalMaxPoints);

        for (var fq : frozenQuestions) {
            testRepository.createAttemptQuestion(attempt.id(), fq.questionId(), fq.sectionId(), fq.position());
        }

        return attempt;
    }

    public boolean submitAttempt(int attemptId) {
        boolean submitted = testRepository.submitAttempt(attemptId);
        if (submitted) {
            autoGradeAnswers(attemptId);
        }
        return submitted;
    }

    private void autoGradeAnswers(int attemptId) {
        var answers = testRepository.findAnswers(attemptId);
        var mapper = new tools.jackson.databind.ObjectMapper();

        for (var answer : answers) {
            var question = catalogRepository.findQuestionById(answer.questionId());
            if (question.isEmpty()) continue;
            var q = question.get();
            int autoPoints = autoGradeQuestion(q, answer.answer(), mapper);
            if (autoPoints >= 0) {
                testRepository.gradeAnswer(answer.id(), autoPoints);
            }
        }
    }

    private int autoGradeQuestion(QuizQuestion q, String answerJson, tools.jackson.databind.ObjectMapper mapper) {
        if (answerJson == null || answerJson.isBlank()) return 0;
        try {
            var config = q.config();
            var answer = mapper.readTree(answerJson);
            return switch (q.questionType()) {
                case MULTIPLE_CHOICE -> gradeMultipleChoice(config, answer, q.points());
                case TRUE_FALSE -> gradeTrueFalse(config, answer, q.points());
                case CONNECT -> gradeConnect(config, answer, q.points());
                case ORDERING -> gradeOrdering(config, answer, q.points());
                case FILL_IN_THE_BLANK -> gradeFillBlank(config, answer, q.points());
                case ENUMERATION -> gradeEnumeration(config, answer, q.points());
                case FREE_ANSWER, IMAGE_TEXT -> -1; // manual grading
            };
        } catch (Exception e) {
            return -1;
        }
    }

    private int gradeMultipleChoice(
            tools.jackson.databind.JsonNode config, tools.jackson.databind.JsonNode answer, int maxPoints) {
        var options = config.get("options");
        var selected = answer.get("selected");
        if (options == null || selected == null || !selected.isArray()) return 0;

        var correctSet = new java.util.HashSet<Integer>();
        for (int i = 0; i < options.size(); i++) {
            if (options.get(i).has("correct") && options.get(i).get("correct").asBoolean()) {
                correctSet.add(i);
            }
        }

        double pointsPerCorrect =
                config.has("pointsPerCorrect") ? config.get("pointsPerCorrect").asDouble() : 0.5;
        int correct = 0;
        int wrong = 0;
        for (var s : selected) {
            if (correctSet.contains(s.asInt())) correct++;
            else wrong++;
        }

        double points = (correct * pointsPerCorrect) - (wrong * pointsPerCorrect);
        return Math.max(0, (int) Math.round(Math.min(points, maxPoints)));
    }

    private int gradeTrueFalse(
            tools.jackson.databind.JsonNode config, tools.jackson.databind.JsonNode answer, int maxPoints) {
        if (!config.has("correctAnswer") || !answer.has("value")) return 0;
        boolean correct = config.get("correctAnswer").asBoolean();
        boolean given = answer.get("value").asBoolean();
        return correct == given ? maxPoints : 0;
    }

    private int gradeConnect(
            tools.jackson.databind.JsonNode config, tools.jackson.databind.JsonNode answer, int maxPoints) {
        var pairs = config.get("pairs");
        var givenPairs = answer.get("pairs");
        if (pairs == null || givenPairs == null) return 0;

        int correct = 0;
        for (int i = 0; i < pairs.size(); i++) {
            String expectedRight = pairs.get(i).get("right").asText();
            var given = givenPairs.get(String.valueOf(i));
            if (given != null && given.asText().equals(expectedRight)) {
                correct++;
            }
        }

        int totalPairs = pairs.size();
        return totalPairs == 0 ? 0 : Math.round((float) correct / totalPairs * maxPoints);
    }

    private int gradeOrdering(
            tools.jackson.databind.JsonNode config, tools.jackson.databind.JsonNode answer, int maxPoints) {
        var items = config.get("items");
        var order = answer.get("order");
        if (items == null || order == null || !order.isArray()) return 0;

        // Correct order is 0, 1, 2, ... (items are stored in correct order)
        boolean allCorrect = true;
        for (int i = 0; i < order.size(); i++) {
            if (order.get(i).asInt() != i) {
                allCorrect = false;
                break;
            }
        }
        return allCorrect ? maxPoints : 0;
    }

    private int gradeFillBlank(
            tools.jackson.databind.JsonNode config, tools.jackson.databind.JsonNode answer, int maxPoints) {
        var correctAnswers = config.get("answers");
        if (correctAnswers == null || !correctAnswers.isArray()) return -1;
        if (correctAnswers.isEmpty()) return -1;

        // New format: gaps = {"0": "answer1", "1": "answer2"}
        var gaps = answer.get("gaps");
        if (gaps != null && gaps.isObject()) {
            int correct = 0;
            for (int i = 0; i < correctAnswers.size(); i++) {
                var given = gaps.get(String.valueOf(i));
                if (given != null
                        && given.asText()
                                .trim()
                                .equalsIgnoreCase(correctAnswers.get(i).asText().trim())) {
                    correct++;
                }
            }
            int total = correctAnswers.size();
            return total == 0 ? 0 : Math.round((float) correct / total * maxPoints);
        }

        // Legacy format: text = "single answer"
        String given = answer.has("text") ? answer.get("text").asText().trim() : "";
        if (given.isEmpty()) return 0;
        for (var a : correctAnswers) {
            if (a.asText().trim().equalsIgnoreCase(given)) {
                return maxPoints;
            }
        }
        return 0;
    }

    private int gradeEnumeration(
            tools.jackson.databind.JsonNode config, tools.jackson.databind.JsonNode answer, int maxPoints) {
        var correctAnswers = config.get("answers");
        if (correctAnswers == null || !correctAnswers.isArray()) return -1;
        int requiredCount =
                config.has("requiredCount") ? config.get("requiredCount").asInt() : correctAnswers.size();
        boolean ordered =
                config.has("orderedRequired") && config.get("orderedRequired").asBoolean();

        var items = answer.get("items");
        if (items == null || !items.isArray()) return 0;

        // Build set of correct answers (lowercase for comparison)
        var correctSet = new java.util.ArrayList<String>();
        for (var a : correctAnswers) correctSet.add(a.asText().trim().toLowerCase());

        int correct = 0;
        for (int i = 0; i < Math.min(items.size(), requiredCount); i++) {
            String given = items.get(i).asText().trim().toLowerCase();
            if (ordered) {
                // Must match position
                if (i < correctSet.size() && correctSet.get(i).equals(given)) {
                    correct++;
                }
            } else {
                // Any match counts
                if (correctSet.contains(given)) {
                    correct++;
                }
            }
        }
        return requiredCount == 0 ? 0 : Math.round((float) correct / requiredCount * maxPoints);
    }

    public List<QuizTestAttemptQuestion> findAttemptQuestions(int attemptId) {
        return testRepository.findAttemptQuestions(attemptId);
    }

    // -- Answers --

    public List<QuizTestAnswer> findAnswers(int attemptId) {
        return testRepository.findAnswers(attemptId);
    }

    public void saveAnswer(int attemptId, int questionId, String answer) {
        testRepository.saveAnswer(attemptId, questionId, answer);
    }

    public boolean gradeAnswer(int answerId, int points) {
        return testRepository.gradeAnswer(answerId, points);
    }

    public boolean gradeAttempt(int attemptId, int gradedBy) {
        var answers = testRepository.findAnswers(attemptId);
        int total = answers.stream()
                .filter(QuizTestAnswer::graded)
                .mapToInt(a -> a.points() != null ? a.points() : 0)
                .sum();
        return testRepository.gradeAttempt(attemptId, total, gradedBy);
    }

    // -- Member Access --

    public void grantMemberAccess(int testId, int memberId, Instant closesAt) {
        testRepository.grantMemberAccess(testId, memberId, closesAt);
    }

    public void revokeMemberAccess(int testId, int memberId) {
        testRepository.revokeMemberAccess(testId, memberId);
    }

    // -- Restrictions --

    public List<Integer> findRoleRestrictions(int testId) {
        return testRepository.findRoleRestrictions(testId);
    }

    public List<Integer> findGroupRestrictions(int testId) {
        return testRepository.findGroupRestrictions(testId);
    }

    public List<Integer> findTagRestrictions(int testId) {
        return testRepository.findTagRestrictions(testId);
    }

    public void setRestrictions(int testId, List<Integer> roleIds, List<Integer> groupIds, List<Integer> tagIds) {
        testRepository.setRoleRestrictions(testId, roleIds);
        testRepository.setGroupRestrictions(testId, groupIds);
        testRepository.setTagRestrictions(testId, tagIds);
    }

    public boolean canMemberAccess(
            int testId, List<Integer> memberRoleIds, List<Integer> memberGroupIds, List<Integer> memberTagIds) {
        var roleRestrictions = testRepository.findRoleRestrictions(testId);
        var groupRestrictions = testRepository.findGroupRestrictions(testId);
        var tagRestrictions = testRepository.findTagRestrictions(testId);
        // No restrictions = open to all
        if (roleRestrictions.isEmpty() && groupRestrictions.isEmpty() && tagRestrictions.isEmpty()) return true;
        // Check if member matches any restriction
        for (int rId : roleRestrictions) {
            if (memberRoleIds.contains(rId)) return true;
        }
        for (int gId : groupRestrictions) {
            if (memberGroupIds.contains(gId)) return true;
        }
        for (int tId : tagRestrictions) {
            if (memberTagIds.contains(tId)) return true;
        }
        return false;
    }

    // -- Records --

    public record SectionEntry(String title, String description, List<SourceEntry> sources) {}

    public record SourceEntry(int catalogId, Integer categoryId, int questionCount) {}

    private record AttemptQuestionEntry(int questionId, Integer sectionId) {}
}
