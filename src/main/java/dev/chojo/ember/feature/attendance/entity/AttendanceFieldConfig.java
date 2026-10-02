/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.attendance.entity;

import dev.chojo.ember.feature.question.QuestionConfigs;
import dev.chojo.ember.feature.question.QuestionSettings;
import dev.chojo.ember.feature.question.QuestionValues;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.util.List;

/**
 * Configuration for an attendance template field, parsed from JSONB storage.
 *
 * @param required     whether the field must be filled in
 * @param groupId      optional member group restriction for this field
 * @param autoAttend   whether members referenced in this field are automatically marked as present
 * @param options      selectable options for choice-type fields
 * @param defaultValue default value to pre-populate when creating a session
 * @param width        how much of a row the field takes when the sheet is drawn, which is the
 *                     station's own layout choice and means nothing to the server
 */
public record AttendanceFieldConfig(
        boolean required,
        @Nullable Integer groupId,
        boolean autoAttend,
        @Nullable List<String> options,
        @Nullable Object defaultValue,
        @Nullable String width) {
    private static final AttendanceFieldConfig EMPTY = new AttendanceFieldConfig(false, null, false, null, null, null);
    private static final String TODAY = "__TODAY__";

    /**
     * Parses a JSON string into an {@link AttendanceFieldConfig}, returning an empty default on failure.
     *
     * @param json the JSON string to parse, may be {@code null} or blank
     * @return the parsed config or an empty default
     */
    public static AttendanceFieldConfig parse(String json) {
        return QuestionConfigs.parse(json, AttendanceFieldConfig.class, EMPTY);
    }

    public String toJson() {
        return QuestionConfigs.toJson(this);
    }

    /**
     * What this field says about the question it asks, as everything that measures one reads it.
     *
     * <p>The starting value is the one a sheet made now would start at, so a date field that starts
     * at today is measured as the date it will hold rather than as the word that stands for it.
     */
    public QuestionSettings settings() {
        return QuestionSettings.required(required)
                .withDefault(startingAnswer())
                .withOptions(options)
                .withWidth(width)
                .withMembers(groupId, null, null);
    }

    /**
     * What a sheet made now starts this field at, as plain text.
     *
     * @return the starting answer, today's date for a date field that starts at today, or null where
     *     the field starts empty
     */
    public @Nullable String startingAnswer() {
        if (defaultValue == null) return null;
        if (TODAY.equals(defaultValue)) return LocalDate.now().toString();
        return QuestionValues.read(QuestionConfigs.toJson(defaultValue));
    }
}
