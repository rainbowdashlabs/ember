/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.quiz.repository;

import de.chojo.sadu.queries.api.call.Call;
import de.chojo.sadu.queries.api.query.Query;
import dev.chojo.ember.feature.quiz.entity.QuizTest;
import dev.chojo.ember.feature.quiz.entity.QuizTestAnswer;
import dev.chojo.ember.feature.quiz.entity.QuizTestAttempt;
import dev.chojo.ember.feature.quiz.entity.QuizTestAttemptQuestion;
import dev.chojo.ember.feature.quiz.entity.QuizTestFrozenQuestion;
import dev.chojo.ember.feature.quiz.entity.QuizTestSection;
import dev.chojo.ember.feature.quiz.entity.QuizTestSectionSource;
import dev.chojo.ember.feature.quiz.entity.TestStatus;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

@Singleton
public class QuizTestRepository {

    private static final String TEST_COLUMNS =
            "t.id, t.station_id, t.title, t.description, t.status, t.time_limit, t.shuffle, t.forced, t.start_at, t.end_at, t.created_by, t.created_at, t.updated_at, t.restriction_mode, EXISTS(SELECT 1 FROM quiz_test_restriction r WHERE r.test_id = t.id) AS restricted";
    private static final String TEST_COLUMNS_BARE =
            "id, station_id, title, description, status, time_limit, shuffle, forced, start_at, end_at, created_by, created_at, updated_at, restriction_mode, EXISTS(SELECT 1 FROM quiz_test_restriction r WHERE r.test_id = id) AS restricted";

    // -- Tests --

    public List<QuizTest> findByStation(int stationId) {
        return Query.query("SELECT " + TEST_COLUMNS
                        + " FROM quiz_test t WHERE t.station_id = :station_id ORDER BY t.created_at DESC;")
                .single(Call.of().bind("station_id", stationId))
                .map(QuizTest.map())
                .all();
    }

    public List<QuizTest> findByStationForMember(int stationId, int memberId) {
        return Query.query("SELECT " + TEST_COLUMNS + " FROM quiz_test t"
                        + " WHERE t.station_id = :station_id"
                        + " AND check_restriction('quiz_test_restriction', 'test_id', 'quiz_test', 'id', t.id, :member_id, 'QUIZ_MANAGER')"
                        + " ORDER BY t.created_at DESC;")
                .single(Call.of().bind("station_id", stationId).bind("member_id", memberId))
                .map(QuizTest.map())
                .all();
    }

    public Optional<QuizTest> findById(int id) {
        return Query.query("SELECT " + TEST_COLUMNS + " FROM quiz_test t WHERE t.id = :id;")
                .single(Call.of().bind("id", id))
                .map(QuizTest.map())
                .first();
    }

    public List<QuizTest> findForcedPending(int stationId, int memberId) {
        return Query.query("SELECT " + TEST_COLUMNS
                        + " FROM quiz_test t WHERE t.station_id = :station_id AND t.forced = true AND t.status = 'ACTIVE'"
                        + " AND (t.start_at IS NULL OR t.start_at <= now()) AND (t.end_at IS NULL OR t.end_at >= now())"
                        + " AND NOT EXISTS (SELECT 1 FROM quiz_test_attempt a WHERE a.test_id = t.id AND a.member_id = :member_id AND a.status IN ('SUBMITTED', 'GRADED'))"
                        + " ORDER BY t.title;")
                .single(Call.of().bind("station_id", stationId).bind("member_id", memberId))
                .map(QuizTest.map())
                .all();
    }

    public QuizTest create(
            int stationId, String title, String description, Integer timeLimit, boolean shuffle, int createdBy) {
        return Query.query("""
                        INSERT INTO quiz_test(station_id, title, description, time_limit, shuffle, created_by)
                        VALUES (:station_id, :title, :description, :time_limit, :shuffle, :created_by)
                        RETURNING\s""" + TEST_COLUMNS_BARE + ";")
                .single(Call.of()
                        .bind("station_id", stationId)
                        .bind("title", title)
                        .bind("description", description)
                        .bind("time_limit", timeLimit)
                        .bind("shuffle", shuffle)
                        .bind("created_by", createdBy))
                .map(QuizTest.map())
                .first()
                .orElseThrow();
    }

