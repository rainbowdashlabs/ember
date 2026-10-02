/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import dev.chojo.ember.feature.question.FieldType;
import dev.chojo.ember.feature.question.Question;
import org.jspecify.annotations.Nullable;

/**
 * One question of an appointment template as it arrives to be stored.
 *
 * @param defaultValue what an appointment made from this template starts the question off with, or
 *                     null where it starts empty
 */
public record AppointmentTemplateFieldDraft(
        String name,
        FieldType fieldType,
        EventQuestionSettings config,
        int position,
        boolean overview,
        boolean isPublic,
        @Nullable Integer attendanceFieldId,
        @Nullable String defaultValue) {

    /** The question this field asks, starting from its default, which is how the default is checked. */
    public Question question() {
        return config.withDefault(defaultValue).asQuestion(name, fieldType);
    }

    /** The same field without its tie to a field of an attendance sheet. */
    public AppointmentTemplateFieldDraft untied() {
        return new AppointmentTemplateFieldDraft(
                name, fieldType, config, position, overview, isPublic, null, defaultValue);
    }
}
