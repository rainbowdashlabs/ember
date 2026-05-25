/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.quiz.repository;

import de.chojo.sadu.queries.api.call.Call;
import de.chojo.sadu.queries.api.query.Query;
import dev.chojo.ember.feature.quiz.entity.QuestionType;
import dev.chojo.ember.feature.quiz.entity.QuizCatalog;
import dev.chojo.ember.feature.quiz.entity.QuizCategory;
import dev.chojo.ember.feature.quiz.entity.QuizQuestion;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Optional;

@Singleton
public class QuizCatalogRepository {

    // -- Catalogs --

    public List<QuizCatalog> findByStation(int stationId) {
        return Query.query("SELECT * FROM quiz_catalog WHERE station_id = :station_id ORDER BY name;")
                .single(Call.of().bind("station_id", stationId))
                .map(QuizCatalog.map())
                .all();
    }

    public Optional<QuizCatalog> findById(int id) {
        return Query.query("SELECT * FROM quiz_catalog WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .map(QuizCatalog.map())
                .first();
    }

    public QuizCatalog create(int stationId, String name, String description, boolean trainingEnabled) {
        return Query.query("""
                        INSERT INTO quiz_catalog(station_id, name, description, training_enabled)
                        VALUES (:station_id, :name, :description, :training_enabled)
                        RETURNING *;""")
                .single(Call.of()
                        .bind("station_id", stationId)
                        .bind("name", name)
                        .bind("description", description)
                        .bind("training_enabled", trainingEnabled))
                .map(QuizCatalog.map())
                .first()
                .orElseThrow();
    }

    public boolean update(int id, String name, String description, boolean trainingEnabled) {
        return Query.query("""
                        UPDATE quiz_catalog
                        SET name = :name, description = :description, training_enabled = :training_enabled, updated_at = now()
                        WHERE id = :id;""")
                .single(Call.of()
                        .bind("id", id)
                        .bind("name", name)
                        .bind("description", description)
                        .bind("training_enabled", trainingEnabled))
                .update()
                .changed();
    }

    public boolean delete(int id) {
        return Query.query("DELETE FROM quiz_catalog WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .delete()
                .changed();
    }

    // -- Categories --

    public List<QuizCategory> findCategoriesByStation(int stationId) {
        return Query.query("SELECT * FROM quiz_category WHERE station_id = :station_id ORDER BY position;")
                .single(Call.of().bind("station_id", stationId))
                .map(QuizCategory.map())
                .all();
    }

    public Optional<QuizCategory> findCategoryById(int id) {
        return Query.query("SELECT * FROM quiz_category WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .map(QuizCategory.map())
                .first();
    }

    public QuizCategory createCategory(int stationId, String name, String description, int position) {
        return Query.query("""
                        INSERT INTO quiz_category(station_id, name, description, position)
                        VALUES (:station_id, :name, :description, :position)
                        RETURNING *;""")
                .single(Call.of()
                        .bind("station_id", stationId)
                        .bind("name", name)
                        .bind("description", description)
                        .bind("position", position))
                .map(QuizCategory.map())
                .first()
                .orElseThrow();
    }

    public boolean updateCategory(int id, String name, String description, int position) {
        return Query.query(
                        "UPDATE quiz_category SET name = :name, description = :description, position = :position WHERE id = :id;")
                .single(Call.of()
                        .bind("id", id)
                        .bind("name", name)
                        .bind("description", description)
                        .bind("position", position))
                .update()
                .changed();
    }

    public boolean deleteCategory(int id) {
        return Query.query("DELETE FROM quiz_category WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .delete()
                .changed();
    }

    // -- Questions --

    public List<QuizQuestion> findQuestions(int catalogId) {
        return Query.query("SELECT * FROM quiz_question WHERE catalog_id = :catalog_id ORDER BY position;")
                .single(Call.of().bind("catalog_id", catalogId))
                .map(QuizQuestion.map())
                .all();
    }

    public List<QuizQuestion> findQuestionsByCategory(int catalogId, int categoryId) {
        return Query.query(
                        "SELECT * FROM quiz_question WHERE catalog_id = :catalog_id AND category_id = :category_id ORDER BY position;")
                .single(Call.of().bind("catalog_id", catalogId).bind("category_id", categoryId))
                .map(QuizQuestion.map())
                .all();
    }

    public Optional<QuizQuestion> findQuestionById(int id) {
        return Query.query("SELECT * FROM quiz_question WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .map(QuizQuestion.map())
                .first();
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
            String config,
            int position) {
        return Query.query("""
                        INSERT INTO quiz_question(catalog_id, category_id, question_type, title, description, image_url, points, auto_points, config, position)
                        VALUES (:catalog_id, :category_id, :question_type, :title, :description, :image_url, :points, :auto_points, :config::jsonb, :position)
                        RETURNING *;""")
                .single(Call.of()
                        .bind("catalog_id", catalogId)
                        .bind("category_id", categoryId)
                        .bind("question_type", questionType.name())
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
        return Query.query("""
                        UPDATE quiz_question
                        SET category_id = :category_id, title = :title, description = :description,
                            image_url = :image_url, points = :points, auto_points = :auto_points,
                            config = :config::jsonb, position = :position, updated_at = now()
                        WHERE id = :id;""")
                .single(Call.of()
                        .bind("id", id)
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
        return Query.query("DELETE FROM quiz_question WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .delete()
                .changed();
    }

    public int countQuestions(int catalogId) {
        return Query.query("SELECT count(*) AS cnt FROM quiz_question WHERE catalog_id = :catalog_id;")
                .single(Call.of().bind("catalog_id", catalogId))
                .map(row -> row.getInt("cnt"))
                .first()
                .orElse(0);
    }

    public List<QuizCatalog> findTrainingCatalogs(int stationId) {
        return Query.query(
                        "SELECT * FROM quiz_catalog WHERE station_id = :station_id AND training_enabled = true ORDER BY name;")
                .single(Call.of().bind("station_id", stationId))
                .map(QuizCatalog.map())
                .all();
    }
}
