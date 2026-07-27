/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.quiz.repository;

import dev.chojo.ember.feature.quiz.entity.QuizTest;
import dev.chojo.ember.feature.quiz.entity.QuizTestAnswer;
import dev.chojo.ember.feature.quiz.entity.QuizTestAttempt;
import dev.chojo.ember.feature.quiz.entity.QuizTestAttemptQuestion;
import dev.chojo.ember.feature.quiz.entity.QuizTestFrozenQuestion;
import dev.chojo.ember.feature.quiz.entity.QuizTestSection;
import dev.chojo.ember.feature.quiz.entity.QuizTestSectionSource;
import dev.chojo.ember.feature.quiz.entity.TestStatus;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import dev.chojo.ember.util.sql.SqlSupport;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;
import static dev.chojo.ember.util.sql.SqlSupport.count;
import static dev.chojo.ember.util.sql.SqlSupport.deleteById;
import static dev.chojo.ember.util.sql.SqlSupport.exists;
import static dev.chojo.ember.util.sql.SqlSupport.insertReturning;

@Singleton
public class QuizTestRepository {

    private static final String TEST_COLUMNS =
            "t.id, t.station_id, t.title, t.description, t.status, t.time_limit, t.shuffle, t.forced, t.start_at, t.end_at, t.created_by, t.created_at, t.updated_at, t.restriction_mode, EXISTS(SELECT 1 FROM quiz_test_restriction r WHERE r.test_id = t.id) AS restricted";
    private static final String TEST_COLUMNS_BARE =
            "id, station_id, title, description, status, time_limit, shuffle, forced, start_at, end_at, created_by, created_at, updated_at, restriction_mode, EXISTS(SELECT 1 FROM quiz_test_restriction r WHERE r.test_id = id) AS restricted";
    private static final String QUIZ_TEST_SECTION_COLUMNS = "id, test_id, title, description, position";
    private static final String QUIZ_TEST_SECTION_SOURCE_COLUMNS =
            "id, section_id, catalog_id, category_id, question_count";
    private static final String QUIZ_TEST_ATTEMPT_COLUMNS =
            "id, test_id, member_id, status, started_at, submitted_at, graded_at, graded_by, total_points, max_points";
    private static final String QUIZ_TEST_ATTEMPT_QUESTION_COLUMNS =
            "id, attempt_id, question_id, section_id, position";
    private static final String QUIZ_TEST_ANSWER_COLUMNS =
            "id, attempt_id, question_id, section_id, answer, points, graded, position";
    private static final String QUIZ_TEST_FROZEN_QUESTION_COLUMNS = "id, test_id, question_id, section_id, position";

    // -- Tests --

    public List<QuizTest> findByStation(int stationId) {
        return query("""
                SELECT %s
                FROM quiz_test t
                WHERE t.station_id = :station_id
                ORDER BY t.created_at DESC;""", TEST_COLUMNS)
                .single(call().bind("station_id", stationId))
                .map(QuizTest.map())
                .all();
    }

    public List<QuizTest> findByStationForMember(int stationId, int memberId) {
        return query("""
                SELECT %s
                FROM quiz_test t
                WHERE t.station_id = :station_id
                  AND check_restriction('quiz_test_restriction', 'test_id', 'quiz_test', 'id', t.id, :member_id, 'TEST_MANAGER')
                ORDER BY t.created_at DESC;""", TEST_COLUMNS)
                .single(call().bind("station_id", stationId).bind("member_id", memberId))
                .map(QuizTest.map())
                .all();
    }

    public Optional<QuizTest> findById(int id) {
        return query("""
                SELECT %s
                FROM quiz_test t
                WHERE t.id = :id;""", TEST_COLUMNS)
                .single(call().bind("id", id))
                .map(QuizTest.map())
                .first();
    }

    public List<QuizTest> findForcedPending(int stationId, int memberId) {
        return query("""
                SELECT %s
                FROM quiz_test t
                WHERE t.station_id = :station_id
                  AND t.forced = TRUE
                  AND t.status = 'ACTIVE'
                  AND (t.start_at IS NULL OR t.start_at <= now())
                  AND (t.end_at IS NULL OR t.end_at >= now())
                  AND NOT exists (
                      SELECT 1 FROM quiz_test_attempt a
                      WHERE a.test_id = t.id AND a.member_id = :member_id
                        AND a.status IN ('SUBMITTED', 'GRADED'))
                ORDER BY t.title;""", TEST_COLUMNS)
                .single(call().bind("station_id", stationId).bind("member_id", memberId))
                .map(QuizTest.map())
                .all();
    }

