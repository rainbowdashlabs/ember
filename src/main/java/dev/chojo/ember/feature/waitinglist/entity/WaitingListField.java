/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.waitinglist.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import dev.chojo.ember.feature.question.FieldType;
import dev.chojo.ember.feature.question.Question;

public record WaitingListField(
        int id,
        int listId,
        String name,
        FieldType fieldType,
        WaitingListFieldConfig config,
        int position,
        boolean required,
        boolean isPublic) {

    /**
     * This field as everything that checks a question reads it.
     *
     * <p>A waiting list has no starting value to give, so the question it asks has none either.
     */
    public Question question() {
        return config.settings()
                .withRequired(required)
                .asQuestion(name, fieldType)
                .orElseThrow(
                        () -> new IllegalStateException("Every waiting list question holds a value: " + fieldType));
    }

    public static RowMapping<WaitingListField> map() {
        return row -> new WaitingListField(
                row.getInt("id"),
                row.getInt("list_id"),
                row.getString("name"),
                FieldType.valueOf(row.getString("field_type")),
                WaitingListFieldConfig.parse(row.getString("config")),
                row.getInt("position"),
                row.getBoolean("required"),
                row.getBoolean("public"));
    }
}
