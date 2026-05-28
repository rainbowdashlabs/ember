/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.quiz.service;

import dev.chojo.ember.feature.federation.entity.CapabilityType;
import dev.chojo.ember.feature.federation.entity.ContentType;
import dev.chojo.ember.feature.federation.entity.Direction;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.federation.service.FederationHttpClient;
import dev.chojo.ember.feature.federation.service.FederationService;
import dev.chojo.ember.feature.quiz.entity.QuestionConfig;
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
import dev.chojo.ember.feature.quiz.entity.SectionEntry;
import dev.chojo.ember.feature.quiz.entity.TestStatus;
import dev.chojo.ember.feature.quiz.repository.QuizCatalogRepository;
import dev.chojo.ember.feature.quiz.repository.QuizTestRepository;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import dev.chojo.ember.feature.restriction.RestrictionRepository;
import dev.chojo.ember.feature.restriction.RestrictionSet;
import dev.chojo.ember.feature.restriction.RestrictionType;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.system.service.RequirementsService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Singleton
public class QuizService {
    private static final Logger log = LoggerFactory.getLogger(QuizService.class);
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private final QuizCatalogRepository catalogRepository;
    private final QuizTestRepository testRepository;
    private final RestrictionRepository restrictionRepository;
    private final FederationService federationService;
    private final FederationRepository federationRepository;
    private final FederationHttpClient federationHttpClient;
    private final StationRepository stationRepository;

