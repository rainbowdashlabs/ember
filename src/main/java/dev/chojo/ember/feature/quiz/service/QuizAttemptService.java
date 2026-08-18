/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.quiz.service;

import dev.chojo.ember.feature.quiz.entity.QuizAnswerValue;
import dev.chojo.ember.feature.quiz.entity.QuizTestAnswer;
import dev.chojo.ember.feature.quiz.entity.QuizTestAttempt;
import dev.chojo.ember.feature.quiz.entity.QuizTestAttemptQuestion;
import dev.chojo.ember.feature.quiz.entity.QuizTestFrozenQuestion;
import dev.chojo.ember.feature.quiz.repository.QuizTestRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

/**
 * A member's run through a test: the attempt itself, the answers given, and the grading
 * that follows submission.
 */
@Singleton
public class QuizAttemptService {
    private static final Logger log = LoggerFactory.getLogger(QuizAttemptService.class);

    private final QuizTestRepository testRepository;
    private final QuizQuestionService questionService;
    private final QuizAnswerGrader grader;

    @Inject
    public QuizAttemptService(
            QuizTestRepository testRepository, QuizQuestionService questionService, QuizAnswerGrader grader) {
        this.testRepository = testRepository;
        this.questionService = questionService;
        this.grader = grader;
    }

    public List<QuizTestAttempt> findAttempts(int testId) {
        return testRepository.findAttempts(testId);
    }

    public Optional<QuizTestAttempt> findAttempt(int testId, int memberId) {
        return testRepository.findAttempt(testId, memberId);
    }

    public Optional<QuizTestAttempt> findAttemptById(int id) {
        return testRepository.findAttemptById(id);
    }

    public List<QuizTestAttemptQuestion> findAttemptQuestions(int attemptId) {
        return testRepository.findAttemptQuestions(attemptId);
    }

    public List<QuizTestAnswer> findAnswers(int attemptId) {
        return testRepository.findAnswers(attemptId);
    }

    public Optional<QuizTestAnswer> findAnswerById(int answerId) {
        return testRepository.findAnswerById(answerId);
    }

    /**
     * Opens an attempt on the question set that was frozen when the test was activated.
     *
     * @throws IllegalStateException if the test carries no frozen question set
     */
    public QuizTestAttempt startAttempt(int testId, int memberId) {
        var frozenQuestions = testRepository.findFrozenQuestions(testId);
        if (frozenQuestions.isEmpty()) {
            throw new IllegalStateException("Test has no frozen questions - was it activated properly?");
        }

        double totalMaxPoints = sumQuestionPoints(
                frozenQuestions.stream().map(QuizTestFrozenQuestion::questionId).toList());
        var attempt = testRepository.createAttempt(testId, memberId, totalMaxPoints);

        for (var fq : frozenQuestions) {
            testRepository.createAttemptQuestion(attempt.id(), fq.questionId(), fq.sectionId(), fq.position());
        }

        log.info("Started quiz attempt {} on test {} for member {}", attempt.id(), testId, memberId);
        return attempt;
    }

    /**
     * Stores a submitted answer, validating the payload against the shape the
     * answered question's type prescribes.
     *
     * @param attemptId  the attempt the answer belongs to
     * @param questionId the answered question
     * @param answer     the raw submitted JSON payload
     * @throws IllegalArgumentException if the question is unknown or the payload does not fit its type
     */
    public void saveAnswer(int attemptId, int questionId, String answer) {
        var question = questionService
                .findQuestion(questionId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown quiz question " + questionId));
        testRepository.saveAnswer(attemptId, questionId, QuizAnswerValue.parse(question.quizQuestionType(), answer));
    }

    /**
     * Closes an attempt and scores every answer that can be graded automatically.
     */
    public boolean submitAttempt(int attemptId) {
        boolean submitted = testRepository.submitAttempt(attemptId);
        if (submitted) {
            autoGradeAnswers(attemptId);
            log.info("Submitted quiz attempt {}", attemptId);
        } else {
            log.warn("Submit for quiz attempt {} affected zero rows", attemptId);
        }
        return submitted;
    }

    public boolean gradeAnswer(int answerId, double points) {
        boolean graded = testRepository.gradeAnswer(answerId, points);
        if (graded) {
            log.info("Graded quiz answer {} with {} points", answerId, points);
        } else {
            log.warn("Grade for quiz answer {} affected zero rows", answerId);
        }
        return graded;
    }

    /**
     * Totals the graded answers of an attempt and records the result, refreshing the
     * attainable points from the questions as they stand now.
     */
    public boolean gradeAttempt(int attemptId, int gradedBy) {
        var answers = testRepository.findAnswers(attemptId);
        double total = answers.stream()
                .filter(QuizTestAnswer::graded)
                .mapToDouble(a -> a.points() != null ? a.points() : 0)
                .sum();

        testRepository.updateAttemptMaxPoints(
                attemptId,
                sumQuestionPoints(
                        answers.stream().map(QuizTestAnswer::questionId).toList()));

        boolean graded = testRepository.gradeAttempt(attemptId, total, gradedBy);
        if (graded) {
            log.info("Graded quiz attempt {} with {} points (grader member {})", attemptId, total, gradedBy);
        } else {
            log.warn("Grade for quiz attempt {} affected zero rows", attemptId);
        }
        return graded;
    }

    private void autoGradeAnswers(int attemptId) {
        for (var answer : testRepository.findAnswers(attemptId)) {
            var question = questionService.findQuestion(answer.questionId());
            if (question.isEmpty()) continue;
            double autoPoints = grader.autoGrade(question.get(), answer.answer());
            if (autoPoints >= 0) {
                testRepository.gradeAnswer(answer.id(), autoPoints);
            }
        }
    }

    private double sumQuestionPoints(List<Integer> questionIds) {
        double total = 0;
        for (var questionId : questionIds) {
            var question = questionService.findQuestion(questionId);
            if (question.isPresent()) {
                total += question.get().points();
            }
        }
        return total;
    }
}
