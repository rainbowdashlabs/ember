/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.waitinglist.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import dev.chojo.ember.feature.question.Question;

public record WaitingListField(
        int id,
        int listId,
        String name,
        WaitingListFieldType fieldType,
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
        return config.settings().withRequired(required).asQuestion(name, fieldType.kind());
    }

    public static RowMapping<WaitingListField> map() {
        return row -> new WaitingListField(
                row.getInt("id"),
                row.getInt("list_id"),
                row.getString("name"),
                row.getEnum("field_type", WaitingListFieldType.class),
                WaitingListFieldConfig.parse(row.getString("config")),
                row.getInt("position"),
                row.getBoolean("required"),
                row.getBoolean("public"));
    }
}
