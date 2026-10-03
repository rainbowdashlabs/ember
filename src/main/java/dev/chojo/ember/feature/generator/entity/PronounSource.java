/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.entity;

import dev.chojo.ember.feature.question.QuestionConfigs;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Objects;

/**
 * The choice profile field a template takes its pronouns from, and what each answer means.
 *
 * <p>The field is the station's own wording ("Geschlecht", "Anrede"), so its answers are mapped once
 * per template rather than guessed: "männlich" to {@link PronounForm#ER}, "weiblich" to
 * {@link PronounForm#SIE}, and "divers" to {@link PronounForm#NAME}, say. An empty field, or an answer
 * the mapping does not name, takes the fallback.
 *
 * <p>The field is kept in a column of its own and the mapping beside it, so a deleted field takes the
 * source with it and a station moved to another instance keeps pointing at its own field.
 *
 * @param fieldId  the choice profile field
 * @param answers  the form each answer stands for, keyed by the answer as it is stored
 * @param fallback the form for an empty or unmapped answer
 */
public record PronounSource(int fieldId, Map<String, PronounForm> answers, PronounForm fallback) {
    private static final Mapping NO_MAPPING = new Mapping(Map.of(), PronounForm.NAME);

    public PronounSource {
        answers = Map.copyOf(answers == null ? Map.of() : answers);
        fallback = fallback == null ? PronounForm.NAME : fallback;
    }

    /**
     * The form for one stored answer.
     *
     * @param answer the answer of the field, or null where there is none
     * @return the form it stands for, or the fallback
     */
    public PronounForm formOf(@Nullable String answer) {
        if (answer == null || answer.isBlank()) return fallback;
        return answers.getOrDefault(answer, fallback);
    }

    /**
     * Reads the two stored columns.
     *
     * @param fieldId the field column, or null where the template names none
     * @param mapping the mapping column
     * @return the source, or null where the template names no field
     */
    public static @Nullable PronounSource of(@Nullable Integer fieldId, @Nullable String mapping) {
        if (fieldId == null) return null;
        var read = QuestionConfigs.parse(Objects.requireNonNullElse(mapping, ""), Mapping.class, NO_MAPPING);
        return new PronounSource(fieldId, read.answers(), read.fallback());
    }

    /**
     * @return the mapping column as it is stored
     */
    public String mappingJson() {
        return QuestionConfigs.toJson(new Mapping(answers, fallback));
    }

    /**
     * The part of the source kept as JSON beside the field.
     *
     * @param answers  the form each answer stands for
     * @param fallback the form for an empty or unmapped answer
     */
    record Mapping(Map<String, PronounForm> answers, PronounForm fallback) {}
}
