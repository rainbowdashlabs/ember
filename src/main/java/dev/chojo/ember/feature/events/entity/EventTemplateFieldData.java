/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import dev.chojo.ember.feature.question.FieldType;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * One question of an event template, as the editor sends it to be stored.
 *
 * @param defaultValue what an appointment made from this template starts the question off with, or
 *                     null where it starts empty
 */
public record EventTemplateFieldData(
        String name,
        @Nullable FieldType fieldType,
        @Nullable EventQuestionSettings config,
        int position,
        boolean overview,
        boolean isPublic,
        @Nullable Integer attendanceFieldId,
        @Nullable String defaultValue) {

    /** The question as the server stores it; no type reads as a line of text, no settings as none. */
    public AppointmentTemplateFieldDraft toDraft() {
        return new AppointmentTemplateFieldDraft(
                name,
                Objects.requireNonNullElse(fieldType, FieldType.TEXT),
                Objects.requireNonNullElse(config, EventQuestionSettings.empty())
                        .organisers(),
                position,
                overview,
                isPublic,
                attendanceFieldId,
                defaultValue);
    }
}