    public QuizTest create(
            int stationId,
            String title,
            String description,
            Integer timeLimit,
            boolean shuffle,
            boolean forced,
            int createdBy) {
        return insertReturning(
                """
                INSERT INTO quiz_test(station_id, title, description, time_limit, shuffle, forced, created_by)
                VALUES (:station_id, :title, :description, :time_limit, :shuffle, :forced, :created_by)
                RETURNING %s;""",
                call().bind("station_id", stationId)
                        .bind("title", title)
                        .bind("description", description)
                        .bind("time_limit", timeLimit)
                        .bind("shuffle", shuffle)
                        .bind("forced", forced)
                        .bind("created_by", createdBy),
                QuizTest.map(),
                TEST_COLUMNS_BARE);
    }

    public boolean update(
            int id,
            String title,
            String description,
            Integer timeLimit,
            boolean shuffle,
            boolean forced,
            Instant startAt,
            Instant endAt) {
        return query("""
                UPDATE quiz_test
                SET title = :title, description = :description, time_limit = :time_limit,
                    shuffle = :shuffle, forced = :forced, start_at = :start_at, end_at = :end_at,
                    updated_at = now()
                WHERE id = :id;""")
                .single(call().bind("id", id)
                        .bind("title", title)
                        .bind("description", description)
                        .bind("time_limit", timeLimit)
                        .bind("shuffle", shuffle)
                        .bind("forced", forced)
                        .bind("start_at", startAt, INSTANT_TIMESTAMP)
                        .bind("end_at", endAt, INSTANT_TIMESTAMP))
                .update()
                .changed();
    }

    public boolean updateStatus(int id, TestStatus status) {
        return query("UPDATE quiz_test SET status = :status, updated_at = now() WHERE id = :id;")
                .single(call().bind("id", id).bind("status", status.name()))
                .update()
                .changed();
    }

    public boolean delete(int id) {
        return deleteById("quiz_test", id);
    }

    public int countAttempts(int testId) {
        return count(
                "SELECT count(*) AS cnt FROM quiz_test_attempt WHERE test_id = :test_id;",
                call().bind("test_id", testId));
    }

    // -- Sections --

    public List<QuizTestSection> findSections(int testId) {
        return query(
                        "SELECT %s FROM quiz_test_section WHERE test_id = :test_id ORDER BY position;",
                        QUIZ_TEST_SECTION_COLUMNS)
                .single(call().bind("test_id", testId))
                .map(QuizTestSection.map())
                .all();
    }

    public QuizTestSection createSection(int testId, String title, String description, int position) {
        return insertReturning(
                """
                INSERT INTO quiz_test_section(test_id, title, description, position)
                VALUES (:test_id, :title, :description, :position)
                RETURNING %s;""",
                call().bind("test_id", testId)
                        .bind("title", title)
                        .bind("description", description)
                        .bind("position", position),
                QuizTestSection.map(),
                QUIZ_TEST_SECTION_COLUMNS);
    }

    public boolean updateSection(int id, String title, String description, int position) {
        return query(
                        "UPDATE quiz_test_section SET title = :title, description = :description, position = :position WHERE id = :id;")
                .single(call().bind("id", id)
                        .bind("title", title)
                        .bind("description", description)
                        .bind("position", position))
                .update()
                .changed();
    }

    public boolean deleteSection(int id) {
        return deleteById("quiz_test_section", id);
    }

    public void deleteSectionsByTest(int testId) {
        query("DELETE FROM quiz_test_section WHERE test_id = :test_id;")
                .single(call().bind("test_id", testId))
                .delete();
    }

    // -- Section Sources --

    public List<QuizTestSectionSource> findSources(int sectionId) {
        return query(
                        "SELECT %s FROM quiz_test_section_source WHERE section_id = :section_id;",
                        QUIZ_TEST_SECTION_SOURCE_COLUMNS)
                .single(call().bind("section_id", sectionId))
                .map(QuizTestSectionSource.map())
                .all();
    }

    public QuizTestSectionSource createSource(int sectionId, int catalogId, Integer categoryId, int questionCount) {
        return insertReturning(
                """
                INSERT INTO quiz_test_section_source(section_id, catalog_id, category_id, question_count)
                VALUES (:section_id, :catalog_id, :category_id, :question_count)
                RETURNING %s;""",
                call().bind("section_id", sectionId)
                        .bind("catalog_id", catalogId)
                        .bind("category_id", categoryId)
                        .bind("question_count", questionCount),
                QuizTestSectionSource.map(),
                QUIZ_TEST_SECTION_SOURCE_COLUMNS);
    }

