/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.quiz.entity;

import tools.jackson.databind.JsonNode;

import java.util.List;

/**
 * The file shape a question catalog is exported to and imported from.
 *
 * <p>Categories are addressed by a key the file itself defines rather than by a database id. A
 * file travels between stations, and an id from the station that wrote it names nothing at the
 * station that reads it.
 *
 * <p>The question type and the config arrive as raw text and JSON rather than as parsed values.
 * A file written by hand gets both wrong regularly, and reading them here rather than in Jackson
 * is what lets the import answer with every problem it found instead of the first one.
 *
 * @param formatVersion which shape the file was written in
 * @param catalog       the catalog it describes
 * @param categories    the categories its questions refer to
 * @param questions     the questions, created in the order they appear
 */
public record CatalogTransfer(
        int formatVersion, CatalogInfo catalog, List<CategoryEntry> categories, List<QuestionEntry> questions) {

    /** The shape written by this version. */
    public static final int FORMAT_VERSION = 1;

    public CatalogTransfer {
        categories = categories != null ? List.copyOf(categories) : List.of();
        questions = questions != null ? List.copyOf(questions) : List.of();
    }

    /**
     * @param name            the catalog name, the one part a file cannot leave out
     * @param description     what the catalog is for
     * @param trainingEnabled whether members may train against it
     * @param metadata        where the questions came from
     */
    public record CatalogInfo(String name, String description, boolean trainingEnabled, CatalogMetadata metadata) {

        public CatalogInfo {
            description = description != null ? description : "";
            metadata = CatalogMetadata.orNone(metadata);
        }
    }

    /**
     * @param key         how the questions in this file refer to the category
     * @param name        the category name, matched against the station's own categories on import
     * @param description what the category covers, used only when the category has to be created
     * @param position    where it sorts, used only when the category has to be created
     */
    public record CategoryEntry(String key, String name, String description, int position) {

        public CategoryEntry {
            description = description != null ? description : "";
        }
    }

    /**
     * @param categoryKey      the {@link CategoryEntry#key()} this question belongs to, or absent
     * @param quizQuestionType the {@link QuizQuestionType} name, resolved on import
     * @param title            the question text
     * @param description      supplementary text
     * @param imageUrl         an illustrating image already reachable by URL
     * @param points           the manual point value, one when absent
     * @param autoPoints       whether points are derived from the config, on when absent
     * @param config           the config of the question's type
     * @param position         where it sorts inside the catalog, its index in the file when absent
     */
    public record QuestionEntry(
            String categoryKey,
            String quizQuestionType,
            String title,
            String description,
            String imageUrl,
            Double points,
            Boolean autoPoints,
            JsonNode config,
            Integer position) {}
}
