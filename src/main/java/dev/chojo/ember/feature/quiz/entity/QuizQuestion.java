/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.quiz.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.chojo.sadu.mapper.rowmapper.RowMapping;
import dev.chojo.ember.util.Json;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

import java.time.Instant;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

public record QuizQuestion(
        int id,
        int catalogId,
        @Nullable Integer categoryId,
        QuizQuestionType quizQuestionType,
        String title,
        String description,
        @Nullable String imageUrl,
        double points,
        boolean autoPoints,
        QuestionConfig config,
        int position,
        Instant createdAt,
        Instant updatedAt)
        implements QuizQuestionRead {

    /**
     * Reads a question as another instance serves it. The config's shape follows from the question
     * type beside it, which no record component can express on its own, so the config arrives as a
     * node and is bound once the type is known.
     */
    @JsonCreator
    public static QuizQuestion fromJson(
            @JsonProperty("id") int id,
            @JsonProperty("catalogId") int catalogId,
            @JsonProperty("categoryId") Integer categoryId,
            @JsonProperty("quizQuestionType") QuizQuestionType quizQuestionType,
            @JsonProperty("title") String title,
            @JsonProperty("description") String description,
            @JsonProperty("imageUrl") String imageUrl,
            @JsonProperty("points") double points,
            @JsonProperty("autoPoints") boolean autoPoints,
            @JsonProperty("config") JsonNode config,
            @JsonProperty("position") int position,
            @JsonProperty("createdAt") Instant createdAt,
            @JsonProperty("updatedAt") Instant updatedAt) {
        var parsed = quizQuestionType == null || config == null
                ? new QuestionConfig.Unknown()
                : quizQuestionType.parseConfig(config.toString());
        return new QuizQuestion(
                id,
                catalogId,
                categoryId,
                quizQuestionType,
                title,
                description,
                imageUrl,
                points,
                autoPoints,
                parsed,
                position,
                createdAt,
                updatedAt);
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

    /**
     * Returns the config as a JsonNode (for legacy code that still uses raw JSON).
     */
    public JsonNode configNode() {
        try {
            return Json.MAPPER.valueToTree(config);
        } catch (Exception e) {
            return Json.MAPPER.createObjectNode();
        }
    }
}
