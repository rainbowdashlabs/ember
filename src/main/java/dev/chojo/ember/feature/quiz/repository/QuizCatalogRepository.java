/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.quiz.repository;

import de.chojo.sadu.postgresql.types.PostgreSqlTypes;
import dev.chojo.ember.feature.quiz.entity.QuizCatalog;
import dev.chojo.ember.feature.quiz.entity.QuizCategory;
import dev.chojo.ember.feature.quiz.entity.QuizQuestion;
import dev.chojo.ember.feature.quiz.entity.QuizQuestionType;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

@Singleton
public class QuizCatalogRepository {

    // -- Catalogs --

    public List<QuizCatalog> findByStation(int stationId) {
        return query("SELECT * FROM quiz_catalog WHERE station_id = :station_id ORDER BY name;")
                .single(call().bind("station_id", stationId))
                .map(QuizCatalog.map())
                .all();
    }

    public Optional<QuizCatalog> findById(int id) {
        return query("SELECT * FROM quiz_catalog WHERE id = :id;")
                .single(call().bind("id", id))
                .map(QuizCatalog.map())
                .first();
    }

    public QuizCatalog create(int stationId, String name, String description, boolean trainingEnabled) {
        return query("""
                        INSERT INTO quiz_catalog(station_id, name, description, training_enabled)
                        VALUES (:station_id, :name, :description, :training_enabled)
                        RETURNING *;""")
                .single(call().bind("station_id", stationId)
                        .bind("name", name)
                        .bind("description", description)
                        .bind("training_enabled", trainingEnabled))
                .map(QuizCatalog.map())
                .first()
                .orElseThrow();
    }

    public boolean update(int id, String name, String description, boolean trainingEnabled) {
        return query("""
                        UPDATE quiz_catalog
                        SET name = :name, description = :description, training_enabled = :training_enabled, updated_at = now()
                        WHERE id = :id;""")
                .single(call().bind("id", id)
                        .bind("name", name)
                        .bind("description", description)
                        .bind("training_enabled", trainingEnabled))
                .update()
                .changed();
    }

    public boolean delete(int id) {
        return query("DELETE FROM quiz_catalog WHERE id = :id;")
                .single(call().bind("id", id))
                .delete()
                .changed();
    }

    /**
     * Flips the {@code public_render} flag — opts the catalog into (or out of) being eligible
     * for the public QUIZ_TEASER random endpoint (concept §3.15 / §4.7).
     */
    public boolean setPublicRender(int id, boolean publicRender) {
        return query("""
                        UPDATE quiz_catalog
                        SET public_render = :public_render, updated_at = now()
                        WHERE id = :id;""")
                .single(call().bind("id", id).bind("public_render", publicRender))
                .update()
                .changed();
    }

    /**
     * Returns catalogs of the given station whose {@code public_render} flag is on. Backs the
     * editor's catalog picker and the public quiz random endpoint (concept §3.15 / §4.7).
     */
    public List<QuizCatalog> findPublicByStation(int stationId) {
        return query("""
                        SELECT * FROM quiz_catalog
                        WHERE station_id = :station_id AND public_render = TRUE
                        ORDER BY name;""")
                .single(call().bind("station_id", stationId))
                .map(QuizCatalog.map())
                .all();
    }

    /**
     * Picks one random {@link QuizQuestion} from the given catalog ids, restricted to catalogs
     * that belong to {@code stationId} and have {@code public_render = TRUE}. The station scoping
     * stops a cell on station A from rendering questions of an unrelated station B by guessing
     * its catalog ids. Returns {@link Optional#empty()} when no eligible catalogs / questions
     * exist.
     */
    public Optional<QuizQuestion> findRandomPublicQuestion(int stationId, List<Integer> catalogIds) {
        if (catalogIds == null || catalogIds.isEmpty()) return Optional.empty();
        return query("""
                        SELECT q.* FROM quiz_question q
                        JOIN quiz_catalog c ON c.id = q.catalog_id
                        WHERE c.station_id = :station_id
                          AND c.public_render = TRUE
                          AND q.catalog_id = ANY(:catalog_ids)
                        ORDER BY random()
                        LIMIT 1;""")
                .single(call().bind("station_id", stationId).bind("catalog_ids", catalogIds, PostgreSqlTypes.INTEGER))
                .map(QuizQuestion.map())
                .first();
    }

    // -- Categories --

    public List<QuizCategory> findCategoriesByStation(int stationId) {
        return query("SELECT * FROM quiz_category WHERE station_id = :station_id ORDER BY position;")
                .single(call().bind("station_id", stationId))
                .map(QuizCategory.map())
                .all();
    }

    public Optional<QuizCategory> findCategoryById(int id) {
        return query("SELECT * FROM quiz_category WHERE id = :id;")
                .single(call().bind("id", id))
                .map(QuizCategory.map())
                .first();
    }

