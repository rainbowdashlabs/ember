/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.quiz.service;

import dev.chojo.ember.feature.quiz.entity.QuizQuestionReport;
import dev.chojo.ember.feature.quiz.repository.QuizQuestionReportRepository;
import io.javalin.http.BadRequestResponse;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

/**
 * The notes members leave on a question while training.
 *
 * <p>A note has no state of its own: it exists while something about the question is unsettled and
 * is deleted once whoever maintains the catalog has acknowledged it. That keeps the list on a
 * catalog a list of open business rather than an archive nobody reads.
 */
@Singleton
public class QuizQuestionReportService {
    private static final Logger log = LoggerFactory.getLogger(QuizQuestionReportService.class);
    private static final int MAX_NOTE_LENGTH = 2000;

    private final QuizQuestionReportRepository reportRepository;

    @Inject
    public QuizQuestionReportService(QuizQuestionReportRepository reportRepository) {
        this.reportRepository = reportRepository;
    }

    /**
     * Records what a member says is wrong with a question.
     *
     * @param memberId who is saying it, or {@code null} when the member cannot be resolved
     * @throws BadRequestResponse when the note is empty, because a note without a reason gives
     *                            whoever reads it nothing to act on
     */
    public QuizQuestionReport report(int questionId, Integer memberId, String note) {
        String trimmed = note != null ? note.trim() : "";
        if (trimmed.isEmpty()) throw new BadRequestResponse("A report needs a note");
        if (trimmed.length() > MAX_NOTE_LENGTH) trimmed = trimmed.substring(0, MAX_NOTE_LENGTH);

        var report = reportRepository.create(questionId, memberId, trimmed);
        log.info("Question {} was reported by member {}", questionId, memberId);
        return report;
    }

    public List<QuizQuestionReport> findByCatalog(int catalogId) {
        return reportRepository.findByCatalog(catalogId);
    }

    public Optional<Integer> findCatalogOfReport(int reportId) {
        return reportRepository.findCatalogOfReport(reportId);
    }

    /** Acknowledges a note, which is what removes it. */
    public boolean acknowledge(int reportId) {
        boolean deleted = reportRepository.delete(reportId);
        if (deleted) {
            log.info("Question report {} was acknowledged", reportId);
        } else {
            log.warn("Acknowledging question report {} affected zero rows", reportId);
        }
        return deleted;
    }
}
