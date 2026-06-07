/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.quiz.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

public record QuizQuestion(
        int id,
        int catalogId,
        Integer categoryId,
        QuizQuestionType quizQuestionType,
        String title,
        String description,
        String imageUrl,
        double points,
        boolean autoPoints,
        QuestionConfig config,
        int position,
        Instant createdAt,
        Instant updatedAt) {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    /**
     * Returns the config as a JsonNode (for legacy code that still uses raw JSON).
     */
    public JsonNode configNode() {
        try {
            return MAPPER.valueToTree(config);
        } catch (Exception e) {
            return MAPPER.createObjectNode();
        }
    }

    public static RowMapping<QuizQuestion> map() {
        return row -> {
            var type = row.getEnum("question_type", QuizQuestionType.class);
            String raw = row.getString("config");
            var config = type.parseConfig(raw != null ? raw : "{}");
            return new QuizQuestion(
                    row.getInt("id"),
                    row.getInt("catalog_id"),
                    row.getObject("category_id", Integer.class),
                    type,
                    row.getString("title"),
                    row.getString("description"),
                    row.getString("image_url"),
                    row.getDouble("points"),
                    row.getBoolean("auto_points"),
                    config,
                    row.getInt("position"),
                    row.get("created_at", INSTANT_TIMESTAMP),
                    row.get("updated_at", INSTANT_TIMESTAMP));
        };
    }
}
