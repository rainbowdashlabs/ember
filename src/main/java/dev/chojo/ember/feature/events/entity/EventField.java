/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import org.jspecify.annotations.Nullable;

/**
 * A question of an appointment and its answer, as screens and partner stations read it.
 *
 * <p>Only what crosses the wire speaks this shape; the server works with {@link AppointmentField}. The
 * components stay because a partner station compares them before it shares an appointment.
 */
public record EventField(
        int id,
        int eventId,
        String name,
        EventFieldType fieldType,
        EventFieldConfig config,
        String value,
        int position,
        boolean overview,
        @Nullable Integer attendanceFieldId,
        boolean isPublic) {

    /** The wire shape of a question of an appointment. */
    public static EventField of(AppointmentField field) {
        return new EventField(
                field.id(),
                field.eventId(),
                field.name(),
                EventFieldType.of(field.fieldType()),
                EventFieldConfig.of(field.config()),
                field.value(),
                field.position(),
                field.overview(),
                field.attendanceFieldId(),
                field.isPublic());
    }
}
