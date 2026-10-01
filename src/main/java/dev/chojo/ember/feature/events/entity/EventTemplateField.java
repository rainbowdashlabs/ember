/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import org.jspecify.annotations.Nullable;

/**
 * One question an event template asks, as screens read it.
 *
 * @param defaultValue what an appointment made from this template starts the question off with, or
 *                     null where it starts empty. Kept apart from the answer the appointment ends up
 *                     with, so changing the template leaves appointments already written alone
 */
public record EventTemplateField(
        int id,
        int templateId,
        String name,
        EventFieldType fieldType,
        EventFieldConfig config,
        int position,
        boolean overview,
        boolean isPublic,
        @Nullable Integer attendanceFieldId,
        @Nullable String defaultValue) {

    /** The shape screens read a template's question in. */
    public static EventTemplateField of(AppointmentTemplateField field) {
        return new EventTemplateField(
                field.id(),
                field.templateId(),
                field.name(),
                EventFieldType.of(field.fieldType()),
                EventFieldConfig.of(field.config()),
                field.position(),
                field.overview(),
                field.isPublic(),
                field.attendanceFieldId(),
                field.defaultValue());
    }
}
