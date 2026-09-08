/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.question.QuestionConfigs;
import dev.chojo.ember.feature.question.QuestionSettings;

import java.util.List;

/**
 * Configuration for a registration question, stored as JSONB.
 *
 * <p>The names match {@code AttendanceFieldConfig} so the frontend reads every custom field
 * configuration the same way.
 *
 * @param required     whether a registration is refused without an answer
 * @param defaultValue value the form starts with; it becomes the answer only once submitted
 * @param options      selectable values for {@code ENUM} questions
 * @param min          smallest accepted value for {@code NUMBER} questions
 * @param max          largest accepted value for {@code NUMBER} questions
 * @param groupId      referenced member group for {@code *_OF_GROUP} questions
 * @param userType     referenced user type for {@code *_OF_TYPE} questions
 * @param tagId        referenced user tag for {@code *_OF_TAG} questions
 * @param managersOnly whether the question belongs to whoever runs the event: it is neither asked
 *                     of nor answered by the member registering, and its answers never leave the
 *                     server for anyone without the event edit right
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EventRegistrationFieldConfig(
        boolean required,
        String defaultValue,
        List<String> options,
        Integer min,
        Integer max,
        Integer groupId,
        StationUserType userType,
        Integer tagId,
        boolean managersOnly) {
    private static final EventRegistrationFieldConfig EMPTY =
            new EventRegistrationFieldConfig(false, null, null, null, null, null, null, null, false);

    public static EventRegistrationFieldConfig empty() {
        return EMPTY;
    }

    public static EventRegistrationFieldConfig parse(String json) {
        return QuestionConfigs.parse(json, EventRegistrationFieldConfig.class, EMPTY);
    }

    public String toJson() {
        return QuestionConfigs.toJson(this);
    }

    /** What this question says about itself, as everything that measures an answer reads it. */
    public QuestionSettings settings() {
        return QuestionSettings.required(required)
                .withDefault(defaultValue)
                .withOptions(options)
                .withBounds(min, max);
    }
}
