/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.quiz.entity;

/**
 * Everything needed to create a quiz question. Replaces the ten-positional-argument
 * call shape that made bare {@code true}/{@code null} arguments unreadable at the call
 * site. Build instances through {@link #builder(int, QuizQuestionType, String)}; the
 * optional parts carry the same defaults the API used to apply by hand.
 *
 * @param catalogId    the owning catalog
 * @param categoryId   the category the question belongs to, or {@code null}
 * @param questionType the question type, which also decides the config shape
 * @param title        the question text
 * @param description  supplementary text, never {@code null}
 * @param imageUrl     an illustrating image, or {@code null}
 * @param points       the manual point value, used when {@code autoPoints} is off
 * @param autoPoints   whether the point value is derived from the config
 * @param config       the typed question config, never {@code null}
 * @param position     the sort position inside the catalog
 */
public record CreateQuestionCommand(
        int catalogId,
        Integer categoryId,
        QuizQuestionType questionType,
        String title,
        String description,
        String imageUrl,
        double points,
        boolean autoPoints,
        QuestionConfig config,
        int position) {

    public CreateQuestionCommand {
        description = description != null ? description : "";
        config = config != null ? config : new QuestionConfig.Unknown();
    }

    /**
     * Starts a command for the three parts every question needs. Everything else keeps
     * its default until set: no category, empty description, no image, one point,
     * automatic point calculation, unknown config, position zero.
     */
    public static Builder builder(int catalogId, QuizQuestionType questionType, String title) {
        return new Builder(catalogId, questionType, title);
    }

    /**
     * Fluent assembler for {@link CreateQuestionCommand}. Every setter tolerates
     * {@code null} and falls back to the default, so callers can forward optional
     * request fields without pre-checking them.
     */
    public static final class Builder {
        private final int catalogId;
        private final QuizQuestionType questionType;
        private final String title;
        private Integer categoryId;
        private String description = "";
        private String imageUrl;
        private double points = 1;
        private boolean autoPoints = true;
        private QuestionConfig config = new QuestionConfig.Unknown();
        private int position;

        private Builder(int catalogId, QuizQuestionType questionType, String title) {
            this.catalogId = catalogId;
            this.questionType = questionType;
            this.title = title;
        }

        public Builder category(Integer categoryId) {
            this.categoryId = categoryId;
            return this;
        }

        public Builder description(String description) {
            this.description = description != null ? description : "";
            return this;
        }

        public Builder imageUrl(String imageUrl) {
            this.imageUrl = imageUrl;
            return this;
        }

        public Builder points(Double points) {
            this.points = points != null ? points : 1;
            return this;
        }

        public Builder autoPoints(Boolean autoPoints) {
            this.autoPoints = autoPoints == null || autoPoints;
            return this;
        }

        public Builder config(QuestionConfig config) {
            this.config = config != null ? config : new QuestionConfig.Unknown();
            return this;
        }

        /**
         * Sets the config from a raw JSON payload, parsed against the question type.
         * Convenience for the import and API paths that receive the config as text.
         */
        public Builder configJson(String configJson) {
            return config(questionType.parseConfig(configJson != null ? configJson : "{}"));
        }

        public Builder position(Integer position) {
            this.position = position != null ? position : 0;
            return this;
        }

        public CreateQuestionCommand build() {
            return new CreateQuestionCommand(
                    catalogId,
                    categoryId,
                    questionType,
                    title,
                    description,
                    imageUrl,
                    points,
                    autoPoints,
                    config,
                    position);
        }
    }
}
