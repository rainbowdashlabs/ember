/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import dev.chojo.ember.feature.question.FieldType;
import dev.chojo.ember.feature.question.Question;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * A question the organiser answers once for an appointment, or once per date of it, together with
 * the answer it carries.
 *
 * <p>What the server and its own screens work with. Partner stations are handed an {@link EventField},
 * which keeps the names they have always read.
 *
 * @param value             the answer, in the one shape its type is stored in
 * @param attendanceFieldId the field of the attendance sheet the answer fills in, or null
 * @param isPublic          whether readers outside the station and partner stations see it
 */
public record AppointmentField(
        int id,
        int eventId,
        String name,
        FieldType fieldType,
        EventQuestionSettings config,
        String value,
        int position,
        boolean overview,
        @Nullable Integer attendanceFieldId,
        boolean isPublic) {

    /**
     * Where an appointment takes place: the first location question it answers.
     *
     * @param fields the questions of the appointment, in their order
     * @return the place, trimmed, or null where no location question is answered
     */
    public static @Nullable String firstLocation(List<AppointmentField> fields) {
        for (var field : fields) {
            if (field.fieldType() == FieldType.LOCATION
                    && field.value() != null
                    && !field.value().isBlank()) {
                return field.value().trim();
            }
        }
        return null;
    }

    /** The question this field asks, as the one check measures an answer against it. */
    public Question question() {
        return config.asQuestion(name, fieldType);
    }

    public static RowMapping<AppointmentField> map() {
        return row -> new AppointmentField(
                row.getInt("id"),
                row.getInt("event_id"),
                row.getString("name"),
                row.getEnum("field_type", FieldType.class),
                EventQuestionSettings.parse(row.getString("config")),
                row.getString("value"),
                row.getInt("position"),
                row.getBoolean("overview"),
                row.getObject("attendance_field_id", Integer.class),
                row.getBoolean("public"));
    }
}