    public boolean deleteSource(int id) {
        return deleteById("quiz_test_section_source", id);
    }

    public void deleteSourcesBySection(int sectionId) {
        query("DELETE FROM quiz_test_section_source WHERE section_id = :section_id;")
                .single(call().bind("section_id", sectionId))
                .delete();
    }

    // -- Attempts --

    public List<QuizTestAttempt> findAttempts(int testId) {
        return query(
                        "SELECT %s FROM quiz_test_attempt WHERE test_id = :test_id ORDER BY started_at DESC;",
                        QUIZ_TEST_ATTEMPT_COLUMNS)
                .single(call().bind("test_id", testId))
                .map(QuizTestAttempt.map())
                .all();
    }

    public Optional<QuizTestAttempt> findAttempt(int testId, int memberId) {
        return query(
                        "SELECT %s FROM quiz_test_attempt WHERE test_id = :test_id AND member_id = :member_id;",
                        QUIZ_TEST_ATTEMPT_COLUMNS)
                .single(call().bind("test_id", testId).bind("member_id", memberId))
                .map(QuizTestAttempt.map())
                .first();
    }

    public Optional<QuizTestAttempt> findAttemptById(int id) {
        return SqlSupport.findById("quiz_test_attempt", QUIZ_TEST_ATTEMPT_COLUMNS, id, QuizTestAttempt.map());
    }

    public QuizTestAttempt createAttempt(int testId, int memberId, double maxPoints) {
        return insertReturning(
                """
                INSERT INTO quiz_test_attempt(test_id, member_id, max_points)
                VALUES (:test_id, :member_id, :max_points)
                ON CONFLICT (test_id, member_id) DO NOTHING
                RETURNING %s;""",
                call().bind("test_id", testId).bind("member_id", memberId).bind("max_points", maxPoints),
                QuizTestAttempt.map(),
                QUIZ_TEST_ATTEMPT_COLUMNS);
    }

    public boolean submitAttempt(int id) {
        return query(
                        "UPDATE quiz_test_attempt SET status = 'SUBMITTED', submitted_at = now() WHERE id = :id AND status = 'IN_PROGRESS';")
                .single(call().bind("id", id))
                .update()
                .changed();
    }

    public void updateAttemptMaxPoints(int id, double maxPoints) {
        query("UPDATE quiz_test_attempt SET max_points = :max_points WHERE id = :id;")
                .single(call().bind("id", id).bind("max_points", maxPoints))
                .update()
                .changed();
    }

    public boolean gradeAttempt(int id, double totalPoints, int gradedBy) {
        return query("""
                UPDATE quiz_test_attempt
                SET status = 'GRADED', total_points = :total_points, graded_at = now(), graded_by = :graded_by
                WHERE id = :id;""")
                .single(call().bind("id", id).bind("total_points", totalPoints).bind("graded_by", gradedBy))
                .update()
                .changed();
    }

    // -- Attempt Questions --

    public List<QuizTestAttemptQuestion> findAttemptQuestions(int attemptId) {
        return query(
                        "SELECT %s FROM quiz_test_attempt_question WHERE attempt_id = :attempt_id ORDER BY position;",
                        QUIZ_TEST_ATTEMPT_QUESTION_COLUMNS)
                .single(call().bind("attempt_id", attemptId))
                .map(QuizTestAttemptQuestion.map())
                .all();
    }

    public void createAttemptQuestion(int attemptId, int questionId, Integer sectionId, int position) {
        query("""
                INSERT INTO quiz_test_attempt_question(attempt_id, question_id, section_id, position)
                VALUES (:attempt_id, :question_id, :section_id, :position);""")
                .single(call().bind("attempt_id", attemptId)
                        .bind("question_id", questionId)
                        .bind("section_id", sectionId)
                        .bind("position", position))
                .insert();
    }

    // -- Answers --

    public List<QuizTestAnswer> findAnswers(int attemptId) {
        return query(
                        "SELECT %s FROM quiz_test_answer WHERE attempt_id = :attempt_id ORDER BY position;",
                        QUIZ_TEST_ANSWER_COLUMNS)
                .single(call().bind("attempt_id", attemptId))
                .map(QuizTestAnswer.map())
                .all();
    }

