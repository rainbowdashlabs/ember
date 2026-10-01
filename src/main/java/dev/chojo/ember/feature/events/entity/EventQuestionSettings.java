/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.question.FieldType;
import dev.chojo.ember.feature.question.Question;
import dev.chojo.ember.feature.question.QuestionConfigs;
import dev.chojo.ember.feature.question.QuestionSettings;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * The settings of a question an appointment asks, whether the organiser answers it once for the
 * appointment or every registrant answers it for themselves.
 *
 * <p>Both kinds of question, and the templates of both, keep their settings in a JSON column under
 * the same names, so one record reads all four columns. Each kind uses the part it owns and leaves
 * the rest empty: the organiser's question has a width, may let members put themselves in and may
 * be answered per date; the registrant's question may be required, start from a default, bound a
 * number and keep its answers for the organisers.
 *
 * @param options          the answers a choice offers
 * @param groupId          the group a member question is narrowed to
 * @param userType         the user type a member question is narrowed to
 * @param tagId            the tag a member question is narrowed to
 * @param width            how much of a row the question takes when the form is drawn
 * @param selfRegistration whether members may put themselves into a member question without the
 *                         right to edit the appointment
 * @param perDate          whether the question carries one answer per date of the appointment rather
 *                         than one for the whole series
 * @param required         whether a registration is refused without an answer
 * @param defaultValue     what the registration form starts from; it becomes the answer only once
 *                         submitted
 * @param min              the smallest number a registrant may answer
 * @param max              the largest number a registrant may answer
 * @param managersOnly     whether the answer is kept for whoever runs the appointment: the registrant
 *                         is still asked, but only they, their household and the organisers read it
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EventQuestionSettings(
        @Nullable List<String> options,
        @Nullable Integer groupId,
        @Nullable StationUserType userType,
        @Nullable Integer tagId,
        @Nullable String width,
        boolean selfRegistration,
        boolean perDate,
        boolean required,
        @Nullable String defaultValue,
        @Nullable Integer min,
        @Nullable Integer max,
        boolean managersOnly) {
    private static final EventQuestionSettings EMPTY =
            new EventQuestionSettings(null, null, null, null, null, false, false, false, null, null, null, false);

    /** The settings of a question that carries none. */
    public static EventQuestionSettings empty() {
        return EMPTY;
    }

    /** The settings a column holds, or the empty ones where it holds nothing readable. */
    public static EventQuestionSettings parse(String json) {
        return QuestionConfigs.parse(json, EventQuestionSettings.class, EMPTY);
    }

    /** The settings as the column stores them. */
    public String toJson() {
        return QuestionConfigs.toJson(this);
    }

    /** What this question says about the answer it takes, as the one check reads it. */
    public QuestionSettings settings() {
        return QuestionSettings.required(required)
                .withDefault(defaultValue)
                .withOptions(options)
                .withBounds(min, max)
                .withWidth(width)
                .withMembers(groupId, userType, tagId);
    }

    /**
     * The question a field of this type asks under these settings.
     *
     * <p>Every type an appointment offers holds an answer, which is what the stored type names are
     * held to; a type that holds none is refused rather than checked as something it is not.
     *
     * @param name what the question is called, which is what a refusal names
     * @param type the answer it takes
     * @throws IllegalArgumentException for a type that holds no answer
     */
    public Question asQuestion(String name, FieldType type) {
        return settings()
                .asQuestion(name, type)
                .orElseThrow(() -> new IllegalArgumentException(type + " holds no answer"));
    }

    /** The same settings with another starting value, which a template keeps beside them. */
    public EventQuestionSettings withDefault(@Nullable String value) {
        return new EventQuestionSettings(
                options,
                groupId,
                userType,
                tagId,
                width,
                selfRegistration,
                perDate,
                required,
                value,
                min,
                max,
                managersOnly);
    }
}
