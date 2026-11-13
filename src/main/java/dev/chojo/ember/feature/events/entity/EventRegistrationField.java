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

/**
 * A question an event, or an event group for all its parts, asks of everyone registering for it. Unlike
 * {@link AppointmentField}, which describes the event and holds one value, this holds one answer per
 * registration.
 *
 * @param eventId  the appointment that asks it, or null where an event group does
 * @param eventGroupId the event group that asks it, or null where an appointment does
 */
public record EventRegistrationField(
        int id,
        @Nullable Integer eventId,
        @Nullable Integer eventGroupId,
        String name,
        FieldType fieldType,
        EventQuestionSettings config,
        int position,
        boolean overview) {

    /**
     * This question as everything that checks one reads it.
     *
     * <p>The row stays what it is; what it means is said once, here, so the same rules measure an
     * answer to it as measure an answer to a profile field or a waiting list.
     */
    public Question question() {
        return config.asQuestion(name, fieldType);
    }

    public static RowMapping<EventRegistrationField> map() {
        return row -> new EventRegistrationField(
                row.getInt("id"),
                (Integer) row.getObject("event_id"),
                (Integer) row.getObject("group_id"),
                row.getString("name"),
                row.getEnum("field_type", FieldType.class),
                EventQuestionSettings.parse(row.getString("config")),
                row.getInt("position"),
                row.getBoolean("overview"));
    }
}
