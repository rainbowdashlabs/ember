/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.entity;

import dev.chojo.ember.feature.question.QuestionConfigs;
import dev.chojo.ember.feature.question.QuestionSettings;

import java.util.List;

/**
 * What a profile field's question is made of, parsed from JSON.
 *
 * <p>Only the meaning lives here. How the question is put to one audience, which is its place on the
 * form, its width, whether that audience may write it and whether that audience must answer, belongs
 * to the assignment instead: the same question is asked of several, and they do not have to agree
 * about any of those.
 *
 * @param description    a sentence saying what the question is after, shown under its label. A
 *                       field name has to be short enough for a table column, which leaves no room
 *                       to say what counts as an answer, and the person filling it in is the one
 *                       who needs that said.
 * @param notifyOnChange whether changes to this field require manager acknowledgement
 * @param overview       whether the field is shown in the member overview table
 * @param options        the list of allowed values for ENUM fields
 * @param defaultValue   the default value for new members
 * @param computed       whether this field is computed from another field
 * @param sourceField    the name of the question a computed field counts from, which is what was
 *                       written down before questions could be pointed at, and is still what an
 *                       older field carries
 * @param sourceFieldId  the question a computed field counts from. Preferred over the name, because
 *                       renaming a question used to leave the age counting from nothing
 * @param ageMode        the age calculation mode (e.g. for AGE-type fields)
 */
public record ProfileFieldConfig(
        String description,
        boolean notifyOnChange,
        boolean overview,
        List<String> options,
        Object defaultValue,
        boolean computed,
        String sourceField,
        Integer sourceFieldId,
        String ageMode) {
    private static final ProfileFieldConfig EMPTY =
            new ProfileFieldConfig(null, false, false, null, null, false, null, null, null);

    /**
     * The settings of a field that names none.
     */
    public static ProfileFieldConfig empty() {
        return EMPTY;
    }

    /**
     * Parses a JSON string into a {@link ProfileFieldConfig}, returning a default empty config on failure.
     *
     * @param json the JSON configuration string, may be null or blank
     * @return the parsed config or a default empty config
     */
    public static ProfileFieldConfig parse(String json) {
        return QuestionConfigs.parse(json, ProfileFieldConfig.class, EMPTY);
    }

    public String toJson() {
        return QuestionConfigs.toJson(this);
    }

    /**
     * What this field says about the question it asks, as everything that measures one reads it.
     *
     * @param required whether an answer is expected, which the field carries rather than its config
     */
    public QuestionSettings settings(boolean required) {
        return QuestionSettings.required(required).withDefault(defaultValue).withOptions(options);
    }
}
