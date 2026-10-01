/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.chojo.ember.api.auth.StationUserType;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * The settings of a registration question as screens read and write them.
 *
 * <p>The server keeps them as {@link EventQuestionSettings}, beside those of the organiser's questions.
 *
 * @param required     whether a registration is refused without an answer
 * @param defaultValue value the form starts with; it becomes the answer only once submitted
 * @param options      selectable values for a choice
 * @param min          smallest accepted value for a number
 * @param max          largest accepted value for a number
 * @param groupId      referenced member group for {@code *_OF_GROUP} questions
 * @param userType     referenced user type for {@code *_OF_TYPE} questions
 * @param tagId        referenced user tag for {@code *_OF_TAG} questions
 * @param managersOnly whether the answer is private to whoever runs the event: the member registering
 *                     is still asked and must answer it when it is required, but only they, their
 *                     household and holders of the event edit right ever read the answer
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EventRegistrationFieldConfig(
        boolean required,
        @Nullable String defaultValue,
        @Nullable List<String> options,
        @Nullable Integer min,
        @Nullable Integer max,
        @Nullable Integer groupId,
        @Nullable StationUserType userType,
        @Nullable Integer tagId,
        boolean managersOnly) {
    private static final EventRegistrationFieldConfig EMPTY =
            new EventRegistrationFieldConfig(false, null, null, null, null, null, null, null, false);

    public static EventRegistrationFieldConfig empty() {
        return EMPTY;
    }

    /** The shape screens read a question's settings in. */
    public static EventRegistrationFieldConfig of(EventQuestionSettings settings) {
        return new EventRegistrationFieldConfig(
                settings.required(),
                settings.defaultValue(),
                settings.options(),
                settings.min(),
                settings.max(),
                settings.groupId(),
                settings.userType(),
                settings.tagId(),
                settings.managersOnly());
    }

    /** These settings as the server keeps them. */
    public EventQuestionSettings settings() {
        return new EventQuestionSettings(
                options, groupId, userType, tagId, null, false, false, required, defaultValue, min, max, managersOnly);
    }
}
