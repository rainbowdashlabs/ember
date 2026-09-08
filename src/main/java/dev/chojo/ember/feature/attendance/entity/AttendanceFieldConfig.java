/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.attendance.entity;

import dev.chojo.ember.feature.question.QuestionConfigs;
import dev.chojo.ember.feature.question.QuestionSettings;

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
        Integer groupId,
        boolean autoAttend,
        List<String> options,
        Object defaultValue,
        String width) {
    private static final AttendanceFieldConfig EMPTY = new AttendanceFieldConfig(false, null, false, null, null, null);

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

    /** What this field says about the question it asks, as everything that measures one reads it. */
    public QuestionSettings settings() {
        return QuestionSettings.required(required).withDefault(defaultValue).withOptions(options);
    }

    /**
     * Checks whether this config specifies a default value.
     *
     * @return {@code true} if a default value is set
     */
    public boolean hasDefaultValue() {
        return defaultValue != null;
    }

    /**
     * Returns the default value as a JSON string suitable for JSONB storage.
     * Handles the __TODAY__ sentinel for date fields.
     */
    public String resolveDefaultValueJson() {
        if (defaultValue == null) return null;
        if (defaultValue instanceof String s) {
            if ("__TODAY__".equals(s)) {
                return "\"" + LocalDate.now() + "\"";
            }
        }
        return QuestionConfigs.toJson(defaultValue);
    }
}
