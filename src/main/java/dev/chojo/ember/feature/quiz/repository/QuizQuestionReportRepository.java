/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.quiz.repository;

import dev.chojo.ember.feature.quiz.entity.QuizQuestionReport;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static dev.chojo.ember.util.sql.SqlSupport.deleteById;
import static dev.chojo.ember.util.sql.SqlSupport.insertReturning;

/**
 * The notes members leave on quiz questions. Reads join the reporter's name rather than storing it,
 * so a member who renames themselves is named correctly on notes they wrote earlier.
 */
@Singleton
public class QuizQuestionReportRepository {
    private static final String REPORT_COLUMNS =
            "r.id, r.question_id, m.display_name AS reporter_name, r.note, r.created_at";

    public QuizQuestionReport create(int questionId, Integer memberId, String note) {
        return insertReturning(
                """
                WITH inserted AS (
                    INSERT INTO quiz_question_report(question_id, reported_by, note)
                    VALUES (:question_id, :reported_by, :note)
                    RETURNING id, question_id, reported_by, note, created_at
                )
                SELECT %s FROM inserted r
                LEFT JOIN station_member m ON m.id = r.reported_by;""",
                call().bind("question_id", questionId)
                        .bind("reported_by", memberId)
                        .bind("note", note),
                QuizQuestionReport.map(),
                REPORT_COLUMNS);
    }

    /** Every open note on the questions of one catalog, oldest first. */
    public List<QuizQuestionReport> findByCatalog(int catalogId) {
        return query("""
                SELECT %s FROM quiz_question_report r
                JOIN quiz_question q ON q.id = r.question_id
                LEFT JOIN station_member m ON m.id = r.reported_by
                WHERE q.catalog_id = :catalog_id
                ORDER BY r.created_at;""", REPORT_COLUMNS)
                .single(call().bind("catalog_id", catalogId))
                .map(QuizQuestionReport.map())
                .all();
    }

    /**
     * The catalog a note belongs to, which is what the permission check needs before anybody may
     * read or acknowledge it.
     */
    public Optional<Integer> findCatalogOfReport(int reportId) {
        return query("""
                SELECT q.catalog_id FROM quiz_question_report r
                JOIN quiz_question q ON q.id = r.question_id
                WHERE r.id = :id;""")
                .single(call().bind("id", reportId))
                .map(row -> row.getInt("catalog_id"))
                .first();
    }

    public boolean delete(int id) {
        return deleteById("quiz_question_report", id);
    }
}