    @Inject
    public QuizService(
            QuizCatalogRepository catalogRepository,
            QuizTestRepository testRepository,
            RestrictionRepository restrictionRepository,
            FederationService federationService,
            FederationRepository federationRepository,
            FederationHttpClient federationHttpClient,
            StationRepository stationRepository) {
        this.catalogRepository = catalogRepository;
        this.testRepository = testRepository;
        this.restrictionRepository = restrictionRepository;
        this.federationService = federationService;
        this.federationRepository = federationRepository;
        this.federationHttpClient = federationHttpClient;
        this.stationRepository = stationRepository;
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

    public Optional<QuizCategory> findCategory(int id) {
        return catalogRepository.findCategoryById(id);
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
            double points,
            boolean autoPoints,
            QuestionConfig config,
            int position) {
        String configStr = serializeConfig(config);
        double effectivePoints = autoPoints ? calculateAutoPoints(questionType, configStr, points) : points;
        return catalogRepository.createQuestion(
                catalogId,
                categoryId,
                questionType,
                title,
                description,
                imageUrl,
                effectivePoints,
                autoPoints,
                configStr,
                position);
    }

    /**
     * Convenience overload that accepts a raw JSON config string.
     * Parses it to the appropriate {@link QuestionConfig} using the question type.
     */
    public QuizQuestion createQuestion(
            int catalogId,
            Integer categoryId,
            QuestionType questionType,
            String title,
            String description,
            String imageUrl,
            double points,
            boolean autoPoints,
            String configJson,
            int position) {
        return createQuestion(
                catalogId,
                categoryId,
                questionType,
                title,
                description,
                imageUrl,
                points,
                autoPoints,
                questionType.parseConfig(configJson != null ? configJson : "{}"),
                position);
    }

    private String serializeConfig(QuestionConfig config) {
        if (config == null) return "{}";
        try {
            return MAPPER.writeValueAsString(config);
        } catch (Exception e) {
            return "{}";
        }
    }

    public boolean updateQuestion(
            int id,
            Integer categoryId,
            String title,
            String description,
            String imageUrl,
            double points,
            boolean autoPoints,
            String config,
            int position) {
        double effectivePoints = points;
        if (autoPoints && config != null) {
            // Determine question type from existing record
            var existing = catalogRepository.findQuestionById(id);
            if (existing.isPresent()) {
                effectivePoints = calculateAutoPoints(existing.get().questionType(), config, points);
            }
        }
        return catalogRepository.updateQuestion(
                id, categoryId, title, description, imageUrl, effectivePoints, autoPoints, config, position);
    }

    /**
     * Calculates auto-points from the question config based on type.
     * Uses {@link QuestionType#parseConfig} for typed deserialization and
     * {@link QuestionConfig#autoPoints} for the calculation.
     */
    private double calculateAutoPoints(QuestionType type, String configStr, double fallback) {
        var config = type.parseConfig(configStr);
        if (config instanceof QuestionConfig.Unknown) return fallback;
        double points = config.autoPoints();
        return points > 0 ? points : fallback;
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

    public List<QuizTest> findTestsForMember(int stationId, int memberId) {
        return testRepository.findByStationForMember(stationId, memberId);
    }

    public int countAttempts(int testId) {
        return testRepository.countAttempts(testId);
    }

    public Optional<QuizTest> findTest(int id) {
        return testRepository.findById(id);
    }

    public List<RequirementsService.RequirementItem> findForcedPending(int stationId, int memberId) {
        return testRepository.findForcedPending(stationId, memberId).stream()
                .map(t -> new RequirementsService.RequirementItem(t.id(), t.title()))
                .toList();
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
                frozen.stream().map(QuizTestFrozenQuestion::questionId).collect(Collectors.toSet());

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
                frozen.stream().map(QuizTestFrozenQuestion::questionId).collect(Collectors.toSet());

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
        // Check per-member override (grants access regardless of restrictions)
        if (testRepository.hasMemberAccess(test.id(), memberId)) return true;
        // Check role/group/tag restrictions (DB resolves roles with inheritance + manager bypass)
        return canMemberAccess(test.id(), memberId);
    }

    // -- Sections --

    public List<QuizTestSection> findSections(int testId) {
        return testRepository.findSections(testId);
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

        double totalMaxPoints = 0;
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
        var mapper = new ObjectMapper();

        for (var answer : answers) {
            var question = catalogRepository.findQuestionById(answer.questionId());
            if (question.isEmpty()) continue;
            var q = question.get();
            double autoPoints = autoGradeQuestion(q, answer.answer(), mapper);
            if (autoPoints >= 0) {
                testRepository.gradeAnswer(answer.id(), autoPoints);
            }
        }
    }

    private double autoGradeQuestion(QuizQuestion q, String answerJson, ObjectMapper mapper) {
        if (answerJson == null || answerJson.isBlank()) return 0;
        try {
            var config = q.configNode();
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

    private double gradeMultipleChoice(JsonNode config, JsonNode answer, double maxPoints) {
        var options = config.get("options");
        var selected = answer.get("selected");
        if (options == null || selected == null || !selected.isArray()) return 0;

        var correctSet = new HashSet<Integer>();
        for (int i = 0; i < options.size(); i++) {
            if (options.get(i).has("correct") && options.get(i).get("correct").asBoolean()) {
                correctSet.add(i);
            }
        }

        double pointsPerCorrect =
                config.has("pointsPerCorrect") ? config.get("pointsPerCorrect").asDouble() : 1;
        int correct = 0;
        int wrong = 0;
        for (var s : selected) {
            if (correctSet.contains(s.asInt())) correct++;
            else wrong++;
        }

        double points = (correct * pointsPerCorrect) - (wrong * pointsPerCorrect);
        return Math.clamp(points, 0, maxPoints);
    }

    private double gradeTrueFalse(JsonNode config, JsonNode answer, double maxPoints) {
        if (!config.has("correctAnswer") || !answer.has("value")) return 0;
        boolean correct = config.get("correctAnswer").asBoolean();
        boolean given = answer.get("value").asBoolean();
        return correct == given ? maxPoints : 0;
    }

    private double gradeConnect(JsonNode config, JsonNode answer, double maxPoints) {
        var pairs = config.get("pairs");
        var givenPairs = answer.get("pairs");
        if (pairs == null || givenPairs == null) return 0;

        double ppc =
                config.has("pointsPerCorrect") ? config.get("pointsPerCorrect").asDouble() : 0;

        int correct = 0;
        for (int i = 0; i < pairs.size(); i++) {
            String expectedRight = pairs.get(i).get("right").asString();
            var given = givenPairs.get(String.valueOf(i));
            if (given != null && given.asString().equals(expectedRight)) {
                correct++;
            }
        }

        if (ppc > 0) return Math.min(correct * ppc, maxPoints);
        int totalPairs = pairs.size();
        return totalPairs == 0 ? 0 : (double) correct / totalPairs * maxPoints;
    }

    private double gradeOrdering(JsonNode config, JsonNode answer, double maxPoints) {
        var items = config.get("items");
        var order = answer.get("order");
        if (items == null || order == null || !order.isArray()) return 0;

        double ppc =
                config.has("pointsPerCorrect") ? config.get("pointsPerCorrect").asDouble() : 0;

        int correct = 0;
        for (int i = 0; i < order.size(); i++) {
            if (order.get(i).asInt() == i) correct++;
        }

        if (ppc > 0) return Math.min(correct * ppc, maxPoints);
        return correct == items.size() ? maxPoints : 0;
    }

    private double gradeFillBlank(JsonNode config, JsonNode answer, double maxPoints) {
        var correctAnswers = config.get("answers");
        if (correctAnswers == null || !correctAnswers.isArray()) return -1;
        if (correctAnswers.isEmpty()) return -1;

        double ppc =
                config.has("pointsPerCorrect") ? config.get("pointsPerCorrect").asDouble() : 0;

        // New format: gaps = {"0": "answer1", "1": "answer2"}
        var gaps = answer.get("gaps");
        if (gaps != null && gaps.isObject()) {
            int correct = 0;
            for (int i = 0; i < correctAnswers.size(); i++) {
                var given = gaps.get(String.valueOf(i));
                if (given != null
                        && given.asString()
                                .trim()
                                .equalsIgnoreCase(
                                        correctAnswers.get(i).asString().trim())) {
                    correct++;
                }
            }
            if (ppc > 0) return Math.min(correct * ppc, maxPoints);
            int total = correctAnswers.size();
            return total == 0 ? 0 : (double) correct / total * maxPoints;
        }

        // Legacy format: text = "single answer"
        String given = answer.has("text") ? answer.get("text").asString().trim() : "";
        if (given.isEmpty()) return 0;
        for (var a : correctAnswers) {
            if (a.asString().trim().equalsIgnoreCase(given)) {
                return ppc > 0 ? ppc : maxPoints;
            }
        }
        return 0;
    }

    private double gradeEnumeration(JsonNode config, JsonNode answer, double maxPoints) {
        var correctAnswers = config.get("answers");
        if (correctAnswers == null || !correctAnswers.isArray()) return -1;
        int requiredCount =
                config.has("requiredCount") ? config.get("requiredCount").asInt() : correctAnswers.size();
        boolean ordered =
                config.has("orderedRequired") && config.get("orderedRequired").asBoolean();

        var items = answer.get("items");
        if (items == null || !items.isArray()) return 0;

        // Build set of correct answers (lowercase for comparison)
        var correctSet = new ArrayList<String>();
        for (var a : correctAnswers) correctSet.add(a.asString().trim().toLowerCase());

        int correct = 0;
        for (int i = 0; i < Math.min(items.size(), requiredCount); i++) {
            String given = items.get(i).asString().trim().toLowerCase();
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

    public boolean gradeAnswer(int answerId, double points) {
        return testRepository.gradeAnswer(answerId, points);
    }

    public boolean gradeAttempt(int attemptId, int gradedBy) {
        var answers = testRepository.findAnswers(attemptId);
        double total = answers.stream()
                .filter(QuizTestAnswer::graded)
                .mapToDouble(a -> a.points() != null ? a.points() : 0)
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

    /**
     * Retrieves the restriction set for a test.
     */
    public RestrictionSet findRestrictions(int testId) {
        var test = testRepository.findById(testId).orElse(null);
        RestrictionMode mode = test != null ? test.restrictionMode() : RestrictionMode.OR;
        return restrictionRepository.findRestrictionSet(
                RestrictionType.QUIZ_TEST.table(), RestrictionType.QUIZ_TEST.fkColumn(), testId, mode);
    }

    public void setRestrictions(
            int testId, List<Integer> roleIds, List<Integer> groupIds, List<Integer> tagIds, List<Integer> memberIds) {
        restrictionRepository.setRestrictions(
                RestrictionType.QUIZ_TEST.table(),
                RestrictionType.QUIZ_TEST.fkColumn(),
                testId,
                roleIds != null ? roleIds : List.of(),
                groupIds != null ? groupIds : List.of(),
                tagIds != null ? tagIds : List.of(),
                memberIds != null ? memberIds : List.of());
    }

    public void updateRestrictionMode(int testId, RestrictionMode mode) {
        testRepository.updateRestrictionMode(testId, mode);
    }

    /**
     * Checks if a member can access a quiz test based on its restrictions.
     * Delegates to the DB function which resolves the member's identity internally.
     */
    public boolean canMemberAccess(int testId, int memberId) {
        return restrictionRepository.checkRestriction(RestrictionType.QUIZ_TEST, testId, memberId);
    }

    private record AttemptQuestionEntry(int questionId, Integer sectionId) {}

    // -- Federated Quiz --

    public List<SharedQuizItem> browseSharedQuiz(int stationId) {
        var futures = new ArrayList<CompletableFuture<List<SharedQuizItem>>>();
        for (var partner : federationService.findPartners(stationId)) {
            if (partner.status() != FederationPartner.FederationStatus.ACTIVE) continue;
            if (!federationService.hasCapability(partner.id(), CapabilityType.QUIZ_SHARE, Direction.IMPORT)) continue;
            int remoteStationId = resolvePartnerStationId(partner);

            futures.add(CompletableFuture.supplyAsync(() -> {
                var items = new ArrayList<SharedQuizItem>();
                if (partner.isRemote()) {
                    browseSharedQuizViaHttp(stationId, partner, remoteStationId, items);
                } else {
                    browseSharedQuizDirect(remoteStationId, partner, items);
                }
                return items;
            }));
        }
        return collectResults(futures);
    }

    private void browseSharedQuizDirect(int remoteStationId, FederationPartner partner, List<SharedQuizItem> result) {
        var shares = federationRepository.findQuizShares(remoteStationId);
        for (var share : shares) {
            if (share.catalogId() != null) {
                findCatalog(share.catalogId()).ifPresent(catalog -> {
                    result.add(new SharedQuizItem(
                            catalog.id(), catalog.name(), catalog.description(), remoteStationId, partner.id()));
                    federationRepository.upsertMetadataCache(
                            partner.id(), ContentType.QUIZ, catalog.id(), catalog.name(), catalog.description());
                });
            }
        }
    }

    private void browseSharedQuizViaHttp(
            int localStationId, FederationPartner partner, int remoteStationId, List<SharedQuizItem> result) {
        var catalogs = federationHttpClient.fetchSharedQuizCatalogs(
                partner.remoteHost(), localStationId, getPrivateKey(localStationId));
        for (var remoteCatalog : catalogs) {
            result.add(new SharedQuizItem(
                    remoteCatalog.id(),
                    remoteCatalog.name(),
                    remoteCatalog.description(),
                    remoteStationId,
                    partner.id()));
            federationRepository.upsertMetadataCache(
                    partner.id(),
                    ContentType.QUIZ,
                    remoteCatalog.id(),
                    remoteCatalog.name(),
                    remoteCatalog.description());
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getFederatedQuizCatalog(int localStationId, UUID partnerStationUid, int catalogId) {
        var partner = resolveActivePartner(localStationId, partnerStationUid);
        if (partner.isRemote()) {
            String json = federationHttpClient.signedGetJson(
                    partner.remoteHost(),
                    "/remote/quiz/catalogs/" + catalogId,
                    localStationId,
                    getPrivateKey(localStationId));
            if (json == null) throw new IllegalStateException("Failed to fetch catalog from remote partner");
            try {
                return federationHttpClient.getMapper().readValue(json, Map.class);
            } catch (Exception e) {
                throw new IllegalStateException("Failed to parse remote catalog response", e);
            }
        }
        var catalog = findCatalog(catalogId).orElseThrow();
        int partnerStationId = resolvePartnerStationId(partner);
        if (catalog.stationId() != partnerStationId) {
            throw new IllegalArgumentException("Catalog does not belong to this partner");
        }
        var categories = findCategories(catalog.stationId());
        var questions = findQuestions(catalog.id());
        return Map.of("catalog", catalog, "categories", categories, "questions", questions);
    }

    public QuizCatalog copyQuizCatalog(int catalogId, int targetStationId) {
        var source = findCatalog(catalogId).orElseThrow();
        var newCatalog = createCatalog(targetStationId, source.name(), source.description(), source.trainingEnabled());

        var categories = findCategories(source.id());
        var categoryMap = new HashMap<Integer, Integer>();
        for (var cat : categories) {
            var newCat = createCategory(newCatalog.id(), cat.name(), cat.description(), cat.position());
            categoryMap.put(cat.id(), newCat.id());
        }

        var questions = findQuestions(source.id());
        for (var q : questions) {
            Integer newCatId = q.categoryId() != null ? categoryMap.get(q.categoryId()) : null;
            createQuestion(
                    newCatalog.id(),
                    newCatId,
                    q.questionType(),
                    q.title(),
                    q.description(),
                    q.imageUrl(),
                    q.points(),
                    q.autoPoints(),
                    q.config() != null ? q.config() : new QuestionConfig.Unknown(),
                    q.position());
        }
        return newCatalog;
    }

    // -- Federation helpers --

    private String getPrivateKey(int stationId) {
        return stationRepository
                .findById(stationId)
                .map(Station::federationPrivateKey)
                .orElse(null);
    }

    private FederationPartner resolveActivePartner(int localStationId, UUID partnerStationUid) {
        var partner = federationRepository
                .findPartnerByStationAndRemoteUid(localStationId, partnerStationUid)
                .orElseThrow(() -> new IllegalArgumentException("Unknown partner"));
        if (partner.status() != FederationPartner.FederationStatus.ACTIVE) {
            throw new IllegalArgumentException("Partner is not active");
        }
        return partner;
    }

    private int resolvePartnerStationId(FederationPartner partner) {
        return stationRepository
                .findByUid(partner.partnerStationId())
                .map(Station::id)
                .orElse(0);
    }

    private <T> List<T> collectResults(List<CompletableFuture<List<T>>> futures) {
        var allFuture = CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
        try {
            allFuture.join();
        } catch (Exception e) {
            log.error("Error during parallel federation fetch", e);
        }
        var result = new ArrayList<T>();
        for (var future : futures) {
            try {
                result.addAll(future.get());
            } catch (Exception e) {
                log.error("Error collecting federation results", e);
            }
        }
        return result;
    }

    public record SharedQuizItem(int id, String name, String description, int sourceStationId, int partnerId) {}
}