    public boolean update(
            int id,
            String title,
            String description,
            Integer timeLimit,
            boolean shuffle,
            Instant startAt,
            Instant endAt) {
        return Query.query("""
                        UPDATE quiz_test
                        SET title = :title, description = :description, time_limit = :time_limit,
                            shuffle = :shuffle, start_at = :start_at, end_at = :end_at, updated_at = now()
                        WHERE id = :id;""")
                .single(Call.of()
                        .bind("id", id)
                        .bind("title", title)
                        .bind("description", description)
                        .bind("time_limit", timeLimit)
                        .bind("shuffle", shuffle)
                        .bind("start_at", startAt, INSTANT_TIMESTAMP)
                        .bind("end_at", endAt, INSTANT_TIMESTAMP))
                .update()
                .changed();
    }

    public boolean updateStatus(int id, TestStatus status) {
        return Query.query("UPDATE quiz_test SET status = :status, updated_at = now() WHERE id = :id;")
                .single(Call.of().bind("id", id).bind("status", status.name()))
                .update()
                .changed();
    }

    public boolean delete(int id) {
        return Query.query("DELETE FROM quiz_test WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .delete()
                .changed();
    }

    public int countAttempts(int testId) {
        return Query.query("SELECT count(*) AS cnt FROM quiz_test_attempt WHERE test_id = :test_id;")
                .single(Call.of().bind("test_id", testId))
                .map(row -> row.getInt("cnt"))
                .first()
                .orElse(0);
    }

    // -- Sections --

    public List<QuizTestSection> findSections(int testId) {
        return Query.query("SELECT * FROM quiz_test_section WHERE test_id = :test_id ORDER BY position;")
                .single(Call.of().bind("test_id", testId))
                .map(QuizTestSection.map())
                .all();
    }

    public QuizTestSection createSection(int testId, String title, String description, int position) {
        return Query.query("""
                        INSERT INTO quiz_test_section(test_id, title, description, position)
                        VALUES (:test_id, :title, :description, :position)
                        RETURNING *;""")
                .single(Call.of()
                        .bind("test_id", testId)
                        .bind("title", title)
                        .bind("description", description)
                        .bind("position", position))
                .map(QuizTestSection.map())
                .first()
                .orElseThrow();
    }

    public boolean updateSection(int id, String title, String description, int position) {
        return Query.query(
                        "UPDATE quiz_test_section SET title = :title, description = :description, position = :position WHERE id = :id;")
                .single(Call.of()
                        .bind("id", id)
                        .bind("title", title)
                        .bind("description", description)
                        .bind("position", position))
                .update()
                .changed();
    }

    public boolean deleteSection(int id) {
        return Query.query("DELETE FROM quiz_test_section WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .delete()
                .changed();
    }

    public void deleteSectionsByTest(int testId) {
        Query.query("DELETE FROM quiz_test_section WHERE test_id = :test_id;")
                .single(Call.of().bind("test_id", testId))
                .delete();
    }

    // -- Section Sources --

    public List<QuizTestSectionSource> findSources(int sectionId) {
        return Query.query("SELECT * FROM quiz_test_section_source WHERE section_id = :section_id;")
                .single(Call.of().bind("section_id", sectionId))
                .map(QuizTestSectionSource.map())
                .all();
    }

    public QuizTestSectionSource createSource(int sectionId, int catalogId, Integer categoryId, int questionCount) {
        return Query.query("""
                        INSERT INTO quiz_test_section_source(section_id, catalog_id, category_id, question_count)
                        VALUES (:section_id, :catalog_id, :category_id, :question_count)
                        RETURNING *;""")
                .single(Call.of()
                        .bind("section_id", sectionId)
                        .bind("catalog_id", catalogId)
                        .bind("category_id", categoryId)
                        .bind("question_count", questionCount))
                .map(QuizTestSectionSource.map())
                .first()
                .orElseThrow();
    }

    public boolean deleteSource(int id) {
        return Query.query("DELETE FROM quiz_test_section_source WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .delete()
                .changed();
    }

    public void deleteSourcesBySection(int sectionId) {
        Query.query("DELETE FROM quiz_test_section_source WHERE section_id = :section_id;")
                .single(Call.of().bind("section_id", sectionId))
                .delete();
    }

    // -- Attempts --

    public List<QuizTestAttempt> findAttempts(int testId) {
        return Query.query("SELECT * FROM quiz_test_attempt WHERE test_id = :test_id ORDER BY started_at DESC;")
                .single(Call.of().bind("test_id", testId))
                .map(QuizTestAttempt.map())
                .all();
    }

    public Optional<QuizTestAttempt> findAttempt(int testId, int memberId) {
        return Query.query("SELECT * FROM quiz_test_attempt WHERE test_id = :test_id AND member_id = :member_id;")
                .single(Call.of().bind("test_id", testId).bind("member_id", memberId))
                .map(QuizTestAttempt.map())
                .first();
    }

    public Optional<QuizTestAttempt> findAttemptById(int id) {
        return Query.query("SELECT * FROM quiz_test_attempt WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .map(QuizTestAttempt.map())
                .first();
    }

    public QuizTestAttempt createAttempt(int testId, int memberId, double maxPoints) {
        return Query.query("""
                        INSERT INTO quiz_test_attempt(test_id, member_id, max_points)
                        VALUES (:test_id, :member_id, :max_points)
                        ON CONFLICT (test_id, member_id) DO NOTHING
                        RETURNING *;""")
                .single(Call.of()
                        .bind("test_id", testId)
                        .bind("member_id", memberId)
                        .bind("max_points", maxPoints))
                .map(QuizTestAttempt.map())
                .first()
                .orElseThrow();
    }

    public boolean submitAttempt(int id) {
        return Query.query(
                        "UPDATE quiz_test_attempt SET status = 'SUBMITTED', submitted_at = now() WHERE id = :id AND status = 'IN_PROGRESS';")
                .single(Call.of().bind("id", id))
                .update()
                .changed();
    }

    public boolean gradeAttempt(int id, double totalPoints, int gradedBy) {
        return Query.query("""
                        UPDATE quiz_test_attempt
                        SET status = 'GRADED', total_points = :total_points, graded_at = now(), graded_by = :graded_by
                        WHERE id = :id;""")
                .single(Call.of()
                        .bind("id", id)
                        .bind("total_points", totalPoints)
                        .bind("graded_by", gradedBy))
                .update()
                .changed();
    }

    // -- Attempt Questions --

    public List<QuizTestAttemptQuestion> findAttemptQuestions(int attemptId) {
        return Query.query("SELECT * FROM quiz_test_attempt_question WHERE attempt_id = :attempt_id ORDER BY position;")
                .single(Call.of().bind("attempt_id", attemptId))
                .map(QuizTestAttemptQuestion.map())
                .all();
    }

    public void createAttemptQuestion(int attemptId, int questionId, Integer sectionId, int position) {
        Query.query("""
                        INSERT INTO quiz_test_attempt_question(attempt_id, question_id, section_id, position)
                        VALUES (:attempt_id, :question_id, :section_id, :position);""")
                .single(Call.of()
                        .bind("attempt_id", attemptId)
                        .bind("question_id", questionId)
                        .bind("section_id", sectionId)
                        .bind("position", position))
                .insert();
    }

    // -- Answers --

    public List<QuizTestAnswer> findAnswers(int attemptId) {
        return Query.query("SELECT * FROM quiz_test_answer WHERE attempt_id = :attempt_id ORDER BY position;")
                .single(Call.of().bind("attempt_id", attemptId))
                .map(QuizTestAnswer.map())
                .all();
    }

    public void upsertAnswer(int attemptId, int questionId, Integer sectionId, String answer, int position) {
        Query.query("""
                        INSERT INTO quiz_test_answer(attempt_id, question_id, section_id, answer, position)
                        VALUES (:attempt_id, :question_id, :section_id, :answer::jsonb, :position)
                        ON CONFLICT (id) DO UPDATE SET answer = :answer::jsonb;""")
                .single(Call.of()
                        .bind("attempt_id", attemptId)
                        .bind("question_id", questionId)
                        .bind("section_id", sectionId)
                        .bind("answer", answer)
                        .bind("position", position))
                .insert();
    }

    public void saveAnswer(int attemptId, int questionId, String answer) {
        Query.query("""
                        INSERT INTO quiz_test_answer(attempt_id, question_id, answer)
                        VALUES (:attempt_id, :question_id, :answer::jsonb)
                        ON CONFLICT DO NOTHING;""")
                .single(Call.of()
                        .bind("attempt_id", attemptId)
                        .bind("question_id", questionId)
                        .bind("answer", answer))
                .insert();
        Query.query("""
                        UPDATE quiz_test_answer SET answer = :answer::jsonb
                        WHERE attempt_id = :attempt_id AND question_id = :question_id;""")
                .single(Call.of()
                        .bind("attempt_id", attemptId)
                        .bind("question_id", questionId)
                        .bind("answer", answer))
                .update();
    }

    public boolean gradeAnswer(int answerId, double points) {
        return Query.query("UPDATE quiz_test_answer SET points = :points, graded = true WHERE id = :id;")
                .single(Call.of().bind("id", answerId).bind("points", points))
                .update()
                .changed();
    }

    // -- Member Access --

    public void grantMemberAccess(int testId, int memberId, Instant closesAt) {
        Query.query("""
                        INSERT INTO quiz_test_member_access(test_id, member_id, closes_at)
                        VALUES (:test_id, :member_id, :closes_at)
                        ON CONFLICT (test_id, member_id) DO UPDATE SET closes_at = :closes_at, opened_at = now();""")
                .single(Call.of()
                        .bind("test_id", testId)
                        .bind("member_id", memberId)
                        .bind("closes_at", closesAt, INSTANT_TIMESTAMP))
                .insert();
    }

    public boolean hasMemberAccess(int testId, int memberId) {
        return Query.query("""
                        SELECT 1 FROM quiz_test_member_access
                        WHERE test_id = :test_id AND member_id = :member_id
                        AND (closes_at IS NULL OR closes_at > now());""")
                .single(Call.of().bind("test_id", testId).bind("member_id", memberId))
                .map(row -> true)
                .first()
                .isPresent();
    }

    public void revokeMemberAccess(int testId, int memberId) {
        Query.query("DELETE FROM quiz_test_member_access WHERE test_id = :test_id AND member_id = :member_id;")
                .single(Call.of().bind("test_id", testId).bind("member_id", memberId))
                .delete();
    }

    // -- Frozen Questions --

    public List<QuizTestFrozenQuestion> findFrozenQuestions(int testId) {
        return Query.query("SELECT * FROM quiz_test_frozen_question WHERE test_id = :test_id ORDER BY position;")
                .single(Call.of().bind("test_id", testId))
                .map(QuizTestFrozenQuestion.map())
                .all();
    }

    public void createFrozenQuestion(int testId, int questionId, Integer sectionId, int position) {
        Query.query("""
                        INSERT INTO quiz_test_frozen_question(test_id, question_id, section_id, position)
                        VALUES (:test_id, :question_id, :section_id, :position);""")
                .single(Call.of()
                        .bind("test_id", testId)
                        .bind("question_id", questionId)
                        .bind("section_id", sectionId)
                        .bind("position", position))
                .insert();
    }

    public void deleteFrozenQuestions(int testId) {
        Query.query("DELETE FROM quiz_test_frozen_question WHERE test_id = :test_id;")
                .single(Call.of().bind("test_id", testId))
                .delete();
    }

    public void deleteFrozenQuestionAtPosition(int testId, int position) {
        Query.query("DELETE FROM quiz_test_frozen_question WHERE test_id = :test_id AND position = :position;")
                .single(Call.of().bind("test_id", testId).bind("position", position))
                .delete();
    }

    // -- Restrictions --

    public boolean updateRestrictionMode(int testId, RestrictionMode mode) {
        return Query.query("UPDATE quiz_test SET restriction_mode = :mode WHERE id = :id;")
                .single(Call.of().bind("mode", mode.name()).bind("id", testId))
                .update()
                .changed();
    }
}
