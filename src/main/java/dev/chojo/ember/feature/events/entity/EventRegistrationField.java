/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import dev.chojo.ember.feature.question.FieldType;
import dev.chojo.ember.feature.question.Question;

/**
 * A question an event asks of everyone registering for it. Unlike {@link AppointmentField}, which
 * describes the event and holds one value, this holds one answer per registration.
 */
public record EventRegistrationField(
        int id,
        int eventId,
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
                row.getInt("event_id"),
                row.getString("name"),
                FieldType.valueOf(row.getString("field_type")),
                EventQuestionSettings.parse(row.getString("config")),
                row.getInt("position"),
                row.getBoolean("overview"));
    }
}