    public Optional<QuizTestAnswer> findAnswerById(int id) {
        return SqlSupport.findById("quiz_test_answer", QUIZ_TEST_ANSWER_COLUMNS, id, QuizTestAnswer.map());
    }

    public void upsertAnswer(int attemptId, int questionId, Integer sectionId, String answer, int position) {
        query("""
                INSERT INTO quiz_test_answer(attempt_id, question_id, section_id, answer, position)
                VALUES (:attempt_id, :question_id, :section_id, :answer::JSONB, :position)
                ON CONFLICT (attempt_id, question_id) DO UPDATE SET answer = excluded.answer, section_id = excluded.section_id, position = excluded.position;""")
                .single(call().bind("attempt_id", attemptId)
                        .bind("question_id", questionId)
                        .bind("section_id", sectionId)
                        .bind("answer", answer)
                        .bind("position", position))
                .insert();
    }

    public void saveAnswer(int attemptId, int questionId, String answer) {
        query("""
                INSERT INTO quiz_test_answer(attempt_id, question_id, answer)
                VALUES (:attempt_id, :question_id, :answer::jsonb)
                ON CONFLICT (attempt_id, question_id) DO UPDATE SET answer = EXCLUDED.answer;""")
                .single(call().bind("attempt_id", attemptId)
                        .bind("question_id", questionId)
                        .bind("answer", answer))
                .insert();
    }

    public boolean gradeAnswer(int answerId, double points) {
        return query("UPDATE quiz_test_answer SET points = :points, graded = TRUE WHERE id = :id;")
                .single(call().bind("id", answerId).bind("points", points))
                .update()
                .changed();
    }

    // -- Member Access --

    public void grantMemberAccess(int testId, int memberId, Instant closesAt) {
        query("""
                INSERT INTO quiz_test_member_access(test_id, member_id, closes_at)
                VALUES (:test_id, :member_id, :closes_at)
                ON CONFLICT (test_id, member_id) DO UPDATE SET closes_at = :closes_at, opened_at = now();""")
                .single(call().bind("test_id", testId)
                        .bind("member_id", memberId)
                        .bind("closes_at", closesAt, INSTANT_TIMESTAMP))
                .insert();
    }

    public boolean hasMemberAccess(int testId, int memberId) {
        return exists("""
                SELECT 1 FROM quiz_test_member_access
                WHERE test_id = :test_id AND member_id = :member_id
                AND (closes_at IS NULL OR closes_at > now());""", call().bind("test_id", testId).bind("member_id", memberId));
    }

    public void revokeMemberAccess(int testId, int memberId) {
        query("DELETE FROM quiz_test_member_access WHERE test_id = :test_id AND member_id = :member_id;")
                .single(call().bind("test_id", testId).bind("member_id", memberId))
                .delete();
    }

    // -- Frozen Questions --

    public List<QuizTestFrozenQuestion> findFrozenQuestions(int testId) {
        return query(
                        "SELECT %s FROM quiz_test_frozen_question WHERE test_id = :test_id ORDER BY position;",
                        QUIZ_TEST_FROZEN_QUESTION_COLUMNS)
                .single(call().bind("test_id", testId))
                .map(QuizTestFrozenQuestion.map())
                .all();
    }

    public void createFrozenQuestion(int testId, int questionId, Integer sectionId, int position) {
        query("""
                INSERT INTO quiz_test_frozen_question(test_id, question_id, section_id, position)
                VALUES (:test_id, :question_id, :section_id, :position);""")
                .single(call().bind("test_id", testId)
                        .bind("question_id", questionId)
                        .bind("section_id", sectionId)
                        .bind("position", position))
                .insert();
    }

    public void deleteFrozenQuestions(int testId) {
        query("DELETE FROM quiz_test_frozen_question WHERE test_id = :test_id;")
                .single(call().bind("test_id", testId))
                .delete();
    }

    public void deleteFrozenQuestionAtPosition(int testId, int position) {
        query("DELETE FROM quiz_test_frozen_question WHERE test_id = :test_id AND position = :position;")
                .single(call().bind("test_id", testId).bind("position", position))
                .delete();
    }

    // -- Restrictions --

    public boolean updateRestrictionMode(int testId, RestrictionMode mode) {
        return query("UPDATE quiz_test SET restriction_mode = :mode WHERE id = :id;")
                .single(call().bind("mode", mode.name()).bind("id", testId))
                .update()
                .changed();
    }
}
