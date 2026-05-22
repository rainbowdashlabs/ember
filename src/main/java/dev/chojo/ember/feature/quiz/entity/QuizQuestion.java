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
        QuestionType questionType,
        String title,
        String description,
        String imageUrl,
        int points,
        boolean autoPoints,
        JsonNode config,
        int position,
        Instant createdAt,
        Instant updatedAt) {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    public String configString() {
        return config != null ? config.toString() : "{}";
    }

    public static RowMapping<QuizQuestion> map() {
        return row -> {
            JsonNode cfg;
            try {
                String raw = row.getString("config");
                cfg = raw != null ? MAPPER.readTree(raw) : MAPPER.createObjectNode();
            } catch (Exception e) {
                cfg = MAPPER.createObjectNode();
            }
            return new QuizQuestion(
                    row.getInt("id"),
                    row.getInt("catalog_id"),
                    row.getObject("category_id", Integer.class),
                    row.getEnum("question_type", QuestionType.class),
                    row.getString("title"),
                    row.getString("description"),
                    row.getString("image_url"),
                    row.getInt("points"),
                    row.getBoolean("auto_points"),
                    cfg,
                    row.getInt("position"),
                    row.get("created_at", INSTANT_TIMESTAMP),
                    row.get("updated_at", INSTANT_TIMESTAMP));
        };
    }
}
