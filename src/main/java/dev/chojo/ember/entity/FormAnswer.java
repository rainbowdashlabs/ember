/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

public record FormAnswer(int id, int responseId, int questionId, String value) {
    public static RowMapping<FormAnswer> map() {
        return row -> new FormAnswer(
                row.getInt("id"), row.getInt("response_id"), row.getInt("question_id"), row.getString("value"));
    }
}