    public QuizCategory createCategory(int stationId, String name, String description, int position) {
        return query("""
                        INSERT INTO quiz_category(station_id, name, description, position)
                        VALUES (:station_id, :name, :description, :position)
                        RETURNING *;""")
                .single(call().bind("station_id", stationId)
                        .bind("name", name)
                        .bind("description", description)
                        .bind("position", position))
                .map(QuizCategory.map())
                .first()
                .orElseThrow();
    }

    public boolean updateCategory(int id, String name, String description, int position) {
        return query(
                        "UPDATE quiz_category SET name = :name, description = :description, position = :position WHERE id = :id;")
                .single(call().bind("id", id)
                        .bind("name", name)
                        .bind("description", description)
                        .bind("position", position))
                .update()
                .changed();
    }

    public boolean deleteCategory(int id) {
        return query("DELETE FROM quiz_category WHERE id = :id;")
                .single(call().bind("id", id))
                .delete()
                .changed();
    }

    // -- Questions --

    public List<QuizQuestion> findQuestions(int catalogId) {
        return query("SELECT * FROM quiz_question WHERE catalog_id = :catalog_id ORDER BY position;")
                .single(call().bind("catalog_id", catalogId))
                .map(QuizQuestion.map())
                .all();
    }

    public List<QuizQuestion> findQuestionsByCategory(int catalogId, int categoryId) {
        return query(
                        "SELECT * FROM quiz_question WHERE catalog_id = :catalog_id AND category_id = :category_id ORDER BY position;")
                .single(call().bind("catalog_id", catalogId).bind("category_id", categoryId))
                .map(QuizQuestion.map())
                .all();
    }

    public Optional<QuizQuestion> findQuestionById(int id) {
        return query("SELECT * FROM quiz_question WHERE id = :id;")
                .single(call().bind("id", id))
                .map(QuizQuestion.map())
                .first();
    }

    public List<QuizQuestion> findQuestionsByIds(List<Integer> ids) {
        if (ids.isEmpty()) return List.of();
        return query("SELECT * FROM quiz_question WHERE id = ANY(:ids);")
                .single(call().bind("ids", ids, PostgreSqlTypes.INTEGER))
                .map(QuizQuestion.map())
                .all();
    }

    public QuizQuestion createQuestion(
            int catalogId,
            Integer categoryId,
            QuizQuestionType quizQuestionType,
            String title,
            String description,
            String imageUrl,
            double points,
            boolean autoPoints,
            String config,
            int position) {
        return query("""
                        INSERT INTO quiz_question(catalog_id, category_id, question_type, title, description, image_url, points, auto_points, config, position)
                        VALUES (:catalog_id, :category_id, :question_type, :title, :description, :image_url, :points, :auto_points, :config::jsonb, :position)
                        RETURNING *;""")
                .single(call().bind("catalog_id", catalogId)
                        .bind("category_id", categoryId)
                        .bind("question_type", quizQuestionType.name())
                        .bind("title", title)
                        .bind("description", description)
                        .bind("image_url", imageUrl)
                        .bind("points", points)
                        .bind("auto_points", autoPoints)
                        .bind("config", config)
                        .bind("position", position))
                .map(QuizQuestion.map())
                .first()
                .orElseThrow();
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
        return query("""
                        UPDATE quiz_question
                        SET category_id = :category_id, title = :title, description = :description,
                            image_url = :image_url, points = :points, auto_points = :auto_points,
                            config = :config::jsonb, position = :position, updated_at = now()
                        WHERE id = :id;""")
                .single(call().bind("id", id)
                        .bind("category_id", categoryId)
                        .bind("title", title)
                        .bind("description", description)
                        .bind("image_url", imageUrl)
                        .bind("points", points)
                        .bind("auto_points", autoPoints)
                        .bind("config", config)
                        .bind("position", position))
                .update()
                .changed();
    }

    public boolean deleteQuestion(int id) {
        return query("DELETE FROM quiz_question WHERE id = :id;")
                .single(call().bind("id", id))
                .delete()
                .changed();
    }

    public int countQuestions(int catalogId) {
        return query("SELECT count(*) AS cnt FROM quiz_question WHERE catalog_id = :catalog_id;")
                .single(call().bind("catalog_id", catalogId))
                .map(row -> row.getInt("cnt"))
                .first()
                .orElse(0);
    }

    public List<QuizCatalog> findTrainingCatalogs(int stationId) {
        return query(
                        "SELECT * FROM quiz_catalog WHERE station_id = :station_id AND training_enabled = true ORDER BY name;")
                .single(call().bind("station_id", stationId))
                .map(QuizCatalog.map())
                .all();
    }
}
